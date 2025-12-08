package com.cybergarden.metapetz.utils

import android.content.Context
import android.graphics.Color
import android.util.Log
import com.cybergarden.metapetz.model.Accessory
import com.cybergarden.metapetz.model.PetColors
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Utility to modify material colors in GLB files at runtime.
 *
 * GLB format structure:
 * - 12-byte header: magic (4), version (4), length (4)
 * - Chunks: each chunk has length (4), type (4), data (length bytes)
 *   - Chunk type 0x4E4F534A = "JSON" (little-endian)
 *   - Chunk type 0x004E4942 = "BIN\0" (little-endian)
 */
object GlbColorizer {
    private const val TAG = "GlbColorizer"

    private const val GLB_MAGIC = 0x46546C67 // "glTF" in little-endian
    private const val GLB_VERSION = 2
    private const val CHUNK_TYPE_JSON = 0x4E4F534A // "JSON" in little-endian
    private const val CHUNK_TYPE_BIN = 0x004E4942  // "BIN\0" in little-endian

    /**
     * Load a GLB from assets, modify material colors, and save to cache.
     * Returns the path to the modified file.
     */
    fun colorizeGlb(
        context: Context,
        assetPath: String,
        colors: PetColors,
        outputFileName: String
    ): File? {
        try {
            // Read the original GLB from assets
            val inputStream = context.assets.open(assetPath)
            val glbBytes = inputStream.readBytes()
            inputStream.close()

            Log.d(TAG, "Read GLB file: $assetPath (${glbBytes.size} bytes)")

            // Parse and modify the GLB
            val modifiedBytes = modifyGlbColors(glbBytes, colors)
            if (modifiedBytes == null) {
                Log.e(TAG, "Failed to modify GLB colors")
                return null
            }

            // Write to cache directory
            val cacheDir = File(context.cacheDir, "colored_models")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val outputFile = File(cacheDir, outputFileName)
            outputFile.writeBytes(modifiedBytes)

            Log.d(TAG, "Wrote colorized GLB to: ${outputFile.absolutePath}")
            return outputFile

        } catch (e: Exception) {
            Log.e(TAG, "Error colorizing GLB: ${e.message}", e)
            return null
        }
    }

    private fun modifyGlbColors(glbBytes: ByteArray, colors: PetColors): ByteArray? {
        val buffer = ByteBuffer.wrap(glbBytes).order(ByteOrder.LITTLE_ENDIAN)

        // Read header
        val magic = buffer.int
        if (magic != GLB_MAGIC) {
            Log.e(TAG, "Invalid GLB magic: $magic")
            return null
        }

        val version = buffer.int
        if (version != GLB_VERSION) {
            Log.e(TAG, "Unsupported GLB version: $version")
            return null
        }

        val totalLength = buffer.int
        Log.d(TAG, "GLB header: version=$version, length=$totalLength")

        // Read JSON chunk
        val jsonChunkLength = buffer.int
        val jsonChunkType = buffer.int

        if (jsonChunkType != CHUNK_TYPE_JSON) {
            Log.e(TAG, "First chunk is not JSON: $jsonChunkType")
            return null
        }

        val jsonBytes = ByteArray(jsonChunkLength)
        buffer.get(jsonBytes)
        val jsonString = String(jsonBytes, Charsets.UTF_8).trimEnd('\u0000', ' ')

        Log.d(TAG, "JSON chunk: $jsonChunkLength bytes")

        // Parse and modify JSON
        val json = JSONObject(jsonString)
        val modifiedJson = modifyMaterialColors(json, colors)

        // Convert back to padded bytes (GLB requires 4-byte alignment)
        val modifiedJsonString = modifiedJson.toString()
        val modifiedJsonBytes = modifiedJsonString.toByteArray(Charsets.UTF_8)
        val paddedLength = (modifiedJsonBytes.size + 3) and 0x7FFFFFFC.toInt() // Round up to 4
        val paddedJsonBytes = ByteArray(paddedLength)
        System.arraycopy(modifiedJsonBytes, 0, paddedJsonBytes, 0, modifiedJsonBytes.size)
        // Fill padding with spaces (0x20) as per GLB spec for JSON chunk
        for (i in modifiedJsonBytes.size until paddedLength) {
            paddedJsonBytes[i] = 0x20
        }

        // Read remaining chunks (BIN chunk)
        val remainingBytes = ByteArray(buffer.remaining())
        buffer.get(remainingBytes)

        // Reconstruct GLB
        val newTotalLength = 12 + 8 + paddedJsonBytes.size + remainingBytes.size
        val output = ByteBuffer.allocate(newTotalLength).order(ByteOrder.LITTLE_ENDIAN)

        // Header
        output.putInt(GLB_MAGIC)
        output.putInt(GLB_VERSION)
        output.putInt(newTotalLength)

        // JSON chunk
        output.putInt(paddedJsonBytes.size)
        output.putInt(CHUNK_TYPE_JSON)
        output.put(paddedJsonBytes)

        // Remaining chunks (BIN)
        output.put(remainingBytes)

        Log.d(TAG, "Rebuilt GLB: $newTotalLength bytes (was $totalLength)")

        return output.array()
    }

