# Android Notification Mirror (Mac)

A macOS menu bar app that receives notifications from an Android device by listening to Supabase Realtime and shows them as native macOS notifications. In this repository it pairs with the [android](../android/) Android app.

**In this repo:** Part of [Android–Mac Notification Mirror](../README.md). For architecture and full setup, start from the root README.

## Requirements

- macOS
- A Supabase Project
- The [NotificationMirror](../NotificationMirror/) Android app (in this repo)

## Setup

1. **Clone the repo** and open the `AndroidNotificationMirror` project in Xcode.

2. **Supply your Supabase credentials.**
   Xcode uses a configuration file named `Secrets.xcconfig` that is deliberately gitignored so you don't commit your keys.
   - Duplicate `Secrets.example.xcconfig` and rename the copy to `Secrets.xcconfig`.
   - Open `Secrets.xcconfig` and fill in your Supabase variables.
   
   Example `Secrets.xcconfig`:
   ```properties
   SUPABASE_HOST = your-project-id.supabase.co
   SUPABASE_PUBLISHABLE_KEY = sb_publishable_your_key_here
   ```

3. **Build and run** the app in Xcode. 
   The app will automatically subscribe to the `public.notifications` channel on your Supabase project.

4. To allow two-way dismissal functionality (Command: "Clear on Phone"), the macOS app will automatically flip the `is_dismissed` flag in the Supabase database. The Android app listens for this flag and dismisses the notification concurrently.

## License

Use and modify as you like.
