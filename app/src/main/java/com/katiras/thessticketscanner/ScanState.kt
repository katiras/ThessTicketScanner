package com.katiras.thessticketscanner

import androidx.annotation.StringRes

sealed interface ScanState {
    data object Waiting : ScanState
    data class Success(val remainingTrips: Int) : ScanState
    data class Failed(@StringRes val message: Int) : ScanState
}
