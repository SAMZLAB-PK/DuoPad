package com.unipoint.core.util

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Crash-safe breadcrumbs for the optional live-video screen.
 * Startup marker is written BEFORE startActivity() so even Activity launch
 * failures and native decoder process crashes leave an observable trace.
 *
 * Only local files are used; nothing is uploaded or sent to any server.
 */
object MirrorCrashRecorder {
    private const val FILE_NAME = "doupad_mirror_diagnostics.txt"
    private const val PREFS = "doupad_mirror_crash_state"
    private const val ACTIVE = "mirror_active"
    private val installed = AtomicBoolean(false)
    private val armed = AtomicBoolean(false)
    private val lock = Any()

    fun file(context: Context): File = File(context.applicationContext.filesDir, FILE_NAME)

    fun note(context: Context, message: String) {
        synchronized(lock) {
            try {
                val file = file(context)
                if (file.exists() && file.length() > 64 * 1024) {
                    file.writeText(file.readText().takeLast(28 * 1024))
                }
                file.appendText("${System.currentTimeMillis()} $message\n")
                Log.i("DouPadMirror", message)
            } catch (e: Exception) {
                Log.e("DouPadMirror", "Cannot write local mirror breadcrumb", e)
            }
        }
    }

    /** Delete historical app diagnostics without touching saved devices or ADB keys. */
    fun clear(context: Context) {
        synchronized(lock) {
            runCatching { file(context).delete() }
                .onFailure { Log.w("DouPadMirror", "Cannot clear mirror logs", it) }
        }
    }

    fun launch(context: Context, endpointPresent: Boolean) {
        armed.set(true)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(ACTIVE, true).commit()
        note(context, "USER_LAUNCH: Live Mirror requested; endpoint supplied=$endpointPresent")
    }

    fun started(context: Context) {
        armed.set(true)
        note(context, "ACTIVITY_ON_CREATE: Mirror Activity entered")
    }

    fun finish(context: Context, detail: String = "ended") {
        note(context, "MIRROR_FINISHED: $detail")
        armed.set(false)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(ACTIVE, false).commit()
    }

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val app = context.applicationContext
        val preferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (preferences.getBoolean(ACTIVE, false)) {
            note(app, "PREVIOUS_MIRROR_UNEXPECTED_EXIT: Android terminated the app before normal mirror cleanup. Check Android process-exit information below.")
            // Keep the last crash marker without showing another one on every launch.
            preferences.edit().putBoolean(ACTIVE, false).commit()
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // The mirror Activity disarms onDestroy(), but a stale video worker
            // can still throw immediately afterward. Capture its stack trace too.
            val mirrorWorker = thread.name.startsWith("doupad-") ||
                error.stackTrace.any { it.className.contains("DoupadMirrorActivity") }
            if (armed.get() || preferences.getBoolean(ACTIVE, false) || mirrorWorker) {
                note(app, "UNCAUGHT_EXCEPTION thread=${thread.name}: ${error.javaClass.simpleName}: ${error.message}")
                note(app, error.stackTraceToString().take(12000))
            }
            if (previous != null) previous.uncaughtException(thread, error)
            else android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
}
