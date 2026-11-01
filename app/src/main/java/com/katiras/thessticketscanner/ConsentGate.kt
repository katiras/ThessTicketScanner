package com.katiras.thessticketscanner

import android.app.Activity
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean

private const val CONSENT_LOG_TAG = "Consent"

object ConsentGate {

    private val requested = AtomicBoolean(false)

    fun requestConsentThenInitializeAds(activity: Activity) {
        if (!requested.compareAndSet(false, true)) return

        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)

        consentInformation.requestConsentInfoUpdate(
            activity,
            consentRequestParameters(activity),
            {
                if (activity.isAlive()) {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                        if (formError != null) {
                            Log.w(CONSENT_LOG_TAG, "Form error ${formError.errorCode}: ${formError.message}")
                        }
                        releaseAdsIfAllowed(activity, consentInformation)
                    }
                } else {
                    releaseAdsIfAllowed(activity, consentInformation)
                }
            },
            { requestError ->
                Log.w(CONSENT_LOG_TAG, "Info update failed ${requestError.errorCode}: ${requestError.message}")
                releaseAdsIfAllowed(activity, consentInformation)
            }
        )

        releaseAdsIfAllowed(activity, consentInformation)
    }

    private fun consentRequestParameters(activity: Activity): ConsentRequestParameters {
        val builder = ConsentRequestParameters.Builder()
        if (BuildConfig.DEBUG && BuildConfig.TEST_DEVICE_HASH.isNotEmpty()) {
            builder.setConsentDebugSettings(
                ConsentDebugSettings.Builder(activity)
                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                    .addTestDeviceHashedId(BuildConfig.TEST_DEVICE_HASH)
                    .build()
            )
        }
        return builder.build()
    }

    private fun releaseAdsIfAllowed(activity: Activity, info: ConsentInformation) {
        if (info.canRequestAds()) {
            AdsInitializer.initializeOnce(activity)
        }
    }

    private fun Activity.isAlive(): Boolean = !isFinishing && !isDestroyed
}
