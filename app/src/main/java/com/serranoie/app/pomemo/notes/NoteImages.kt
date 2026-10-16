package com.serranoie.app.pomemo.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.graphics.scale
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.serranoie.app.pomemo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Pictures in notes. The photo picker only lends a URI for a while, so each image is copied into
 * app storage (scaled to at most [MAX_SIDE] px, JPEG) and the note keeps a relative path to it:
 * `![](note_images/<uuid>.jpg)`. Paths outside that folder are never opened, so a crafted note or
 * backup can't point at other app files.
 */
object NoteImages {
    private const val DIR = "note_images"
    private const val MAX_SIDE = 1600

    private fun dir(context: Context) = File(context.filesDir, DIR)

    /** The stored file for [source], or null if it isn't one of ours. */
    fun file(context: Context, source: String): File? {
        val name = source.removePrefix("$DIR/")
        if (name == source || name.isEmpty() || '/' in name || name.startsWith(".")) return null
        return File(dir(context), name)
    }

    /** Copies the picked image in and returns the source to write in the note, or null if it can't be read. */
    suspend fun import(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val sample = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE)
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val scaled = fit(bitmap, MAX_SIDE)
            val name = "${UUID.randomUUID()}.jpg"
            dir(context).mkdirs()
            File(dir(context), name).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            "$DIR/$name"
        }.getOrNull()
    }

    /** Deletes stored images no note mentions any more (removed lines, deleted or discarded notes). */
    suspend fun sweep(context: Context, bodies: List<String>) = withContext(Dispatchers.IO) {
        val used = bodies.joinToString("\n")
        dir(context).listFiles()?.forEach { f -> if ("$DIR/${f.name}" !in used) f.delete() }
    }

    /** Decoded for display at about [maxSide] px; null if missing or unreadable. */
    suspend fun load(context: Context, source: String, maxSide: Int = 1080): ImageBitmap? = withContext(Dispatchers.IO) {
        val f = file(context, source)?.takeIf { it.exists() } ?: return@withContext null
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide) }
            BitmapFactory.decodeFile(f.path, opts)?.asImageBitmap()
        }.getOrNull()
    }

    private fun sampleSize(w: Int, h: Int, maxSide: Int): Int {
        var s = 1
        while (maxOf(w, h) / (s * 2) >= maxSide) s *= 2
        return s
    }

    private fun fit(b: Bitmap, maxSide: Int): Bitmap {
        val side = maxOf(b.width, b.height)
        if (side <= maxSide) return b
        val k = maxSide.toFloat() / side
        return b.scale((b.width * k).toInt(), (b.height * k).toInt())
    }
}

private sealed interface ImageLoad {
    data class Loaded(val bitmap: ImageBitmap) : ImageLoad
    data object Missing : ImageLoad
}

/** A note picture, full width with rounded corners; says so when the file is gone. */
@Composable
fun NoteImage(source: String, alt: String, modifier: Modifier = Modifier, maxSide: Int = 1080, contentScale: ContentScale = ContentScale.FillWidth) {
    val context = LocalContext.current
    // Null while loading (draw nothing yet), then the picture or Missing.
    val state by produceState<ImageLoad?>(null, source) { value = NoteImages.load(context, source, maxSide)?.let(ImageLoad::Loaded) ?: ImageLoad.Missing }
    val description = alt.ifBlank { stringResource(R.string.note_image) }
    val loaded = state
    if (loaded == null) return
    if (loaded is ImageLoad.Loaded) {
        val image = loaded.bitmap
        Image(image, contentDescription = description, contentScale = contentScale, modifier = modifier.fillMaxWidth().clip(MaterialTheme.shapes.large))
    } else {
        Text("🖼 $description", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
    }
}
