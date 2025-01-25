package com.katiras.thessticketscanner

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.katiras.thessticketscanner.ui.theme.ThessTicketScannerTheme
import java.io.IOException

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setContent {
            ThessTicketScannerTheme {
                val displayedCounterValue by remember { mutableStateOf("Waiting for scan...") }
                val tagId by remember { mutableStateOf("") }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        CounterDisplay(displayedCounterValue, tagId)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_MUTABLE
        )
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == NfcAdapter.ACTION_TAG_DISCOVERED) {
            val nfcTag: Tag? = getNfcTagFromIntent(intent)
            nfcTag?.let {
                val mifareCard = MifareUltralight.get(it)
                mifareCard?.let { card ->
                    try {
                        card.connect()
                        val counterValue = fetchFirstCounter(card)
                        val tagIdValue = it.id.joinToString(separator = "") { byte -> "%02X".format(byte) }

                        runOnUiThread {
                            setContent {
                                ThessTicketScannerTheme {
                                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(innerPadding),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CounterDisplay(
                                                getRemainingTripsFromFirstCounter(counterValue).toString(),
                                                tagIdValue
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: IOException) {
                        Log.e("NFC", "Error connecting to MIFARE Ultralight EV1 card", e)
                    } finally {
                        card.close()
                    }
                }
            }
        }
    }

    private fun getRemainingTripsFromFirstCounter(input: Int): Int {
        val hexString = input.toString(16)

        val trimmedHex = hexString.takeLast(4).padStart(4, '0')

        val decimalValue = trimmedHex.toInt(16)

        return 65535 - decimalValue
    }

    private fun fetchFirstCounter(card: MifareUltralight): Int {
        return try {
            val response = card.transceive(byteArrayOf(0x39.toByte(), 0x00.toByte()))
            if (response.size == 3) {
                (response[0].toInt() and 0xFF) or
                        ((response[1].toInt() and 0xFF) shl 8) or
                        ((response[2].toInt() and 0xFF) shl 16)
            } else {
                Log.e("NFC", "Invalid counter value")
                -1
            }
        } catch (e: IOException) {
            Log.e("NFC", "Error reading first counter", e)
            -1
        }
    }
}

@Composable
fun CounterDisplay(text: String, tagId: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = text,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )

        if (tagId.isNotEmpty()) {
            Text(
                text = tagId,
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            )
        }
    }
}

fun getNfcTagFromIntent(intent: Intent?): Tag? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG)
    }
}

@Preview(showBackground = true)
@Composable
fun CounterDisplayPreview() {
    ThessTicketScannerTheme {
        CounterDisplay("11", "00000000000000")
    }
}