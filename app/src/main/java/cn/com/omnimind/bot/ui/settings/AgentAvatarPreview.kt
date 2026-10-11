package cn.com.omnimind.bot.ui.settings

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import cn.com.omnimind.nativeui.settings.AGENT_AVATAR_PRESETS

/**
 * A small preview of the Agent avatar for native pages: the cropped photo,
 * else the chosen preset from the native resources (5j). It used to read
 * the presets out of Flutter's packaged assets, which go away with Flutter.
 */
internal fun loadAgentAvatarPreview(context: Context): ImageBitmap? = runCatching {
    val choice = AgentAvatarStore(context).read()
    choice.customPath?.let { path ->
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = previewSampleSize(bounds.outWidth, bounds.outHeight) }
        BitmapFactory.decodeFile(path, options)?.asImageBitmap()?.let { return@runCatching it }
    }
    BitmapFactory.decodeResource(context.resources, AGENT_AVATAR_PRESETS[choice.presetIndex])?.asImageBitmap()
}.getOrNull()

private const val PREVIEW_SIDE = 128

/**
 * The power-of-two sample that brings the photo near [PREVIEW_SIDE]. The old
 * loop started from `BitmapFactory.Options.inSampleSize`, which is 0, so it
 * divided by zero and a custom avatar never showed on native pages (fixed in 5j).
 */
internal fun previewSampleSize(width: Int, height: Int): Int {
    var sample = 1
    while (width / sample > PREVIEW_SIDE || height / sample > PREVIEW_SIDE) sample *= 2
    return sample
}
