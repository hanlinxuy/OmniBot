package cn.com.omnimind.bot.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.nativeui.settings.BridgeQrCode
import cn.com.omnimind.nativeui.settings.BridgeScannerScreen
import cn.com.omnimind.nativeui.settings.BridgeScannerStatus
import cn.com.omnimind.nativeui.settings.parseBridgeQrCode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.concurrent.Executors

/** How long a found code stays on screen before the page closes, so the green frame registers. */
private const val FOUND_HOLD_MS = 450L

/** How long "not a Bridge code" stays before scanning hints return (Dart throttled its toast to 2 s). */
private const val WRONG_CODE_HOLD_MS = 2_000L

/**
 * The native PC Bridge scanner (batch 5i), replacing the Flutter
 * `CodexBridgeQrScannerPage`: CameraX preview, ML Kit QR detection, and the
 * native overlay. [onScanned] runs once with the first Bridge code.
 */
@Composable
internal fun BridgeQrScannerRoute(onScanned: (BridgeQrCode) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val currentOnScanned by rememberUpdatedState(onScanned)
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var asked by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(BridgeScannerStatus.Scanning) }
    var found by remember { mutableStateOf<BridgeQrCode?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    // Back from app settings, where the user may have allowed the camera.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (!granted) granted = context.hasCameraPermission() }
    BackHandler(onBack = onBack)

    LaunchedEffect(found) {
        val code = found ?: return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        delay(FOUND_HOLD_MS)
        currentOnScanned(code)
    }
    LaunchedEffect(status) {
        if (status == BridgeScannerStatus.NotABridgeCode) {
            delay(WRONG_CODE_HOLD_MS)
            if (status == BridgeScannerStatus.NotABridgeCode) status = BridgeScannerStatus.Scanning
        }
    }

    if (granted) {
        BindCamera(
            onSurface = { surfaceRequest = it },
            onCamera = { camera = it },
            onFailed = { status = BridgeScannerStatus.CameraUnavailable },
            onCode = { raw ->
                if (found != null) return@BindCamera
                val code = parseBridgeQrCode(raw)
                if (code != null) {
                    found = code
                    status = BridgeScannerStatus.Found
                } else if (status == BridgeScannerStatus.Scanning) {
                    status = BridgeScannerStatus.NotABridgeCode
                }
            },
        )
    }

    BridgeScannerScreen(
        status = when {
            !granted && asked -> BridgeScannerStatus.PermissionDenied
            else -> status
        },
        torchOn = torchOn,
        torchAvailable = camera?.cameraInfo?.hasFlashUnit() == true,
        onToggleTorch = {
            camera?.cameraControl?.enableTorch(!torchOn)
            torchOn = !torchOn
        },
        // After a permanent denial the system dialog no longer appears; send the user to app settings.
        onGrantPermission = { context.openAppSettings() },
        onBack = onBack,
    ) {
        surfaceRequest?.let { CameraXViewfinder(it, Modifier.fillMaxSize()) }
    }
}

/** Binds a back-camera preview and QR analysis to this composition's lifecycle. */
@Composable
private fun BindCamera(
    onSurface: (SurfaceRequest) -> Unit,
    onCamera: (Camera) -> Unit,
    onFailed: () -> Unit,
    onCode: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }
    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            executor.shutdown()
        }
    }
    LaunchedEffect(lifecycleOwner) {
        try {
            val provider = ProcessCameraProvider.awaitInstance(context)
            val preview = Preview.Builder().build().apply { setSurfaceProvider { onSurface(it) } }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) @OptIn(ExperimentalGetImage::class) { proxy ->
                val image = proxy.image
                if (image == null) {
                    proxy.close()
                    return@setAnalyzer
                }
                scanner.process(InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener { codes ->
                        codes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }?.let(currentOnCode)
                    }
                    .addOnCompleteListener { proxy.close() }
            }
            provider.unbindAll()
            onCamera(provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            OmniLog.w("BridgeScanner", "Camera unavailable: ${error.javaClass.simpleName}")
            onFailed()
        }
    }
}

private fun Context.hasCameraPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    runCatching {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
