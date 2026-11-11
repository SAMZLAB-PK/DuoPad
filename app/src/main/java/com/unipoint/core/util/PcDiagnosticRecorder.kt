package com.unipoint.core.util

import android.content.Context
import android.util.Log
import java.io.File

/** Local PC discovery and connection breadcrumbs. No PINs, keyboard input, or file contents. */
object PcDiagnosticRecorder {
    private const val FILE_NAME = "duopad_pc_diagnostics.txt"
    private val lock = Any()

    fun file(context: Context): File = File(context.applicationContext.filesDir, FILE_NAME)

    fun note(context: Context, event: String) {
        synchronized(lock) {
            try {
                val target = file(context)
                if (target.exists() && target.length() > 64 * 1024L) {
                    target.writeText(target.readText().takeLast(28 * 1024))
                }
                target.appendText("${System.currentTimeMillis()} $event\n")
                Log.i("DuoPadPC", event)
            } catch (error: Exception) {
                Log.w("DuoPadPC", "Unable to store PC diagnostic event", error)
            }
        }
    }

    fun clear(context: Context) {
        synchronized(lock) { runCatching { file(context).delete() } }
    }
}
