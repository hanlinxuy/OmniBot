package cn.com.omnimind.bot.ui.workspace

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import cn.com.omnimind.nativeui.workspace.PdfPageSize
import cn.com.omnimind.nativeui.workspace.PdfPages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * An open PDF for the native preview (batch 5l). The renderer stays open
 * while the page shows it, so scrolling does not reopen the file per page
 * as `PdfPreviewChannel` did. `PdfRenderer` allows one open page at a time,
 * so every render is serialized.
 */
internal class NativePdfPages private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    override val pages: List<PdfPageSize>,
) : PdfPages {
    private val lock = Mutex()
    private var closed = false
    @Volatile private var closePending = false

    override suspend fun render(index: Int, widthPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            if (closed || index !in pages.indices) return@withLock null
            runCatching {
                renderer.openPage(index).use { page ->
                    val width = widthPx.coerceAtLeast(1)
                    val height = (page.height * width.toFloat() / page.width).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }.asImageBitmap()
                }
            }.getOrNull().also { if (closePending) closeNow() }
        }
    }

    /**
     * Releases the file. A render in flight finishes first and then sees
     * [closed]; closing the renderer under an open page crashes, so the last
     * render closes it if one is running now.
     */
    fun close() {
        if (lock.tryLock()) {
            try {
                closeNow()
            } finally {
                lock.unlock()
            }
        } else {
            closePending = true
        }
    }

    private fun closeNow() {
        if (closed) return
        closed = true
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        /** Opens [path] and reads every page size once; null when it is not a readable PDF. */
        suspend fun open(path: String): NativePdfPages? = withContext(Dispatchers.IO) {
            val descriptor = runCatching { ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()
                ?: return@withContext null
            try {
                val renderer = PdfRenderer(descriptor)
                val sizes = (0 until renderer.pageCount).map { index ->
                    renderer.openPage(index).use { PdfPageSize(it.width, it.height) }
                }
                NativePdfPages(descriptor, renderer, sizes)
            } catch (error: Exception) {
                // Encrypted or damaged files throw here; the page offers "open with" instead.
                runCatching { descriptor.close() }
                null
            }
        }
    }
}