    private fun modifyMaterialColors(json: JSONObject, colors: PetColors): JSONObject {
        if (!json.has("materials")) {
            Log.w(TAG, "No materials found in GLB")
            return json
        }

        val materials = json.getJSONArray("materials")
        Log.d(TAG, "Found ${materials.length()} materials")

        for (i in 0 until materials.length()) {
            val material = materials.getJSONObject(i)
            val name = material.optString("name", "").lowercase()

            Log.d(TAG, "Material $i: name='$name'")

            val colorHex = when {
                name.contains("coat") -> colors.coat
                name.contains("eye") -> colors.eye
                name.contains("snout") -> colors.snout
                else -> null
            }

            if (colorHex != null) {
                val rgba = hexToRgba(colorHex)
                Log.d(TAG, "Setting $name color to $colorHex -> RGBA${rgba.contentToString()}")

                // Ensure pbrMetallicRoughness exists
                val pbr = if (material.has("pbrMetallicRoughness")) {
                    material.getJSONObject("pbrMetallicRoughness")
                } else {
                    JSONObject().also { material.put("pbrMetallicRoughness", it) }
                }

                // Set baseColorFactor [R, G, B, A] as floats 0-1
                val colorArray = org.json.JSONArray()
                colorArray.put(rgba[0].toDouble())
                colorArray.put(rgba[1].toDouble())
                colorArray.put(rgba[2].toDouble())
                colorArray.put(rgba[3].toDouble())
                pbr.put("baseColorFactor", colorArray)

                // Remove baseColorTexture if present (solid color override)
                // Actually, keep the texture - baseColorFactor will multiply with it
                // pbr.remove("baseColorTexture")
            }
        }

        return json
    }

