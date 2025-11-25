package com.cybergarden.metapetz

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Manages Replicate API calls for background removal and 3D generation.
 * Uses bria/remove-background and firtoz/trellis models.
 */
class ReplicateManager(
    private val apiToken: String,
    private val context: Context? = null
) {

    private val TAG = "ReplicateManager"
    private val BASE_URL = "https://api.replicate.com/v1"
    private val CACHE_DIR = "custom_pets"

    // Trellis model for image-to-3D conversion
    private val TRELLIS_MODEL_VERSION = "firtoz/trellis:e8f6c45206993f297372f5436b90350817bd9b4a0d52d2a76df50c1c8afa2b3c"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // Cache directory for downloaded GLB models
    private val cacheDir: File? by lazy {
        context?.let {
            File(it.cacheDir, CACHE_DIR).also { dir ->
                if (!dir.exists()) dir.mkdirs()
            }
        }
    }

    /**
     * Remove background from an image using Replicate's bria/remove-background model.
     * @param imageUrl URL of the image to process
     * @return URL of the processed image with background removed, or null on failure
     */
    suspend fun removeBackground(imageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val isDataUrl = imageUrl.startsWith("data:")
            Log.d(TAG, "Starting background removal, isDataUrl: $isDataUrl, length: ${imageUrl.length}")

            // Create prediction request
            val requestJson = JSONObject().apply {
                put("input", JSONObject().apply {
                    put("image", imageUrl)
                })
            }

            Log.d(TAG, "Sending request to Replicate API...")
            val request = Request.Builder()
                .url("$BASE_URL/models/bria/remove-background/predictions")
                .addHeader("Authorization", "Bearer $apiToken")
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "wait") // Wait for result
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            Log.d(TAG, "Response code: ${response.code}")
            Log.d(TAG, "Response body: ${responseBody?.take(500)}")

            if (!response.isSuccessful) {
                Log.e(TAG, "API error: ${response.code} - $responseBody")
                return@withContext null
            }

            val json = JSONObject(responseBody ?: "{}")
            val status = json.optString("status")
            Log.d(TAG, "Prediction status: $status")

            // If using "Prefer: wait", we should get the result directly
            if (status == "succeeded") {
                val output = json.optString("output")
                Log.d(TAG, "Background removal succeeded: $output")
                return@withContext output
            }

            // If prediction is still processing, poll for result
            if (status == "starting" || status == "processing") {
                val predictionId = json.optString("id")
                Log.d(TAG, "Prediction in progress, polling for id: $predictionId")
                return@withContext pollForResult(predictionId)
            }

            Log.e(TAG, "Unexpected status: $status, full response: $responseBody")
            return@withContext null

        } catch (e: Exception) {
            Log.e(TAG, "Error removing background: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Generate a 3D model from an image using Trellis model.
     * @param imageUrl URL of the image (with background removed works best)
     * @param onProgress Optional callback for progress updates (0-100)
     * @return URL of the generated GLB 3D model, or null on failure
     */
    suspend fun generateModel3D(
        imageUrl: String,
        onProgress: ((Int) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting 3D model generation for: ${imageUrl.take(100)}...")

            // Create prediction request using version format
            // Trellis expects "images" array, not single "image"
            val imagesArray = org.json.JSONArray().apply {
                put(imageUrl)
            }

            val requestJson = JSONObject().apply {
                put("version", TRELLIS_MODEL_VERSION.split(":").last())
                put("input", JSONObject().apply {
                    put("images", imagesArray)
                    // Trellis parameters
                    put("texture_size", 1024)
                    put("mesh_simplify", 0.9)
                    put("generate_model", true)
                    put("save_gaussian_ply", false) // We don't need the PLY file
                    put("ss_sampling_steps", 38)
                })
            }

            Log.d(TAG, "Sending 3D generation request to Replicate API...")
            val request = Request.Builder()
                .url("$BASE_URL/predictions")
                .addHeader("Authorization", "Bearer $apiToken")
                .addHeader("Content-Type", "application/json")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            Log.d(TAG, "3D Gen Response code: ${response.code}")
            Log.d(TAG, "3D Gen Response body: ${responseBody?.take(500)}")

            if (!response.isSuccessful) {
                Log.e(TAG, "3D API error: ${response.code} - $responseBody")
                return@withContext null
            }

            val json = JSONObject(responseBody ?: "{}")
            val status = json.optString("status")
            val predictionId = json.optString("id")

            Log.d(TAG, "3D Generation status: $status, id: $predictionId")

            // 3D generation takes time, need to poll for result
            onProgress?.invoke(5) // Initial progress
            if (status == "starting" || status == "processing") {
                return@withContext pollFor3DResult(predictionId, onProgress)
            }

            if (status == "succeeded") {
                return@withContext extract3DModelUrl(json)
            }

            Log.e(TAG, "Unexpected 3D status: $status")
            return@withContext null

        } catch (e: Exception) {
            Log.e(TAG, "Error generating 3D model: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Poll for 3D model generation result (takes longer than background removal)
     */
    private suspend fun pollFor3DResult(
        predictionId: String,
        onProgress: ((Int) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        val maxAttempts = 60 // Max 60 attempts (2 minutes for 3D generation)
        repeat(maxAttempts) { attempt ->
            delay(2000) // Wait 2 seconds between polls

            // Calculate progress: 5% initial + up to 90% during polling (final 5% for download)
            val progressPercent = 5 + ((attempt.toFloat() / maxAttempts) * 90).toInt()
            onProgress?.invoke(progressPercent)

            try {
                val request = Request.Builder()
                    .url("$BASE_URL/predictions/$predictionId")
                    .addHeader("Authorization", "Bearer $apiToken")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                val json = JSONObject(responseBody ?: "{}")
                val status = json.optString("status")

                Log.d(TAG, "3D Poll attempt ${attempt + 1}: status=$status, progress=$progressPercent%")

                when (status) {
                    "succeeded" -> {
                        onProgress?.invoke(100)
                        return@withContext extract3DModelUrl(json)
                    }
                    "failed", "canceled" -> {
                        Log.e(TAG, "3D Prediction $status: ${json.optString("error")}")
                        return@withContext null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "3D Polling error", e)
            }
        }

        Log.e(TAG, "3D generation polling timeout")
        return@withContext null
    }

    /**
     * Extract the GLB model URL from Trellis output
     * Trellis can return output in different formats:
     * - Array of objects: [{"gaussian_ply": "...", "model_file": "...glb", "render_video": "..."}]
     * - Object: {"glb": "url", "video": "url"}
     * - Direct string URL
     */
    private fun extract3DModelUrl(json: JSONObject): String? {
        val output = json.opt("output")
        Log.d(TAG, "Extracting GLB from output type: ${output?.javaClass?.simpleName}")

        // Try as JSONArray (Trellis returns array when using "images" array input)
        if (output is org.json.JSONArray && output.length() > 0) {
            val firstResult = output.optJSONObject(0)
            if (firstResult != null) {
                // Look for "model_file" which contains the GLB
                val modelFile = firstResult.optString("model_file")
                if (modelFile.isNotEmpty() && modelFile.endsWith(".glb")) {
                    Log.d(TAG, "3D model GLB URL from array: $modelFile")
                    return modelFile
                }
                // Fallback to "glb" key
                val glbUrl = firstResult.optString("glb")
                if (glbUrl.isNotEmpty()) {
                    Log.d(TAG, "3D model GLB URL: $glbUrl")
                    return glbUrl
                }
            }
        }

        // Try as JSONObject
        if (output is JSONObject) {
            val glbUrl = output.optString("glb")
            if (glbUrl.isNotEmpty()) {
                Log.d(TAG, "3D model GLB URL: $glbUrl")
                return glbUrl
            }
            val modelFile = output.optString("model_file")
            if (modelFile.isNotEmpty() && modelFile.endsWith(".glb")) {
                Log.d(TAG, "3D model GLB URL from model_file: $modelFile")
                return modelFile
            }
        }

        // Try direct output string (different output format)
        val directOutput = json.optString("output")
        if (directOutput.isNotEmpty() && directOutput.endsWith(".glb")) {
            Log.d(TAG, "3D model direct URL: $directOutput")
            return directOutput
        }

        Log.e(TAG, "Could not extract GLB URL from response: ${json.toString().take(1000)}")
        return null
    }

    /**
     * Poll for prediction result if not using "Prefer: wait"
     */
    private suspend fun pollForResult(predictionId: String): String? = withContext(Dispatchers.IO) {
        repeat(30) { // Max 30 attempts (60 seconds)
            delay(2000) // Wait 2 seconds between polls

            try {
                val request = Request.Builder()
                    .url("$BASE_URL/predictions/$predictionId")
                    .addHeader("Authorization", "Bearer $apiToken")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()
                val json = JSONObject(responseBody ?: "{}")
                val status = json.optString("status")

                when (status) {
                    "succeeded" -> {
                        val output = json.optString("output")
                        Log.d(TAG, "Polling succeeded: $output")
                        return@withContext output
                    }
                    "failed", "canceled" -> {
                        Log.e(TAG, "Prediction $status: ${json.optString("error")}")
                        return@withContext null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Polling error", e)
            }
        }

        Log.e(TAG, "Polling timeout")
        return@withContext null
    }

    /**
     * Convert a Bitmap to a data URL for Replicate API
     * Resizes and compresses to keep size manageable
     */
    fun bitmapToDataUrl(bitmap: Bitmap): String {
        // Resize if too large (max 512px on longest side for faster processing)
        val maxSize = 512
        val scaledBitmap = if (bitmap.width > maxSize || bitmap.height > maxSize) {
            val scale = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            Log.d(TAG, "Resizing bitmap from ${bitmap.width}x${bitmap.height} to ${newWidth}x${newHeight}")
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        // Use JPEG for smaller file size
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        val base64 = Base64.encodeToString(byteArray, Base64.NO_WRAP)
        Log.d(TAG, "Data URL size: ${base64.length} chars (${byteArray.size / 1024} KB)")
        return "data:image/jpeg;base64,$base64"
    }

    /**
     * Download an image from URL and return as Bitmap
     */
    suspend fun downloadImage(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val url = URL(imageUrl)
            val connection = url.openConnection()
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            val inputStream = connection.getInputStream()
            return@withContext BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading image", e)
            return@withContext null
        }
    }

    /**
     * Download a GLB model from URL and cache it locally.
     * Returns the local file URI if successful, or the original URL if caching fails.
     * @param glbUrl URL of the GLB model to download
     * @return Local file URI (file://...) if cached, or original URL
     */
    suspend fun downloadAndCacheGlb(glbUrl: String): String = withContext(Dispatchers.IO) {
        if (cacheDir == null) {
            Log.w(TAG, "No cache directory available, using remote URL")
            return@withContext glbUrl
        }

        try {
            // Generate a unique filename based on URL hash
            val hash = glbUrl.md5Hash()
            val cachedFile = File(cacheDir, "pet_$hash.glb")

            // Check if already cached
            if (cachedFile.exists()) {
                Log.d(TAG, "GLB model found in cache: ${cachedFile.absolutePath}")
                return@withContext "file://${cachedFile.absolutePath}"
            }

            // Download the file
            Log.d(TAG, "Downloading GLB model to cache...")
            val request = Request.Builder()
                .url(glbUrl)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to download GLB: ${response.code}")
                return@withContext glbUrl
            }

            // Save to cache
            response.body?.byteStream()?.use { inputStream ->
                FileOutputStream(cachedFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            Log.d(TAG, "GLB model cached: ${cachedFile.absolutePath} (${cachedFile.length() / 1024} KB)")
            return@withContext "file://${cachedFile.absolutePath}"

        } catch (e: Exception) {
            Log.e(TAG, "Error caching GLB model: ${e.message}", e)
            return@withContext glbUrl
        }
    }

    /**
     * Get list of cached custom pet model files
     */
    fun getCachedModels(): List<File> {
        return cacheDir?.listFiles { file -> file.extension == "glb" }?.toList() ?: emptyList()
    }

    /**
     * Clear all cached GLB models
     */
    fun clearCache() {
        cacheDir?.listFiles()?.forEach { file ->
            file.delete()
        }
        Log.d(TAG, "GLB cache cleared")
    }

    /**
     * Extension function to generate MD5 hash of a string
     */
    private fun String.md5Hash(): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(this.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        // API token loaded from BuildConfig (set in local.properties)
        val DEFAULT_API_TOKEN: String = BuildConfig.REPLICATE_API_TOKEN
    }
}
