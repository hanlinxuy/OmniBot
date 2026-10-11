package cn.com.omnimind.nativeui.settings

import androidx.compose.runtime.Immutable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The six preset Agent avatars (Dart `AgentAvatarService.presetAvatars`). */
const val AGENT_AVATAR_PRESET_COUNT = 6

/** Side of the saved avatar in pixels (Dart `_outputSize`). */
const val AGENT_AVATAR_OUTPUT_SIZE = 512

/** How far the user can zoom past "fill the circle" (Dart `_maxUserScale`). */
const val AGENT_AVATAR_MAX_ZOOM = 4f

/**
 * Where the photo sits under the circular crop: [zoom] over the fill scale
 * and the photo center's offset from the circle center, in viewport pixels.
 */
@Immutable
data class AvatarCropTransform(val zoom: Float = 1f, val offsetX: Float = 0f, val offsetY: Float = 0f)

/** The scale at which the photo just covers a [viewport]-sized square (Dart `_baseScale`). */
fun avatarFillScale(imageWidth: Int, imageHeight: Int, viewport: Float): Float =
    max(viewport / imageWidth, viewport / imageHeight)

/** Keeps the photo covering the circle: no gap shows at any edge (Dart `_clampOffset`). */
fun AvatarCropTransform.clamped(imageWidth: Int, imageHeight: Int, viewport: Float): AvatarCropTransform {
    val zoom = zoom.coerceIn(1f, AGENT_AVATAR_MAX_ZOOM)
    val scale = avatarFillScale(imageWidth, imageHeight, viewport) * zoom
    val maxX = max(0f, (imageWidth * scale - viewport) / 2)
    val maxY = max(0f, (imageHeight * scale - viewport) / 2)
    return AvatarCropTransform(zoom, offsetX.coerceIn(-maxX, maxX), offsetY.coerceIn(-maxY, maxY))
}

/**
 * One pinch or drag step. Dart zoomed around the circle's center, so a pinch
 * on someone's face slid it away; zooming here keeps the point under the
 * fingers ([focusX], [focusY], relative to the circle center) still.
 */
fun AvatarCropTransform.gesture(
    panX: Float,
    panY: Float,
    zoomBy: Float,
    focusX: Float,
    focusY: Float,
    imageWidth: Int,
    imageHeight: Int,
    viewport: Float,
): AvatarCropTransform {
    val nextZoom = (zoom * zoomBy).coerceIn(1f, AGENT_AVATAR_MAX_ZOOM)
    val ratio = nextZoom / zoom
    val x = focusX - (focusX - offsetX) * ratio + panX
    val y = focusY - (focusY - offsetY) * ratio + panY
    return AvatarCropTransform(nextZoom, x, y).clamped(imageWidth, imageHeight, viewport)
}

/** Source pixels inside the circle's square, as left, top and side (Dart `_renderCroppedAvatar`). */
@Immutable
data class AvatarSourceRect(val left: Int, val top: Int, val side: Int)

fun AvatarCropTransform.sourceRect(imageWidth: Int, imageHeight: Int, viewport: Float): AvatarSourceRect {
    val scale = avatarFillScale(imageWidth, imageHeight, viewport) * zoom
    val side = min(viewport / scale, min(imageWidth, imageHeight).toFloat())
    val left = (imageWidth / 2f - offsetX / scale - side / 2).coerceIn(0f, imageWidth - side)
    val top = (imageHeight / 2f - offsetY / scale - side / 2).coerceIn(0f, imageHeight - side)
    // Rounded: float error must not drop the last row (1000 px read back as 999).
    val px = side.roundToInt().coerceIn(1, min(imageWidth, imageHeight))
    return AvatarSourceRect(left.roundToInt().coerceIn(0, imageWidth - px), top.roundToInt().coerceIn(0, imageHeight - px), px)
}

/**
 * The decode sample size that keeps the shorter side at least [target]
 * pixels. Dart decoded the full photo: a 50-megapixel camera image took
 * about 200 MB before cropping to 512 px.
 */
fun avatarSampleSize(width: Int, height: Int, target: Int = AGENT_AVATAR_OUTPUT_SIZE * 2): Int {
    var sample = 1
    while (min(width, height) / (sample * 2) >= target) sample *= 2
    return sample
}
