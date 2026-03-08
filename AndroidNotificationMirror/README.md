# Android Notification Mirror (Mac)

A macOS menu bar app that receives notifications from an Android device over HTTP and shows them as native macOS notifications. In this repository it pairs with the [NotificationMirror](../NotificationMirror/) Android app.

**In this repo:** Part of [Android–Mac Notification Mirror](../README.md). For architecture and full setup, start from the root README.

## Requirements

- macOS
- The [NotificationMirror](../NotificationMirror/) Android app (in this repo) or any client that POSTs to the `/notify` API

## Setup

1. **Build and run** the app in Xcode.

2. **Set the shared secret** (required for `/notify` requests). The app reads it from the environment variable `NOTIFICATION_MIRROR_SECRET`. Use the same value in your Android-side app.

   **Option A – Xcode (for development)**  
   Edit the run scheme: **Product → Scheme → Edit Scheme… → Run → Arguments** → add to **Environment Variables**:
   - Name: `NOTIFICATION_MIRROR_SECRET`  
   - Value: your chosen secret (e.g. a long random string)

   **Option B – Terminal (for a built app)**  
   ```bash
   NOTIFICATION_MIRROR_SECRET='your_secret_here' open -a AndroidNotificationMirror
   ```

   **Option C – Launch Agent / login item**  
   If you use “Launch at Login”, set the same variable in the environment that launches the app (e.g. in a launchd plist or wrapper script).

3. **Point the Android app** at your Mac’s URL: `http://<your-mac-ip>:8080`  
   - Get your Mac’s IP from the menu bar (click the bell icon → “Copy Server URL” or note the shown URL).  
   - The Android app must send the same secret in the `secret` field of the `/notify` JSON body.

## API

- **GET** `/ping` — returns `pong` (health check).
- **POST** `/notify` — expects JSON:
  - `title`, `text`, `secret`, `notificationKey`, `phoneIp` (required)
  - `appName`, `appIconBase64` (optional)  
  Request must include the correct `secret` (same as `NOTIFICATION_MIRROR_SECRET`); otherwise the server responds with 401.

## License

Use and modify as you like.
