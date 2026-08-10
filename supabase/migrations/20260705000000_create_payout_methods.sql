-- supabase/migrations/20260705000000_create_payout_methods.sql
CREATE TABLE IF NOT EXISTS payout_methods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type TEXT NOT NULL CHECK (type IN ('BANK_ACCOUNT', 'UPI')),
    account_holder_name TEXT NOT NULL,
    account_number TEXT,
    bank_name TEXT,
    ifsc_code TEXT,
    upi_id TEXT,
    is_default BOOLEAN DEFAULT false,
    status TEXT DEFAULT 'PENDING_VERIFICATION',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT valid_bank_account CHECK (
        (type = 'BANK_ACCOUNT' AND account_number IS NOT NULL AND bank_name IS NOT NULL AND ifsc_code IS NOT NULL) OR
        (type = 'UPI' AND upi_id IS NOT NULL)
    )
);

-- RLS Policies
ALTER TABLE payout_methods ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can view own payout methods"
ON payout_methods FOR SELECT
USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own payout methods"
ON payout_methods FOR INSERT
WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own payout methods"
ON payout_methods FOR UPDATE
USING (auth.uid() = user_id);

CREATE POLICY "Users can delete own payout methods"
ON payout_methods FOR DELETE
USING (auth.uid() = user_id);

-- Only one default per user
CREATE UNIQUE INDEX idx_unique_default_payout_method
ON payout_methods (user_id) WHERE is_default = true;