    /**
     * Convert hex color string to RGBA float array (0-1 range)
     */
    private fun hexToRgba(hex: String): FloatArray {
        return try {
            val colorInt = Color.parseColor(hex)
            floatArrayOf(
                Color.red(colorInt) / 255f,
                Color.green(colorInt) / 255f,
                Color.blue(colorInt) / 255f,
                Color.alpha(colorInt) / 255f
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse color: $hex", e)
            floatArrayOf(1f, 1f, 1f, 1f) // Default to white
        }
    }

    /**
     * Load a GLB from assets, modify accessory colors (ass1, ass2), and save to cache.
     * Returns the path to the modified file.
     */
    fun colorizeAccessoryGlb(
        context: Context,
        assetPath: String,
        accessory: Accessory,
        outputFileName: String
    ): File? {
        try {
            // Read the original GLB from assets
            val inputStream = context.assets.open(assetPath)
            val glbBytes = inputStream.readBytes()
            inputStream.close()

            Log.d(TAG, "Read accessory GLB file: $assetPath (${glbBytes.size} bytes)")

            // Parse and modify the GLB
            val modifiedBytes = modifyAccessoryGlbColors(glbBytes, accessory)
            if (modifiedBytes == null) {
                Log.e(TAG, "Failed to modify accessory GLB colors")
                return null
            }

            // Write to cache directory
            val cacheDir = File(context.cacheDir, "colored_accessories")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val outputFile = File(cacheDir, outputFileName)
            outputFile.writeBytes(modifiedBytes)

            Log.d(TAG, "Wrote colorized accessory GLB to: ${outputFile.absolutePath}")
            return outputFile

        } catch (e: Exception) {
            Log.e(TAG, "Error colorizing accessory GLB: ${e.message}", e)
            return null
        }
    }

    private fun modifyAccessoryGlbColors(glbBytes: ByteArray, accessory: Accessory): ByteArray? {
        val buffer = ByteBuffer.wrap(glbBytes).order(ByteOrder.LITTLE_ENDIAN)

        // Read header
        val magic = buffer.int
        if (magic != GLB_MAGIC) {
            Log.e(TAG, "Invalid GLB magic: $magic")
            return null
        }

        val version = buffer.int
        if (version != GLB_VERSION) {
            Log.e(TAG, "Unsupported GLB version: $version")
            return null
        }

        val totalLength = buffer.int
        Log.d(TAG, "Accessory GLB header: version=$version, length=$totalLength")

        // Read JSON chunk
        val jsonChunkLength = buffer.int
        val jsonChunkType = buffer.int

        if (jsonChunkType != CHUNK_TYPE_JSON) {
            Log.e(TAG, "First chunk is not JSON: $jsonChunkType")
            return null
        }

        val jsonBytes = ByteArray(jsonChunkLength)
        buffer.get(jsonBytes)
        val jsonString = String(jsonBytes, Charsets.UTF_8).trimEnd('\u0000', ' ')

        Log.d(TAG, "Accessory JSON chunk: $jsonChunkLength bytes")

        // Parse and modify JSON
        val json = JSONObject(jsonString)
        val modifiedJson = modifyAccessoryMaterialColors(json, accessory)

        // Convert back to padded bytes (GLB requires 4-byte alignment)
        val modifiedJsonString = modifiedJson.toString()
        val modifiedJsonBytes = modifiedJsonString.toByteArray(Charsets.UTF_8)
        val paddedLength = (modifiedJsonBytes.size + 3) and 0x7FFFFFFC.toInt() // Round up to 4
        val paddedJsonBytes = ByteArray(paddedLength)
        System.arraycopy(modifiedJsonBytes, 0, paddedJsonBytes, 0, modifiedJsonBytes.size)
        // Fill padding with spaces (0x20) as per GLB spec for JSON chunk
        for (i in modifiedJsonBytes.size until paddedLength) {
            paddedJsonBytes[i] = 0x20
        }

        // Read remaining chunks (BIN chunk)
        val remainingBytes = ByteArray(buffer.remaining())
        buffer.get(remainingBytes)

        // Reconstruct GLB
        val newTotalLength = 12 + 8 + paddedJsonBytes.size + remainingBytes.size
        val output = ByteBuffer.allocate(newTotalLength).order(ByteOrder.LITTLE_ENDIAN)

        // Header
        output.putInt(GLB_MAGIC)
        output.putInt(GLB_VERSION)
        output.putInt(newTotalLength)

        // JSON chunk
        output.putInt(paddedJsonBytes.size)
        output.putInt(CHUNK_TYPE_JSON)
        output.put(paddedJsonBytes)

        // Remaining chunks (BIN)
        output.put(remainingBytes)

        Log.d(TAG, "Rebuilt accessory GLB: $newTotalLength bytes (was $totalLength)")

        return output.array()
    }

    private fun modifyAccessoryMaterialColors(json: JSONObject, accessory: Accessory): JSONObject {
        if (!json.has("materials")) {
            Log.w(TAG, "No materials found in accessory GLB")
            return json
        }

        val materials = json.getJSONArray("materials")
        Log.d(TAG, "Found ${materials.length()} materials in accessory")

        for (i in 0 until materials.length()) {
            val material = materials.getJSONObject(i)
            val name = material.optString("name", "").lowercase()

            Log.d(TAG, "Accessory material $i: name='$name'")

            // Map ass1 and ass2 material names to colors
            val colorHex = when {
                name.contains("ass1") -> accessory.ass1
                name.contains("ass2") -> accessory.ass2
                else -> null
            }

            if (colorHex != null) {
                val rgba = hexToRgba(colorHex)
                Log.d(TAG, "Setting accessory $name color to $colorHex -> RGBA${rgba.contentToString()}")

                // Ensure pbrMetallicRoughness exists
                val pbr = if (material.has("pbrMetallicRoughness")) {
                    material.getJSONObject("pbrMetallicRoughness")
                } else {
                    JSONObject().also { material.put("pbrMetallicRoughness", it) }
                }

                // Set baseColorFactor [R, G, B, A] as floats 0-1
                val colorArray = org.json.JSONArray()
                colorArray.put(rgba[0].toDouble())
                colorArray.put(rgba[1].toDouble())
                colorArray.put(rgba[2].toDouble())
                colorArray.put(rgba[3].toDouble())
                pbr.put("baseColorFactor", colorArray)
            }
        }

        return json
    }
}
