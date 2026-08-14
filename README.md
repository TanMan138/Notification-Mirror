# Android–Mac Notification Mirror

A native bridge that beams Android notifications to macOS, lets you reply to messages from the Mac, and allows two-way dismissal — all over real-time cloud synchronisation via Supabase.

### Demo

![Notification mirror demo](docs/demo.gif)

---

## Features

- **Two-way background sync** — Dismiss a notification on your Mac and it pushes a state update to Supabase, which instantly clears it on your Android device over WebSockets.
- **Reply from the Mac** — Messaging notifications arrive with a reply field. Type into the banner and the text is delivered by the app that posted it, using the notification's own `RemoteInput` action. If the notification is gone by then, the Mac says so and hands your text back rather than losing it.
- **No duplicate banners** — Group summaries, the collapsed stand-in messaging apps post alongside the real notification, are filtered out on the phone.
- **On-the-fly Base64 image encoding** — App icons are sent from the phone and shown in macOS notifications and in the menu bar app list.
- **Dynamic app filtering** — Enable or disable notifications per app from the macOS menu bar; state is stored in UserDefaults and survives restarts.
- **Cloud-Powered WebSockets** — Powered by Supabase Realtime (PostgreSQL). It works anywhere in the world, unconstrained by local Wi-Fi limitations.
- **Your own backend** — You create a free Supabase project and the two apps talk only through it. There is no shared server, no account to sign up for, and nothing for anyone else to see.

---

## Architecture

| Layer | Stack |
|-------|--------|
| **Android** | Kotlin, `NotificationListenerService` to intercept notifications, and `Ktor` + `OkHttp` Supabase Realtime SDK. |
| **Mac** | SwiftUI menu bar app, `UNUserNotificationCenter` for native notifications, and the Supabase Swift Realtime SDK. |
| **Backend** | Supabase Postgres with Realtime channels enabled. |

**Flow:**

1. **Android**: `NotificationListenerService` receives a notification → app encodes title, body, app name, icon (Base64) → executes an `INSERT` on the Supabase `notifications` table.
2. **Mac**: The menu bar app subscribes to `postgresChange` events on the `notifications` table. It receives the `INSERT` payload, checks the per-app filter, and displays a native macOS notification with a “Clear on Phone” action.
3. **Mac**: User taps “Clear on Phone” → Mac updates the row in the `notifications` table, setting `is_dismissed = true`.
4. **Android**: A background listener subscribed to `UPDATE` events sees `is_dismissed = true` → extracts the `notification_key` and uses native Android APIs to cancel the notification on the phone.
5. **Mac**: For a notification the phone flagged repliable, the banner carries a reply field. Sending writes `reply_text` and `reply_status = 'pending'` to that row.
6. **Android**: The same listener sees the pending reply, fills the notification's `RemoteInput` and fires the posting app's own `PendingIntent`, then writes `reply_status` back as `sent` or `failed`. The `PendingIntent` never leaves the phone — it cannot be serialised — so it lives in an in-memory registry keyed on the notification, rebuilt from `getActiveNotifications()` whenever the listener reconnects.

Both directions address the row by primary key, not `notification_key`: apps reuse one key for every message in a conversation, so filtering on it would act on the whole history at once.

---

## Install

Grab both from the [releases page](../../releases):

| | File | Notes |
|---|---|---|
| Android | `app-release.apk` | Set up the phone first — it creates the database and generates the pairing code |
| macOS | `AndroidNotificationMirror.dmg` | Not notarized; first launch needs **System Settings → Privacy & Security → Open Anyway** |

Start with [android/README.md](android/README.md); the Mac just pairs with it.

---

## Repository layout

```
Android-Mac-Notification-Mirror/
├── macos/    # Xcode project (macOS menu bar app)
└── android/  # Android Studio project (Kotlin + NotificationListenerService)
```

- **Android app setup:** See [android/README.md](android/README.md) — create the Supabase project, run the SQL, enter your keys. Start here.
- **Mac app setup:** See [macos/README.md](macos/README.md) — paste the pairing code the phone generates.

---

## Your data

- Notifications pass through **your own** Supabase project. Nothing is sent anywhere else, and neither app talks to any server the other user controls.
- Rows older than 24 hours are deleted hourly by a scheduled job in your own project, with the phone sweeping as a fallback.
- Credentials live in the Android Keystore and the macOS Keychain, never in a plain preferences file.
- A pairing code carries your project URL and publishable key and grants full access to that table. It expires after five minutes — do not share it.

---

## Security

- Use the **publishable** key. The service role key bypasses row level security and would expose your whole database; both apps refuse it.
- Keystores and private credentials (`.jks`, `local.properties`, `Secrets.xcconfig`) are strictly `.gitignore`d.
- Row Level Security is enabled by the setup SQL.

---

## Acknowledgements

- <a href="https://www.flaticon.com/free-icons/notification" title="notification icons">Notification icons created by Pixel perfect - Flaticon</a>

