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
  expires_at timestamptz not null default (now() + interval '15 days'),
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
  kind text not null check (kind in ('agency_extra_slots','agency_certification','listing_promotion')),
  amount bigint not null,
  currency text not null default 'XOF',
  status text not null default 'pending' check (status in ('pending','paid','failed','cancelled')),
  provider text not null default 'kkiapay',
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
  values(auth.uid(), p.agency_id, p.first_name, p.phone, p.country_code, p.country, p_city, p_mode, p_property_type, coalesce(p_photo_urls,'{}'), p_price, p_deposit_months, p_description, p_relationship, now()+interval '15 days', coalesce(a.name,''), coalesce(a.certified,false) and coalesce(a.certification_expires_at,now()) > now())
  returning * into result;
  return result;
end;
$$;

-- ---------- Relist / delete ----------
create or replace function public.get_my_listings()
returns setof public.listings language plpgsql security definer set search_path=public as $$
begin
  update public.listings set active=false where owner_id=auth.uid() and active=true and expires_at<=now();
  return query select * from public.listings where owner_id=auth.uid() order by created_at desc;
end; $$;

create or replace function public.relist_listing(p_listing_id uuid)
returns void language plpgsql security definer set search_path=public as $$
begin
  update public.listings set active=true, created_at=now(), expires_at=now()+interval '15 days'
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


-- ---------- Promotion campaigns ----------
create table if not exists public.promotions (
  id uuid primary key default gen_random_uuid(),
  listing_id uuid not null references public.listings(id) on delete cascade,
  owner_id uuid not null references public.profiles(id) on delete cascade,
  payment_id uuid references public.payments(id) on delete set null,
  duration_days integer not null check (duration_days between 5 and 15),
  target_users integer not null check (target_users >= 50 and target_users <= 5000),
  amount bigint not null check (amount > 0),
  status text not null default 'pending' check (status in ('pending','paid','active','completed','failed','cancelled')),
  starts_at timestamptz,
  ends_at timestamptz,
  delivered_count integer not null default 0,
  created_at timestamptz not null default now()
);
create index if not exists promotions_active_idx on public.promotions(status, starts_at, ends_at);
create index if not exists promotions_listing_idx on public.promotions(listing_id);

create table if not exists public.promotion_impressions (
  id uuid primary key default gen_random_uuid(),
  promotion_id uuid not null references public.promotions(id) on delete cascade,
  user_id uuid not null references public.profiles(id) on delete cascade,
  shown_at timestamptz not null default now(),
  unique(promotion_id, user_id)
);
create index if not exists promotion_impressions_promotion_idx on public.promotion_impressions(promotion_id, shown_at);

alter table public.promotions enable row level security;
alter table public.promotion_impressions enable row level security;

-- Pricing: 1,000 FCFA buys one block of 50 targeted accounts for 5 days.
-- Additional blocks of 50 users and additional blocks of 5 days multiply the price.
create or replace function public.create_promotion_intent(p_listing_id uuid, p_days integer, p_target_users integer)
returns jsonb language plpgsql security definer set search_path=public as $$
declare
  l public.listings;
  amount_value bigint;
  payment_id uuid;
  promotion_id uuid;
begin
  if p_days < 5 or p_days > 15 then raise exception 'PROMOTION_DAYS_MUST_BE_5_TO_15'; end if;
  if p_target_users < 50 or p_target_users > 5000 then raise exception 'PROMOTION_TARGET_MUST_BE_50_TO_5000'; end if;
  select * into l from public.listings where id=p_listing_id and owner_id=auth.uid();
  if l.id is null then raise exception 'LISTING_NOT_FOUND'; end if;
  if not l.active or l.expires_at <= now() then raise exception 'LISTING_NOT_ACTIVE'; end if;
  amount_value := ((p_target_users + 49) / 50) * ((p_days + 4) / 5) * 1000;
  insert into public.payments(user_id, kind, amount, currency, status, provider, metadata)
  values(auth.uid(), 'listing_promotion', amount_value, 'XOF', 'pending', 'kkiapay', jsonb_build_object('listing_id',p_listing_id,'duration_days',p_days,'target_users',p_target_users))
  returning id into payment_id;
  insert into public.promotions(listing_id, owner_id, payment_id, duration_days, target_users, amount, status)
  values(p_listing_id, auth.uid(), payment_id, p_days, p_target_users, amount_value, 'pending')
  returning id into promotion_id;
  return jsonb_build_object('payment_id',payment_id,'promotion_id',promotion_id,'amount',amount_value);
