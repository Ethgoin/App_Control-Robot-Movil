package com.example.control_rm_v1.data

/**
 * Immutable representation of the telemetry values reported by the Pololu robot.
 *
 * @param distanceCm Distance measured by the ultrasonic sensor, expressed in centimetres.
 * @param lightPercentage Light intensity reported by the LDR sensor as a percentage (0-100).
 * @param temperatureCelsius Ambient temperature detected by the robot in degrees Celsius.
 */
data class TelemetryData(
    val distanceCm: Float? = null,
    val lightPercentage: Float? = null,
    val temperatureCelsius: Float? = null
)
