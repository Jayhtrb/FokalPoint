-- supabase/migrations/20260705000001_update_creators_table.sql

-- Update creators table to include new fields
ALTER TABLE creators 
ADD COLUMN IF NOT EXISTS skillsets TEXT[] DEFAULT '{}',
ADD COLUMN IF NOT EXISTS languages TEXT[] DEFAULT '{}',
ADD COLUMN IF NOT EXISTS equipment JSONB DEFAULT '[]',
ADD COLUMN IF NOT EXISTS portfolio_urls JSONB DEFAULT '{
  "instagram": "",
  "youtube": "",
  "website": ""
}';

-- Create function to validate URLs
CREATE OR REPLACE FUNCTION validate_url(url TEXT)
RETURNS BOOLEAN AS $$
BEGIN
    RETURN url IS NULL OR url = '' OR url ~ '^https?://.*';
END;
$$ LANGUAGE plpgsql;

-- Add constraint for portfolio URLs
ALTER TABLE creators 
ADD CONSTRAINT valid_portfolio_urls 
CHECK (
    (portfolio_urls->>'instagram' = '' OR validate_url(portfolio_urls->>'instagram')) AND
    (portfolio_urls->>'youtube' = '' OR validate_url(portfolio_urls->>'youtube')) AND
    (portfolio_urls->>'website' = '' OR validate_url(portfolio_urls->>'website'))
);
