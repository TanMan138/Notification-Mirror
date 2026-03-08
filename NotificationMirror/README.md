# Notification Mirror (Android)

Android app that mirrors device notifications to a Mac over the local network. Works with the Mac menu bar app in this repository — see [AndroidNotificationMirror](../AndroidNotificationMirror/) — or any server that implements the same API.

**In this repo:** Part of [Android–Mac Notification Mirror](../README.md). For architecture and full setup, start from the root README.

## Requirements

- Android 9+ (API 28+)
- Notification Access and (recommended) Battery Optimization whitelist
- Mac and phone on the same Wi‑Fi network

## Setup

### 1. Open the Android project

From the repository root, open the **NotificationMirror** folder in Android Studio (File → Open → select `NotificationMirror`). Sync Gradle.

### 2. Set the shared secret

The app sends a `secret` with every POST to `/notify`. The Mac server must use the same value to accept requests.

1. Copy the example config:
   ```bash
   cp local.properties.example local.properties
   ```
2. If you already have a `local.properties` (e.g. with `sdk.dir`), just add the line below to it.
3. Edit `local.properties` and set your secret:
   ```properties
   notification_mirror_secret=your_actual_secret
   ```
   Use the same value you set for `NOTIFICATION_MIRROR_SECRET` on the Mac (environment variable or Xcode scheme).

**Do not commit `local.properties`** — it is in `.gitignore`. If the property is missing or empty, the app still builds; the server will reject requests with an invalid/empty secret.

### 3. Run the app

- Enable **Notification Access** for the app in system settings when prompted.
- Optionally disable battery optimization for reliable mirroring.
- Enter your Mac’s IP (e.g. from the Mac menu bar app) and use **Ping Mac** / **Send test notification** to verify.

## API (client → Mac)

- **POST** `http://<mac_ip>:8080/notify` — JSON body: `title`, `text`, `secret`, `appName`, `appIconBase64`, `notificationKey`, `phoneIp`.
- **GET** `http://<mac_ip>:8080/ping` — used by “Ping Mac” to test connectivity.

Dismiss from Mac back to phone:

- **POST** `http://<phone_ip>:8081/dismiss` — JSON body: `{"key": "<notificationKey>"}`.

## License

Use and modify as you like.
