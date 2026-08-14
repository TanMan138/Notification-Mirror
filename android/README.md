# Notification Mirror (Android)

Android app that listens to device notifications and syncs them to a Supabase project via Realtime WebSockets. Notifications from messaging apps can be replied to from the Mac, and clearing one on either device clears it on the other. Works with the Mac menu bar app in this repository — see [macos](../macos/).

**In this repo:** Part of [Android–Mac Notification Mirror](../README.md). For architecture, start from the root README.

## Requirements

- Android 9+ (API 28+)
- Notification Access and (recommended) a battery optimization exemption
- A free Supabase project of your own

## Setup

### 1. Install the APK

Download `app-release.apk` from the [releases page](../../releases) and open it
on the phone. Android will ask you to allow installing from your browser or file
manager the first time.

The APK ships with no credentials, so it opens straight to the setup screen.

### 2. Create a Supabase project

Sign up at [supabase.com](https://supabase.com) and create a free project. It is yours alone — this app never uses a shared backend.

### 3. Create the table

Open the project's SQL editor and run the SQL the app shows on its setup screen (tap **Copy SQL**). It is reproduced here:

```sql
create table if not exists public.notifications (
  id bigint primary key generated always as identity,
  notification_key text not null,
  app_name text,
  title text not null,
  body_text text,
  base64_icon text,
  is_dismissed boolean not null default false,
  created_at timestamptz not null default now()
);

create index if not exists notifications_created_idx
  on public.notifications (created_at desc);

-- Separate statements, not part of create table, so that a project created
-- before replies existed picks them up on a re-run.
alter table public.notifications add column if not exists is_repliable boolean not null default false;
alter table public.notifications add column if not exists reply_text text;
-- null | pending | sent | failed. The Mac writes pending; the phone writes the outcome.
alter table public.notifications add column if not exists reply_status text;

-- Required, or the UPDATE payload omits notification_key and phone-side
-- dismissal silently stops working.
alter table public.notifications replica identity full;
alter table public.notifications enable row level security;

-- Policies filter rows that privileges already allow, so the grant is separate.
-- Without it the app fails with a permission error if the project was created
-- with "Automatically expose new tables" turned off.
grant select, insert, update, delete on public.notifications to anon;

-- Without this the table is not streamed at all: the connection test still
-- passes, but no notification ever reaches the Mac and no dismissal ever
-- reaches the phone.
do $$
begin
  alter publication supabase_realtime add table public.notifications;
exception when duplicate_object then null;
end $$;

-- create policy has no "if not exists", and this script is meant to be re-run.
drop policy if exists anon_select on public.notifications;
drop policy if exists anon_insert on public.notifications;
drop policy if exists anon_update on public.notifications;
drop policy if exists anon_delete on public.notifications;

create policy anon_select on public.notifications for select to anon using (true);
create policy anon_insert on public.notifications for insert to anon with check (true);
create policy anon_update on public.notifications for update to anon using (true);
create policy anon_delete on public.notifications for delete to anon using (true);

-- Lets the apps confirm the settings above actually took effect, since none of
-- them is visible through the normal API.
-- The return type gained a column, and create or replace cannot widen it.
drop function if exists public.mirror_setup_status();
create or replace function public.mirror_setup_status()
returns table (in_publication boolean, replica_identity_full boolean, reply_columns boolean)
language sql
security definer
set search_path = ''
as $$
  select
    exists (
      select 1 from pg_catalog.pg_publication_tables
      where pubname = 'supabase_realtime'
        and schemaname = 'public'
        and tablename = 'notifications'
    ),
    coalesce((
      select c.relreplident = 'f'
      from pg_catalog.pg_class c
      join pg_catalog.pg_namespace n on n.oid = c.relnamespace
      where n.nspname = 'public' and c.relname = 'notifications'
    ), false),
    exists (
      select 1 from pg_catalog.pg_attribute a
      join pg_catalog.pg_class c on c.oid = a.attrelid
      join pg_catalog.pg_namespace n on n.oid = c.relnamespace
      where n.nspname = 'public' and c.relname = 'notifications'
        and a.attname = 'reply_status' and not a.attisdropped
    );
$$;

grant execute on function public.mirror_setup_status() to anon;

-- Deletes rows hourly. The phone also sweeps, but only while it is running,
-- so without this a busy month can fill the free tier.
create extension if not exists pg_cron;

select cron.schedule(
  'notification-mirror-retention',
  '0 * * * *',
  $$delete from public.notifications where created_at < now() - interval '24 hours'$$
);
```

The whole script is safe to re-run.

**Upgrading from v1.0.0?** Run the script again. Replying from the Mac needs three
columns that projects set up before it do not have, and **Test connection** says so
if they are missing. Everything else keeps working in the meantime.

If `create extension pg_cron` errors, enable **pg_cron** under Database → Extensions in the dashboard, then re-run.

### 4. Connect the app

From **Project Settings → API**, copy the project URL and the **publishable** key into the app, then tap **Test connection**.

> **Use the publishable key, never the service role key.** The service role key bypasses row level security entirely: anyone who extracted it from the app could read, alter, or delete everything in your database, not just the notifications table. The app refuses a service role key on purpose.

### 5. Grant permissions and pair

- Enable **Notification Access** when prompted.
- Optionally exempt the app from battery optimization for reliable mirroring.
- Tap **Generate pairing code** and paste the code into the Mac app. It expires after five minutes and carries your credentials, so do not share it.

## Checking it works

The **Testing** card has two buttons:

- **Send test notification** posts an ordinary notification. It should appear on the Mac with a *Clear on Phone* action and no reply field.
- **Send repliable test notification** posts one shaped like a messaging app's, carrying a `RemoteInput` reply action. Reply to it from the Mac and the phone answers with a *Reply received* notification, which mirrors back — so the whole round trip is visible without anyone messaging you. Replying a second time to the same, now-cleared notification exercises the failure path: the Mac reports the reply as undelivered and returns your text.

Group summaries — the collapsed entry a messaging app posts next to the real notification — are not mirrored, so one message produces one banner.

## Building from source

Open the **android** folder in Android Studio (not the repository root) and sync Gradle.

Build-time credentials are optional and only used when nothing has been entered in the app:

```bash
cp local.properties.example local.properties
```

```properties
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_PUBLISHABLE_KEY=your_publishable_key_here
```

`local.properties` is gitignored.

Gradle needs JDK 21 or earlier.

## License

Use and modify as you like.
