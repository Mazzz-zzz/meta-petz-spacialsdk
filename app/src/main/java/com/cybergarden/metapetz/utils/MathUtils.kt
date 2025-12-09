package com.cybergarden.metapetz.utils

import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object MathUtils {
    /**
     * Constructs a quaternion from an axis-angle representation of a rotation.
     */
    fun Quaternion.Companion.fromAxisAngle(axis: Vector3, angleDegrees: Float): Quaternion {
        val angleRadians = angleDegrees * PI / 180f
        val halfAngle = angleRadians / 2
        val sinHalfAngle = sin(halfAngle).toFloat()

        return Quaternion(
            cos(halfAngle).toFloat(),
            axis.x * sinHalfAngle,
            axis.y * sinHalfAngle,
            axis.z * sinHalfAngle,
        ).normalize()
    }

    /**
     * Creates a Quaternion from sequential pitch, yaw, roll rotations in degrees.
     */
    fun Quaternion.Companion.fromSequentialPYR(
        pitchDeg: Float,
        yawDeg: Float,
        rollDeg: Float,
    ): Quaternion {
        return fromAxisAngle(Vector3.Right, pitchDeg)
            .times(fromAxisAngle(Vector3.Up, yawDeg))
            .times(fromAxisAngle(Vector3.Forward, rollDeg))
            .normalize()
    }
}
