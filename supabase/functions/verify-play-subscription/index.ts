// Verifies a Google Play "Creator Pro" subscription purchase and grants Pro.
//
// The app calls this after every purchase and on launch (to pick up renewals).
// The purchase is checked directly with the Google Play Developer API, so a
// modified app cannot grant itself Pro. Required secrets:
//   PLAY_PACKAGE_NAME            e.g. com.fokalpoint.app
//   GOOGLE_SERVICE_ACCOUNT_JSON  service account JSON with "View financial data"
//                                + "Manage orders and subscriptions" in Play Console
import { adminClient, callerId, cors, json } from "../_shared/auth.ts";

const PRODUCT_ID = "creator_pro";
const ACTIVE_STATES = new Set([
  "SUBSCRIPTION_STATE_ACTIVE",
  "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
  "SUBSCRIPTION_STATE_CANCELED", // cancelled but paid up until expiry
]);

function b64url(data: ArrayBuffer | string): string {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : new Uint8Array(data);
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

/** OAuth access token for the Play Developer API via a signed service-account JWT. */
async function googleAccessToken(): Promise<string> {
  const sa = JSON.parse(Deno.env.get("GOOGLE_SERVICE_ACCOUNT_JSON")!);
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64url(JSON.stringify({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/androidpublisher",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const pem = (sa.private_key as string).replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"],
  );
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claims}`));
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${header}.${claims}.${b64url(sig)}`,
    }),
  });
  if (!res.ok) throw new Error(`Google auth failed: ${res.status}`);
  return (await res.json()).access_token;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { headers: cors });
  if (req.method !== "POST") return json(405, { error: "Method not allowed" });

  const admin = adminClient();
  const uid = await callerId(req, admin);
  if (!uid) return json(401, { error: "Not signed in" });

  const { purchaseToken } = await req.json().catch(() => ({}));
  if (typeof purchaseToken !== "string" || purchaseToken.length < 10) {
    return json(400, { error: "Missing purchase token" });
  }

  const { data: creator } = await admin.from("creators").select("id").eq("id", uid).maybeSingle();
  if (!creator) return json(403, { error: "Creator Pro is for creator accounts" });

  // A purchase token may only ever be linked to one FokalPoint account.
  const { data: existing } = await admin.from("subscriptions")
    .select("user_id").eq("purchase_token", purchaseToken).maybeSingle();
  if (existing && existing.user_id !== uid) return json(409, { error: "This purchase belongs to another account" });

  const pkg = Deno.env.get("PLAY_PACKAGE_NAME")!;
  const token = await googleAccessToken();
  const res = await fetch(
    `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${pkg}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  if (!res.ok) return json(400, { error: "Google Play could not verify this purchase" });
  const sub = await res.json();

  const item = (sub.lineItems ?? []).find((li: { productId: string }) => li.productId === PRODUCT_ID);
  // The app sets obfuscatedAccountId = user id at checkout; reject tokens bought by someone else.
  const boughtBy = sub.externalAccountIdentifiers?.obfuscatedExternalAccountId;
  if (!item || (boughtBy && boughtBy !== uid)) return json(403, { error: "Purchase does not match this account" });

  const expiry = item.expiryTime as string;
  const active = ACTIVE_STATES.has(sub.subscriptionState) && new Date(expiry).getTime() > Date.now();

  await admin.from("subscriptions").upsert({
    user_id: uid,
    plan: item.offerDetails?.basePlanId ?? "creator_pro",
    product_id: PRODUCT_ID,
    status: sub.subscriptionState,
    expires_at: expiry,
    purchase_token: purchaseToken,
    updated_at: new Date().toISOString(),
  }, { onConflict: "purchase_token" });

  await admin.from("creators").update({ pro_until: active ? expiry : null }).eq("id", uid);

  return json(200, { active, proUntil: active ? expiry : null });
});
