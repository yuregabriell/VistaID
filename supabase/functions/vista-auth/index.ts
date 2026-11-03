// Vista ID secure sign-in and TV pairing. Deploy with verify_jwt=false:
// some actions are unauthenticated, but user actions verify their JWT here.
// Only server-side SUPABASE_SERVICE_ROLE_KEY can touch control tables.
import { createClient } from "npm:@supabase/supabase-js@2";

const url = Deno.env.get("SUPABASE_URL")!;
const secret = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const publicKey = Deno.env.get("SUPABASE_ANON_KEY") ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY")!;
const admin = createClient(url, secret, { auth: { autoRefreshToken: false, persistSession: false } });
const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const headers = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Cache-Control": "no-store",
};
const reply = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), { status, headers: { ...headers, "Content-Type": "application/json" } });
const fail = (status: number, message: string) => reply({ error: message }, status);
const random = (count = 32) => crypto.getRandomValues(new Uint8Array(count));
function base64url(bytes: Uint8Array) {
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "");
}
function humanCode(length = 6) {
  return [...random(length)].map((i) => alphabet[i & 31]).join("");
}
async function sha256(input: string) {
  const hash = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(input));
  return Array.from(new Uint8Array(hash), (b) => b.toString(16).padStart(2, "0")).join("");
}
async function rotatingCode(seed: string, step: number) {
  const key = await crypto.subtle.importKey(
    "raw", Uint8Array.from(atob(seed), (c) => c.charCodeAt(0)),
    { name: "HMAC", hash: "SHA-256" }, false, ["sign"],
  );
  const counter = new ArrayBuffer(8);
  new DataView(counter).setBigUint64(0, BigInt(step), false);
  const digest = new Uint8Array(await crypto.subtle.sign("HMAC", key, counter));
  return [...digest.slice(0, 8)].map((i) => alphabet[i & 31]).join("");
}
const validUsername = (v: unknown) => typeof v === "string" && /^[a-z0-9_]{3,24}$/.test(v);
const validUuid = (v: unknown) => typeof v === "string" &&
  /^[a-f0-9]{8}-[a-f0-9]{4}-[1-8][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/i.test(v);
async function getSignedInUser(req: Request) {
  const jwt = req.headers.get("authorization")?.match(/^Bearer (.+)$/i)?.[1];
  if (!jwt) return null;
  const { data, error } = await admin.auth.getUser(jwt);
  return error ? null : data.user;
}
async function limit(req: Request, action: string, identity: string) {
  // Both limits use atomic PostgreSQL updates; never rely solely on the client.
  const ip = req.headers.get("cf-connecting-ip") ??
    req.headers.get("x-real-ip") ?? req.headers.get("x-forwarded-for")?.split(",")[0] ?? "unknown";
  const buckets: Array<[string, number]> = action === "pair_create"
    ? [[`ip:${ip}:${action}`, 10]]
    : [[`ip:${ip}:${action}`, 80],
       [`id:${identity}:${action}`, action === "pair_redeem" ? 35 : 8]];
  for (const [value, max] of buckets) {
    const key = await sha256(value);
    const { data, error } = await admin.rpc("vid_limit_attempt", { p_key: key, p_max: max });
    if (error || data !== true) return false;
  }
  return true;
}
async function resolveUser(handle: string) {
  const { data: profile, error } = await admin.from("vid_profiles")
    .select("user_id").eq("username", handle).maybeSingle();
  if (error || !profile) return null;
  const userResult = await admin.auth.admin.getUserById(profile.user_id);
  return userResult.error ? null : userResult.data.user;
}
async function issueOtpHash(email: string) {
  // No email is sent: only a one-time Supabase Auth token_hash is generated.
  const { data, error } = await admin.auth.admin.generateLink({ type: "magiclink", email });
  if (error || !data?.properties?.hashed_token) throw new Error("Could not issue session");
  return data.properties.hashed_token;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response(null, { status: 204, headers });
  if (req.method !== "POST") return fail(405, "Método inválido");
  if (Number(req.headers.get("content-length") ?? "0") > 4096) return fail(413, "Pedido demasiado grande");
  let body: Record<string, unknown>;
  try { body = await req.json(); if (!body || typeof body !== "object") throw Error(); }
  catch { return fail(400, "Pedido inválido"); }
  const action = body.action;
  try {
    if (action === "username_password") {
      const username = String(body.username ?? "").toLowerCase();
      const password = String(body.password ?? "");
      if (!validUsername(username) || !password || password.length > 256) return fail(400, "Dados inválidos");
      if (!(await limit(req, "password", username))) return fail(429, "Demasiadas tentativas");
      const account = await resolveUser(username);
      if (!account?.email) return fail(401, "Credenciais inválidas");
      const client = createClient(url, publicKey, { auth: { persistSession: false, autoRefreshToken: false } });
      const { data, error } = await client.auth.signInWithPassword({ email: account.email, password });
      if (error || !data.session) return fail(401, "Credenciais inválidas");
      return reply(data.session);
    }
    if (action === "profile_update") {
      const user = await getSignedInUser(req);
      if (!user) return fail(401, "Inicia sessão primeiro");
      const displayName = typeof body.display_name === "string" ? body.display_name.trim() : "";
      if (displayName.length < 2 || displayName.length > 60)
        return fail(400, "Nome deve ter entre 2 e 60 caracteres");
      const { error } = await admin.from("vid_profiles")
        .update({ display_name: displayName }).eq("user_id", user.id);
      if (error) throw error;
      return reply({ updated: true, display_name: displayName });
    }
    if (action === "vs_reset") {
      const user = await getSignedInUser(req);
      if (!user) return fail(401, "Inicia sessão primeiro");
      if (body.confirm !== "DELETE_MY_VS_DATA") return fail(400, "Confirmação obrigatória");
      const { error } = await admin.from("vs_user_data")
        .update({ settings: {}, favorites: [], progress: {} }).eq("user_id", user.id);
      if (error) throw error;
      return reply({ reset: true });
    }
    if (action === "code_now") {
      const user = await getSignedInUser(req);
      if (!user) return fail(401, "Inicia sessão no Vista ID");
      const { data, error } = await admin.from("vid_auth_secrets").select("seed_b64")
        .eq("user_id", user.id).single();
      if (error || !data) return fail(404, "Autenticador indisponível");
      const step = Math.floor(Date.now() / 30_000);
      return reply({ code: await rotatingCode(data.seed_b64, step), expires_at: (step + 1) * 30 });
    }
    if (action === "code_login") {
      const username = String(body.username ?? "").toLowerCase();
      const code = String(body.code ?? "").toUpperCase().replace(/\s/g, "");
      if (!validUsername(username) || !/^[A-HJ-NP-Z2-9]{8}$/.test(code)) return fail(400, "Dados inválidos");
      if (!(await limit(req, "code", username))) return fail(429, "Demasiadas tentativas");
      const account = await resolveUser(username);
      if (!account?.email) return fail(401, "Nome ou código inválido");
      const { data: secretRow } = await admin.from("vid_auth_secrets").select("seed_b64")
        .eq("user_id", account.id).maybeSingle();
      if (!secretRow) return fail(401, "Nome ou código inválido");
      const nowStep = Math.floor(Date.now() / 30_000);
      let matched = -1;
      for (const step of [nowStep, nowStep - 1]) {
        if (code === await rotatingCode(secretRow.seed_b64, step)) { matched = step; break; }
      }
      if (matched < 0) return fail(401, "Nome ou código inválido");
      const { data: consumed } = await admin.rpc("vid_consume_step",
        { p_user: account.id, p_step: matched });
      if (!consumed) return fail(401, "Código já utilizado");
      return reply({ token_hash: await issueOtpHash(account.email), type: "magiclink" });
    }
    if (action === "pair_create") {
      if (body.app_id !== "vs") return fail(400, "Aplicação não suportada");
      if (!(await limit(req, "pair_create", "vs"))) return fail(429, "Demasiados pedidos");
      const id = crypto.randomUUID();
      const poll_secret = base64url(random(32));
      const verification_code = humanCode();
      const { error } = await admin.from("vid_device_pairings").insert({
        id, app_id: "vs", poll_secret_hash: await sha256(poll_secret), verification_code,
      });
      if (error) throw error;
      return reply({
        request_id: id, poll_secret, verification_code,
        qr_uri: `vistaid://pair?request=${encodeURIComponent(id)}`,
        expires_in: 120,
      });
    }
    if (action === "pair_details") {
      if (!validUuid(body.request_id)) return fail(400, "QR inválido");
      if (!(await limit(req, "pair_details", String(body.request_id)))) return fail(429, "Demasiadas tentativas");
      const { data } = await admin.from("vid_device_pairings")
        .select("app_id,status,verification_code,expires_at")
        .eq("id", body.request_id).maybeSingle();
      if (!data || data.status !== "pending" || new Date(data.expires_at).getTime() <= Date.now())
        return fail(410, "Pedido expirado");
      return reply({ app_id: data.app_id, app_name: "VistaStream",
        verification_code: data.verification_code, expires_at: data.expires_at });
    }
    if (action === "pair_approve") {
      const user = await getSignedInUser(req);
      if (!user?.email) return fail(401, "Inicia sessão primeiro");
      if (!validUuid(body.request_id)) return fail(400, "Pedido inválido");
      if (!(await limit(req, "pair_approve", user.id))) return fail(429, "Demasiadas tentativas");
      const { data: pair } = await admin.from("vid_device_pairings")
        .select("app_id,status,expires_at").eq("id", body.request_id).maybeSingle();
      if (!pair || pair.app_id !== "vs" || pair.status !== "pending" ||
          new Date(pair.expires_at).getTime() <= Date.now()) return fail(410, "Pedido expirado");
      const token_hash = await issueOtpHash(user.email);
      const { data, error } = await admin.from("vid_device_pairings")
        .update({ approved_user: user.id, login_token_hash: token_hash, status: "approved" })
        .eq("id", body.request_id).eq("status", "pending")
        .gt("expires_at", new Date().toISOString()).select("id").maybeSingle();
      if (error || !data) return fail(409, "O pedido já foi tratado");
      return reply({ approved: true, app_id: "vs" });
    }
    if (action === "pair_redeem") {
      if (!validUuid(body.request_id) || typeof body.poll_secret !== "string" ||
          !/^[\w-]{32,80}$/.test(body.poll_secret)) return fail(400, "Pedido inválido");
      if (!(await limit(req, "pair_redeem", String(body.request_id))))
        return fail(429, "Demasiados pedidos");
      const secret_hash = await sha256(body.poll_secret);
      const { data: pair } = await admin.from("vid_device_pairings")
        .select("status,poll_secret_hash,expires_at,approved_user")
        .eq("id", body.request_id).maybeSingle();
      if (!pair || pair.poll_secret_hash !== secret_hash ||
          new Date(pair.expires_at).getTime() <= Date.now()) return fail(410, "Pedido expirado");
      if (pair.status === "pending") return reply({ status: "pending" });
      if (pair.status !== "approved") return fail(410, "Pedido já utilizado");
      const { data: token_hash } = await admin.rpc("vid_redeem_pair",
        { p_id: body.request_id, p_secret_hash: secret_hash });
      if (!token_hash) return fail(409, "Pedido já utilizado");
      if (pair.approved_user) await admin.from("vs_user_data")
        .update({ connected_at: new Date().toISOString() }).eq("user_id", pair.approved_user);
      return reply({ status: "approved", token_hash, type: "magiclink" });
    }
    return fail(400, "Ação desconhecida");
  } catch (error) {
    console.error("Vista ID action failed", action, error instanceof Error ? error.name : "error");
    return fail(503, "Serviço temporariamente indisponível");
  }
});
