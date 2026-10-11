package cn.com.omnimind.bot.ui.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.nativeui.R
import cn.com.omnimind.nativeui.settings.AgentAvatarPickerState
import cn.com.omnimind.nativeui.settings.AgentAvatarSheet
import cn.com.omnimind.nativeui.settings.AvatarCropScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The native Agent avatar editor (batch 5j), replacing the Flutter
 * `_AgentAvatarPickerDialog` and its crop dialog: a sheet of presets and a
 * full-screen crop for a photo. [onChanged] runs after every saved change so
 * the opener can show the new avatar.
 */
@Composable
internal fun AgentAvatarRoute(show: Boolean, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { AgentAvatarStore(context) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(AgentAvatarPickerState()) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    val photoBitmap: ImageBitmap? = remember(photo) { photo?.asImageBitmap() }

    suspend fun reload() {
        val choice = withContext(Dispatchers.IO) { store.read() }
        val custom = choice.customPath?.let { path ->
            withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
        }
        state = state.copy(presetIndex = choice.presetIndex, custom = custom)
    }
    LaunchedEffect(show) { if (show) reload() }

    fun save(block: suspend () -> Unit) {
        if (state.saving) return
        state = state.copy(saving = true)
        scope.launch {
            try {
                block()
                reload()
                onChanged()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                OmniLog.w("AgentAvatar", "Avatar save failed: ${error.javaClass.simpleName}")
                Toast.makeText(context, R.string.omni_avatar_save_failed, Toast.LENGTH_SHORT).show()
            } finally {
                state = state.copy(saving = false)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val opened = store.openPhoto(uri)
            if (opened == null) Toast.makeText(context, R.string.omni_avatar_open_failed, Toast.LENGTH_SHORT).show()
            photo = opened
        }
    }

    AgentAvatarSheet(
        show = show && photo == null,
        state = state,
        onPreset = { index -> save { store.choosePreset(index) } },
        onPickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onDismiss = onDismiss,
    )
    // A full-screen window over everything, so the page's top bar never covers the crop; back cancels it.
    photoBitmap?.let { bitmap ->
        Dialog(onDismissRequest = { if (!state.saving) photo = null }, properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false)) {
            AvatarCropScreen(bitmap, state.saving, onCancel = { photo = null }, onConfirm = { source ->
                val original = photo ?: return@AvatarCropScreen
                save {
                    store.saveCrop(original, source)
                    photo = null
                }
            })
        }
    }
}
