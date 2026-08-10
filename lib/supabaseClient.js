import { createClient } from '@supabase/supabase-js';

// Retrieve Supabase credentials from environment variables configuration
const supabaseUrl = process.env.SUPABASE_URL || '';
const supabaseAnonKey = process.env.SUPABASE_ANON_KEY || '';

if (!supabaseUrl || !supabaseAnonKey) {
  console.warn(
    'Warning: SUPABASE_URL or SUPABASE_ANON_KEY is not defined in your environment variables. ' +
    'Please ensure they are defined in your .env configuration file.'
  );
}

// Initialize and export the Supabase client instance
export const supabase = createClient(supabaseUrl, supabaseAnonKey);
