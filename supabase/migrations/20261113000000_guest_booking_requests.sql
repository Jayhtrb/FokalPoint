-- FokalPoint guest + registered customer booking requests.
-- Apply only to the dedicated FokalPoint Supabase project after reviewing its schema.
create table if not exists public.booking_requests (
  id uuid primary key default gen_random_uuid(),
  booking_reference text not null unique,
  creator_slug text not null,
  creator_id uuid references public.creators(id) on delete set null,
  customer_id uuid references auth.users(id) on delete set null,
  customer_name text not null,
  customer_email text not null,
  customer_phone text not null,
  event_type text not null,
  event_date date not null,
  event_time time not null,
  event_location text not null,
  package_name text not null,
  estimated_amount numeric(12,2) not null check (estimated_amount >= 0),
  estimated_tax numeric(12,2) not null default 0 check (estimated_tax >= 0),
  currency text not null default 'INR',
  preferred_payment_method text not null check (preferred_payment_method in ('upi','card','netbanking')),
  customer_notes text,
  status text not null default 'requested' check (status in ('requested','reviewing','quoted','accepted','declined','cancelled')),
  created_at timestamptz not null default now()
);

create index if not exists booking_requests_customer_created_idx
  on public.booking_requests(customer_id, created_at desc);
create index if not exists booking_requests_creator_slug_created_idx
  on public.booking_requests(creator_slug, created_at desc);
create index if not exists booking_requests_status_created_idx
  on public.booking_requests(status, created_at desc);

alter table public.booking_requests enable row level security;

drop policy if exists booking_requests_guest_insert on public.booking_requests;
create policy booking_requests_guest_insert
  on public.booking_requests for insert to anon
  with check (
    customer_id is null
    and status = 'requested'
    and length(trim(customer_name)) between 2 and 120
    and length(trim(customer_email)) between 5 and 254
    and position('@' in customer_email) > 1
    and length(trim(customer_phone)) between 7 and 30
    and length(trim(creator_slug)) between 2 and 100
  );

drop policy if exists booking_requests_customer_insert on public.booking_requests;
create policy booking_requests_customer_insert
  on public.booking_requests for insert to authenticated
  with check (
    customer_id = (select auth.uid())
    and status = 'requested'
    and length(trim(customer_name)) between 2 and 120
    and length(trim(customer_email)) between 5 and 254
    and position('@' in customer_email) > 1
    and length(trim(customer_phone)) between 7 and 30
    and length(trim(creator_slug)) between 2 and 100
  );

drop policy if exists booking_requests_customer_read on public.booking_requests;
create policy booking_requests_customer_read
  on public.booking_requests for select to authenticated
  using (customer_id = (select auth.uid()));

-- Deliberately no anonymous SELECT/UPDATE/DELETE policy.
-- New tables are not automatically exposed through the Data API on current Supabase projects.
grant insert on public.booking_requests to anon;
grant insert, select on public.booking_requests to authenticated;
