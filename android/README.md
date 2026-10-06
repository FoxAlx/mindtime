# Mindtime for Android

Native Android meditation timer written in Kotlin.

## Open and run

Open this directory in Android Studio and let Gradle sync. Run the `app`
configuration on an Android device or emulator (API 26 or newer).

## Timer behavior

- Configure the main meditation (1–180 minutes) and Metta (1–60 minutes).
- Starting a session begins the main meditation; Metta starts automatically
  when it ends.
- The foreground timer service uses monotonic elapsed time and keeps its
  session state across service process restarts.
- The media-style notification provides pause/resume and stop controls, and
  exposes the session on the lock screen.

For reliable sessions on Huawei/HarmonyOS and other devices with aggressive
power management, allow Mindtime to run in the background. In the app, open
the battery settings shortcut and set battery usage to unrestricted; where
available, also enable the app's background activity and auto-launch options.
Manufacturers can still impose their own process restrictions.
