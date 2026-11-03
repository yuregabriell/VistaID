create or replace function public.vid_redeem_pair(p_id uuid, p_secret_hash text)
returns text language plpgsql security definer set search_path = ''
as $$
declare result_hash text;
begin
  select login_token_hash into result_hash
    from public.vid_device_pairings
    where id = p_id and poll_secret_hash = p_secret_hash
      and status = 'approved' and expires_at > now()
    for update;
  if not found then return null; end if;
  update public.vid_device_pairings
    set status = 'redeemed', login_token_hash = null
    where id = p_id;
  return result_hash;
end; $$;
revoke all on function public.vid_redeem_pair(uuid, text) from public, anon, authenticated;
grant execute on function public.vid_redeem_pair(uuid, text) to service_role;