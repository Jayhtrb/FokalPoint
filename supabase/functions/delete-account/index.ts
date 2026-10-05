// Permanently deletes the calling user's account and all their data.
// Google Play requires in-app account deletion for apps with account creation.
//
// Deleting the auth user cascades through public.users to creators, bookings,
// messages, reviews, favorites, shoot alerts and payout details (ON DELETE CASCADE).
// Uploaded portfolio / avatar files are removed from storage first.
import { adminClient, callerId, cors, json } from "../_shared/auth.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: cors });
  if (req.method !== "POST") return json(405, { error: "Method not allowed" });

  const admin = adminClient();
  const uid = await callerId(req, admin);
  if (!uid) return json(401, { error: "Not signed in" });

  for (const bucket of ["portfolios", "avatars"]) {
    const { data: files } = await admin.storage.from(bucket).list(uid, { limit: 1000 });
    if (files && files.length > 0) {
      await admin.storage.from(bucket).remove(files.map((f) => `${uid}/${f.name}`));
    }
  }

  const { error } = await admin.auth.admin.deleteUser(uid);
  if (error) return json(500, { error: "Could not delete account. Please contact support." });
  return json(200, { deleted: true });
});
