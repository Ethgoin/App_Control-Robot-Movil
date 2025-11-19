package com.example.control_rm_v1.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.control_rm_v1.data.TelemetryData
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel that orchestrates manual control commands and telemetry updates for the Pololu robot.
 * It relies on the shared [BluetoothViewModel] to interact with the Bluetooth repository.
 *
 * @param bluetoothViewModel Shared ViewModel that maintains the Bluetooth connection lifecycle.
 */
class MainControlViewModel(private val bluetoothViewModel: BluetoothViewModel) : ViewModel() {

    /**
     * Observable state representing the overall Bluetooth connection status.
     */
    val connectionUiState: StateFlow<BluetoothConnectionUiState> = bluetoothViewModel.uiState

    /**
     * Real-time telemetry flow emitted by the Bluetooth repository.
     */
    val telemetryState: StateFlow<TelemetryData> = bluetoothViewModel.telemetryState

    private var lastDirection: MovementDirection = MovementDirection.STOP

    /**
     * Send a new movement command when the joystick direction changes.
     *
     * @param direction Direction selected by the user on the virtual joystick.
     */
    fun sendMovement(direction: MovementDirection) {
        if (direction == MovementDirection.STOP) {
            stopMovement()
            return
        }

        if (direction == lastDirection) {
            return
        }

        lastDirection = direction
        bluetoothViewModel.sendCommand(direction.toCommand())
    }

    /**
     * Issue a stop command when the joystick returns to the centre position.
     */
    fun stopMovement() {
        if (lastDirection == MovementDirection.STOP) {
            return
        }

        lastDirection = MovementDirection.STOP
        bluetoothViewModel.sendCommand(MovementDirection.STOP.toCommand())
    }

    /**
     * Reset the stored direction without transmitting a command, typically after a connection drop.
     */
    fun resetMovementState() {
        lastDirection = MovementDirection.STOP
    }

    /**
     * Request the shared ViewModel to retry the last successful Bluetooth connection.
     */
    fun retryConnection() {
        bluetoothViewModel.retryLastConnection()
    }

    /**
     * Close the current Bluetooth session and ensure the robot is stopped.
     */
    fun disconnect() {
        stopMovement()
        bluetoothViewModel.disconnect()
    }

    /**
     * Clear the currently displayed error message.
     */
    fun clearError() {
        bluetoothViewModel.clearError()
    }

    /**
     * Enumeration of the supported movement directions.
     */
    enum class MovementDirection {
        FORWARD,
        BACKWARD,
        LEFT,
        RIGHT,
        STOP
    }

    /**
     * Factory required to create [MainControlViewModel] with the shared [BluetoothViewModel].
     */
    class Factory(private val bluetoothViewModel: BluetoothViewModel) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainControlViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainControlViewModel(bluetoothViewModel) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    private fun MovementDirection.toCommand(): String {
        return when (this) {
            MovementDirection.FORWARD -> "F"
            MovementDirection.BACKWARD -> "B"
            MovementDirection.LEFT -> "L"
            MovementDirection.RIGHT -> "R"
            MovementDirection.STOP -> "S"
        }
    }
}
