# Notification Mirror (Android)

Android app that listens to device notifications and syncs them directly to a Supabase project via Realtime WebSockets. Works with the Mac menu bar app in this repository — see [macos](../macos/).

**In this repo:** Part of [Android–Mac Notification Mirror](../README.md). For architecture and full setup, start from the root README.

## Requirements

- Android 9+ (API 28+)
- Notification Access and (recommended) Battery Optimization whitelist
- A Supabase Project

## Setup

### 1. Open the Android project

From the repository root, open the **NotificationMirror** folder in Android Studio (File → Open → select `NotificationMirror`). Sync Gradle.

### 2. Set the database keys

The app needs access to your Supabase project URL and Annonymous key to communicate over WebSockets.

1. Copy the example config:
   ```bash
   cp local.properties.example local.properties
   ```
2. Edit `local.properties` and set your Supabase configurations:
   ```properties
   SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
   SUPABASE_PUBLISHABLE_KEY=your_publishable_key_here
   ```

**Do not commit `local.properties`** — it is natively included in `.gitignore`.

### 3. Setup the Supabase Schema
For the app to store and relay the synchronized notifications, run this SQL query in your Supabase SQL Editor:
```sql
create table if not exists public.notifications (
  id bigint primary key generated always as identity,
  notification_key text not null,
  app_name text,
  title text not null,
  body_text text,
  base64_icon text,
  is_dismissed boolean default false,
  created_at timestamptz default now()
);

-- Crucial for allowing Android to receive the notification_key on update!
ALTER TABLE public.notifications REPLICA IDENTITY FULL;

alter table public.notifications enable row level security;
create policy "Allow anon insert" on public.notifications for insert to anon with check (true);
create policy "Allow anon select" on public.notifications for select to anon using (true);
create policy "Allow anon update" on public.notifications for update to anon using (true);
```

### 4. Run the app

- Enable **Notification Access** for the app in system settings when prompted.
- Optionally disable battery optimization for reliable mirroring.

## License

Use and modify as you like.
