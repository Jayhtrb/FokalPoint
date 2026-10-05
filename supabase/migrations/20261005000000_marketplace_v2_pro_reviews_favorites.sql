-- =============================================================================
-- FokalPoint v2: Creator Pro subscriptions, reviews, favorites, direct UPI
-- payments, portfolio storage, leads gating, account deletion support.
-- Every perk and every money-related field is enforced here, not in the app.
-- =============================================================================

-- ---------------------------------------------------------------- creators
ALTER TABLE public.creators
    ADD COLUMN IF NOT EXISTS headline TEXT,
    ADD COLUMN IF NOT EXISTS cover_image TEXT,
    ADD COLUMN IF NOT EXISTS pro_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS review_count INTEGER NOT NULL DEFAULT 0;

-- Pro is active while pro_until is in the future (renewals extend it).
CREATE OR REPLACE FUNCTION public.creator_is_pro(c public.creators)
RETURNS BOOLEAN LANGUAGE sql STABLE AS $$ SELECT coalesce(c.pro_until > now(), false) $$;

CREATE OR REPLACE FUNCTION public.is_pro(p_creator UUID)
RETURNS BOOLEAN LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
    SELECT coalesce((SELECT pro_until > now() FROM public.creators WHERE id = p_creator), false)
$$;

-- Clients may not grant themselves Pro, ratings, review counts or verification.
CREATE OR REPLACE FUNCTION public.guard_creator_fields()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF public.is_service_role() THEN RETURN NEW; END IF;
    IF TG_OP = 'INSERT' THEN
        NEW.rating := 0;
        NEW.verified := FALSE;
        NEW.pro_until := NULL;
        NEW.review_count := 0;
    ELSE
        NEW.rating := OLD.rating;
        NEW.verified := OLD.verified;
        NEW.pro_until := OLD.pro_until;
        NEW.review_count := OLD.review_count;
    END IF;
    RETURN NEW;
END;
$$;

