package com.cybergarden.metapetz.ecs

import android.util.Log
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Query
import com.meta.spatial.core.SystemBase
import com.meta.spatial.mruk.TrackedQrCode
import com.meta.spatial.mruk.getPayloadAsString

/**
 * System that monitors for QR codes detected by MRUK tracker.
 * When a QR code is found, it extracts the pet ID and notifies via callback.
 */
class QRCodeSystem(
    private val onQRCodeDetected: (String) -> Unit
) : SystemBase() {

    companion object {
        private const val TAG = "QRCodeSystem"
    }

    private val processedQRCodes = mutableSetOf<Entity>()
    var isEnabled = false

    override fun execute() {
        if (!isEnabled) return

        Query.where { changed(TrackedQrCode.id) }
            .eval()
            .forEach { qrCodeEntity ->
                if (!processedQRCodes.contains(qrCodeEntity)) {
                    processedQRCodes.add(qrCodeEntity)

                    val payload = qrCodeEntity.getComponent<TrackedQrCode>().getPayloadAsString()
                    Log.d(TAG, "QR Code detected: $payload")

                    // Extract pet ID (last 6 chars or full if it's just the ID)
                    val petId = if (payload.length >= 6) {
                        payload.takeLast(6).uppercase()
                    } else {
                        payload.uppercase()
                    }

                    Log.d(TAG, "Extracted pet ID: $petId")
                    onQRCodeDetected(petId)
                }
            }
    }

    fun reset() {
        processedQRCodes.clear()
    }
}
