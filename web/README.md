# FokalPoint Web

The web-first FokalPoint marketplace, built with Next.js, TypeScript, Supabase and Vercel.

## Product direction

FokalPoint connects clients with photographers, videographers, content creators and other creative professionals through a trusted workflow:

**Discover → Connect → Brief → Book → Pay → Create → Review**

## Architecture

- **Next.js App Router** for the product experience
- **TypeScript** for type safety
- **Supabase** for Postgres, Auth, Storage and Realtime
- **Vercel** for hosting, previews and server-side execution
- **GitHub** as the source of truth

The Android/Room implementation in the repository is retained as historical prototype code while the web platform is rebuilt on `web-platform-rebuild`.

## Local development

```bash
cd web
npm install
npm run dev
```

Copy `.env.example` to `.env.local` and configure the Supabase project.


## Marketplace UI

The `ui/champagne-espresso-v1` branch includes the Champagne & Espresso discovery experience, creator profiles, and a booking/checkout preview at `/booking/[slug]`. Payment processing and persisted booking creation are not enabled in this preview.


## Supabase customer auth and booking requests

The web app uses cookie-based Supabase Auth via `@supabase/ssr` (Next.js 16 `proxy.ts` session refresh). Customers can sign in or register with email/password, or continue booking as a guest.

Configure these Vercel environment variables for the `web` project:
- `NEXT_PUBLIC_SUPABASE_URL`
- `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY`

Do not put a Supabase secret/service-role key in any `NEXT_PUBLIC_*` variable.

Before enabling live auth/booking:
1. Connect the dedicated FokalPoint Supabase project.
2. Apply `supabase/migrations/20261113000000_guest_booking_requests.sql` after the FokalPoint marketplace base schema is present.
3. In Supabase Auth URL Configuration, set the production Site URL and allow the exact callback URL `https://<your-domain>/auth/callback` (plus local development callback URLs if needed).
4. Confirm email/password sign-up is enabled and configure SMTP/email confirmation templates for production delivery.

Guest booking requests are insert-only from the public client. Anonymous users cannot read or modify submitted requests. Authenticated customers can read only their own requests. Creator review and payment processing are separate follow-on workflows; the current booking UI stores a request and a preferred payment method but does not collect money.