-- Private payout details: only the creator can read/write; customers get them via
-- get_payment_details() once their booking is accepted.
CREATE TABLE IF NOT EXISTS public.creator_private (
    creator_id UUID PRIMARY KEY REFERENCES public.creators(id) ON DELETE CASCADE,
    upi_id TEXT CHECK (upi_id IS NULL OR upi_id ~ '^[A-Za-z0-9._-]{2,128}@[A-Za-z][A-Za-z0-9.-]{1,64}$'),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE public.creator_private ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Creators manage own private details" ON public.creator_private;
CREATE POLICY "Creators manage own private details" ON public.creator_private
    FOR ALL TO authenticated USING (auth.uid() = creator_id) WITH CHECK (auth.uid() = creator_id);

CREATE OR REPLACE FUNCTION public.get_payment_details(p_booking_id BIGINT)
RETURNS TABLE (upi_id TEXT, payee_name TEXT, amount DOUBLE PRECISION)
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
    SELECT p.upi_id, u.name, b.price
    FROM public.bookings b
    JOIN public.users u ON u.id = b.creator_id
    LEFT JOIN public.creator_private p ON p.creator_id = b.creator_id
    WHERE b.id = p_booking_id
      AND b.customer_id = auth.uid()
      AND b.status IN ('Accepted', 'Confirmed')
$$;
REVOKE ALL ON FUNCTION public.get_payment_details(BIGINT) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_payment_details(BIGINT) TO authenticated;

-- ---------------------------------------------------------------- bookings
ALTER TABLE public.bookings
    ADD COLUMN IF NOT EXISTS package_name TEXT,
    ADD COLUMN IF NOT EXISTS notes TEXT CHECK (notes IS NULL OR char_length(notes) <= 2000);

-- package_name / notes are part of the booking terms: immutable after creation.
CREATE OR REPLACE FUNCTION public.guard_booking_terms_v2()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF public.is_service_role() THEN RETURN NEW; END IF;
    IF NEW.package_name IS DISTINCT FROM OLD.package_name OR NEW.notes IS DISTINCT FROM OLD.notes THEN
        RAISE EXCEPTION 'Booking terms cannot be changed; cancel and rebook instead';
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS guard_booking_terms_v2 ON public.bookings;
CREATE TRIGGER guard_booking_terms_v2 BEFORE UPDATE ON public.bookings
    FOR EACH ROW EXECUTE FUNCTION public.guard_booking_terms_v2();

-- ---------------------------------------------------------------- reviews
CREATE TABLE IF NOT EXISTS public.reviews (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE REFERENCES public.bookings(id) ON DELETE CASCADE,
    creator_id UUID NOT NULL REFERENCES public.creators(id) ON DELETE CASCADE,
    customer_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT CHECK (comment IS NULL OR char_length(comment) <= 2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_reviews_creator ON public.reviews (creator_id, created_at DESC);
ALTER TABLE public.reviews ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Reviews are public" ON public.reviews;
CREATE POLICY "Reviews are public" ON public.reviews FOR SELECT USING (true);
DROP POLICY IF EXISTS "Customers review their completed bookings" ON public.reviews;
CREATE POLICY "Customers review their completed bookings" ON public.reviews
    FOR INSERT TO authenticated WITH CHECK (
        auth.uid() = customer_id AND EXISTS (
            SELECT 1 FROM public.bookings b
            WHERE b.id = booking_id AND b.customer_id = auth.uid()
              AND b.creator_id = reviews.creator_id AND b.status = 'Completed'
        )
    );

-- Ratings are always the real average of reviews.
CREATE OR REPLACE FUNCTION public.refresh_creator_rating()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE target UUID := coalesce(NEW.creator_id, OLD.creator_id);
BEGIN
    UPDATE public.creators c
       SET rating = coalesce((SELECT round(avg(r.rating)::numeric, 2) FROM public.reviews r WHERE r.creator_id = target), 0),
           review_count = (SELECT count(*) FROM public.reviews r WHERE r.creator_id = target)
     WHERE c.id = target;
    RETURN NULL;
END;
$$;
DROP TRIGGER IF EXISTS refresh_creator_rating ON public.reviews;
CREATE TRIGGER refresh_creator_rating AFTER INSERT OR DELETE ON public.reviews
    FOR EACH ROW EXECUTE FUNCTION public.refresh_creator_rating();

-- ---------------------------------------------------------------- favorites
CREATE TABLE IF NOT EXISTS public.favorites (
    customer_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    creator_id UUID NOT NULL REFERENCES public.creators(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (customer_id, creator_id)
);
ALTER TABLE public.favorites ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "Users manage own favorites" ON public.favorites;
CREATE POLICY "Users manage own favorites" ON public.favorites
    FOR ALL TO authenticated USING (auth.uid() = customer_id) WITH CHECK (auth.uid() = customer_id);

-- ---------------------------------------------------------------- portfolio limit
CREATE OR REPLACE FUNCTION public.enforce_portfolio_limit()
RETURNS TRIGGER LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
    IF NOT public.is_pro(NEW.creator_id)
       AND (SELECT count(*) FROM public.portfolios WHERE creator_id = NEW.creator_id) >= 12 THEN
        RAISE EXCEPTION 'Free profiles can show up to 12 photos. Upgrade to Creator Pro for an unlimited portfolio.';
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS enforce_portfolio_limit ON public.portfolios;
CREATE TRIGGER enforce_portfolio_limit BEFORE INSERT ON public.portfolios
    FOR EACH ROW EXECUTE FUNCTION public.enforce_portfolio_limit();

-- ---------------------------------------------------------------- subscriptions
-- Only the verify-play-subscription edge function (service role) writes these.
DROP POLICY IF EXISTS "Users can insert own subscriptions" ON public.subscriptions;
ALTER TABLE public.subscriptions
    ADD COLUMN IF NOT EXISTS purchase_token TEXT UNIQUE,
    ADD COLUMN IF NOT EXISTS product_id TEXT,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- ---------------------------------------------------------------- leads gating
-- Creators read leads through list_shoot_alerts(): 3 newest for free, all for Pro.
DROP POLICY IF EXISTS "Signed-in users can view shoot alerts" ON public.shoot_alerts;
DROP POLICY IF EXISTS "Customers view own shoot alerts" ON public.shoot_alerts;
CREATE POLICY "Customers view own shoot alerts" ON public.shoot_alerts
    FOR SELECT TO authenticated USING (auth.uid() = customer_id);

CREATE OR REPLACE FUNCTION public.list_shoot_alerts()
RETURNS TABLE (
    id UUID, customer_id UUID, customer_name TEXT, event_type TEXT, location TEXT, city_id TEXT,
    budget NUMERIC, timeframe TEXT, description TEXT, additional_details TEXT,
    created_at TIMESTAMPTZ, total_available BIGINT, is_pro BOOLEAN
)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
DECLARE
    pro BOOLEAN := public.is_pro(auth.uid());
    total BIGINT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.creators WHERE creators.id = auth.uid()) THEN
        RAISE EXCEPTION 'Only creators can browse shoot alerts';
    END IF;
    SELECT count(*) INTO total FROM public.shoot_alerts s WHERE s.status = 'pending';
    RETURN QUERY
        SELECT s.id, s.customer_id, u.name, s.event_type, s.location, s.city_id, s.budget, s.timeframe,
               s.description, s.additional_details, s.created_at, total, pro
        FROM public.shoot_alerts s
        JOIN public.users u ON u.id = s.customer_id
        WHERE s.status = 'pending'
        ORDER BY s.created_at DESC
        LIMIT CASE WHEN pro THEN 200 ELSE 3 END;
END;
$$;
REVOKE ALL ON FUNCTION public.list_shoot_alerts() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.list_shoot_alerts() TO authenticated;

-- ---------------------------------------------------------------- search v2
DROP FUNCTION IF EXISTS public.search_creators(TEXT, TEXT, TEXT, DOUBLE PRECISION, DOUBLE PRECISION, INTEGER);
CREATE FUNCTION public.search_creators(
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
    headline TEXT, cover_image TEXT, review_count INTEGER, is_pro BOOLEAN,
    distance_km DOUBLE PRECISION
)
LANGUAGE sql STABLE SECURITY INVOKER SET search_path = public AS $$
    WITH base AS (
        SELECT c.*, u.name AS u_name, u.profile_image AS u_image,
               u.city AS u_city, u.state AS u_state, u.country AS u_country,
               coalesce(c.pro_until > now(), false) AS pro,
               CASE
                   WHEN p_lat IS NULL OR p_lng IS NULL OR c.latitude IS NULL OR c.longitude IS NULL THEN NULL
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
           b.latitude, b.longitude, b.search_radius,
           b.headline, b.cover_image, b.review_count, b.pro, b.dist
    FROM base b
    WHERE (p_city IS NULL OR b.u_city ILIKE '%' || p_city || '%')
      AND (p_event_type IS NULL OR b.skillset ILIKE '%' || p_event_type || '%'
                                OR b.creator_type ILIKE '%' || p_event_type || '%')
      AND (coalesce(p_query, '') = ''
           OR b.u_name ILIKE '%' || p_query || '%'
           OR b.bio ILIKE '%' || p_query || '%'
           OR b.headline ILIKE '%' || p_query || '%'
           OR b.skillset ILIKE '%' || p_query || '%'
           OR b.u_city ILIKE '%' || p_query || '%')
      AND (b.dist IS NULL OR b.dist <= p_radius_km)
    -- Creator Pro = featured placement.
    ORDER BY b.pro DESC, b.dist NULLS LAST, b.rating DESC, b.review_count DESC
    LIMIT 100;
$$;
GRANT EXECUTE ON FUNCTION public.search_creators(TEXT, TEXT, TEXT, DOUBLE PRECISION, DOUBLE PRECISION, INTEGER)
    TO anon, authenticated;

-- ---------------------------------------------------------------- storage
-- Uploads must go into the uploader's own folder: <bucket>/<auth.uid()>/<file>.
INSERT INTO storage.buckets (id, name, public) VALUES ('avatars', 'avatars', true)
ON CONFLICT (id) DO NOTHING;

DROP POLICY IF EXISTS "Authenticated Users Upload Portfolios" ON storage.objects;
DROP POLICY IF EXISTS "Users upload to own portfolio folder" ON storage.objects;
CREATE POLICY "Users upload to own portfolio folder" ON storage.objects
    FOR INSERT TO authenticated
    WITH CHECK (bucket_id IN ('portfolios', 'avatars') AND (storage.foldername(name))[1] = auth.uid()::text);

DROP POLICY IF EXISTS "Public Read Access Avatars" ON storage.objects;
CREATE POLICY "Public Read Access Avatars" ON storage.objects FOR SELECT USING (bucket_id = 'avatars');

DROP POLICY IF EXISTS "Users can delete their own portfolios files" ON storage.objects;
DROP POLICY IF EXISTS "Users delete own uploads" ON storage.objects;
CREATE POLICY "Users delete own uploads" ON storage.objects
    FOR DELETE TO authenticated
    USING (bucket_id IN ('portfolios', 'avatars') AND (storage.foldername(name))[1] = auth.uid()::text);

-- Message/review attachment buckets were world-writable for any signed-in user and
-- are unused by the app: stop accepting uploads to them.
DROP POLICY IF EXISTS "Authenticated Users Upload Messages" ON storage.objects;
DROP POLICY IF EXISTS "Authenticated Users Upload Reviews" ON storage.objects;
