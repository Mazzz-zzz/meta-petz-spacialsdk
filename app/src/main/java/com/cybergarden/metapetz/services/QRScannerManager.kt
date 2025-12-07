package com.cybergarden.metapetz.services

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat as AndroidImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.DecodeHintType
import com.google.zxing.BarcodeFormat
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * QR Code scanner using Camera2 API + ZXing (no Google Play Services needed).
 * Based on PhotoCaptureManager pattern for Meta Quest compatibility.
 */
class QRScannerManager(private val context: Context) {

    companion object {
        private const val TAG = "QRScanner"
        private const val CAMERA_IMAGE_FORMAT = android.graphics.ImageFormat.YUV_420_888
        private const val CAMERA_POSITION_KEY = "com.meta.extra_metadata.position"
        const val PERMISSION = "horizonos.permission.HEADSET_CAMERA"
    }

    private lateinit var cameraManager: CameraManager
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageReaderThread: HandlerThread
    private lateinit var imageReaderHandler: Handler

    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var cameraId: String? = null
    private var outputSize: Size = Size(1280, 720)

    private var isInitialized = false
    private var isScanning = false
    private var onQRCodeCallback: ((String?) -> Unit)? = null

    // ZXing reader - pure Java, no initialization needed
    private val qrReader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true
        )
        setHints(hints)
    }

    // Debug: frame counter
    private var frameCount = 0
    private var lastFrameLogTime = 0L

    /**
     * Initialize the camera system. Call after permission is granted.
     */
    fun initialize(): Boolean {
        try {
            Log.d(TAG, "Initializing QR Scanner with ZXing...")

            cameraExecutor = Executors.newSingleThreadExecutor()
            imageReaderThread = HandlerThread("QRScannerThread").apply {
                start()
                imageReaderHandler = Handler(this.looper)
            }

            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            if (cameraManager.cameraIdList.isEmpty()) {
                Log.e(TAG, "No cameras found")
                return false
            }

            Log.d(TAG, "Found cameras: ${cameraManager.cameraIdList.joinToString()}")

            // Find the left eye camera (position 0) or fallback to first available
            var fallbackCameraId: String? = null
            var fallbackSize: Size? = null

            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val position = characteristics.get(
                    CameraCharacteristics.Key(CAMERA_POSITION_KEY, Int::class.java)
                )

                Log.d(TAG, "Camera $id has position: $position")

                // Get output sizes for this camera
                val sizes = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(CAMERA_IMAGE_FORMAT)

                val selectedSize = if (sizes != null && sizes.isNotEmpty()) {
                    // Prefer 1280x720 for good QR detection, or largest available
                    sizes.find { it.width == 1280 && it.height == 720 }
                        ?: sizes.find { it.width == 640 && it.height == 480 }
                        ?: sizes.maxByOrNull { it.width * it.height }
                        ?: Size(640, 480)
                } else {
                    Size(640, 480)
                }

                // Save first camera as fallback
                if (fallbackCameraId == null) {
                    fallbackCameraId = id
                    fallbackSize = selectedSize
                }

                if (position == 0) { // Left camera - preferred
                    cameraId = id
                    outputSize = selectedSize
                    Log.d(TAG, "Using left camera $id with size $outputSize")
                    break
                }
            }

            // Use fallback if no position 0 camera found
            if (cameraId == null && fallbackCameraId != null) {
                cameraId = fallbackCameraId
                outputSize = fallbackSize ?: Size(640, 480)
                Log.d(TAG, "Using fallback camera $cameraId with size $outputSize")
            }

            if (cameraId == null) {
                Log.e(TAG, "Could not find any suitable camera")
                return false
            }

            isInitialized = true
            Log.d(TAG, "QR Scanner initialized successfully!")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize camera", e)
            return false
        }
    }

    /**
     * Start scanning for QR codes.
     * Returns the QR code value via callback when found.
     */
    fun startScanning(callback: (String?) -> Unit) {
        if (!isInitialized) {
            Log.e(TAG, "Camera not initialized")
            callback(null)
            return
        }

        if (isScanning) {
            Log.w(TAG, "Already scanning")
            return
        }

        Log.d(TAG, "Starting QR scan...")
        onQRCodeCallback = callback
        isScanning = true
        frameCount = 0

        CoroutineScope(Dispatchers.Main).launch {
            try {
                startCameraAndScan()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start scanning", e)
                isScanning = false
                callback(null)
            }
        }
    }

    /**
     * Stop scanning.
     */
    fun stopScanning() {
        Log.d(TAG, "Stopping QR scanner")
        isScanning = false
        stopCamera()
    }

    @SuppressLint("MissingPermission")
    private suspend fun startCameraAndScan() {
        try {
            // Open camera
            Log.d(TAG, "Opening camera $cameraId...")
            camera = openCamera(cameraManager, cameraId!!, cameraExecutor)
            Log.d(TAG, "Camera opened successfully")

            // Create image reader
            imageReader = ImageReader.newInstance(
                outputSize.width,
                outputSize.height,
                CAMERA_IMAGE_FORMAT,
                2
            )
            Log.d(TAG, "ImageReader created: ${outputSize.width}x${outputSize.height}")

            // Create session
            session = createCaptureSession(camera!!, listOf(imageReader!!.surface), cameraExecutor)
            Log.d(TAG, "Capture session created")

            // Set up image listener for continuous scanning
            imageReader?.setOnImageAvailableListener({ reader ->
                if (!isScanning) return@setOnImageAvailableListener

                val image = reader.acquireLatestImage()
                if (image != null) {
                    try {
                        frameCount++
                        val now = System.currentTimeMillis()
                        if (now - lastFrameLogTime > 2000) {
                            Log.d(TAG, "Processing frame #$frameCount (${outputSize.width}x${outputSize.height})")
                            lastFrameLogTime = now
                        }
                        processImageForQR(image)
                    } finally {
                        image.close()
                    }
                }
            }, imageReaderHandler)

            // Start capture
            val captureRequest = camera!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(imageReader!!.surface)
            }

            session!!.setSingleRepeatingRequest(
                captureRequest.build(),
                cameraExecutor,
                object : CameraCaptureSession.CaptureCallback() {}
            )

            Log.d(TAG, "QR scanning started - waiting for frames...")

        } catch (e: Exception) {
            Log.e(TAG, "Camera scan error", e)
            stopCamera()
            isScanning = false
            onQRCodeCallback?.invoke(null)
        }
    }

    private fun processImageForQR(image: Image) {
        try {
            // Convert YUV_420_888 to Bitmap
            val bitmap = yuvImageToBitmap(image)
            if (bitmap == null) {
                Log.e(TAG, "Failed to convert YUV image to bitmap")
                return
            }

            // Convert bitmap to ZXing format
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            try {
                val result = qrReader.decode(binaryBitmap)
                val value = result.text

                Log.d(TAG, "QR Code found: $value")

                // Extract pet ID from QR code (remove dashes, take last 6 chars)
                val cleanValue = value.replace("-", "")
                val petId = if (cleanValue.length >= 6) {
                    cleanValue.takeLast(6).uppercase()
                } else {
                    cleanValue.uppercase()
                }

                // Stop scanning and return result
                isScanning = false
                CoroutineScope(Dispatchers.Main).launch {
                    stopCamera()
                    onQRCodeCallback?.invoke(petId)
                    onQRCodeCallback = null
                }
            } catch (e: com.google.zxing.NotFoundException) {
                // No QR code found in this frame - this is normal, keep scanning
            } catch (e: Exception) {
                Log.w(TAG, "ZXing decode error: ${e.message}")
            }

            bitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image for QR", e)
        }
    }

    /**
     * Convert YUV_420_888 Image to Bitmap.
     */
    private fun yuvImageToBitmap(image: Image): Bitmap? {
        try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)

            // Y plane
            yBuffer.get(nv21, 0, ySize)
            // V and U planes (NV21 format: YYYYYYYY VUVU)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, AndroidImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 85, out)
            val jpegBytes = out.toByteArray()

            return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "YUV to Bitmap conversion failed", e)
            return null
        }
    }

    private suspend fun openCamera(
        manager: CameraManager,
        cameraId: String,
        executor: Executor
    ): CameraDevice = suspendCoroutine { cont ->
        manager.openCamera(
            cameraId,
            executor,
            object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.d(TAG, "Camera onOpened callback")
                    cont.resume(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.d(TAG, "Camera disconnected")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error")
                    cont.resumeWithException(RuntimeException("Camera error: $error"))
                }
            }
        )
    }

    private suspend fun createCaptureSession(
        device: CameraDevice,
        targets: List<android.view.Surface>,
        executor: Executor
    ): CameraCaptureSession = suspendCoroutine { cont ->
        device.createCaptureSession(
            SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                targets.map { OutputConfiguration(it) },
                executor,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        Log.d(TAG, "Capture session onConfigured")
                        cont.resume(session)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Capture session configuration failed")
                        cont.resumeWithException(RuntimeException("Session config failed"))
                    }
                }
            )
        )
    }

    private fun stopCamera() {
        session?.close()
        session = null
        camera?.close()
        camera = null
        imageReader?.close()
        imageReader = null
    }

    fun dispose() {
        stopScanning()
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
        if (::imageReaderThread.isInitialized) {
            imageReaderThread.quitSafely()
        }
    }
}
