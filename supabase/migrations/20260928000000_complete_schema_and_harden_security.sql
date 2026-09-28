-- =============================================================================
-- FokalPoint: complete the schema the Android app uses and close privilege gaps.
--
-- 1. Columns / tables the app reads & writes that earlier migrations never created
--    (creators.skillset/youtube, messages, blocked_dates).
-- 2. search_creators(): a parameterized replacement for the client-built SQL that
--    the app previously sent to an `execute_sql` RPC (SQL injection).
-- 3. Guard triggers so clients cannot set server-owned fields:
--      creators.rating / creators.verified, bookings.price / payment_status, etc.
-- 4. Payments are written by the server only (service role / payment webhook).
-- =============================================================================

-- ---------------------------------------------------------------- 1. schema gaps
ALTER TABLE public.creators
    ADD COLUMN IF NOT EXISTS skillset TEXT NOT NULL DEFAULT 'Photographer',
    ADD COLUMN IF NOT EXISTS youtube TEXT;

-- New creators start unrated until they receive reviews.
ALTER TABLE public.creators ALTER COLUMN rating SET DEFAULT 0;

CREATE TABLE IF NOT EXISTS public.messages (
    id BIGSERIAL PRIMARY KEY,
    sender_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    receiver_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    message TEXT NOT NULL CHECK (char_length(message) BETWEEN 1 AND 4000),
    media_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (sender_id <> receiver_id)
);
CREATE INDEX IF NOT EXISTS idx_messages_pair ON public.messages (sender_id, receiver_id, created_at);
CREATE INDEX IF NOT EXISTS idx_messages_receiver ON public.messages (receiver_id, created_at);
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Participants read messages" ON public.messages;
CREATE POLICY "Participants read messages" ON public.messages
    FOR SELECT TO authenticated USING (auth.uid() IN (sender_id, receiver_id));

DROP POLICY IF EXISTS "Users send messages as themselves" ON public.messages;
CREATE POLICY "Users send messages as themselves" ON public.messages
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = sender_id);

CREATE TABLE IF NOT EXISTS public.blocked_dates (
    creator_id UUID NOT NULL REFERENCES public.creators(id) ON DELETE CASCADE,
    blocked_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (creator_id, blocked_date)
);
ALTER TABLE public.blocked_dates ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Availability is public" ON public.blocked_dates;
CREATE POLICY "Availability is public" ON public.blocked_dates
    FOR SELECT USING (true);

DROP POLICY IF EXISTS "Creators manage own availability" ON public.blocked_dates;
CREATE POLICY "Creators manage own availability" ON public.blocked_dates
    FOR ALL TO authenticated USING (auth.uid() = creator_id) WITH CHECK (auth.uid() = creator_id);

-- The original "manage own profile" policies had no WITH CHECK clause, so a user could
-- UPDATE their row's id to someone else's. Re-create them with explicit checks.
DROP POLICY IF EXISTS "Users Manage Own Profile" ON public.users;
CREATE POLICY "Users Manage Own Profile" ON public.users
    FOR ALL TO authenticated USING (auth.uid() = id) WITH CHECK (auth.uid() = id);

DROP POLICY IF EXISTS "Creators Manage Own Profile" ON public.creators;
CREATE POLICY "Creators Manage Own Profile" ON public.creators
    FOR ALL TO authenticated USING (auth.uid() = id) WITH CHECK (auth.uid() = id);

DROP POLICY IF EXISTS "Creators Manage Own Portfolio" ON public.portfolios;
CREATE POLICY "Creators Manage Own Portfolio" ON public.portfolios
    FOR ALL TO authenticated USING (auth.uid() = creator_id) WITH CHECK (auth.uid() = creator_id);

-- Shoot alerts: the payout/contact details of customers should only be visible to
-- signed-in users (creators browsing leads), not the anonymous internet.
DROP POLICY IF EXISTS "Anyone can view shoot alerts" ON public.shoot_alerts;
DROP POLICY IF EXISTS "Signed-in users can view shoot alerts" ON public.shoot_alerts;
CREATE POLICY "Signed-in users can view shoot alerts" ON public.shoot_alerts
    FOR SELECT TO authenticated USING (true);

