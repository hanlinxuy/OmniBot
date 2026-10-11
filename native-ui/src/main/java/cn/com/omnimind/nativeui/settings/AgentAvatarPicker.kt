package cn.com.omnimind.nativeui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.com.omnimind.nativeui.R
import cn.com.omnimind.nativeui.components.OmniIcon
import cn.com.omnimind.nativeui.theme.LocalOmniPalette
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

/** The six preset avatars, packaged natively (batch 5j). */
val AGENT_AVATAR_PRESETS = listOf(
    R.drawable.omni_agent_avatar_1, R.drawable.omni_agent_avatar_2, R.drawable.omni_agent_avatar_3,
    R.drawable.omni_agent_avatar_4, R.drawable.omni_agent_avatar_5, R.drawable.omni_agent_avatar_6,
)

@Immutable
data class AgentAvatarPickerState(
    val presetIndex: Int = 0,
    /** The cropped photo in use, which wins over the preset. */
    val custom: ImageBitmap? = null,
    val saving: Boolean = false,
)

/**
 * Picks the Agent avatar (Dart `_AgentAvatarPickerDialog`) as a sheet: the
 * current avatar large, the six presets with a springing selection ring, and
 * a photo entry. Choosing a preset applies at once; the sheet stays open so
 * the change can be seen and compared.
 */
@Composable
fun AgentAvatarSheet(
    show: Boolean,
    state: AgentAvatarPickerState,
    onPreset: (Int) -> Unit,
    onPickPhoto: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalOmniPalette.current
    OverlayBottomSheet(show = show, title = stringResource(R.string.omni_avatar_title), onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AvatarImage(state, Modifier.size(88.dp))
            Spacer(Modifier.height(20.dp))
            // Two rows of three keep each target large on phones.
            AGENT_AVATAR_PRESETS.chunked(3).forEachIndexed { row, presets ->
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    presets.forEachIndexed { column, drawable ->
                        val index = row * 3 + column
                        PresetAvatar(drawable, selected = state.custom == null && state.presetIndex == index,
                            label = stringResource(R.string.omni_avatar_preset, index + 1), enabled = !state.saving) { onPreset(index) }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.secondarySurface)
                .clickable(enabled = !state.saving, role = Role.Button, onClick = onPickPhoto)
                .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                OmniIcon(R.drawable.omni_image, size = 20.dp, tint = palette.accent)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.omni_avatar_from_photo), fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    color = palette.text, modifier = Modifier.weight(1f))
                if (state.custom != null) {
                    Text(stringResource(R.string.omni_avatar_in_use), fontSize = 12.sp, color = palette.accent)
                    Spacer(Modifier.width(6.dp))
                }
                OmniIcon(R.drawable.omni_chevron_right, size = 16.dp, tint = palette.tertiaryText)
            }
        }
    }
}

@Composable
fun AvatarImage(state: AgentAvatarPickerState, modifier: Modifier) {
    val custom = state.custom
    if (custom != null) Image(custom, null, modifier.clip(CircleShape), contentScale = ContentScale.Crop)
    else Image(painterResource(AGENT_AVATAR_PRESETS[state.presetIndex.coerceIn(0, AGENT_AVATAR_PRESETS.lastIndex)]), null,
        modifier.clip(CircleShape), contentScale = ContentScale.Crop)
}

@Composable
private fun PresetAvatar(drawable: Int, selected: Boolean, label: String, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalOmniPalette.current
    val ring by animateColorAsState(if (selected) palette.accent else Color.Transparent, label = "avatar-ring")
    val scale by animateFloatAsState(if (selected) 1f else .9f, spring(dampingRatio = .55f, stiffness = Spring.StiffnessMedium),
        label = "avatar-scale")
    Box(Modifier.size(72.dp).clip(CircleShape).border(3.dp, ring, CircleShape)
        .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .semantics { contentDescription = label; this.selected = selected }
        .padding(6.dp), contentAlignment = Alignment.Center) {
        Image(painterResource(drawable), null, Modifier.fillMaxSize().scale(scale).clip(CircleShape), contentScale = ContentScale.Crop)
    }
}

/**
 * Crops a photo to the circle (Dart `_AgentAvatarCropDialog`), full screen:
 * pinch to zoom around the fingers, drag to move, and the photo always
 * covers the circle. [onConfirm] gets the source square in [photo] pixels.
 */
@Composable
fun AvatarCropScreen(
    photo: ImageBitmap,
    saving: Boolean,
    onCancel: () -> Unit,
    onConfirm: (AvatarSourceRect) -> Unit,
) {
    val palette = LocalOmniPalette.current
    val density = LocalDensity.current
    var transform by remember(photo) { mutableStateOf(AvatarCropTransform()) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val viewport = with(density) { (minOf(size.width, size.height).toFloat() - 48.dp.toPx()).coerceAtLeast(1f) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().onSizeChanged { size = it }
            .pointerInput(photo, viewport) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    if (saving) return@detectTransformGestures
                    val focus = centroid - Offset(this.size.width / 2f, this.size.height / 2f)
                    transform = transform.gesture(pan.x, pan.y, zoom, focus.x, focus.y, photo.width, photo.height, viewport)
                }
            }) {
            val fill = avatarFillScale(photo.width, photo.height, viewport) * transform.zoom
            Image(photo, null, Modifier.align(Alignment.Center).requiredSize(
                with(density) { (photo.width * fill).toDp() }, with(density) { (photo.height * fill).toDp() })
                .graphicsLayer { translationX = transform.offsetX; translationY = transform.offsetY },
                contentScale = ContentScale.FillBounds)
            Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                drawRect(Color.Black.copy(alpha = .62f))
                drawCircle(Color.Transparent, radius = viewport / 2, blendMode = BlendMode.Clear)
                drawCircle(Color.White.copy(alpha = .9f), radius = viewport / 2, style = Stroke(1.5.dp.toPx()))
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(stringResource(R.string.omni_cancel), onCancel, Modifier.weight(1f), enabled = !saving)
            Button({ onConfirm(transform.sourceRect(photo.width, photo.height, viewport)) }, Modifier.weight(1f),
                enabled = !saving, colors = ButtonDefaults.buttonColorsPrimary(color = palette.accent)) {
                if (saving) {
                    CircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp,
                        colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = Color.White,
                            backgroundColor = Color.White.copy(alpha = .3f)))
                } else {
                    Text(stringResource(R.string.omni_avatar_use), color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text(stringResource(R.string.omni_avatar_crop_hint), color = Color.White.copy(alpha = .75f), fontSize = 13.sp,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 20.dp))
    }
}
