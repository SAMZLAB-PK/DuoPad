package com.unipoint.core.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.unipoint.data.remote.NetworkPcDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bi-directional clipboard sync between phone and PC (Network mode).
 *
 * Phone → PC : send text via 0x21 packet
 * PC → Phone : host can push clipboard updates (future extension)
 */
@Singleton
class ClipboardSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val network: NetworkPcDataSource
) {
    private val tag = "ClipboardSync"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    private val _lastSynced = MutableStateFlow<String?>(null)
    val lastSynced: StateFlow<String?> = _lastSynced.asStateFlow()

    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun startWatching() {
        stopWatching()
        job = scope.launch {
            var lastText: String? = null
            while (isActive) {
                try {
                    val clip = clipboard.primaryClip
                    val text = clip?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (!text.isNullOrBlank() && text != lastText) {
                        lastText = text
                        if (network.isConnected) {
                            // Send to PC
                            network.sendClipboard(text)
                            _lastSynced.value = text
                            Log.d(tag, "Synced to PC: ${text.take(40)}…")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(tag, "Clipboard read error", e)
                }
                delay(800) // poll interval
            }
        }
    }

    fun stopWatching() {
        job?.cancel()
        job = null
    }

    fun setClipboard(text: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText("UniPoint", text))
        _lastSynced.value = text
    }
}
