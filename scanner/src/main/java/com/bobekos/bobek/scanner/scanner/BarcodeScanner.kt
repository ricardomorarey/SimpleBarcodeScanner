package com.bobekos.bobek.scanner.scanner

import android.annotation.SuppressLint
import android.content.Context
import android.view.SurfaceHolder
import com.bobekos.bobek.scanner.BarcodeView
import com.bobekos.bobek.scanner.overlay.Optional
import com.google.android.gms.vision.Detector
import com.google.android.gms.vision.MultiProcessor
import com.google.android.gms.vision.Tracker
import com.google.android.gms.vision.barcode.Barcode
import com.google.android.gms.vision.barcode.BarcodeDetector
import io.reactivex.Observable
import io.reactivex.ObservableEmitter


internal class BarcodeScanner(
        private val context: Context?,
        private val holder: SurfaceHolder,
        private val config: BarcodeScannerConfig,
        private val holderAvailable: Boolean) {

    private val barcodeDetector by lazy {
        BarcodeDetector.Builder(context)
                .setBarcodeFormats(config.barcodeFormat)
                .build()
    }

    private val camera by lazy {
        Camera(context, barcodeDetector, config)
    }

    @SuppressLint("MissingPermission")
    fun getObservable(): Observable<Barcode> {
        return Observable.create<Barcode> { emitter ->
            if (!holderAvailable) {
                emitter.onComplete()
            } else {
                if (context == null && !emitter.isDisposed) {
                    emitter.onError(NullPointerException("Context is null"))
                } else {
                    camera.getCameraSource().start(holder)
                    camera.setParametersFromConfig()

                    val tracker = BarcodeTracker(emitter)
                    val processor = MultiProcessor.Builder(BarcodeTrackerFactory(tracker)).build()
                    barcodeDetector.setProcessor(processor)

                    emitter.setCancellable {
                        camera.getCameraSource().release()
                    }
                }
            }
        }
    }

    inner class BarcodeTracker(private val subscriber: ObservableEmitter<Barcode>) : Tracker<Barcode>() {

        override fun onNewItem(id: Int, barcode: Barcode?) {
            if (barcode != null) {
                if (config.drawOverLay) {
                    BarcodeView.overlaySubject.onNext(Optional.Some(barcode))
                }

                if (!subscriber.isDisposed) {
                    subscriber.onNext(barcode)
                }
            }
        }

        override fun onUpdate(detection: Detector.Detections<Barcode>?, barcode: Barcode?) {
            if (barcode != null && config.drawOverLay) {
                BarcodeView.overlaySubject.onNext(Optional.Some(barcode))
            }
        }

        override fun onMissing(p0: Detector.Detections<Barcode>?) {

        }

        override fun onDone() {
            if (config.drawOverLay) {
                BarcodeView.overlaySubject.onNext(Optional.None)
            }
        }
    }
}