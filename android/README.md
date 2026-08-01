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

create policy "anon select" on public.notifications for select to anon using (true);
create policy "anon insert" on public.notifications for insert to anon with check (true);
create policy "anon update" on public.notifications for update to anon using (true);
create policy "anon delete" on public.notifications for delete to anon using (true);
```

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
