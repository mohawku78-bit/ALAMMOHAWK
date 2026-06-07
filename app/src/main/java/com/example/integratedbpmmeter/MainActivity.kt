package com.example.integratedbpmmeter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.integratedbpmmeter.ui.IntegratedBpmMeterApp
import com.example.integratedbpmmeter.ui.IntegratedBpmMeterTheme

class MainActivity : ComponentActivity() {
    private var incomingAudioUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingAudioUri = intent.extractAudioUri()
        setContent {
            IntegratedBpmMeterTheme {
                IntegratedBpmMeterApp(
                    incomingAudioUri = incomingAudioUri,
                    onIncomingAudioConsumed = { incomingAudioUri = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingAudioUri = intent.extractAudioUri()
    }

    private fun Intent?.extractAudioUri(): Uri? {
        if (this == null) return null
        return when (action) {
            Intent.ACTION_SEND -> getParcelableExtra(Intent.EXTRA_STREAM)
            Intent.ACTION_VIEW -> data
            else -> null
        }
    }
}
