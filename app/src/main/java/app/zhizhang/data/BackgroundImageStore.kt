package app.zhizhang.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class BackgroundImageStore(private val context: Context) {

    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val original = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@runCatching null
            val maxWidth = 1440
            val bitmap = if (original.width > maxWidth) {
                val height = (original.height * (maxWidth.toFloat() / original.width)).toInt()
                Bitmap.createScaledBitmap(original, maxWidth, height, true).also {
                    if (it !== original) original.recycle()
                }
            } else {
                original
            }
            val dir = File(context.filesDir, "backgrounds").apply { mkdirs() }
            val file = File(dir, "custom-${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            bitmap.recycle()
            dir.listFiles()?.filter { it != file }?.forEach { it.delete() }
            file.absolutePath
        }.getOrNull()
    }
}
