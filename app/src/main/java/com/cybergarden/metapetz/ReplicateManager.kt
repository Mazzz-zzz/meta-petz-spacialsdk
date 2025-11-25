package com.cybergarden.metapetz

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
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Manages Replicate API calls for background removal and 3D generation.
 * Uses bria/remove-background and firtoz/trellis models.
 */
class ReplicateManager(private val apiToken: String) {

    private val TAG = "ReplicateManager"
    private val BASE_URL = "https://api.replicate.com/v1"

    // Trellis model for image-to-3D conversion
    private val TRELLIS_MODEL_VERSION = "firtoz/trellis:e8f6c45206993f297372f5436b90350817bd9b4a0d52d2a76df50c1c8afa2b3c"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

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
     * @return URL of the generated GLB 3D model, or null on failure
     */
    suspend fun generateModel3D(imageUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting 3D model generation for: ${imageUrl.take(100)}...")

            // Create prediction request using version format
            val requestJson = JSONObject().apply {
                put("version", TRELLIS_MODEL_VERSION.split(":").last())
                put("input", JSONObject().apply {
                    put("image", imageUrl)
                    // Optional parameters for Trellis
                    put("ss_sampling_steps", 12)
                    put("slat_sampling_steps", 12)
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
            if (status == "starting" || status == "processing") {
                return@withContext pollFor3DResult(predictionId)
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
    private suspend fun pollFor3DResult(predictionId: String): String? = withContext(Dispatchers.IO) {
        repeat(60) { attempt -> // Max 60 attempts (2 minutes for 3D generation)
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

                Log.d(TAG, "3D Poll attempt ${attempt + 1}: status=$status")

                when (status) {
                    "succeeded" -> {
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
     */
    private fun extract3DModelUrl(json: JSONObject): String? {
        // Trellis outputs: { "glb": "url", "video": "url" }
        val output = json.optJSONObject("output")
        if (output != null) {
            val glbUrl = output.optString("glb")
            if (glbUrl.isNotEmpty()) {
                Log.d(TAG, "3D model GLB URL: $glbUrl")
                return glbUrl
            }
        }
        // Try direct output string (different output format)
        val directOutput = json.optString("output")
        if (directOutput.isNotEmpty() && directOutput.endsWith(".glb")) {
            Log.d(TAG, "3D model direct URL: $directOutput")
            return directOutput
        }
        Log.e(TAG, "Could not extract GLB URL from response: ${json.toString().take(500)}")
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

    companion object {
        // API token loaded from BuildConfig (set in local.properties)
        val DEFAULT_API_TOKEN: String = BuildConfig.REPLICATE_API_TOKEN
    }
}
