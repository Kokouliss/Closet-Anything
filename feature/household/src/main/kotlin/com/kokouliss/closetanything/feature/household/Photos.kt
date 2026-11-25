package com.kokouliss.closetanything.feature.household

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.kokouliss.closetanything.core.domain.model.ImageData
import com.kokouliss.closetanything.core.domain.model.Thing
import com.kokouliss.closetanything.core.ui.component.AppIcon
import com.kokouliss.closetanything.core.ui.component.AppIcons
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decoded off the main thread, so a list of photos scrolls without a hitch. Null until then. */
@Composable
internal fun rememberBitmap(image: ImageData?): ImageBitmap? = produceState<ImageBitmap?>(null, image) {
    value = image?.let {
        withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(it.bytes, 0, it.bytes.size)?.asImageBitmap() }
    }
}.value

/** A picked or captured picture that is not stored yet, scaled to the screen. Null until decoded, or when it cannot be read. */
@Composable
internal fun rememberBitmap(uri: String?): ImageBitmap? {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(null, uri) {
        value = uri?.let {
            withContext(Dispatchers.IO) {
                try {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, Uri.parse(it))) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val scale = PREVIEW_MAX_PX.toFloat() / maxOf(info.size.width, info.size.height)
                        if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                    }.asImageBitmap()
                } catch (e: IOException) {
                    null
                }
            }
        }
    }.value
}

private const val PREVIEW_MAX_PX = 1280

/** Where the camera app writes a new picture. One file is enough: it is shrunk and stored right away. */
internal fun captureUri(context: Context): Uri {
    val file = File(context.cacheDir, "photos/capture.jpg").apply { parentFile?.mkdirs() }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** The small photo of a thing, or the box or picture icon when it has none. Decoration: the row names the thing. */
@Composable
internal fun Thumbnail(thing: Thing, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val bitmap = rememberBitmap(thing.thumbnail)
    Box(
        modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            AppIcon(if (thing.isBox) AppIcons.Box else AppIcons.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
