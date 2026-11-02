package com.katiras.thessticketscanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareUltralight
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.katiras.thessticketscanner.ui.theme.ThessTicketScannerTheme
import kotlinx.coroutines.delay
import java.io.IOException

private const val LOG_TAG = "NFC"
private const val ERROR_CONNECT_MSG = "Error connecting to MIFARE Ultralight EV1 card"
private const val INVALID_COUNTER_MSG = "Invalid counter value"
private const val ERROR_READING_COUNTER_MSG = "Error reading first counter"
private const val AD_LOG_TAG = "Ads"
private const val AD_LOAD_FAILED_MSG = "Banner failed to load, code"

private val AD_RETRY_DELAYS_MS = listOf(30_000L, 60_000L, 120_000L)
private val READ_COUNTER_0_CMD = byteArrayOf(0x39.toByte(), 0x00.toByte())

class MainActivity : ComponentActivity() {
    private val viewModel: ScanViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null
    private var isNfcEnabled by mutableStateOf(false)
    private val nfcStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != NfcAdapter.ACTION_ADAPTER_STATE_CHANGED) return
            isNfcEnabled = nfcAdapter?.isEnabled == true
            if (isNfcEnabled && viewModel.scanState is ScanState.Waiting) {
                enableReaderMode()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        isNfcEnabled = nfcAdapter != null && nfcAdapter!!.isEnabled

        if (BuildConfig.CONSENT_ENABLED) {
            ConsentGate.requestConsentThenInitializeAds(this)
        }

        setContent {
            MainScreen(
                scanState = viewModel.scanState,
                onScanAgain = {
                    viewModel.scanState = ScanState.Waiting
                    enableReaderMode()
                },
                isNfcEnabled = isNfcEnabled,
                adsAllowed = AdsInitializer.adsAllowed
            )
        }
    }

    private fun enableReaderMode() {
        nfcAdapter?.enableReaderMode(
            this,
            NfcAdapter.ReaderCallback { tag -> onTagDiscovered(tag) },
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
    }

    private fun disableReaderMode() {
        nfcAdapter?.disableReaderMode(this)
    }

    private fun onTagDiscovered(tag: Tag) {
        val result = readCard(tag)
        runOnUiThread {
            viewModel.scanState = result
            disableReaderMode()
        }
    }

    override fun onPause() {
        super.onPause()
        disableReaderMode()
        runCatching { unregisterReceiver(nfcStateReceiver) }
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(nfcStateReceiver, IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED))
        isNfcEnabled = nfcAdapter != null && nfcAdapter!!.isEnabled
        if (viewModel.scanState is ScanState.Waiting && isNfcEnabled) {
            enableReaderMode()
        }
    }
}

@Composable
fun CounterDisplay(
    scanState: ScanState,
    onScanAgain: () -> Unit,
    isNfcEnabled: Boolean
) {
    if (!isNfcEnabled) {
        NfcDisabledScreen()
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (scanState) {
                is ScanState.Success -> {
                    Text(
                        text = stringResource(R.string.available_trips_title),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = scanState.remainingTrips.toString(),
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 42.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                is ScanState.Failed -> {
                    Text(
                        text = stringResource(scanState.message),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                ScanState.Waiting -> {
                    Text(
                        text = stringResource(R.string.scan_prompt),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.nfc_animation))
                    LottieAnimation(
                        composition = composition,
                        iterations = LottieConstants.IterateForever,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    )
                }
            }
        }
        if (scanState !is ScanState.Waiting) {
            Button(
                onClick = onScanAgain,
                modifier = Modifier
                    .padding(bottom = 40.dp)
                    .fillMaxWidth(0.9f),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.rescan_prompt),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    scanState: ScanState,
    onScanAgain: () -> Unit,
    isNfcEnabled: Boolean,
    adsAllowed: Boolean
) {
    ThessTicketScannerTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    if (adsAllowed) {
                        AdBanner(adUnitId = stringResource(R.string.admob_top_banner_ad_unit_id))
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CounterDisplay(
                        scanState = scanState,
                        onScanAgain = onScanAgain,
                        isNfcEnabled = isNfcEnabled
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    if (adsAllowed) {
                        AdBanner(adUnitId = stringResource(R.string.admob_bottom_banner_ad_unit_id))
                    }
                }
            }
        }
    }
}

@Composable
fun AdBanner(adUnitId: String) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val adSize = remember(context, resources) {
        val displayMetrics = resources.displayMetrics
        val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
        AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, adWidth)
    }
    var loaded by remember { mutableStateOf(false) }
    var failures by remember { mutableIntStateOf(0) }

    val adView = remember(context, adUnitId, adSize) {
        AdView(context).apply {
            setAdSize(adSize)
            this.adUnitId = adUnitId
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    loaded = true
                    failures = 0
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(AD_LOG_TAG, "$AD_LOAD_FAILED_MSG ${error.code}: ${error.message}")
                    loaded = false
                    failures++
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }

    if (!loaded && failures in 1..AD_RETRY_DELAYS_MS.size) {
        LaunchedEffect(failures) {
            delay(AD_RETRY_DELAYS_MS[failures - 1])
            adView.loadAd(AdRequest.Builder().build())
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, adView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> adView.pause()
                Lifecycle.Event.ON_RESUME -> adView.resume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            adView.destroy()
        }
    }

    val gaveUp = !loaded && failures > AD_RETRY_DELAYS_MS.size
    if (!gaveUp) {
        AndroidView(
            factory = { adView },
            modifier = Modifier
                .fillMaxWidth()
                .height(adSize.height.dp)
        )
    }
}

@Composable
fun NfcDisabledScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.nfc_disabled_message),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(
            onClick = {
                context.startActivity(Intent(android.provider.Settings.ACTION_NFC_SETTINGS))
            },
            modifier = Modifier
                .padding(bottom = 40.dp)
                .fillMaxWidth(0.9f),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.open_nfc_settings),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

fun getRemainingTrips(counter: Int): Int = 65535 - (counter and 0xFFFF)

fun fetchFirstCounter(card: MifareUltralight): Int? {
    return try {
        val response = card.transceive(READ_COUNTER_0_CMD)
        if (response.size == 3) {
            (response[0].toInt() and 0xFF) or ((response[1].toInt() and 0xFF) shl 8) or ((response[2].toInt() and 0xFF) shl 16)
        } else {
            Log.e(LOG_TAG, INVALID_COUNTER_MSG)
            null
        }
    } catch (e: IOException) {
        Log.e(LOG_TAG, ERROR_READING_COUNTER_MSG, e)
        null
    }
}

fun readCard(tag: Tag): ScanState {
    val card = MifareUltralight.get(tag)
        ?: return ScanState.Failed(R.string.error_unsupported_card)
    return try {
        card.connect()
        val counter = fetchFirstCounter(card)
        if (counter == null) {
            ScanState.Failed(R.string.error_read_failed)
        } else {
            ScanState.Success(getRemainingTrips(counter))
        }
    } catch (e: IOException) {
        Log.e(LOG_TAG, ERROR_CONNECT_MSG, e)
        ScanState.Failed(R.string.error_read_failed)
    } finally {
        runCatching { card.close() }
    }
}
