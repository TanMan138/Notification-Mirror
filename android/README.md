# Notification Mirror (Android)

Android app that listens to device notifications and syncs them to a Supabase project via Realtime WebSockets. Works with the Mac menu bar app in this repository — see [macos](../macos/).

**In this repo:** Part of [Android–Mac Notification Mirror](../README.md). For architecture, start from the root README.

## Requirements

- Android 9+ (API 28+)
- Notification Access and (recommended) a battery optimization exemption
- A free Supabase project of your own

## Setup

### 1. Install the APK

Download the latest APK from the releases page and install it.

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

create policy anon_select on public.notifications for select to anon using (true);
create policy anon_insert on public.notifications for insert to anon with check (true);
create policy anon_update on public.notifications for update to anon using (true);
create policy anon_delete on public.notifications for delete to anon using (true);

-- Lets the apps confirm the two settings above actually took effect, since
-- neither is visible through the normal API.
create or replace function public.mirror_setup_status()
returns table (in_publication boolean, replica_identity_full boolean)
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
    ), false);
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

If `create extension pg_cron` errors, enable **pg_cron** under Database → Extensions in the dashboard, then re-run.

### 4. Connect the app

From **Project Settings → API**, copy the project URL and the **publishable** key into the app, then tap **Test connection**.

> **Use the publishable key, never the service role key.** The service role key bypasses row level security entirely: anyone who extracted it from the app could read, alter, or delete everything in your database, not just the notifications table. The app refuses a service role key on purpose.

### 5. Grant permissions and pair

- Enable **Notification Access** when prompted.
- Optionally exempt the app from battery optimization for reliable mirroring.
- Tap **Generate pairing code** and paste the code into the Mac app. It expires after five minutes and carries your credentials, so do not share it.

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
