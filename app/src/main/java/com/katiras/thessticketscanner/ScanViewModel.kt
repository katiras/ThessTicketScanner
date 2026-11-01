package com.katiras.thessticketscanner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class ScanViewModel : ViewModel() {
    var scanState by mutableStateOf<ScanState>(ScanState.Waiting)
}
