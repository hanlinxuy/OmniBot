package cn.com.omnimind.bot.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentAvatarPreviewTest {
    /** Fix (5j): the sample loop started at 0 and divided by zero, so custom avatars never previewed. */
    @Test fun previewSampleStartsAtOneAndHalvesToTheTarget() {
        assertEquals(1, previewSampleSize(128, 128))
        assertEquals(4, previewSampleSize(512, 512))
        assertEquals(1, previewSampleSize(0, 0))
    }
}
