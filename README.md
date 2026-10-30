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

### 4. Activity code, step by step

Replace the contents of `app/src/main/java/com/example/nfcreader/MainActivity.kt` by adding the pieces below in order. Put together, they make the full file. If your package name is different, change the first line and the theme import to match.

#### Step 4.1: Package and imports

```kotlin
package com.example.nfcreader

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.example.nfcreader.ui.theme.NFCReaderTheme
```

`IntentCompat` comes from `androidx.core:core-ktx`, which new projects already include. It reads intent extras without the deprecation warnings on Android 13+.

#### Step 4.2: Class and state

```kotlin
class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var status by mutableStateOf("Hold an NFC tag near the phone")
```

- `nfcAdapter` is the phone's NFC hardware. It's `null` on phones without NFC.
- `status` is the text shown on screen. Because it's `mutableStateOf`, Compose redraws the screen automatically whenever it changes.

#### Step 4.3: `onCreate`: check NFC and set up the UI

```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        when {
            nfcAdapter == null -> status = "This device has no NFC"
            nfcAdapter?.isEnabled == false -> status = "NFC is off. Turn it on in Settings."
        }
        handleNfcIntent(intent) // app may have been launched by a tag scan
        setContent {
            NFCReaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TagInfo(
                        text = status,
                        modifier = Modifier.padding(innerPadding).padding(16.dp)
                    )
                }
            }
        }
    }
```

- Get the adapter and tell the user if NFC is missing or turned off.
- Call `handleNfcIntent(intent)` here because a tag scan may have **launched** the app through the `TECH_DISCOVERED` filter. In that case the tag arrives in the starting intent, not in `onNewIntent`.

#### Step 4.4: `onResume` / `onPause`: foreground dispatch

```kotlin
    override fun onResume() {
        super.onResume()
        // Route every tag to this activity while it's in the foreground.
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }
```

- While the app is open, every tag comes to this activity, so Android doesn't show its "choose an app" popup.
- `FLAG_ACTIVITY_SINGLE_TOP` (together with `launchMode="singleTop"` in the manifest) delivers each tag to the running activity instead of opening a new copy.
- `FLAG_MUTABLE` is **required**, because the system writes the tag into this intent. An immutable intent arrives empty, and on Android 12+ leaving out the flag crashes.
- Always disable dispatch in `onPause`. Android only allows it while the activity is in the foreground.

#### Step 4.5: `onNewIntent` and reading the tag

```kotlin
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNfcIntent(intent)
    }

    private fun handleNfcIntent(intent: Intent) {
        val tag = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java)
            ?: return
        val id = tag.id.joinToString(":") { "%02X".format(it) }
        val techs = tag.techList.joinToString { it.substringAfterLast('.') }
        val message = IntentCompat.getParcelableArrayExtra(intent, NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
            ?.firstOrNull() as NdefMessage?
            ?: Ndef.get(tag)?.cachedNdefMessage
        val records = message?.records?.joinToString("\n") { "• ${it.readable()}" } ?: "(no NDEF data)"
        status = "Tag ID: $id\nTech: $techs\n\nRecords:\n$records"
    }
}
```

- `onNewIntent` is called for each scan while the app is open, through foreground dispatch.
- `EXTRA_TAG` is present for every scanned tag. If it's missing, the intent isn't from NFC (for example, a normal launch), so the function returns early.
- **Tag ID:** the raw bytes, formatted as hex like `04:A2:3B:1C`.
- **Tech list:** shortened from `android.nfc.tech.NfcA` to `NfcA`.
- **NDEF message:** the function first uses the message the system already parsed. If there isn't one, it falls back to `Ndef.get(tag)?.cachedNdefMessage`. Both are `null` on blank or non-NDEF tags.
- The closing `}` ends the `MainActivity` class.

#### Step 4.6: Decoding NDEF records

Add this **outside** the class:

```kotlin
private fun NdefRecord.readable(): String = when {
    tnf == NdefRecord.TNF_WELL_KNOWN && type.contentEquals(NdefRecord.RTD_TEXT) -> {
        // Byte 0: bit 7 = UTF-16 flag, low 6 bits = language code length.
        val langLen = payload[0].toInt() and 0x3F
        val charset = if (payload[0].toInt() and 0x80 != 0) Charsets.UTF_16 else Charsets.UTF_8
        String(payload, 1 + langLen, payload.size - 1 - langLen, charset)
    }
    toUri() != null -> toUri().toString()
    else -> String(payload, Charsets.UTF_8)
}
```

- **Text records:** the payload is `[status byte][language code, e.g. "en"][text]`. The function skips the status byte and the language code, then decodes the rest.
- **URI records:** `toUri()` rebuilds the full URL, adding back the shortened prefix (`https://www.` and similar).
- **Anything else:** the payload is shown as plain UTF-8 text.

#### Step 4.7: The UI

```kotlin
@Composable
fun TagInfo(text: String, modifier: Modifier = Modifier) {
    Text(text = text, modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun TagInfoPreview() {
    NFCReaderTheme {
        TagInfo("Tag ID: 04:A2:3B:1C\nTech: NfcA, Ndef\n\nRecords:\n• hello")
    }
}
```

- `TagInfo` shows the `status` text from step 4.3.
- The preview lets you see the layout in Android Studio without a phone or tag.

#### How it fits together

```
Tag tapped
  ├─ app closed → TECH_DISCOVERED filter launches app → onCreate → handleNfcIntent
  └─ app open   → foreground dispatch → onNewIntent → handleNfcIntent
                                                          ↓
                                    status = "Tag ID … Records …" → Compose redraws
```

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