end; $$;

create or replace function public.create_extra_slots_intent()
returns jsonb language plpgsql security definer set search_path=public as $$
declare payment_id uuid; a public.agencies;
begin
  select * into a from public.agencies where owner_id=auth.uid();
  if a.id is null then raise exception 'AGENCY_NOT_FOUND'; end if;
  if coalesce(a.extra_slots_paid,false) then raise exception 'EXTRA_SLOTS_ALREADY_PAID'; end if;
  insert into public.payments(user_id,kind,amount,currency,status,provider,metadata)
  values(auth.uid(),'agency_extra_slots',5000,'XOF','pending','kkiapay',jsonb_build_object('agency_id',a.id,'extra_slots',10)) returning id into payment_id;
  return jsonb_build_object('payment_id',payment_id,'amount',5000);
end; $$;

create or replace function public.create_certification_intent(p_front_path text, p_back_path text)
returns jsonb language plpgsql security definer set search_path=public as $$
declare a public.agencies; payment_id uuid; cert_id uuid;
begin
  select * into a from public.agencies where owner_id=auth.uid();
  if a.id is null then raise exception 'AGENCY_NOT_FOUND'; end if;
  insert into public.payments(user_id, kind, amount, currency, status, provider, metadata)
  values(auth.uid(),'agency_certification',1000,'XOF','pending','kkiapay',jsonb_build_object('agency_id',a.id,'months',1)) returning id into payment_id;
  insert into public.agency_certifications(agency_id,owner_id,front_path,back_path,payment_id,status,payment_status)
  values(a.id,auth.uid(),p_front_path,p_back_path,payment_id,'pending','pending') returning id into cert_id;
  return jsonb_build_object('payment_id',payment_id,'certification_id',cert_id,'amount',1000);
end; $$;

create or replace function public.attach_payment_transaction(p_payment_id uuid, p_transaction_id text, p_success boolean)
returns void language plpgsql security definer set search_path=public as $$
begin
  update public.payments
     set provider_transaction_id=p_transaction_id,
         status=case when p_success then 'pending' else 'failed' end
   where id=p_payment_id and user_id=auth.uid();
  if not found then raise exception 'PAYMENT_NOT_FOUND'; end if;
end; $$;

-- Called by a trusted KKIAPAY webhook/Edge Function after signature validation.
create or replace function public.confirm_kkiapay_payment(p_payment_id uuid, p_transaction_id text, p_amount bigint, p_success boolean)
returns void language plpgsql security definer set search_path=public as $$
declare k text;
begin
  select kind into k from public.payments where id=p_payment_id and amount=p_amount;
  if k is null then raise exception 'PAYMENT_NOT_FOUND'; end if;
  update public.payments set provider_transaction_id=p_transaction_id, status=case when p_success then 'paid' else 'failed' end, paid_at=case when p_success then now() else null end where id=p_payment_id;
  if p_success and k='listing_promotion' then
    update public.promotions set status='active', starts_at=now(), ends_at=now()+make_interval(days=>duration_days) where payment_id=p_payment_id;
  elsif p_success and k='agency_certification' then
    update public.agency_certifications set payment_status='paid' where payment_id=p_payment_id;
  elsif p_success and k='agency_extra_slots' then
    update public.agencies a set extra_slots_paid=true, extra_slots=10 where a.owner_id=(select user_id from public.payments where id=p_payment_id);
  end if;
end; $$;