-- ---------------------------------------------------------------- 2. safe search
CREATE OR REPLACE FUNCTION public.search_creators(
    p_query TEXT DEFAULT '',
    p_city TEXT DEFAULT NULL,
    p_event_type TEXT DEFAULT NULL,
    p_lat DOUBLE PRECISION DEFAULT NULL,
    p_lng DOUBLE PRECISION DEFAULT NULL,
    p_radius_km INTEGER DEFAULT 50
)
RETURNS TABLE (
    id UUID, name TEXT, profile_image TEXT, city TEXT, state TEXT, country TEXT,
    creator_type TEXT, experience_level TEXT, bio TEXT, languages TEXT, equipment TEXT,
    rating DOUBLE PRECISION, verified BOOLEAN, starting_price DOUBLE PRECISION,
    instagram TEXT, website TEXT, youtube TEXT, skillset TEXT, years_of_experience INTEGER,
    latitude DOUBLE PRECISION, longitude DOUBLE PRECISION, search_radius INTEGER,
    distance_km DOUBLE PRECISION
)
LANGUAGE sql STABLE SECURITY INVOKER SET search_path = public AS $$
    WITH base AS (
        SELECT c.*, u.name AS u_name, u.profile_image AS u_image,
               u.city AS u_city, u.state AS u_state, u.country AS u_country,
               CASE
                   WHEN p_lat IS NULL OR p_lng IS NULL OR c.latitude IS NULL OR c.longitude IS NULL THEN NULL
                   -- LEAST/GREATEST clamp guards acos() against floating-point drift past ±1.
                   ELSE 6371 * acos(LEAST(1, GREATEST(-1,
                        cos(radians(p_lat)) * cos(radians(c.latitude)) *
                        cos(radians(c.longitude) - radians(p_lng)) +
                        sin(radians(p_lat)) * sin(radians(c.latitude)))))
               END AS dist
        FROM public.creators c
        JOIN public.users u ON u.id = c.id
    )
    SELECT b.id, b.u_name, b.u_image, b.u_city, b.u_state, b.u_country,
           b.creator_type, b.experience_level, b.bio, b.languages::TEXT, b.equipment::TEXT,
           b.rating, b.verified, b.starting_price,
           b.instagram, b.website, b.youtube, b.skillset, b.years_of_experience,
           b.latitude, b.longitude, b.search_radius, b.dist
    FROM base b
    WHERE (p_city IS NULL OR b.u_city ILIKE '%' || p_city || '%')
      AND (p_event_type IS NULL OR b.skillset ILIKE '%' || p_event_type || '%'
                                OR b.creator_type ILIKE '%' || p_event_type || '%')
      AND (coalesce(p_query, '') = ''
           OR b.u_name ILIKE '%' || p_query || '%'
           OR b.bio ILIKE '%' || p_query || '%'
           OR b.skillset ILIKE '%' || p_query || '%'
           OR b.u_city ILIKE '%' || p_query || '%')
      AND (b.dist IS NULL OR b.dist <= p_radius_km)
    ORDER BY b.dist NULLS LAST, b.rating DESC
    LIMIT 100;
$$;
GRANT EXECUTE ON FUNCTION public.search_creators(TEXT, TEXT, TEXT, DOUBLE PRECISION, DOUBLE PRECISION, INTEGER)
    TO anon, authenticated;

-- If an execute_sql helper was ever created for the old client code, remove it.
DROP FUNCTION IF EXISTS public.execute_sql(TEXT);

-- ---------------------------------------------------------------- 3. guard triggers
-- True for the service role (edge functions, webhooks, dashboard) — trusted callers.
CREATE OR REPLACE FUNCTION public.is_service_role()
RETURNS BOOLEAN LANGUAGE sql STABLE AS $$
    SELECT coalesce(auth.role(), '') = 'service_role' OR current_user IN ('postgres', 'supabase_admin');
$$;

-- Ratings and verification badges are earned, not self-assigned.
CREATE OR REPLACE FUNCTION public.guard_creator_fields()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF public.is_service_role() THEN RETURN NEW; END IF;
    IF TG_OP = 'INSERT' THEN
        NEW.rating := 0;
        NEW.verified := FALSE;
    ELSE
        NEW.rating := OLD.rating;
        NEW.verified := OLD.verified;
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS guard_creator_fields ON public.creators;
CREATE TRIGGER guard_creator_fields BEFORE INSERT OR UPDATE ON public.creators
    FOR EACH ROW EXECUTE FUNCTION public.guard_creator_fields();

