package com.wooliesx.wooliesscanner

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v4.os.ResultReceiver
import com.wooliesx.wooliesscanner.BarcodeScannerActivity.Companion.EXTRA_ERROR_REASON
import com.wooliesx.wooliesscanner.BarcodeScannerActivity.Companion.RESULT_FAILED
import java.io.Serializable

class BarcodeScanner {
    fun scan(scanType: ScanType, activity: Activity, listener: BarcodeScanListener, overlayResourceId: Int? = null) {
        val intent = Intent(activity, BarcodeScannerActivity::class.java).apply {
            putExtra(EXTRA_SCAN_TYPE, scanType)
            putExtra(EXTRA_LAYOUT_RESOURCE_ID, overlayResourceId)
            putExtra(EXTRA_SCAN_LISTENER, object : ResultReceiver(Handler(Looper.getMainLooper())) {
                override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                    if (resultCode == RESULT_OK) {
                        resultData?.getString(BarcodeScannerActivity.EXTRA_RESULT_URI)
                            ?.let { listener.onSuccess(it) }
                    } else if (resultCode == RESULT_FAILED) {
                        listener.onError(resultData?.get(EXTRA_ERROR_REASON) as ScanErrorReason)
                    }
                }
            })
        }

        activity.startActivity(intent)
    }

    enum class ScanType : Serializable {
        QR, BARCODE, ALL
    }

    interface BarcodeScanListener {
        fun onSuccess(uri: String)
        fun onError(reason: ScanErrorReason, message: String? = null)
    }

    enum class ScanErrorReason : Serializable {
        PERMISSION_MISSING, INVALID_URI, UNKNOWN
    }

    companion object {
        const val EXTRA_SCAN_TYPE = "scan_type"
        const val EXTRA_SCAN_LISTENER = "scan_listener"
        const val EXTRA_LAYOUT_RESOURCE_ID = "extra_overlay_resource_id"


    }
}