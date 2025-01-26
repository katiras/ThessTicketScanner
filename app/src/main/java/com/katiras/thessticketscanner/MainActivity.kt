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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.katiras.thessticketscanner.ui.theme.ThessTicketScannerTheme
import java.io.IOException

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private var displayedCounterValue by mutableStateOf("Σκανάρετε ένα εισιτήριο...")
    private var tagId by mutableStateOf("")
    private var isScanned by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

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
                            text = displayedCounterValue,
                            isScanned = isScanned,
                            onScanAgain = {
                                // Reset the state when scan again button is clicked.
                                displayedCounterValue = "Σκανάρετε ένα εισιτήριο..."
                                tagId = ""
                                isScanned = false
                            }
                        )
                    }
                }
            }
        }
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
                        displayedCounterValue = getRemainingTripsFromFirstCounter(counterValue).toString()
                        tagId = tagIdValue
                        isScanned = true
                    } catch (e: IOException) {
                        Log.e("NFC", "Error connecting to MIFARE Ultralight EV1 card", e)
                    } finally {
                        card.close()
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
fun CounterDisplay(
    text: String,
    isScanned: Boolean,
    onScanAgain: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight(0.8f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isScanned) {
                Text(
                    text = "Διαθέσιμες διαδρομές",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = text,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 42.sp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))
            } else {
                Text(
                    text = "Σκανάρετε ένα εισιτήριο...",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.nfc_animation))
                LottieAnimation(
                    composition = composition,
                    iterations = LottieConstants.IterateForever,
                    modifier = Modifier.fillMaxWidth()
                        .height(300.dp)
                )
            }
        }

        if (isScanned) {
            Button(
                onClick = onScanAgain,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
                    .fillMaxWidth(0.9f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Blue),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                Text(
                    text = "Σκανάρετε ξανα",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
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
