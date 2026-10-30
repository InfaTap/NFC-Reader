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

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var status by mutableStateOf("Hold an NFC tag near the phone")

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
