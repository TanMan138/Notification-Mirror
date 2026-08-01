# Android Notification Mirror (Mac)

A macOS menu bar app that receives notifications from an Android device by listening to Supabase Realtime and shows them as native macOS notifications. In this repository it pairs with the [android](../android/) app.

**In this repo:** Part of [Android–Mac Notification Mirror](../README.md). For architecture, start from the root README.

## Requirements

- macOS 15.0 or later
- The [android](../android/) app, already connected to your own Supabase project
- No credentials of your own to manage — the phone supplies them

## Setup

1. **Install the app** from the releases page and launch it. It lives in the menu bar only.

2. **Open the menu bar icon** and click **Set Up…**. Until it is configured the menu shows **Not configured**.

3. **Pair with your phone.** On the phone, tap **Generate pairing code**, copy it, and paste it into the setup window. Click **Pair**.

   The code carries your project URL and publishable key and expires after five minutes. Anyone holding it can read your mirrored notifications, so do not share it.

   If you would rather not use a code, enter the project URL and publishable key by hand in the same window.

4. Mirroring starts as soon as the connection test passes — no relaunch needed. To move to a different project later, use **Change Project…**.

Two-way dismissal works out of the box: choosing **Clear on Phone** flips `is_dismissed` in your Supabase project, and the phone cancels the notification.

Credentials are stored in the macOS Keychain.

## Building from source

Open `AndroidNotificationMirror.xcodeproj` in Xcode.

Build-time credentials are optional and only used when nothing has been paired:

- Duplicate `Secrets.example.xcconfig` as `Secrets.xcconfig` (gitignored).
- Fill in:

  ```properties
  SUPABASE_HOST = your-project-id.supabase.co
  SUPABASE_PUBLISHABLE_KEY = sb_publishable_your_key_here
  ```

  Host only, no scheme — xcconfig treats `//` as a comment.

## License

Use and modify as you like.
