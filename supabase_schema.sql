-- ImmoLink / Supabase complete database setup (V8 - dependency order fixed)
-- Project: hraiykeenouojxrcwrlx
-- Run this whole script in Supabase SQL Editor.
-- IMPORTANT: tables are created before helper functions that reference them.
-- This script is safe to run again on an already initialized project.

create extension if not exists pgcrypto;

-- ---------- Profiles ----------
create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  first_name text not null default '',
  email text not null default '',
  phone text not null default '',
  country text not null check (country in ('Togo','Bénin','Mali','Burkina Faso','Côte d’Ivoire')),
  country_code text not null default '',
  city text not null default '',
  agency boolean not null default false,
  agency_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Migration sûre si le schéma précédent existe déjà.
alter table public.profiles add column if not exists email text not null default '';

create index if not exists profiles_country_idx on public.profiles(country);

-- Email de connexion : l'authentification réelle reste gérée par auth.users.
-- Cet index permet aussi de retrouver rapidement le profil par e-mail sans stocker le mot de passe.
create unique index if not exists profiles_email_unique_idx
on public.profiles(lower(email)) where email <> '';

-- ---------- Agencies ----------
create table if not exists public.agencies (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null unique references public.profiles(id) on delete cascade,
  name text not null,
  logo_url text not null default '',
  address text not null default '',
  email text not null,
  certified boolean not null default false,
  certification_expires_at timestamptz,
  certification_status text not null default 'none' check (certification_status in ('none','pending','approved','rejected','expired')),
  extra_slots_paid boolean not null default false,
  extra_slots integer not null default 0 check (extra_slots >= 0 and extra_slots <= 10),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.profiles drop constraint if exists profiles_agency_id_fkey;
alter table public.profiles add constraint profiles_agency_id_fkey foreign key (agency_id) references public.agencies(id) on delete set null;

create index if not exists agencies_owner_idx on public.agencies(owner_id);

-- ---------- Listings ----------
create table if not exists public.listings (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.profiles(id) on delete cascade,
  agency_id uuid references public.agencies(id) on delete set null,
  owner_name text not null default '',
  owner_phone text not null default '',
  owner_country_code text not null default '',
  country text not null check (country in ('Togo','Bénin','Mali','Burkina Faso','Côte d’Ivoire')),
  city text not null,
  mode text not null check (mode in ('rent','sale','bail')),
  property_type text not null,
  photo_urls text[] not null default '{}',
  price bigint not null default 0 check (price >= 0),
  deposit_months bigint not null default 0 check (deposit_months >= 0),
  description text not null default '',
  relationship text not null default '',
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default (now() + interval '7 days'),
  active boolean not null default true,
  views bigint not null default 0,
  owner_agency_name text not null default '',
  agency_certified boolean not null default false
);

create index if not exists listings_country_mode_active_idx on public.listings(country, mode, active);
create index if not exists listings_owner_idx on public.listings(owner_id);
create index if not exists listings_agency_idx on public.listings(agency_id);
create index if not exists listings_expires_idx on public.listings(expires_at) where active = true;

-- ---------- Favorites ----------
create table if not exists public.favorites (
  user_id uuid not null references public.profiles(id) on delete cascade,
  listing_id uuid not null references public.listings(id) on delete cascade,
  country text not null,
  created_at timestamptz not null default now(),
  primary key (user_id, listing_id)
);

-- ---------- Saved searches ----------
create table if not exists public.saved_searches (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  country text not null,
  mode text not null check (mode in ('rent','sale','bail')),
  property_type text not null default '',
  city text not null default '',
  min_price bigint not null default 0,
  max_price bigint,
  active boolean not null default true,
  created_at timestamptz not null default now()
);

create index if not exists saved_searches_match_idx on public.saved_searches(country, mode, city, property_type, active);

-- ---------- Chats ----------
create table if not exists public.chats (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid not null references public.listings(id) on delete cascade,
  country text not null,
  participants uuid[] not null,
  last_text text not null default '',
  updated_at timestamptz not null default now()
);

create unique index if not exists chats_unique_listing_participants_idx
on public.chats(listing_id, participants);

create table if not exists public.chat_messages (
  id uuid primary key default gen_random_uuid(),
  chat_id uuid not null references public.chats(id) on delete cascade,
  sender_id uuid not null references public.profiles(id) on delete cascade,
  text text not null,
  created_at timestamptz not null default now()
);

create index if not exists chat_messages_chat_created_idx on public.chat_messages(chat_id, created_at);

-- ---------- Reports ----------
create table if not exists public.reports (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid not null references public.listings(id) on delete cascade,
  reporter_id uuid not null references public.profiles(id) on delete cascade,
  country text not null,
  reason text not null,
  status text not null default 'open' check (status in ('open','reviewing','resolved','rejected')),
  created_at timestamptz not null default now(),
  reviewed_at timestamptz
);

-- ---------- Agency certification ----------
create table if not exists public.agency_certifications (
  id uuid primary key default gen_random_uuid(),
  agency_id uuid not null references public.agencies(id) on delete cascade,
  owner_id uuid not null references public.profiles(id) on delete cascade,
  front_path text not null,
  back_path text not null,
  status text not null default 'pending' check (status in ('pending','approved','rejected')),
  payment_status text not null default 'pending' check (payment_status in ('pending','paid','failed')),
  payment_id uuid,
  submitted_at timestamptz not null default now(),
  reviewed_at timestamptz,
  expires_at timestamptz
);

create index if not exists agency_certifications_agency_idx on public.agency_certifications(agency_id, status);

-- ---------- Payments ----------
create table if not exists public.payments (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  kind text not null check (kind in ('agency_extra_slots','agency_certification')),
  amount bigint not null,
  currency text not null default 'XOF',
  status text not null default 'pending' check (status in ('pending','paid','failed','cancelled')),
  provider text not null default 'cinetpay',
  provider_transaction_id text,
  payment_url text,
  metadata jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  paid_at timestamptz
);

create index if not exists payments_user_idx on public.payments(user_id, created_at desc);

-- ---------- Notifications ----------
create table if not exists public.notifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  type text not null,
  title text not null,
  body text not null,
  data jsonb not null default '{}'::jsonb,
  read boolean not null default false,
  created_at timestamptz not null default now()
);

