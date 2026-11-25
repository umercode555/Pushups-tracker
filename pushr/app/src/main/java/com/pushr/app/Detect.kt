package com.pushr.app

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/** Counts one rep each time face size grows (down) then shrinks (up). */
class RepCounter {
    private var s = 0f
    private var lo = 0f
    private var hi = 0f
    private var down = false
    private var ready = false
    private var lastRep = 0L

    fun feed(h: Float, t: Long): Boolean {
        s = if (!ready) h else s * 0.55f + h * 0.45f
        if (!ready) { lo = s; hi = s; ready = true }
        if (!down) {
            lo = if (s < lo) s else lo + (s - lo) * 0.02f
            if (s > lo * 1.14f) { down = true; hi = s }
        } else {
            hi = if (s > hi) s else hi - (hi - s) * 0.02f
            if (s < hi / 1.10f) {
                down = false
                lo = s
                if (t - lastRep > 450) { lastRep = t; return true }
            }
        }
        return false
    }
}

class FaceAnalyzer(private val cb: (Float?) -> Unit) : ImageAnalysis.Analyzer {
    private val det = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.15f)
            .build()
    )

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(p: ImageProxy) {
        val m = p.image
        if (m == null) { p.close(); return }
        val rot = p.imageInfo.rotationDegrees
        val frameH = if (rot == 90 || rot == 270) m.width else m.height
        det.process(InputImage.fromMediaImage(m, rot))
            .addOnSuccessListener { faces ->
                val f = faces.maxByOrNull { it.boundingBox.height() }
                cb(f?.let { it.boundingBox.height().toFloat() / frameH })
            }
            .addOnCompleteListener { p.close() }
    }
}
