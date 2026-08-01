# Android–Mac Notification Mirror

A native bridge that beams Android notifications to macOS and allows two-way dismissal using real-time cloud synchronisation via Supabase.

### Demo

![Notification mirror demo](docs/demo.gif)

---

## Features

- **Two-way background sync** — Dismiss a notification on your Mac and it pushes a state update to Supabase, which instantly clears it on your Android device over WebSockets.
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
- The phone deletes rows older than 24 hours, at most once a day.
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

