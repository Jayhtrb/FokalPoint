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
