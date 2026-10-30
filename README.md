# NFC Reader

A minimal Android app (Kotlin + Jetpack Compose) that reads NFC tags and shows the tag ID, its tech types and any NDEF records (text and URLs are decoded).

This README covers both running this project and building the same app from scratch.

## Requirements

- Android Studio (recent stable version)
- JDK 11+ (bundled with Android Studio)
- A **physical Android phone with NFC**, since the emulator can't read tags
- An NFC tag or card to test with (NTAG stickers, transit or access cards, etc.)

## Run this project

1. Open the `NFCReader` folder in Android Studio and let Gradle sync.
2. On the phone, enable **Developer options → USB debugging**, and turn on **NFC** in Settings.
3. Connect the phone and press **Run**, or build from the command line:
   ```bash
   ./gradlew :app:assembleDebug
   # APK: app/build/outputs/apk/debug/app-debug.apk
   ./gradlew :app:installDebug   # installs on the connected phone
   ```
4. Open the app and hold a tag against the back of the phone. The tag details appear on screen.

## Build the app from scratch

### 1. Create the project

In Android Studio, choose **File → New → New Project → Empty Activity** (Compose). Use **Minimum SDK** API 24 or higher.

### 2. Manifest: permission, feature and intent filters

In `app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.NFC" />
<uses-feature android:name="android.hardware.nfc" android:required="true" />
```

On the activity, add `android:launchMode="singleTop"` and **two separate** intent filters, plus the tech-list metadata:

```xml
<activity
    android:name=".MainActivity"
    android:exported="true"
    android:launchMode="singleTop">
    <!-- App icon in the launcher -->
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
    <!-- Lets a tag scan launch the app -->
    <intent-filter>
        <action android:name="android.nfc.action.TECH_DISCOVERED" />
    </intent-filter>
    <meta-data
        android:name="android.nfc.action.TECH_DISCOVERED"
        android:resource="@xml/tech_list" />
</activity>
```

### 3. Tech list

Create `app/src/main/res/xml/tech_list.xml`. Give each tech **its own** `<tech-list>`. Lists are OR'd together, but techs inside one list are AND'd, so a tag would have to support all of them at once.

```xml
<resources>
    <tech-list><tech>android.nfc.tech.NfcA</tech></tech-list>
    <tech-list><tech>android.nfc.tech.NfcB</tech></tech-list>
    <tech-list><tech>android.nfc.tech.NfcF</tech></tech-list>
    <tech-list><tech>android.nfc.tech.NfcV</tech></tech-list>
    <tech-list><tech>android.nfc.tech.IsoDep</tech></tech-list>
    <tech-list><tech>android.nfc.tech.Ndef</tech></tech-list>
    <!-- add more as needed -->
</resources>
```

### 4. Activity code

See [`MainActivity.kt`](app/src/main/java/com/example/nfcreader/MainActivity.kt) for the full version. The key pieces are:

1. **Get the adapter** in `onCreate` with `NfcAdapter.getDefaultAdapter(this)`. It's `null` if the device has no NFC. Check `isEnabled` too.
2. **Enable foreground dispatch** in `onResume`, so every tag goes to your app while it's open. Disable it in `onPause`:
   ```kotlin
   val pi = PendingIntent.getActivity(
       this, 0,
       Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
       PendingIntent.FLAG_MUTABLE   // must be mutable: the system adds the tag extras
   )
   nfcAdapter?.enableForegroundDispatch(this, pi, null, null)
   ```
3. **Handle the tag** in both `onCreate(intent)` (the app was launched by a scan) and `onNewIntent(intent)` (the app was already open):
   ```kotlin
   val tag = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java)
   val id = tag.id.joinToString(":") { "%02X".format(it) }
   val message = Ndef.get(tag)?.cachedNdefMessage   // null if the tag has no NDEF data
   ```
4. **Decode NDEF records.** For text records, byte 0 of the payload holds the encoding flag and the language-code length, and the text follows. URI records can use `record.toUri()`.
5. **Show the result** in a Compose `mutableStateOf` so the UI updates on each scan.

### 5. Build, install, test

Follow the steps in [Run this project](#run-this-project) above.

## Common pitfalls

| Symptom | Cause | Fix |
|---|---|---|
| App won't open / "App not installed" | `android:permission="android.permission.DISPATCH_NFC_MESSAGE"` on the activity | Remove it. Only the system has that permission. |
| No launcher icon | `<data>` element inside the MAIN/LAUNCHER filter | Keep the launcher filter separate, with no `<data>`. |
| Scans never reach the app | All techs in one `<tech-list>`, or NFC filter commented out | Use one `<tech-list>` per tech and keep the `TECH_DISCOVERED` filter. |
| Tag ignored while app is open | No foreground dispatch | Call `enableForegroundDispatch` in `onResume`. |
| Crash on Android 12+ creating `PendingIntent` | Missing mutability flag | Use `PendingIntent.FLAG_MUTABLE`. |
| Nothing happens in the emulator | The emulator has no NFC hardware | Test on a real phone. |
| "(no NDEF data)" | Tag is blank or not NDEF-formatted | Expected. The ID and tech types still show. |

## License

Copyright (c) 2026 InfaTap. Released under the [MIT License](LICENSE): free to use, modify and distribute, including commercially, as long as the copyright notice is kept.
