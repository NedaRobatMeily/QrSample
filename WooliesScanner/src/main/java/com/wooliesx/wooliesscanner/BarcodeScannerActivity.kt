package com.wooliesx.wooliesscanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v4.os.ResultReceiver
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.OnSuccessListener
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.wooliesx.wooliesscanner.BarcodeScanner.Companion.EXTRA_LAYOUT_RESOURCE_ID
import com.wooliesx.wooliesscanner.BarcodeScanner.Companion.EXTRA_SCAN_LISTENER
import com.wooliesx.wooliesscanner.BarcodeScanner.Companion.EXTRA_SCAN_TYPE
import com.wooliesx.wooliesscanner.databinding.ActivityBarcodeScannerBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class BarcodeScannerActivity() : AppCompatActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var viewBinding: ActivityBarcodeScannerBinding
    private lateinit var resultReceiver: ResultReceiver
    private lateinit var scanType: BarcodeScanner.ScanType
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        resultReceiver = intent.getParcelableExtra(EXTRA_SCAN_LISTENER)!!
        scanType = intent.getSerializableExtra(EXTRA_SCAN_TYPE) as BarcodeScanner.ScanType

        viewBinding = ActivityBarcodeScannerBinding.inflate(layoutInflater)

        val layoutResourceId = intent.getIntExtra(EXTRA_LAYOUT_RESOURCE_ID, -1)
        if (layoutResourceId != -1) {
            layoutInflater.inflate(layoutResourceId, null).let {
                viewBinding.overlayView.addView(it)
            }
        }

        setContentView(viewBinding.root)
        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera(scanType)
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun startCamera(scanType: BarcodeScanner.ScanType) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.previewView.surfaceProvider)
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeImageAnalyzer(scanType) { barcodes ->
                        barcodes.firstOrNull()?.rawValue?.let {
                            onBarcodeFound(it)
                        }
                    }
                    )
                }

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun onBarcodeFound(barcode: String) {
        resultReceiver.send(RESULT_OK, Bundle().apply {
            this.putString(EXTRA_RESULT_URI, barcode)
        })
        finish()
    }

    private fun onError(errorReason: BarcodeScanner.ScanErrorReason) {
        resultReceiver.send(RESULT_FAILED, Bundle().apply {
            this.putSerializable(EXTRA_ERROR_REASON, errorReason)
        })
        finish()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera(scanType)
            } else {
                onError(BarcodeScanner.ScanErrorReason.PERMISSION_MISSING)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val REQUEST_CODE_PERMISSIONS = 10

        const val EXTRA_RESULT_URI = "extra_result_uri"
        const val EXTRA_ERROR_REASON = "extra_error_reason"

        const val RESULT_FAILED = -2

        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    private class BarcodeImageAnalyzer(
        val scanType: BarcodeScanner.ScanType,
        val onSuccessListener: OnSuccessListener<List<Barcode>>
    ) :
        ImageAnalysis.Analyzer {
        val options = BarcodeScannerOptions.Builder().apply {
            when (scanType) {
                BarcodeScanner.ScanType.ALL -> {
                    this.setBarcodeFormats(
                        Barcode.FORMAT_QR_CODE,
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_EAN_13
                    )
                }
                BarcodeScanner.ScanType.BARCODE -> {
                    this.setBarcodeFormats(
                        Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_EAN_13
                    )
                }
                else -> {
                    this.setBarcodeFormats(
                        Barcode.FORMAT_QR_CODE,
                    )
                }
            }
        }

        val scanner = BarcodeScanning.getClient(options.build())

        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image =
                    InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener {
                        onSuccessListener.onSuccess(it)
                        imageProxy.close()
                    }
                    .addOnFailureListener {
                        Log.d("BARCODE", "Found barcode exception: " + it.message)
                        imageProxy.close()
                    }
            }
        }
    }
}
