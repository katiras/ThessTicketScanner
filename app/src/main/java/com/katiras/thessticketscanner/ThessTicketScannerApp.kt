package com.katiras.thessticketscanner

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import java.util.concurrent.atomic.AtomicBoolean

class ThessTicketScannerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!BuildConfig.CONSENT_ENABLED) {
            AdsInitializer.initializeOnce(this)
        }
    }
}

object AdsInitializer {
    private val initialized = AtomicBoolean(false)

    var adsAllowed by mutableStateOf(!BuildConfig.CONSENT_ENABLED)
        private set

    fun initializeOnce(context: Context) {
        adsAllowed = true
        if (!initialized.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        if (BuildConfig.DEBUG && BuildConfig.TEST_DEVICE_HASH.isNotEmpty()) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf(BuildConfig.TEST_DEVICE_HASH))
                    .build()
            )
        }
        Thread({ MobileAds.initialize(appContext) }, "ads-init").start()
    }
}
