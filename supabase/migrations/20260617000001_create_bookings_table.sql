-- Supabase Database Migration
-- Target: 'bookings' Table
-- Timestamp: 2026-06-17 02:30:49
-- Description: Sets up the FokalPoint system's booking table. Includes custom enum definitions 
-- for status tracking, foreign keys referencing users/creators schemas, indexing on lookup 
-- columns, and proper RLS constraints.

-- ----------------------------------------------------
-- 1. DELETION PREPARATION (IF RE-RUNNING INITIALIZATION)
-- ----------------------------------------------------
-- drop existing types if they already exist to permit idempotency (optional or safe cascade)
-- DROP TYPE IF EXISTS public.booking_status CASCADE;
-- DROP TYPE IF EXISTS public.payment_status CASCADE;

-- ----------------------------------------------------
-- 2. CUSTOM DATA ENUMS/TYPES
-- ----------------------------------------------------
-- Define custom Postgres enum types for robust status handling.
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'booking_status') THEN
        CREATE TYPE public.booking_status AS ENUM (
            'Pending',
            'Accepted',
            'Confirmed',
            'Completed',
            'Cancelled'
        );
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'payment_status') THEN
        CREATE TYPE public.payment_status AS ENUM (
            'Pending',
            'Paid'
        );
    END IF;
END $$;

-- ----------------------------------------------------
-- 3. BOOKINGS SCHEMA TABLE
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS public.bookings (
    id BIGSERIAL PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    creator_id UUID NOT NULL REFERENCES public.creators(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL,
    date DATE NOT NULL,
    time TIME NOT NULL,
    hours INTEGER NOT NULL CHECK (hours > 0),
    price DOUBLE PRECISION NOT NULL CHECK (price >= 0),
    status public.booking_status NOT NULL DEFAULT 'Pending',
    payment_status public.payment_status NOT NULL DEFAULT 'Pending',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::text, NOW()) NOT NULL
);

-- ----------------------------------------------------
-- 4. ROW LEVEL SECURITY (RLS) POLICIES
-- ----------------------------------------------------
-- Enable RLS on bookings
ALTER TABLE public.bookings ENABLE ROW LEVEL SECURITY;

-- Allow reading a booking if the logged in user is either the booking's Customer or Creator
CREATE POLICY "Users can select bookings where they are buyer or host" ON public.bookings
    FOR SELECT TO authenticated USING (
        auth.uid() = customer_id OR auth.uid() = creator_id
    );

-- Allow inserting a booking if the customer_id matches the authenticated user ID
CREATE POLICY "Authenticated users can book creative services" ON public.bookings
    FOR INSERT TO authenticated WITH CHECK (
        auth.uid() = customer_id
    );

-- Allow updating a booking if the user is one of the participating actors
CREATE POLICY "Involved parties can update booking parameters" ON public.bookings
    FOR UPDATE TO authenticated USING (
        auth.uid() = customer_id OR auth.uid() = creator_id
    ) WITH CHECK (
        auth.uid() = customer_id OR auth.uid() = creator_id
    );

-- ----------------------------------------------------
-- 5. PERFORMANCE TUNING INDEXES
-- ----------------------------------------------------
-- Indexes to optimize querying bookings by specific ranges, looking up calendar schedules for creators,
-- and tracking overall booking statuses.
CREATE INDEX IF NOT EXISTS idx_bookings_creator_id ON public.bookings(creator_id);
CREATE INDEX IF NOT EXISTS idx_bookings_customer_id ON public.bookings(customer_id);
CREATE INDEX IF NOT EXISTS idx_bookings_date ON public.bookings(date);
CREATE INDEX IF NOT EXISTS idx_bookings_creator_date ON public.bookings(creator_id, date);
CREATE INDEX IF NOT EXISTS idx_bookings_status ON public.bookings(status);
