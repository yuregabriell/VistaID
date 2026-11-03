-- Vista ID / VistaStream v1. Apply once through Supabase SQL Editor or CLI.
-- No movie bytes, magnets, credentials or streams are stored here.
create extension if not exists pgcrypto;

create table if not exists public.vid_profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  username text not null unique
    check (username ~ '^[a-z0-9_]{3,24}$'),
  display_name text not null default ''
    check (char_length(display_name) <= 60),
  created_at timestamptz not null default now()
);
alter table public.vid_profiles enable row level security;
create policy "read own Vista ID" on public.vid_profiles
  for select to authenticated using (user_id = (select auth.uid()));
create policy "edit own display name" on public.vid_profiles
  for update to authenticated using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));
revoke all on public.vid_profiles from anon, authenticated;
grant select on public.vid_profiles to authenticated;
grant update(display_name) on public.vid_profiles to authenticated;

-- One bounded row per user and one VS-specific table. JSONB is TOAST-compressed
-- when appropriate, but small granular columns prevent a giant rewritten blob.
create table if not exists public.vs_user_data (
  user_id uuid primary key references auth.users(id) on delete cascade,
  settings jsonb not null default '{}'::jsonb
    check (jsonb_typeof(settings) = 'object' and octet_length(settings::text) <= 16384),
  favorites jsonb not null default '[]'::jsonb
    check (jsonb_typeof(favorites) = 'array' and octet_length(favorites::text) <= 32768),
  progress jsonb not null default '{}'::jsonb
    check (jsonb_typeof(progress) = 'object' and octet_length(progress::text) <= 65536),
  connected_at timestamptz,
  version bigint not null default 1,
  updated_at timestamptz not null default now()
);
alter table public.vs_user_data enable row level security;
create policy "read own VS" on public.vs_user_data
  for select to authenticated using (user_id = (select auth.uid()));
create policy "update own VS" on public.vs_user_data
  for update to authenticated using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));
revoke all on public.vs_user_data from anon, authenticated;
grant select on public.vs_user_data to authenticated;
grant update(settings, favorites, progress) on public.vs_user_data to authenticated;

create or replace function public.vs_mark_update()
returns trigger language plpgsql set search_path = ''
as $$
begin
  new.updated_at := now();
  new.version := old.version + 1;
  return new;
end; $$;
create trigger vs_user_data_touch before update on public.vs_user_data
  for each row execute function public.vs_mark_update();

-- Private-by-policy control data. These are intentionally inaccessible via
-- anon/authenticated PostgREST; only the privileged Edge Function may use them.
create table if not exists public.vid_auth_secrets (
  user_id uuid primary key references auth.users(id) on delete cascade,
  seed_b64 text not null default encode(gen_random_bytes(32), 'base64'),
  last_used_step bigint not null default -1
);
alter table public.vid_auth_secrets enable row level security;
revoke all on public.vid_auth_secrets from anon, authenticated;

create table if not exists public.vid_device_pairings (
  id uuid primary key default gen_random_uuid(),
  app_id text not null check (app_id = 'vs'),
  poll_secret_hash text not null,
  verification_code text not null,
  approved_user uuid references auth.users(id) on delete cascade,
  login_token_hash text,
  status text not null default 'pending'
    check (status in ('pending', 'approved', 'redeemed', 'cancelled')),
  expires_at timestamptz not null default (now() + interval '2 minutes'),
  created_at timestamptz not null default now()
);
create index if not exists vid_device_pairings_expiry on public.vid_device_pairings(expires_at);
alter table public.vid_device_pairings enable row level security;
revoke all on public.vid_device_pairings from anon, authenticated;

create table if not exists public.vid_auth_attempts (
  bucket_key text primary key,
  tries integer not null default 1,
  reset_at timestamptz not null
);
alter table public.vid_auth_attempts enable row level security;
revoke all on public.vid_auth_attempts from anon, authenticated;

-- Atomic limiter and one-time consumption, callable ONLY using service-role.
create or replace function public.vid_limit_attempt(
  p_key text, p_max integer default 10
) returns boolean language plpgsql security definer set search_path = ''
as $$
declare n integer;
begin
  if length(p_key) > 160 or p_max < 1 or p_max > 100 then return false; end if;
  insert into public.vid_auth_attempts(bucket_key, tries, reset_at)
    values(p_key, 1, now() + interval '60 seconds')
  on conflict(bucket_key) do update
    set tries = case when public.vid_auth_attempts.reset_at < now()
      then 1 else public.vid_auth_attempts.tries + 1 end,
        reset_at = case when public.vid_auth_attempts.reset_at < now()
      then now() + interval '60 seconds' else public.vid_auth_attempts.reset_at end
  returning tries into n;
  return n <= p_max;
end; $$;
revoke all on function public.vid_limit_attempt(text, integer) from public, anon, authenticated;
grant execute on function public.vid_limit_attempt(text, integer) to service_role;

create or replace function public.vid_consume_step(p_user uuid, p_step bigint)
returns boolean language plpgsql security definer set search_path = ''
as $$
declare found_id uuid;
begin
  update public.vid_auth_secrets
    set last_used_step = p_step
    where user_id = p_user and last_used_step < p_step
    returning user_id into found_id;
  return found_id is not null;
end; $$;
revoke all on function public.vid_consume_step(uuid, bigint) from public, anon, authenticated;
grant execute on function public.vid_consume_step(uuid, bigint) to service_role;

create or replace function public.vid_redeem_pair(p_id uuid, p_secret_hash text)
returns text language plpgsql security definer set search_path = ''
as $$
declare result_hash text;
begin
  with claimed as (
    select id, login_token_hash
    from public.vid_device_pairings
    where id = p_id and poll_secret_hash = p_secret_hash
      and status = 'approved' and expires_at > now()
    for update
  ), marked as (
    update public.vid_device_pairings as p
    set status = 'redeemed', login_token_hash = null
    from claimed
    where p.id = claimed.id
    returning claimed.login_token_hash
  )
  select login_token_hash into result_hash from marked;
  return result_hash;
end; $$;
revoke all on function public.vid_redeem_pair(uuid, text) from public, anon, authenticated;
grant execute on function public.vid_redeem_pair(uuid, text) to service_role;

-- Signup uses ordinary Supabase email+password Auth; this trigger creates the
-- global username profile and the sole VS data row atomically.
create or replace function public.vid_on_user_created()
returns trigger language plpgsql security definer set search_path = ''
as $$
declare handle text;
begin
  handle := lower(trim(coalesce(new.raw_user_meta_data->>'username', '')));
  if handle !~ '^[a-z0-9_]{3,24}$' then
    raise exception 'Vista ID username must be 3-24 characters [a-z0-9_]'
      using errcode = '22023';
  end if;
  insert into public.vid_profiles(user_id, username, display_name)
    values (new.id, handle, handle);
  insert into public.vs_user_data(user_id) values (new.id);
  insert into public.vid_auth_secrets(user_id) values (new.id);
  return new;
end; $$;
drop trigger if exists vid_after_signup on auth.users;
create trigger vid_after_signup after insert on auth.users
  for each row execute function public.vid_on_user_created();

revoke all on function public.vid_on_user_created() from public, anon, authenticated;