create index if not exists notifications_user_idx on public.notifications(user_id, created_at desc);

-- ---------- Helpers ----------
create or replace function public.is_same_country(target_country text)
returns boolean
language sql stable security definer set search_path = public
as $$
  select exists (
    select 1 from public.profiles p
    where p.id = auth.uid() and p.country = target_country
  );
$$;


create or replace function public.my_country()
returns text
language sql stable security definer set search_path = public
as $$
  select country from public.profiles where id = auth.uid();
$$;

create or replace function public.is_admin()
returns boolean
language sql stable security definer set search_path = public
as $$
  select coalesce((auth.jwt() -> 'app_metadata' ->> 'role') = 'admin', false);
$$;


-- ---------- Trigger: create profile from Auth metadata ----------
create or replace function public.handle_new_user()
returns trigger
language plpgsql security definer set search_path = public
as $$
begin
  insert into public.profiles (id, first_name, email, phone, country, country_code, city)
  values (
    new.id,
    coalesce(new.raw_user_meta_data ->> 'firstName',''),
    coalesce(new.email,''),
    coalesce(new.raw_user_meta_data ->> 'phone',''),
    coalesce(new.raw_user_meta_data ->> 'country','Togo'),
    coalesce(new.raw_user_meta_data ->> 'countryCode','00228'),
    coalesce(new.raw_user_meta_data ->> 'city','')
  )
  on conflict (id) do update set
    first_name = excluded.first_name,
    email = excluded.email,
    phone = excluded.phone,
    country = excluded.country,
    country_code = excluded.country_code,
    city = excluded.city,
    updated_at = now();
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
after insert on auth.users
for each row execute procedure public.handle_new_user();

-- Keep the profile e-mail synchronized if the Auth e-mail is changed later.
create or replace function public.sync_auth_user_email()
returns trigger
language plpgsql security definer set search_path = public
as $$
begin
  update public.profiles set email = coalesce(new.email,''), updated_at = now() where id = new.id;
  return new;
end;
$$;

drop trigger if exists on_auth_user_email_updated on auth.users;
create trigger on_auth_user_email_updated
after update of email on auth.users
for each row execute procedure public.sync_auth_user_email();

