create extension if not exists pgcrypto;

create type public.user_role as enum ('customer', 'creator', 'admin');
create type public.creator_status as enum ('draft', 'pending_review', 'published', 'suspended');
create type public.booking_status as enum ('requested', 'quoted', 'accepted', 'payment_pending', 'confirmed', 'in_progress', 'completed', 'cancelled', 'declined');
create type public.availability_status as enum ('available', 'blocked');

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  role public.user_role not null default 'customer',
  display_name text,
  avatar_url text,
  phone text,
  city text,
  bio text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.creators (
  id uuid primary key references public.profiles(id) on delete cascade,
  slug text not null unique,
  headline text,
  status public.creator_status not null default 'draft',
  category text,
  experience_years integer not null default 0 check (experience_years >= 0),
  starting_price numeric(12,2) check (starting_price is null or starting_price >= 0),
  response_rate numeric(5,2) check (response_rate is null or response_rate between 0 and 100),
  verified boolean not null default false,
  rating numeric(3,2) check (rating is null or rating between 0 and 5),
  review_count integer not null default 0 check (review_count >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.creator_services (
  id uuid primary key default gen_random_uuid(),
  creator_id uuid not null references public.creators(id) on delete cascade,
  name text not null,
  description text,
  price numeric(12,2) check (price is null or price >= 0),
  duration_minutes integer check (duration_minutes is null or duration_minutes > 0),
  created_at timestamptz not null default now()
);

create table public.portfolios (
  id uuid primary key default gen_random_uuid(),
  creator_id uuid not null references public.creators(id) on delete cascade,
  title text,
  description text,
  media_url text not null,
  media_type text not null default 'image' check (media_type in ('image', 'video')),
  sort_order integer not null default 0,
  created_at timestamptz not null default now()
);

create table public.availability (
  id uuid primary key default gen_random_uuid(),
  creator_id uuid not null references public.creators(id) on delete cascade,
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  status public.availability_status not null default 'available',
  notes text,
  created_at timestamptz not null default now(),
  constraint availability_valid_window check (ends_at > starts_at)
);

create table public.favorites (
  customer_id uuid not null references public.profiles(id) on delete cascade,
  creator_id uuid not null references public.creators(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (customer_id, creator_id)
);

create table public.bookings (
  id uuid primary key default gen_random_uuid(),
  customer_id uuid not null references public.profiles(id) on delete restrict,
  creator_id uuid not null references public.creators(id) on delete restrict,
  service_id uuid references public.creator_services(id) on delete set null,
  starts_at timestamptz,
  ends_at timestamptz,
  status public.booking_status not null default 'requested',
  customer_notes text,
  creator_notes text,
  quoted_amount numeric(12,2) check (quoted_amount is null or quoted_amount >= 0),
  currency text not null default 'INR',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint booking_valid_window check (ends_at is null or starts_at is null or ends_at > starts_at)
);

create table public.reviews (
  id uuid primary key default gen_random_uuid(),
  booking_id uuid not null unique references public.bookings(id) on delete cascade,
  customer_id uuid not null references public.profiles(id) on delete restrict,
  creator_id uuid not null references public.creators(id) on delete restrict,
  rating integer not null check (rating between 1 and 5),
  review_text text,
  created_at timestamptz not null default now()
);

create index creators_status_category_city_idx on public.creators(status, category);
create index creators_slug_idx on public.creators(slug);
create index creator_services_creator_idx on public.creator_services(creator_id);
create index portfolios_creator_sort_idx on public.portfolios(creator_id, sort_order);
create index availability_creator_time_idx on public.availability(creator_id, starts_at, ends_at);
create index bookings_customer_idx on public.bookings(customer_id, created_at desc);
create index bookings_creator_idx on public.bookings(creator_id, created_at desc);
create index bookings_time_idx on public.bookings(creator_id, starts_at, ends_at);
create index reviews_creator_idx on public.reviews(creator_id, created_at desc);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create trigger profiles_updated_at before update on public.profiles
for each row execute function public.set_updated_at();
create trigger creators_updated_at before update on public.creators
for each row execute function public.set_updated_at();
create trigger bookings_updated_at before update on public.bookings
for each row execute function public.set_updated_at();

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, display_name, avatar_url)
  values (
    new.id,
    coalesce(new.raw_user_meta_data ->> 'full_name', new.raw_user_meta_data ->> 'name', split_part(coalesce(new.email, ''), '@', 1)),
    new.raw_user_meta_data ->> 'avatar_url'
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

create trigger on_auth_user_created
after insert on auth.users
for each row execute function public.handle_new_user();

alter table public.profiles enable row level security;
alter table public.creators enable row level security;
alter table public.creator_services enable row level security;
alter table public.portfolios enable row level security;
alter table public.availability enable row level security;
alter table public.favorites enable row level security;
alter table public.bookings enable row level security;
alter table public.reviews enable row level security;

create policy profiles_select_own on public.profiles for select using (auth.uid() = id);
create policy profiles_update_own on public.profiles for update using (auth.uid() = id) with check (auth.uid() = id);
create policy profiles_insert_own on public.profiles for insert with check (auth.uid() = id);

create policy creators_public_select on public.creators for select using (status = 'published' or id = auth.uid());
create policy creators_insert_own on public.creators for insert with check (id = auth.uid());
create policy creators_update_own on public.creators for update using (id = auth.uid()) with check (id = auth.uid());

create policy services_public_select on public.creator_services for select using (
  exists (select 1 from public.creators c where c.id = creator_id and (c.status = 'published' or c.id = auth.uid()))
);
create policy services_manage_own on public.creator_services for all using (creator_id = auth.uid()) with check (creator_id = auth.uid());

create policy portfolios_public_select on public.portfolios for select using (
  exists (select 1 from public.creators c where c.id = creator_id and (c.status = 'published' or c.id = auth.uid()))
);
create policy portfolios_manage_own on public.portfolios for all using (creator_id = auth.uid()) with check (creator_id = auth.uid());

create policy availability_public_select on public.availability for select using (
  exists (select 1 from public.creators c where c.id = creator_id and (c.status = 'published' or c.id = auth.uid()))
);
create policy availability_manage_own on public.availability for all using (creator_id = auth.uid()) with check (creator_id = auth.uid());

create policy favorites_own on public.favorites for all using (customer_id = auth.uid()) with check (customer_id = auth.uid());

create policy bookings_customer_select on public.bookings for select using (customer_id = auth.uid() or creator_id = auth.uid());
create policy bookings_customer_insert on public.bookings for insert with check (customer_id = auth.uid());
create policy bookings_customer_update on public.bookings for update using (customer_id = auth.uid()) with check (customer_id = auth.uid());
create policy bookings_creator_update on public.bookings for update using (creator_id = auth.uid()) with check (creator_id = auth.uid());

create policy reviews_public_select on public.reviews for select using (true);
create policy reviews_customer_insert on public.reviews for insert with check (customer_id = auth.uid());

create or replace function public.is_creator()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (select 1 from public.profiles where id = auth.uid() and role = 'creator');
$$;
