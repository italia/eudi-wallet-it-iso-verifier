package it.ipzs.scan.scan.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.common.Barcode
import it.ipzs.scan.R
import it.ipzs.scan.scan.utils.BarcodeAnalyser
import java.util.concurrent.Executors

@Composable
fun Camera(
    modifier: Modifier = Modifier,
    onBarcodesDetected: (List<Barcode>) -> Unit
){

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ){

        val lifecycleOwner = LocalLifecycleOwner.current

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PreviewView(context).also {
                    it.scaleType = PreviewView.ScaleType.FILL_CENTER
                }.also {
                    buildCamera(
                        previewView = it,
                        lifecycleOwner,
                        onBarcodesDetected = onBarcodesDetected
                    )
                }
            }
        )

        Image(painter = painterResource(id = R.drawable.image_sight), contentDescription = null)

    }

}

private fun buildCamera(
    previewView: PreviewView,
    lifecycleOwner: LifecycleOwner,
    onBarcodesDetected: (List<Barcode>) -> Unit
){

    val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)

    cameraProviderFuture.addListener({

        val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder()
            .setTargetRotation(previewView.display.rotation)
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        val imageCapture = ImageCapture.Builder().build()
        val imageAnalyzer = ImageAnalysis.Builder()
            .setTargetRotation(previewView.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                setAnalyzer(
                    Executors.newSingleThreadExecutor(),
                    BarcodeAnalyser{
                        cameraProvider.unbindAll()
                        clearAnalyzer()
                        onBarcodesDetected(it)
                    }
                )
            }

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageCapture
        )

        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            imageAnalyzer
        )

    }, ContextCompat.getMainExecutor(previewView.context))

}