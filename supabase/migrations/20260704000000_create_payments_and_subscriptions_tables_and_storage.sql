-- Supabase Database Migration
-- Target: 'payments' and 'subscriptions' Tables, Storage Buckets Configuration
-- Timestamp: 2026-07-04 23:25:00
-- Description: Sets up payments and subscriptions schemas with foreign key references, 
-- and initializes storage buckets with proper security and row-level policies.

-- ----------------------------------------------------
-- 1. PAYMENTS SCHEMA TABLE
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS public.payments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id bigint REFERENCES public.bookings(id) ON DELETE CASCADE,
  customer_id TEXT,
  amount DECIMAL NOT NULL CHECK (amount >= 0),
  payment_method TEXT NOT NULL DEFAULT 'Card',
  status TEXT NOT NULL DEFAULT 'Pending',
  transaction_id TEXT,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Enable RLS for Payments
ALTER TABLE public.payments ENABLE ROW LEVEL SECURITY;

-- ----------------------------------------------------
-- 2. SUBSCRIPTIONS SCHEMA TABLE
-- ----------------------------------------------------
CREATE TABLE IF NOT EXISTS public.subscriptions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES public.users(id) ON DELETE CASCADE,
  plan TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'Active',
  expires_at TIMESTAMP WITH TIME ZONE
);

-- Enable RLS for Subscriptions
ALTER TABLE public.subscriptions ENABLE ROW LEVEL SECURITY;

-- ----------------------------------------------------
-- 3. RLS POLICIES FOR NEW TABLES
-- ----------------------------------------------------
-- Allow users to select payments related to bookings where they are Customer or Creator
CREATE POLICY "Users can select payments for their bookings" ON public.payments
  FOR SELECT TO authenticated USING (
    EXISTS (
      SELECT 1 FROM public.bookings 
      WHERE public.bookings.id = payments.booking_id 
      AND (public.bookings.customer_id = auth.uid() OR public.bookings.creator_id = auth.uid())
    )
  );

-- Allow customers to make payments
CREATE POLICY "Customers can insert payments" ON public.payments
  FOR INSERT TO authenticated WITH CHECK (
    EXISTS (
      SELECT 1 FROM public.bookings 
      WHERE public.bookings.id = booking_id 
      AND public.bookings.customer_id = auth.uid()
    )
  );

-- Allow users to view their own subscriptions
CREATE POLICY "Users can view their own subscriptions" ON public.subscriptions
  FOR SELECT TO authenticated USING (user_id = auth.uid());

-- Allow inserting own subscriptions
CREATE POLICY "Users can insert own subscriptions" ON public.subscriptions
  FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());

-- ----------------------------------------------------
-- 4. STORAGE BUCKETS CONFIGURATION & POLICIES
-- ----------------------------------------------------
-- Insert buckets into storage.buckets table if they do not exist
INSERT INTO storage.buckets (id, name, public)
VALUES 
  ('portfolios', 'portfolios', true),
  ('messages', 'messages', true),
  ('reviews', 'reviews', true)
ON CONFLICT (id) DO NOTHING;

-- RLS Policies for Storage Bucket Objects
-- Public Read Access Policies (as requested or public default)
CREATE POLICY "Public Read Access Portfolios" ON storage.objects
  FOR SELECT USING (bucket_id = 'portfolios');

CREATE POLICY "Public Read Access Messages" ON storage.objects
  FOR SELECT USING (bucket_id = 'messages');

CREATE POLICY "Public Read Access Reviews" ON storage.objects
  FOR SELECT USING (bucket_id = 'reviews');

-- Authenticated Users Upload Policies
CREATE POLICY "Authenticated Users Upload Portfolios" ON storage.objects
  FOR INSERT TO authenticated WITH CHECK (bucket_id = 'portfolios');

CREATE POLICY "Authenticated Users Upload Messages" ON storage.objects
  FOR INSERT TO authenticated WITH CHECK (bucket_id = 'messages');

CREATE POLICY "Authenticated Users Upload Reviews" ON storage.objects
  FOR INSERT TO authenticated WITH CHECK (bucket_id = 'reviews');

-- Delete Access Policies (allowing owners or authorized authenticated users)
CREATE POLICY "Users can delete their own portfolios files" ON storage.objects
  FOR DELETE TO authenticated USING (bucket_id = 'portfolios' AND (auth.uid()::text = owner::text OR owner IS NULL));

CREATE POLICY "Users can delete their own messages files" ON storage.objects
  FOR DELETE TO authenticated USING (bucket_id = 'messages' AND (auth.uid()::text = owner::text OR owner IS NULL));

CREATE POLICY "Users can delete their own reviews files" ON storage.objects
  FOR DELETE TO authenticated USING (bucket_id = 'reviews' AND (auth.uid()::text = owner::text OR owner IS NULL));
