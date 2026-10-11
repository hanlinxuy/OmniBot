package cn.com.omnimind.nativeui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.com.omnimind.nativeui.R
import cn.com.omnimind.nativeui.components.OmniIcon
import cn.com.omnimind.nativeui.components.OmniIconButton
import cn.com.omnimind.nativeui.theme.LocalOmniPalette
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text

/** What the scanner shows over (or instead of) the camera. */
enum class BridgeScannerStatus { Scanning, NotABridgeCode, Found, PermissionDenied, CameraUnavailable }

private val FRAME = 248.dp

/**
 * The Bridge scanner chrome over a camera preview (batch 5i): a dimmed
 * surround with a clear rounded viewfinder, corner brackets, a sweeping
 * line, and a hint that cross-fades when a code is wrong or found. The
 * camera itself belongs to the host ([preview]).
 */
@Composable
fun BridgeScannerScreen(
    status: BridgeScannerStatus,
    torchOn: Boolean,
    torchAvailable: Boolean,
    onToggleTorch: () -> Unit,
    onGrantPermission: () -> Unit,
    onBack: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val palette = LocalOmniPalette.current
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val cameraLive = status != BridgeScannerStatus.PermissionDenied && status != BridgeScannerStatus.CameraUnavailable
        if (cameraLive) {
            preview()
            Viewfinder(found = status == BridgeScannerStatus.Found, accent = palette.accent)
        } else {
            CameraProblem(status, onGrantPermission)
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OmniIconButton(R.drawable.omni_chevron_left, stringResource(R.string.omni_back), onBack, tint = Color.White)
            Text(stringResource(R.string.omni_scan_title), color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
            if (cameraLive && torchAvailable) {
                OmniIconButton(R.drawable.omni_zap, stringResource(if (torchOn) R.string.omni_scan_torch_off else R.string.omni_scan_torch_on),
                    onToggleTorch, tint = if (torchOn) palette.accent else Color.White)
            }
        }
        if (cameraLive) Hint(status, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun Viewfinder(found: Boolean, accent: Color) {
    val sweep by rememberInfiniteTransition(label = "scan-sweep")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sweep")
    Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val side = FRAME.toPx()
        val topLeft = Offset((size.width - side) / 2, (size.height - side) / 2 - 24.dp.toPx())
        val radius = CornerRadius(20.dp.toPx())
        drawRect(Color.Black.copy(alpha = .55f))
        drawRoundRect(Color.Transparent, topLeft, Size(side, side), radius, blendMode = BlendMode.Clear)
        val color = if (found) accent else Color.White
        val arm = 34.dp.toPx()
        val stroke = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
        // Four corner brackets: the rounded frame's outline, clipped to each corner.
        listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f).forEach { (fx, fy) ->
            val x = topLeft.x + fx * (side - arm)
            val y = topLeft.y + fy * (side - arm)
            clipRect(x - stroke.width, y - stroke.width, x + arm + stroke.width, y + arm + stroke.width) {
                drawRoundRect(color, topLeft, Size(side, side), radius, style = stroke)
            }
        }
        if (!found) {
            val y = topLeft.y + 12.dp.toPx() + sweep * (side - 24.dp.toPx())
            drawLine(Brush.horizontalGradient(listOf(Color.Transparent, accent, Color.Transparent),
                startX = topLeft.x, endX = topLeft.x + side), Offset(topLeft.x + 16.dp.toPx(), y),
                Offset(topLeft.x + side - 16.dp.toPx(), y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun Hint(status: BridgeScannerStatus, modifier: Modifier) {
    val palette = LocalOmniPalette.current
    AnimatedContent(status, modifier.navigationBarsPadding().padding(24.dp),
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) }, label = "scan-hint") { current ->
        val (text, tint) = when (current) {
            BridgeScannerStatus.NotABridgeCode -> stringResource(R.string.omni_scan_not_bridge) to Color(0xFFFFB020)
            BridgeScannerStatus.Found -> stringResource(R.string.omni_scan_found) to palette.accent
            else -> stringResource(R.string.omni_scan_hint) to palette.accent
        }
        Row(Modifier.clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = .62f)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OmniIcon(R.drawable.omni_scan_qr, size = 18.dp, tint = tint)
            Spacer(Modifier.width(10.dp))
            Text(text, color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun CameraProblem(status: BridgeScannerStatus, onGrantPermission: () -> Unit) {
    val palette = LocalOmniPalette.current
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        OmniIcon(R.drawable.omni_scan_qr, size = 40.dp, tint = Color.White.copy(alpha = .7f))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(if (status == BridgeScannerStatus.PermissionDenied) R.string.omni_scan_permission
            else R.string.omni_scan_camera_unavailable), color = Color.White, fontSize = 14.sp, lineHeight = 21.sp,
            textAlign = TextAlign.Center)
        if (status == BridgeScannerStatus.PermissionDenied) {
            Spacer(Modifier.height(20.dp))
            Button(onGrantPermission, colors = ButtonDefaults.buttonColorsPrimary(color = palette.accent)) {
                Text(stringResource(R.string.omni_scan_grant), color = Color.White)
            }
        }
    }
}
