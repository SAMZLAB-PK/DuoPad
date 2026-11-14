package com.unipoint.presentation.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Share a first-party, auditable Windows Host bundle using Android's system share sheet.
 * A receiver MUST accept the transfer and manually run the host. DuoPad does not
 * silently install or execute software on a remote computer.
 */
object DuoPadHostShare {
    private val paths = listOf(
        "host/python/unipoint_host.py",
        "host/python/pc_workspace.py",
        "host/python/requirements.txt",
        "host/windows/Install-and-Run-DOUPAD.bat",
        "host/windows/Run-DOUPAD-Host.bat",
        "host/windows/SETUP-FIREWALL-ONCE.bat",
        "host/WINDOWS-SETUP.md"
    )

    fun share(context: Context) {
        val embeddedName = "DuoPad-PC-Companion-R24.zip"
        val embeddedAvailable = context.assets.list("pc-host")?.contains(embeddedName) == true
        val file = File(context.cacheDir,
            if(embeddedAvailable) embeddedName else "DuoPad-Windows-Host-Python-Fallback.zip")
        if (embeddedAvailable) {
            // CI compiled this real Windows GUI EXE and packaged it inside the APK.
            context.assets.open("pc-host/$embeddedName").use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
        } else {
            // Source-only local builds still retain the original manually installed Python fallback.
            ZipOutputStream(FileOutputStream(file).buffered()).use { zip ->
                for (path in paths) {
                    zip.putNextEntry(ZipEntry(path))
                    context.assets.open("pc-host/$path").use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("DuoPad Windows Host", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Send DuoPad Windows Host to PC")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
