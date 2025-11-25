package com.cybergarden.metapetz

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
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
import android.util.Base64
import android.util.Log
import android.util.Size
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
import kotlinx.coroutines.withContext

/**
 * Simplified camera manager for capturing single photos from Meta Quest passthrough camera.
 * Based on Meta Spatial SDK camera examples.
 */
class PhotoCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "PhotoCapture"
        private const val CAMERA_IMAGE_FORMAT = ImageFormat.YUV_420_888
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
    private var outputSize: Size = Size(640, 480)

    private var isInitialized = false
    private var onPhotoCallback: ((Bitmap?) -> Unit)? = null
    private var frameCount = 0
    private var hasDeliveredPhoto = false

    // Number of frames to skip before capturing (camera warmup)
    private val WARMUP_FRAMES = 15

    /**
     * Initialize the camera system. Call after permission is granted.
     */
    fun initialize(): Boolean {
        try {
            cameraExecutor = Executors.newSingleThreadExecutor()
            imageReaderThread = HandlerThread("PhotoCaptureThread").apply {
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
                    sizes.find { it.width == 640 && it.height == 480 }
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
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize camera", e)
            return false
        }
    }

    /**
     * Capture a single photo from the passthrough camera.
     * Returns the photo as a Bitmap via the callback.
     */
    fun capturePhoto(callback: (Bitmap?) -> Unit) {
        if (!isInitialized) {
            Log.e(TAG, "Camera not initialized")
            callback(null)
            return
        }

        onPhotoCallback = callback
        frameCount = 0
        hasDeliveredPhoto = false

        CoroutineScope(Dispatchers.Main).launch {
            try {
                startCameraAndCapture()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to capture photo", e)
                callback(null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun startCameraAndCapture() {
        try {
            // Open camera
            camera = openCamera(cameraManager, cameraId!!, cameraExecutor)

            // Create image reader with larger buffer for warmup frames
            imageReader = ImageReader.newInstance(
                outputSize.width,
                outputSize.height,
                CAMERA_IMAGE_FORMAT,
                WARMUP_FRAMES + 5
            )

            // Create session
            session = createCaptureSession(camera!!, listOf(imageReader!!.surface), cameraExecutor)

            // Set up image listener - skip warmup frames then capture
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    frameCount++

                    // Skip warmup frames (camera needs time to adjust exposure/focus)
                    if (frameCount <= WARMUP_FRAMES) {
                        Log.d(TAG, "Skipping warmup frame $frameCount/$WARMUP_FRAMES")
                        image.close()
                        return@setOnImageAvailableListener
                    }

                    // Only deliver one photo
                    if (hasDeliveredPhoto) {
                        image.close()
                        return@setOnImageAvailableListener
                    }
                    hasDeliveredPhoto = true

                    try {
                        Log.d(TAG, "Capturing frame $frameCount")
                        val bitmap = imageToBitmap(image)
                        image.close()
                        stopCamera()

                        // Crop to square
                        val squareBitmap = cropToSquare(bitmap)

                        CoroutineScope(Dispatchers.Main).launch {
                            onPhotoCallback?.invoke(squareBitmap)
                            onPhotoCallback = null
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing image", e)
                        image.close()
                        onPhotoCallback?.invoke(null)
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

        } catch (e: Exception) {
            Log.e(TAG, "Camera capture error", e)
            stopCamera()
            onPhotoCallback?.invoke(null)
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
                    cont.resume(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.d(TAG, "Camera disconnected")
                }

                override fun onError(camera: CameraDevice, error: Int) {
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
                        cont.resume(session)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        cont.resumeWithException(RuntimeException("Session config failed"))
                    }
                }
            )
        )
    }

    private fun imageToBitmap(image: Image): Bitmap {
        // Convert YUV to NV21
        val nv21 = yuv420ToNv21(image)

        // Convert NV21 to JPEG
        val yuv = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val stream = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, image.width, image.height), 90, stream)
        val jpegBytes = stream.toByteArray()

        // Decode JPEG to Bitmap
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    private fun yuv420ToNv21(image: Image): ByteArray {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val ySize = yPlane.buffer.remaining()
        val uvSize = image.width * image.height / 2

        val nv21 = ByteArray(ySize + uvSize)

        // Copy Y plane
        yPlane.buffer.get(nv21, 0, ySize)

        // Interleave V and U (NV21 format)
        val uvWidth = image.width / 2
        val uvHeight = image.height / 2
        var dst = ySize

        for (row in 0 until uvHeight) {
            for (col in 0 until uvWidth) {
                val vIndex = row * vPlane.rowStride + col * vPlane.pixelStride
                val uIndex = row * uPlane.rowStride + col * uPlane.pixelStride
                nv21[dst++] = vPlane.buffer.get(vIndex)
                nv21[dst++] = uPlane.buffer.get(uIndex)
            }
        }

        return nv21
    }

    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        return Bitmap.createBitmap(bitmap, x, y, size, size)
    }

    /**
     * Convert bitmap to base64 data URL for Replicate API
     */
    fun bitmapToDataUrl(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val bytes = stream.toByteArray()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return "data:image/png;base64,$base64"
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
        stopCamera()
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
        if (::imageReaderThread.isInitialized) {
            imageReaderThread.quitSafely()
        }
    }
}
