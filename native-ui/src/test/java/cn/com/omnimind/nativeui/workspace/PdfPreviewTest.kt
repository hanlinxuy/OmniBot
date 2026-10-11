package cn.com.omnimind.nativeui.workspace

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfPreviewTest {
    @Test fun pagesRenderSharperInStepsAndNeverPastTheCap() {
        assertEquals(1000, pdfRenderWidth(1000, 1f))
        assertEquals(1000, pdfRenderWidth(1000, 1.4f))
        assertEquals(2000, pdfRenderWidth(1000, 2f))
        // 3x of a 1000 px viewport would be 3000; one page is capped at 2400.
        assertEquals(2400, pdfRenderWidth(1000, 4f))
    }

    @Test fun pageAspectFallsBackToA4ForBrokenSizes() {
        assertEquals(.5f, PdfPageSize(100, 200).aspect, .0001f)
        assertEquals(1f / 1.414f, PdfPageSize(0, 0).aspect, .0001f)
    }
}
