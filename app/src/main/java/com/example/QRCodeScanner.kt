package com.example

import android.Manifest
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class QRCodeAnalyzer(private val onQRCodeDetected: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
        )
        setHints(hints)
    }

    override fun analyze(image: ImageProxy) {
        val buffer = image.planes[0].buffer
        val data = buffer.toByteArray()
        val width = image.width
        val height = image.height

        val source = PlanarYUVLuminanceSource(
            data, width, height, 0, 0, width, height, false
        )
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

        try {
            val result = reader.decodeWithState(binaryBitmap)
            onQRCodeDetected(result.text)
        } catch (e: Exception) {
            // No QR code found
        } finally {
            reader.reset()
            image.close()
        }
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()
        val data = ByteArray(remaining())
        get(data)
        return data
    }
}

@Composable
fun QRCodeScannerDialog(
    onScanSuccess: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "دسترسی به دوربین مورد نیاز است.", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }

    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("اسکنر QR Code", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "بستن", tint = Color.White)
                    }
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(Color.Black, shape = RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
                    var isDetected by remember { mutableStateOf(false) }

                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(this.surfaceProvider)
                                    }

                                    val imageAnalysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                        .also { analysis ->
                                            analysis.setAnalyzer(
                                                cameraExecutor,
                                                QRCodeAnalyzer { scanResult ->
                                                    if (!isDetected) {
                                                        isDetected = true
                                                        onScanSuccess(scanResult)
                                                    }
                                                }
                                            )
                                        }

                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageAnalysis
                                        )
                                    } catch (e: Exception) {
                                        Log.e("QRScanner", "Use case binding failed", e)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay boundary scan visual
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .align(Alignment.Center)
                    ) {
                        CanvasOverlay()
                    }
                }
            },
            confirmButton = {},
            containerColor = Color(0xFF2B2930),
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
fun CanvasOverlay() {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val lineLen = width * 0.15f
        val strokeWidth = 4.dp.toPx()
        val color = Color(0xFFD0BCFF)

        // Top Left
        drawLine(color, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(lineLen, 0f), strokeWidth)
        drawLine(color, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, lineLen), strokeWidth)

        // Top Right
        drawLine(color, androidx.compose.ui.geometry.Offset(width, 0f), androidx.compose.ui.geometry.Offset(width - lineLen, 0f), strokeWidth)
        drawLine(color, androidx.compose.ui.geometry.Offset(width, 0f), androidx.compose.ui.geometry.Offset(width, lineLen), strokeWidth)

        // Bottom Left
        drawLine(color, androidx.compose.ui.geometry.Offset(0f, height), androidx.compose.ui.geometry.Offset(lineLen, height), strokeWidth)
        drawLine(color, androidx.compose.ui.geometry.Offset(0f, height), androidx.compose.ui.geometry.Offset(0f, height - lineLen), strokeWidth)

        // Bottom Right
        drawLine(color, androidx.compose.ui.geometry.Offset(width, height), androidx.compose.ui.geometry.Offset(width - lineLen, height), strokeWidth)
        drawLine(color, androidx.compose.ui.geometry.Offset(width, height), androidx.compose.ui.geometry.Offset(width, height - lineLen), strokeWidth)
    }
}
