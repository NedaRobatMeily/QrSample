package com.wooliesx.qrsample

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wooliesx.qrsample.databinding.ActivityMainBinding
import com.wooliesx.wooliesscanner.BarcodeScanner

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private val scanner = BarcodeScanner()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(viewBinding.root)

        viewBinding.buttonScanBarcode.setOnClickListener {
            scanner.scan(BarcodeScanner.ScanType.ALL, this, object :
                BarcodeScanner.BarcodeScanListener {
                override fun onSuccess(uri: String) {
                    Log.d("BARCODE", "Found barcode: " + uri)
                    viewBinding.textResults.text = uri
                }

                override fun onError(reason: BarcodeScanner.ScanErrorReason, message: String?) {
                    showToast("Error scanning barcode $reason")
                }
            }, overlayResourceId = com.wooliesx.qrsample.R.layout.layout_camera_overlay)
        }
    }

    private fun showToast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}