-- ---------- Generic updated_at ----------
create or replace function public.touch_updated_at()
returns trigger language plpgsql as $$
begin new.updated_at = now(); return new; end; $$;

drop trigger if exists profiles_touch_updated_at on public.profiles;
create trigger profiles_touch_updated_at before update on public.profiles for each row execute procedure public.touch_updated_at();
drop trigger if exists agencies_touch_updated_at on public.agencies;
create trigger agencies_touch_updated_at before update on public.agencies for each row execute procedure public.touch_updated_at();

-- ---------- Publish listing with quota checks ----------
create or replace function public.publish_listing(
  p_city text,
  p_mode text,
  p_property_type text,
  p_photo_urls text[],
  p_price bigint,
  p_deposit_months bigint,
  p_description text,
  p_relationship text
)
returns public.listings
language plpgsql security definer set search_path = public
as $$
declare
  p public.profiles;
  a public.agencies;
  active_count integer;
  max_count integer;
  result public.listings;
begin
  select * into p from public.profiles where id = auth.uid();
  if p.id is null then raise exception 'PROFILE_NOT_FOUND'; end if;
  if p.country not in ('Togo','Bénin','Mali','Burkina Faso','Côte d’Ivoire') then raise exception 'COUNTRY_NOT_ALLOWED'; end if;
  if p.city <> p_city then
    -- City may be different only when publishing for a city in the same country; the app supplies selected city.
    null;
  end if;
  if coalesce(array_length(p_photo_urls,1),0) > 4 then raise exception 'MAX_4_PHOTOS'; end if;

  if p.agency and p.agency_id is not null then
    select * into a from public.agencies where id = p.agency_id;
    max_count := case when coalesce(a.extra_slots_paid,false) then 25 else 15 end;
  else
    max_count := 3;
  end if;

  select count(*) into active_count from public.listings where owner_id = auth.uid() and active = true and expires_at > now();
  if active_count >= max_count then raise exception 'LISTING_QUOTA_REACHED'; end if;

  insert into public.listings(owner_id, agency_id, owner_name, owner_phone, owner_country_code, country, city, mode, property_type, photo_urls, price, deposit_months, description, relationship, expires_at, owner_agency_name, agency_certified)
  values(auth.uid(), p.agency_id, p.first_name, p.phone, p.country_code, p.country, p_city, p_mode, p_property_type, coalesce(p_photo_urls,'{}'), p_price, p_deposit_months, p_description, p_relationship, now()+interval '7 days', coalesce(a.name,''), coalesce(a.certified,false) and coalesce(a.certification_expires_at,now()) > now())
  returning * into result;
  return result;
end;
$$;

-- ---------- Relist / delete ----------
create or replace function public.relist_listing(p_listing_id uuid)
returns void language plpgsql security definer set search_path=public as $$
begin
  update public.listings set active=true, created_at=now(), expires_at=now()+interval '7 days'
  where id=p_listing_id and owner_id=auth.uid();
  if not found then raise exception 'LISTING_NOT_FOUND'; end if;
end; $$;

create or replace function public.delete_listing(p_listing_id uuid)
returns void language plpgsql security definer set search_path=public as $$
begin
  update public.listings set active=false where id=p_listing_id and owner_id=auth.uid();
  if not found then raise exception 'LISTING_NOT_FOUND'; end if;
end; $$;

-- ---------- Increment views ----------
create or replace function public.increment_listing_views(p_listing_id uuid)
returns void language sql security definer set search_path=public as $$
  update public.listings set views=views+1 where id=p_listing_id and active=true;
$$;

-- ---------- Toggle favorite ----------
create or replace function public.toggle_favorite(p_listing_id uuid)
returns boolean language plpgsql security definer set search_path=public as $$
declare exists_now boolean;
begin
  select exists(select 1 from public.favorites where user_id=auth.uid() and listing_id=p_listing_id) into exists_now;
  if exists_now then
    delete from public.favorites where user_id=auth.uid() and listing_id=p_listing_id;
    return false;
  else
    insert into public.favorites(user_id, listing_id, country)
    select auth.uid(), l.id, l.country from public.listings l where l.id=p_listing_id and l.country=public.my_country();
    return true;
  end if;
end; $$;

-- ---------- Account deletion ----------
create or replace function public.delete_my_account()
returns void language plpgsql security definer set search_path=public,auth as $$
begin
  delete from auth.users where id=auth.uid();
