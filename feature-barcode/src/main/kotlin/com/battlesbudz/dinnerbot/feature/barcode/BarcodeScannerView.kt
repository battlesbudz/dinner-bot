package com.battlesbudz.dinnerbot.feature.barcode

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The camera surface of every scan flow (Phase V2-5; shared module
 * since v0.6.3, when the add-ingredient form gained its own scan
 * button). ML Kit's bundled barcode model decodes on the device:
 * decoding needs no connection, and no photo is ever stored or sent
 * anywhere; frames are read for a code and dropped. The first code
 * decoded is handed to [onBarcode] exactly once. This composable is
 * the only place the camera opens.
 */
@Composable
fun BarcodeScannerDialog(
    onBarcode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanner = remember { BarcodeScanning.getClient() }
    val delivered = remember { AtomicBoolean(false) }
    // One analyzer executor per scanner session, shut down with it so
    // repeated scans never accumulate idle camera threads.
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(scanner, analysisExecutor) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            runCatching { scanner.close() }
            analysisExecutor.shutdown()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black).testTag("barcode-scanner"),
        ) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val providerFuture = ProcessCameraProvider.getInstance(ctx)
                    providerFuture.addListener(
                        {
                            val cameraProvider = runCatching { providerFuture.get() }.getOrNull()
                                ?: return@addListener
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(analysisExecutor) { proxy ->
                                analyzeFrame(scanner, proxy, delivered) { value ->
                                    ContextCompat.getMainExecutor(ctx).execute { onBarcode(value) }
                                }
                            }
                            runCatching {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis,
                                )
                            }
                        },
                        ContextCompat.getMainExecutor(ctx),
                    )
                    previewView
                },
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Point the camera at the barcode. The code is read on this phone; " +
                        "no photo is taken or sent anywhere.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}

/** Decode one frame; deliver the first code found, once, and always release the frame. */
@SuppressLint("UnsafeOptInUsageError")
private fun analyzeFrame(
    scanner: BarcodeScanner,
    proxy: ImageProxy,
    delivered: AtomicBoolean,
    onBarcode: (String) -> Unit,
) {
    val mediaImage = proxy.image
    if (mediaImage == null || delivered.get()) {
        proxy.close()
        return
    }
    val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            val value = barcodes.firstOrNull()?.rawValue
            if (value != null && delivered.compareAndSet(false, true)) {
                onBarcode(value)
            }
        }
        .addOnCompleteListener { proxy.close() }
}
