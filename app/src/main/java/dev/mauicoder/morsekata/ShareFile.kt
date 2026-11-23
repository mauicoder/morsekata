package dev.mauicoder.morsekata

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.PrintWriter

class ShareFile {

    private val FILE_PROVIDER_AUTHORITY = "dev.mauicoder.morsekata.provider"
    private val FILE_PROVIDER_NAME = "shared_data.wav"

    fun shareAsFile(context: Context, waveStream: ByteArray) {
        val file = File(context.cacheDir, FILE_PROVIDER_NAME)

        file.updateText(waveStream)

        val uri = FileProvider.getUriForFile(
            context.applicationContext,
            FILE_PROVIDER_AUTHORITY,
            file
        )

        Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_STREAM, uri)
        }.also { intent ->
            context.startActivity(
                Intent.createChooser(intent, "Share Sound File").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    private fun File.clearText() {
        PrintWriter(this).also {
            it.print("")
            it.close()
        }
    }

    private fun File.updateText(content: ByteArray) {
        clearText()
        appendBytes(content)
    }

}