end; $$;

-- ---------- RLS ----------
alter table public.profiles enable row level security;
alter table public.agencies enable row level security;
alter table public.listings enable row level security;
alter table public.favorites enable row level security;
alter table public.saved_searches enable row level security;
alter table public.chats enable row level security;
alter table public.chat_messages enable row level security;
alter table public.reports enable row level security;
alter table public.agency_certifications enable row level security;
alter table public.payments enable row level security;
alter table public.notifications enable row level security;

-- Drop existing policies so the script is rerunnable.
do $$ declare r record; begin
  for r in select schemaname, tablename, policyname from pg_policies where schemaname='public' and tablename in ('profiles','agencies','listings','favorites','saved_searches','chats','chat_messages','reports','agency_certifications','payments','notifications') loop
    execute format('drop policy if exists %I on %I.%I', r.policyname, r.schemaname, r.tablename);
  end loop;
end $$;

-- Profiles: users can manage themselves; signed-in users can see profiles in their own country.
create policy profiles_select_same_country on public.profiles for select to authenticated using (id=auth.uid() or country=public.my_country());
create policy profiles_insert_self on public.profiles for insert to authenticated with check (id=auth.uid());
create policy profiles_update_self on public.profiles for update to authenticated using (id=auth.uid()) with check (id=auth.uid());

create policy agencies_select_same_country on public.agencies for select to authenticated using (owner_id=auth.uid() or exists(select 1 from public.profiles owner where owner.id=owner_id and owner.country=public.my_country()));
create policy agencies_insert_self on public.agencies for insert to authenticated with check (owner_id=auth.uid());
create policy agencies_update_self on public.agencies for update to authenticated using (owner_id=auth.uid()) with check (owner_id=auth.uid());

create policy listings_select_same_country on public.listings for select to authenticated using (country=public.my_country());
create policy listings_insert_self on public.listings for insert to authenticated with check (owner_id=auth.uid() and country=public.my_country());
create policy listings_update_self on public.listings for update to authenticated using (owner_id=auth.uid()) with check (owner_id=auth.uid() and country=public.my_country());
create policy listings_delete_self on public.listings for delete to authenticated using (owner_id=auth.uid());

create policy favorites_self_select on public.favorites for select to authenticated using (user_id=auth.uid());
create policy favorites_self_insert on public.favorites for insert to authenticated with check (user_id=auth.uid());
create policy favorites_self_delete on public.favorites for delete to authenticated using (user_id=auth.uid());

create policy saved_searches_self on public.saved_searches for all to authenticated using (user_id=auth.uid()) with check (user_id=auth.uid());

create policy chats_participant_select on public.chats for select to authenticated using (auth.uid()=any(participants));
create policy chats_participant_insert on public.chats for insert to authenticated with check (auth.uid()=any(participants));
create policy chats_participant_update on public.chats for update to authenticated using (auth.uid()=any(participants)) with check (auth.uid()=any(participants));
create policy chats_participant_delete on public.chats for delete to authenticated using (auth.uid()=any(participants));

create policy messages_participant_select on public.chat_messages for select to authenticated using (exists(select 1 from public.chats c where c.id=chat_id and auth.uid()=any(c.participants)));
create policy messages_participant_insert on public.chat_messages for insert to authenticated with check (sender_id=auth.uid() and exists(select 1 from public.chats c where c.id=chat_id and auth.uid()=any(c.participants)));

create policy reports_insert_self on public.reports for insert to authenticated with check (reporter_id=auth.uid() and country=public.my_country());
create policy reports_select_self_or_admin on public.reports for select to authenticated using (reporter_id=auth.uid() or public.is_admin());
create policy reports_update_admin on public.reports for update to authenticated using (public.is_admin()) with check (public.is_admin());

create policy certifications_self_select on public.agency_certifications for select to authenticated using (owner_id=auth.uid() or public.is_admin());
create policy certifications_self_insert on public.agency_certifications for insert to authenticated with check (owner_id=auth.uid());

create policy payments_self_select on public.payments for select to authenticated using (user_id=auth.uid());
create policy notifications_self on public.notifications for all to authenticated using (user_id=auth.uid()) with check (user_id=auth.uid());