create or replace function public.get_promoted_listings(p_user_id uuid, p_mode text, p_limit integer default 5)
returns setof public.listings language plpgsql security definer set search_path=public as $$
declare u public.profiles; r public.promotions%rowtype; l public.listings%rowtype; n integer:=0; take_count integer;
begin
  select * into u from public.profiles where id=p_user_id;
  if u.id is null then return; end if;
  if auth.uid() is distinct from p_user_id then raise exception 'FORBIDDEN'; end if;
  for r in select pr.* from public.promotions pr join public.listings li on li.id=pr.listing_id where pr.status='active' and pr.starts_at<=now() and pr.ends_at>now() and li.active=true and li.expires_at>now() and li.country=u.country and li.mode=p_mode and pr.delivered_count<pr.target_users order by (case when li.city=u.city then 0 else 1 end), pr.created_at desc loop
    if exists(select 1 from public.promotion_impressions pi where pi.promotion_id=r.id and pi.user_id=u.id) then continue; end if;
    select * into l from public.listings where id=r.listing_id;
    if l.id is null then continue; end if;
    insert into public.promotion_impressions(promotion_id,user_id) values(r.id,u.id) on conflict do nothing;
    update public.promotions set delivered_count=delivered_count+1, status=case when delivered_count+1>=target_users then 'completed' else status end where id=r.id;
    n:=n+1; return next l;
    exit when n>=greatest(1,least(p_limit,20));
  end loop;
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
  for r in select schemaname, tablename, policyname from pg_policies where schemaname='public' and tablename in ('profiles','agencies','listings','favorites','saved_searches','chats','chat_messages','reports','agency_certifications','payments','notifications','promotions','promotion_impressions') loop
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
create policy promotions_owner_select on public.promotions for select to authenticated using (owner_id=auth.uid());
create policy promotions_owner_insert on public.promotions for insert to authenticated with check (owner_id=auth.uid());
create policy promotions_owner_update on public.promotions for update to authenticated using (owner_id=auth.uid()) with check (owner_id=auth.uid());
create policy promotion_impressions_owner_select on public.promotion_impressions for select to authenticated using (user_id=auth.uid());

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
grant select, insert, update on public.promotions to authenticated;
grant select on public.promotion_impressions to authenticated;
grant select, update on public.notifications to authenticated;
grant all on all tables in schema public to service_role;

-- Allow authenticated clients to use the country helper functions.
grant execute on function public.is_same_country(text) to authenticated;
grant execute on function public.my_country() to authenticated;
grant execute on function public.is_admin() to authenticated;

grant execute on function public.publish_listing(text,text,text,text[],bigint,bigint,text,text) to authenticated;
grant execute on function public.get_my_listings() to authenticated;
grant execute on function public.relist_listing(uuid) to authenticated;
grant execute on function public.delete_listing(uuid) to authenticated;
grant execute on function public.increment_listing_views(uuid) to authenticated;
grant execute on function public.toggle_favorite(uuid) to authenticated;
grant execute on function public.delete_my_account() to authenticated;
grant execute on function public.create_promotion_intent(uuid,integer,integer) to authenticated;
grant execute on function public.create_certification_intent(text,text) to authenticated;
grant execute on function public.create_extra_slots_intent() to authenticated;
grant execute on function public.attach_payment_transaction(uuid,text,boolean) to authenticated;
grant execute on function public.get_promoted_listings(uuid,text,integer) to authenticated;
grant execute on function public.confirm_kkiapay_payment(uuid,text,bigint,boolean) to service_role;

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


-- ---------- V10 migration: payment provider + promotion kinds ----------
alter table public.payments drop constraint if exists payments_kind_check;
alter table public.payments add constraint payments_kind_check check (kind in ('agency_extra_slots','agency_certification','listing_promotion'));
alter table public.payments alter column provider set default 'kkiapay';

-- ---------- V9/V10 migration: 15-day listing lifetime ----------
alter table public.listings alter column expires_at set default (now() + interval '15 days');
update public.listings set expires_at = created_at + interval '15 days' where active=true and expires_at < created_at + interval '15 days';

-- Certification price is 1,000 FCFA/month. The application records the amount in the payment intent.

select 'ImmoLink promotions + 15-day lifetime installed' as status,
       to_regclass('public.promotions') as promotions_table,
       to_regclass('public.promotion_impressions') as promotion_impressions_table;
