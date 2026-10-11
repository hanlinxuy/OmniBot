package cn.com.omnimind.nativeui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarCropTest {
    private val viewport = 300f

    @Test fun thePhotoAlwaysCoversTheCircle() {
        // A 1200x600 photo fills 300 px tall at scale 0.5: 600 px wide, 150 px of slack each side.
        val clamped = AvatarCropTransform(1f, 400f, 50f).clamped(1200, 600, viewport)
        assertEquals(150f, clamped.offsetX, .01f)
        assertEquals(0f, clamped.offsetY, .01f)
        assertEquals(1f, AvatarCropTransform(.3f).clamped(1200, 600, viewport).zoom, .0001f)
        assertEquals(AGENT_AVATAR_MAX_ZOOM, AvatarCropTransform(9f).clamped(1200, 600, viewport).zoom, .0001f)
    }

    /** Fix (5j): Dart zoomed around the circle center, so a pinch on a face slid it away. */
    @Test fun zoomKeepsThePointUnderTheFingers() {
        val start = AvatarCropTransform()
        val zoomed = start.gesture(0f, 0f, zoomBy = 2f, focusX = 60f, focusY = -40f, imageWidth = 1000, imageHeight = 1000,
            viewport = viewport)
        // The image point under the focus stays put: offset moves by -(focus)·(ratio-1).
        assertEquals(-60f, zoomed.offsetX, .01f)
        assertEquals(40f, zoomed.offsetY, .01f)
        assertEquals(2f, zoomed.zoom, .0001f)
    }

    @Test fun theSourceSquareFollowsZoomAndPan() {
        // Square 1000 px photo, fill scale 0.3: unzoomed, the circle sees the whole photo.
        assertEquals(AvatarSourceRect(0, 0, 1000), AvatarCropTransform().sourceRect(1000, 1000, viewport))
        // At 2x the circle sees half; panned right by 75 viewport px = 125 source px to the left.
        assertEquals(AvatarSourceRect(125, 250, 500), AvatarCropTransform(2f, 75f, 0f).sourceRect(1000, 1000, viewport))
    }

    /** Fix (5j): Dart decoded full-size camera photos before cropping to 512 px. */
    @Test fun largePhotosDecodeSampled() {
        assertEquals(1, avatarSampleSize(1080, 1920))
        assertEquals(4, avatarSampleSize(6144, 8192))
        assertEquals(1, avatarSampleSize(300, 300))
    }
}