-- Booking lifecycle rules:
--  * new bookings always start Pending / unpaid;
--  * the commercial terms (who, when, price) cannot be edited after creation;
--  * the creator accepts / confirms / completes; either party may cancel;
--  * only the creator can mark a booking Paid (confirming receipt of a direct UPI
--    payment) — a payment-gateway webhook running as service role can too.
CREATE OR REPLACE FUNCTION public.guard_booking_changes()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    is_creator BOOLEAN := auth.uid() = OLD.creator_id;
BEGIN
    IF public.is_service_role() THEN RETURN NEW; END IF;

    IF TG_OP = 'INSERT' THEN
        IF NEW.customer_id = NEW.creator_id THEN
            RAISE EXCEPTION 'You cannot book yourself';
        END IF;
        NEW.status := 'Pending';
        NEW.payment_status := 'Pending';
        RETURN NEW;
    END IF;

    IF NEW.customer_id <> OLD.customer_id OR NEW.creator_id <> OLD.creator_id
       OR NEW.price <> OLD.price OR NEW.hours <> OLD.hours
       OR NEW.date <> OLD.date OR NEW.time <> OLD.time OR NEW.event_type <> OLD.event_type THEN
        RAISE EXCEPTION 'Booking terms cannot be changed; cancel and rebook instead';
    END IF;

    IF NEW.payment_status <> OLD.payment_status AND NOT is_creator THEN
        RAISE EXCEPTION 'Only the creator can confirm a payment';
    END IF;

    IF NEW.status <> OLD.status THEN
        IF OLD.status IN ('Completed', 'Cancelled') THEN
            RAISE EXCEPTION 'This booking is already %', OLD.status;
        END IF;
        IF NEW.status <> 'Cancelled' AND NOT is_creator THEN
            RAISE EXCEPTION 'Only the creator can move a booking to %', NEW.status;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS guard_booking_changes ON public.bookings;
CREATE TRIGGER guard_booking_changes BEFORE INSERT OR UPDATE ON public.bookings
    FOR EACH ROW EXECUTE FUNCTION public.guard_booking_changes();

-- Blocked dates guard double-booking at the database level too.
CREATE OR REPLACE FUNCTION public.reject_blocked_booking()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.blocked_dates d
               WHERE d.creator_id = NEW.creator_id AND d.blocked_date = NEW.date) THEN
        RAISE EXCEPTION 'The creator is not available on %', NEW.date;
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS reject_blocked_booking ON public.bookings;
CREATE TRIGGER reject_blocked_booking BEFORE INSERT ON public.bookings
    FOR EACH ROW EXECUTE FUNCTION public.reject_blocked_booking();

-- Payout methods start unverified; verification happens out of band.
CREATE OR REPLACE FUNCTION public.guard_payout_method_status()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF public.is_service_role() THEN RETURN NEW; END IF;
    IF TG_OP = 'INSERT' THEN
        NEW.status := 'PENDING_VERIFICATION';
    ELSE
        NEW.status := OLD.status;
    END IF;
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS guard_payout_method_status ON public.payout_methods;
CREATE TRIGGER guard_payout_method_status BEFORE INSERT OR UPDATE ON public.payout_methods
    FOR EACH ROW EXECUTE FUNCTION public.guard_payout_method_status();

-- ---------------------------------------------------------------- 4. payments
-- Customers previously could INSERT payment rows (with any status). Payment records
-- must come from the payment provider's webhook, which runs as the service role.
DROP POLICY IF EXISTS "Customers can insert payments" ON public.payments;

-- Keep the auth signup trigger tolerant of role casing from older clients.
CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    INSERT INTO public.users (id, name, email, role, profile_image)
    VALUES (
        NEW.id,
        COALESCE(NULLIF(NEW.raw_user_meta_data->>'name', ''),
                 NULLIF(NEW.raw_user_meta_data->>'full_name', ''),
                 split_part(NEW.email, '@', 1)),
        NEW.email,
        CASE WHEN lower(NEW.raw_user_meta_data->>'role') = 'creator' THEN 'Creator' ELSE 'Customer' END,
        COALESCE(NEW.raw_user_meta_data->>'avatar_url', NEW.raw_user_meta_data->>'picture', '')
    )
    ON CONFLICT (id) DO NOTHING;
    RETURN NEW;
END;
$$;
