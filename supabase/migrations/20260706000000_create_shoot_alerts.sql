-- supabase/migrations/20260706000000_create_shoot_alerts.sql

CREATE TABLE IF NOT EXISTS public.shoot_alerts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID REFERENCES public.users(id) ON DELETE CASCADE,
    creator_id UUID REFERENCES public.users(id) ON DELETE SET NULL,
    event_type TEXT NOT NULL,
    location TEXT NOT NULL,
    city_id TEXT NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    budget DECIMAL,
    timeframe TEXT,
    description TEXT,
    additional_details TEXT,
    status TEXT NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

-- Add location columns to creators
ALTER TABLE public.creators 
ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION,
ADD COLUMN IF NOT EXISTS search_radius INTEGER DEFAULT 50;

-- Create index for location-based search
CREATE INDEX IF NOT EXISTS idx_creators_location ON public.creators(latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_shoot_alerts_location ON public.shoot_alerts(latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_shoot_alerts_city ON public.shoot_alerts(city_id);

-- Function to find nearby creators
CREATE OR REPLACE FUNCTION find_nearby_creators(
    lat DOUBLE PRECISION,
    lng DOUBLE PRECISION,
    radius_km INTEGER DEFAULT 50
)
RETURNS TABLE(
    id UUID,
    name TEXT,
    rating DOUBLE PRECISION,
    distance DOUBLE PRECISION
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        c.id,
        u.name,
        c.rating,
        (
            6371 * acos(
                cos(radians(lat)) * cos(radians(c.latitude)) *
                cos(radians(c.longitude) - radians(lng)) +
                sin(radians(lat)) * sin(radians(c.latitude))
            )
        )::DOUBLE PRECISION AS distance
    FROM public.creators c
    JOIN public.users u ON u.id = c.id
    WHERE c.latitude IS NOT NULL 
        AND c.longitude IS NOT NULL
        AND (
            6371 * acos(
                cos(radians(lat)) * cos(radians(c.latitude)) *
                cos(radians(c.longitude) - radians(lng)) +
                sin(radians(lat)) * sin(radians(c.latitude))
            )
        ) <= radius_km
    ORDER BY distance;
END;
$$ LANGUAGE plpgsql;

-- RLS Policies
ALTER TABLE public.shoot_alerts ENABLE ROW LEVEL SECURITY;

-- Check and create policy for SELECT if not exists
DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE tablename = 'shoot_alerts' AND policyname = 'Anyone can view shoot alerts'
    ) THEN
        CREATE POLICY "Anyone can view shoot alerts" ON public.shoot_alerts
            FOR SELECT USING (true);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE tablename = 'shoot_alerts' AND policyname = 'Users can insert own shoot alerts'
    ) THEN
        CREATE POLICY "Users can insert own shoot alerts" ON public.shoot_alerts
            FOR INSERT WITH CHECK (auth.uid() = customer_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE tablename = 'shoot_alerts' AND policyname = 'Users can update own shoot alerts'
    ) THEN
        CREATE POLICY "Users can update own shoot alerts" ON public.shoot_alerts
            FOR UPDATE USING (auth.uid() = customer_id);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE tablename = 'shoot_alerts' AND policyname = 'Users can delete own shoot alerts'
    ) THEN
        CREATE POLICY "Users can delete own shoot alerts" ON public.shoot_alerts
            FOR DELETE USING (auth.uid() = customer_id);
    END IF;
END $$;
