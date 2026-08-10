-- Supabase Database Migration
-- Target: 'users', 'creators', and 'portfolios' Tables
-- Timestamp: 2026-06-17 02:09:33
-- Description: Sets up the schema for FokalPoint. Leverages Supabase Auth,
-- proper UUID foreign keys, automated synchronization from auth.users, indexes,
-- and secure Row Level Security (RLS) policies.

-- ----------------------------------------------------
-- 1. PUBLIC USERS TABLE
-- ----------------------------------------------------
-- Stores core profile information linked directly to Supabase Auth.
CREATE TABLE IF NOT EXISTS public.users (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    phone TEXT,
    role TEXT NOT NULL DEFAULT 'Customer' CHECK (role IN ('Customer', 'Creator')),
    profile_image TEXT,
    city TEXT,
    state TEXT,
    country TEXT DEFAULT 'India',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::text, NOW()) NOT NULL
);

-- Enable RLS for Users
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;

-- Users Row Level Security (RLS) Policies
CREATE POLICY "Public Read Access Profiles" ON public.users
    FOR SELECT TO public USING (true);

CREATE POLICY "Users Manage Own Profile" ON public.users
    FOR ALL TO authenticated USING (auth.uid() = id);

-- ----------------------------------------------------
-- 2. CREATORS TABLE
-- ----------------------------------------------------
-- Contains specialized details for photographer/videographer profiles.
-- Inherits primary identity from public.users via a foreign key relationship.
CREATE TABLE IF NOT EXISTS public.creators (
    id UUID PRIMARY KEY REFERENCES public.users(id) ON DELETE CASCADE,
    creator_type TEXT NOT NULL DEFAULT 'Photographer' CHECK (creator_type IN ('Photographer', 'Videographer', 'Both')),
    experience_level TEXT NOT NULL DEFAULT 'Professional' CHECK (experience_level IN ('Beginner', 'Professional', 'Studio')),
    bio TEXT,
    languages TEXT,
    equipment TEXT,
    rating DOUBLE PRECISION NOT NULL DEFAULT 4.5,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    starting_price DOUBLE PRECISION NOT NULL DEFAULT 0.0 CHECK (starting_price >= 0),
    instagram TEXT,
    website TEXT,
    years_of_experience INTEGER NOT NULL DEFAULT 0 CHECK (years_of_experience >= 0),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::text, NOW()) NOT NULL
);

-- Enable RLS for Creators
ALTER TABLE public.creators ENABLE ROW LEVEL SECURITY;

-- Creators Row Level Security (RLS) Policies
CREATE POLICY "Public Read Access Creators" ON public.creators
    FOR SELECT TO public USING (true);

CREATE POLICY "Creators Manage Own Profile" ON public.creators
    FOR ALL TO authenticated USING (auth.uid() = id);

-- ----------------------------------------------------
-- 3. PORTFOLIOS TABLE
-- ----------------------------------------------------
-- Holds media content (images/videos) uploaded by creators to highlight their craft.
CREATE TABLE IF NOT EXISTS public.portfolios (
    id BIGSERIAL PRIMARY KEY,
    creator_id UUID NOT NULL REFERENCES public.creators(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    category TEXT NOT NULL,
    media_url TEXT NOT NULL,
    media_type TEXT NOT NULL DEFAULT 'IMAGE' CHECK (media_type IN ('IMAGE', 'VIDEO')),
    thumbnail TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT TIMEZONE('utc'::text, NOW()) NOT NULL
);

-- Enable RLS for Portfolios
ALTER TABLE public.portfolios ENABLE ROW LEVEL SECURITY;

-- Portfolios Row Level Security (RLS) Policies
CREATE POLICY "Public Read Access Portfolios" ON public.portfolios
    FOR SELECT TO public USING (true);

CREATE POLICY "Creators Manage Own Portfolio" ON public.portfolios
    FOR ALL TO authenticated USING (auth.uid() = creator_id);

-- ----------------------------------------------------
-- 4. PERFORMANCE TUNED INDEXES
-- ----------------------------------------------------
-- Indexes to optimize search by city, queries, filtered categories, and sorting operations.
CREATE INDEX IF NOT EXISTS idx_users_city ON public.users(city);
CREATE INDEX IF NOT EXISTS idx_users_role ON public.users(role);
CREATE INDEX IF NOT EXISTS idx_creators_starting_price ON public.creators(starting_price);
CREATE INDEX IF NOT EXISTS idx_creators_creator_type ON public.creators(creator_type);
CREATE INDEX IF NOT EXISTS idx_creators_experience_level ON public.creators(experience_level);
CREATE INDEX IF NOT EXISTS idx_portfolios_creator_id ON public.portfolios(creator_id);
CREATE INDEX IF NOT EXISTS idx_portfolios_category ON public.portfolios(category);

-- ----------------------------------------------------
-- 5. AUTO-SYNCHRONIZATION PROFILE TRIGGER
-- ----------------------------------------------------
-- An automated database trigger function that executes whenever a physical user sign-up
-- succeeds in Supabase Auth, seamlessly populating the public.users profile table.
CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.users (id, name, email, role, profile_image)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data->>'name', 'Fokal Artist'),
        NEW.email,
        COALESCE(NEW.raw_user_meta_data->>'role', 'Customer'),
        COALESCE(NEW.raw_user_meta_data->>'avatar_url', '')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Create the trigger linked to auth.users
CREATE OR REPLACE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_auth_user();