-- Least privilege Data API grants.
revoke all on all tables in schema public from anon;
grant select, insert, update, delete on public.profiles to authenticated;
grant select, insert, update on public.agencies to authenticated;
grant select, insert, update, delete on public.listings to authenticated;
grant select, insert, delete on public.favorites to authenticated;
grant select, insert, update, delete on public.saved_searches to authenticated;
grant select, insert, update, delete on public.chats to authenticated;
grant select, insert on public.chat_messages to authenticated;
grant select, insert, update on public.reports to authenticated;
grant select, insert on public.agency_certifications to authenticated;
grant select on public.payments to authenticated;
grant select, update on public.notifications to authenticated;
grant all on all tables in schema public to service_role;

-- Allow authenticated clients to use the country helper functions.
grant execute on function public.is_same_country(text) to authenticated;
grant execute on function public.my_country() to authenticated;
grant execute on function public.is_admin() to authenticated;

grant execute on function public.publish_listing(text,text,text,text[],bigint,bigint,text,text) to authenticated;
grant execute on function public.relist_listing(uuid) to authenticated;
grant execute on function public.delete_listing(uuid) to authenticated;
grant execute on function public.increment_listing_views(uuid) to authenticated;
grant execute on function public.toggle_favorite(uuid) to authenticated;
grant execute on function public.delete_my_account() to authenticated;

-- ---------- Storage ----------
insert into storage.buckets (id, name, public) values ('listing-photos','listing-photos',true) on conflict (id) do update set public=true;
insert into storage.buckets (id, name, public) values ('agency-logos','agency-logos',true) on conflict (id) do update set public=true;
insert into storage.buckets (id, name, public) values ('agency-verification','agency-verification',false) on conflict (id) do update set public=false;

drop policy if exists listing_photos_insert on storage.objects;
drop policy if exists listing_photos_select on storage.objects;
drop policy if exists listing_photos_delete on storage.objects;
create policy listing_photos_insert on storage.objects for insert to authenticated with check (bucket_id='listing-photos' and (storage.foldername(name))[1]=auth.uid()::text);
create policy listing_photos_select on storage.objects for select to authenticated using (bucket_id='listing-photos');
create policy listing_photos_delete on storage.objects for delete to authenticated using (bucket_id='listing-photos' and (storage.foldername(name))[1]=auth.uid()::text);

drop policy if exists agency_logos_insert on storage.objects;
drop policy if exists agency_logos_select on storage.objects;
drop policy if exists agency_logos_delete on storage.objects;
create policy agency_logos_insert on storage.objects for insert to authenticated with check (bucket_id='agency-logos' and (storage.foldername(name))[1]=auth.uid()::text);
create policy agency_logos_select on storage.objects for select to authenticated using (bucket_id='agency-logos');
create policy agency_logos_delete on storage.objects for delete to authenticated using (bucket_id='agency-logos' and (storage.foldername(name))[1]=auth.uid()::text);

drop policy if exists agency_verification_insert on storage.objects;
drop policy if exists agency_verification_select on storage.objects;
drop policy if exists agency_verification_delete on storage.objects;
create policy agency_verification_insert on storage.objects for insert to authenticated with check (bucket_id='agency-verification' and (storage.foldername(name))[1]=auth.uid()::text);
create policy agency_verification_select on storage.objects for select to authenticated using (bucket_id='agency-verification' and ((storage.foldername(name))[1]=auth.uid()::text or public.is_admin()));
create policy agency_verification_delete on storage.objects for delete to authenticated using (bucket_id='agency-verification' and ((storage.foldername(name))[1]=auth.uid()::text or public.is_admin()));

-- ---------- Realtime ----------
do $$
begin
  if not exists (select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='chat_messages') then
    alter publication supabase_realtime add table public.chat_messages;
  end if;
  if not exists (select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename='notifications') then
    alter publication supabase_realtime add table public.notifications;
  end if;
end $$;

-- ---------- Expiration ----------
create or replace function public.expire_listings()
returns void language sql security definer set search_path=public as $$
  update public.listings set active=false where active=true and expires_at <= now();
$$;
grant execute on function public.expire_listings() to service_role;

-- Verification query:
select 'ImmoLink schema installed' as status,
       to_regclass('public.profiles') as profiles_table,
       to_regclass('public.listings') as listings_table;
