package cn.com.omnimind.bot.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import cn.com.omnimind.nativeui.settings.AGENT_AVATAR_OUTPUT_SIZE
import cn.com.omnimind.nativeui.settings.AGENT_AVATAR_PRESET_COUNT
import cn.com.omnimind.nativeui.settings.AvatarSourceRect
import cn.com.omnimind.nativeui.settings.avatarSampleSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The Agent avatar: a preset index, or a cropped photo that takes precedence. */
internal data class AgentAvatarChoice(val presetIndex: Int, val customPath: String?)

/**
 * The Agent avatar on the Flutter keys (batch 5j), so the remaining Flutter
 * surfaces (chat bubbles, the pet) keep showing the same avatar: the preset
 * index (`flutter.agentAvatarIndex`) and the cropped photo
 * (`flutter.agentAvatarCustomImagePath`) under the app's support directory.
 */
internal class AgentAvatarStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
    /** Flutter `getApplicationSupportDirectory()` is the app's `files` directory. */
    private val directory get() = File(appContext.filesDir, "agent_avatars")

    fun read(): AgentAvatarChoice {
        // Flutter writes Long; read any number so a stray Int does not throw away the choice.
        val stored = (preferences.all[KEY_INDEX] as? Number)?.toLong() ?: 0L
        val index = if (stored in 0 until AGENT_AVATAR_PRESET_COUNT) stored.toInt() else 0
        val path = preferences.getString(KEY_CUSTOM, null)?.trim()?.takeIf { it.isNotEmpty() && File(it).isFile }
        return AgentAvatarChoice(index, path)
    }

    /** Chooses a preset and deletes the photo it replaces. */
    suspend fun choosePreset(index: Int) = withContext(Dispatchers.IO) {
        val previous = read().customPath
        check(preferences.edit().putLong(KEY_INDEX, index.toLong().coerceIn(0, AGENT_AVATAR_PRESET_COUNT - 1L))
            .remove(KEY_CUSTOM).commit()) { "Could not save the avatar" }
        previous?.let(::deleteManaged)
    }

    /**
     * Opens the photo at [uri] for cropping: upright (ImageDecoder applies
     * EXIF rotation) and sampled so its shorter side is about twice the
     * output. Dart decoded it at full size.
     */
    suspend fun openPhoto(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(appContext.contentResolver, uri)) { decoder, info, _ ->
                decoder.setTargetSampleSize(avatarSampleSize(info.size.width, info.size.height))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull()
    }

    /** Crops [source] (pixels of [photo]) to a 512 px square and stores it as the avatar. */
    suspend fun saveCrop(photo: Bitmap, source: AvatarSourceRect) = withContext(Dispatchers.IO) {
        val avatar = Bitmap.createBitmap(AGENT_AVATAR_OUTPUT_SIZE, AGENT_AVATAR_OUTPUT_SIZE, Bitmap.Config.ARGB_8888)
        Canvas(avatar).drawBitmap(photo, Rect(source.left, source.top, source.left + source.side, source.top + source.side),
            Rect(0, 0, AGENT_AVATAR_OUTPUT_SIZE, AGENT_AVATAR_OUTPUT_SIZE), Paint(Paint.FILTER_BITMAP_FLAG))
        directory.mkdirs()
        val target = File(directory, "agent_avatar_${System.currentTimeMillis()}.png")
        try {
            target.outputStream().use { check(avatar.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Could not encode the avatar" } }
        } finally {
            avatar.recycle()
        }
        val previous = read().customPath
        check(preferences.edit().putString(KEY_CUSTOM, target.absolutePath).commit()) { "Could not save the avatar" }
        previous?.takeIf { it != target.absolutePath }?.let(::deleteManaged)
    }

    /** Deletes only files this store created, never a path that points elsewhere. */
    private fun deleteManaged(path: String) {
        val file = File(path)
        if (file.parentFile?.canonicalPath == directory.canonicalPath) file.delete()
    }

    private companion object {
        const val KEY_INDEX = "flutter.agentAvatarIndex"
        const val KEY_CUSTOM = "flutter.agentAvatarCustomImagePath"
    }
}
