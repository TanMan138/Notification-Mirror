# Android–Mac Notification Mirror

A native bridge that beams Android notifications to macOS and allows two-way dismissal over local Wi‑Fi.

### Demo

Add a short screen recording or screenshot here so recruiters can see it working. For example, create a `docs/` folder and add `demo.gif` or `screenshot.png`, then uncomment and use:

```markdown
![Notification mirror demo](docs/demo.gif)
```

---

## Features

- **Two-way background sync** — Dismiss a notification on your Mac and it clears on your Android device via a lightweight reverse HTTP call.
- **On-the-fly Base64 image encoding** — App icons are sent from the phone and shown in macOS notifications and in the menu bar app list.
- **Dynamic app filtering** — Enable or disable notifications per app from the macOS menu bar; state is stored in UserDefaults and survives restarts.
- **Zero cloud dependencies** — Everything runs on your local network (Mac HTTP server + Android HTTP server for dismiss); no accounts or internet required.

---

## Architecture

| Layer | Stack |
|-------|--------|
| **Android** | Kotlin, `NotificationListenerService` to intercept notifications, [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) to run a small HTTP server for dismiss-from-Mac commands. |
| **Mac** | SwiftUI menu bar app, [Swifter](https://github.com/httpswift/swifter) HTTP server to receive notifications, `UNUserNotificationCenter` for native macOS notifications. |

**Flow:**

1. Android: `NotificationListenerService` receives a notification → app encodes title, body, app name, icon (Base64), and a shared secret → POST to `http://<mac-ip>:8080/notify`.
2. Mac: Server validates the secret (from env), checks per-app filter, then shows a local notification with optional “Clear on Phone” action.
3. User taps “Clear on Phone” → Mac POSTs to `http://<phone-ip>:8081/dismiss` with the notification key → Android dismisses it.

---

## Repository layout

```
Android-Mac-Notification-Mirror/
├── AndroidNotificationMirror/   # Xcode project (macOS menu bar app)
└── NotificationMirror/           # Android Studio project (Kotlin + NotificationListenerService)
```

- **Mac app setup:** See [AndroidNotificationMirror/README.md](AndroidNotificationMirror/README.md) (env var for shared secret, server URL, etc.).
- **Android app setup:** See [NotificationMirror/README.md](NotificationMirror/README.md) (and `local.properties.example` for the shared secret).

---

## Security

- The shared secret is **not** in source: Mac reads `NOTIFICATION_MIRROR_SECRET` from the environment; Android uses a value from `local.properties` (gitignored). Use the same value on both sides.
- Keystores (`.jks`) are gitignored; keep them outside the repo or in a secure store.
- All traffic is on your LAN; no data is sent to the cloud.
