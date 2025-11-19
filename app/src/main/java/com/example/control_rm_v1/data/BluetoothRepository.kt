package com.example.control_rm_v1.data

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Repository responsible for handling all Bluetooth operations against the Pololu robot.
 * Encapsulates adapter access, device discovery and socket communication.
 */
class BluetoothRepository(private val bluetoothManager: BluetoothManager) {

    companion object {
        private const val TAG = "BluetoothRepository"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var bluetoothSocket: BluetoothSocket? = null
    private var connectedDevice: BluetoothDevice? = null

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager.adapter

    private val telemetryScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var telemetryJob: Job? = null

    private val _telemetryFlow: MutableStateFlow<TelemetryData> = MutableStateFlow(TelemetryData())

    /**
     * Public stream that emits telemetry updates in real time.
     */
    val telemetryFlow: StateFlow<TelemetryData> = _telemetryFlow.asStateFlow()

    /**
     * Initialize the Bluetooth adapter.
     *
     * @return true when the adapter exists and is already enabled.
     */
    fun initializeBluetooth(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Request the adapter to enable Bluetooth programmatically.
     *
     * @return true if the enable request was accepted, false otherwise.
     */
    fun enableBluetooth(): Boolean {
        return bluetoothAdapter?.enable() == true
    }

    /**
     * Request the adapter to disable Bluetooth programmatically.
     *
     * @return true if the disable request was accepted, false otherwise.
     */
    fun disableBluetooth(): Boolean {
        return bluetoothAdapter?.disable() == true
    }

    /**
     * Check whether Bluetooth is currently enabled on the device.
     *
     * @return true when the adapter reports an enabled state, false otherwise.
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Obtain the list of paired Bluetooth devices.
     *
     * @return a list with the bonded devices or an empty list when unavailable.
     */
    fun getPairedDevices(): List<BluetoothDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        return adapter.bondedDevices.toList()
    }

    /**
     * Attempt to connect to a Bluetooth device by its MAC address.
     *
     * @param deviceAddress MAC address of the target device.
     * @return true when the connection succeeds, false for any failure.
     */
    suspend fun connectToDevice(deviceAddress: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val adapter = bluetoothAdapter ?: return@withContext false
            val device = adapter.getRemoteDevice(deviceAddress)
            connectedDevice = device

            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothSocket = socket

            adapter.cancelDiscovery()
            socket.connect()
            startTelemetryListener(socket)

            Log.d(TAG, "Connected to device: ${device.name}")
            true
        } catch (ioException: IOException) {
            Log.e(TAG, "Connection failed: ${ioException.message}")
            closeConnection()
            false
        } catch (throwable: Throwable) {
            Log.e(TAG, "Unexpected error: ${throwable.message}")
            closeConnection()
            false
        }
    }

    /**
     * Send a command to the connected robot.
     *
     * @param command Command to send (F, B, L, R, S).
     * @return true when the command is written successfully, false otherwise.
     */
    suspend fun sendCommand(command: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val socket = bluetoothSocket ?: return@withContext false
            socket.outputStream.write(command.toByteArray())
            Log.d(TAG, "Command sent: $command")
            true
        } catch (ioException: IOException) {
            Log.e(TAG, "Failed to send command: ${ioException.message}")
            false
        }
    }

    /**
     * Check if there is an active connection.
     *
     * @return true when the socket is connected, false otherwise.
     */
    fun isConnected(): Boolean {
        return bluetoothSocket?.isConnected == true
    }

    /**
     * Provide the name of the currently connected device.
     *
     * @return the device name or null if there is no active connection.
     */
    fun getConnectedDeviceName(): String? = connectedDevice?.name

    /**
     * Close any active Bluetooth connection.
     */
    fun closeConnection() {
        try {
            bluetoothSocket?.close()
        } catch (ioException: IOException) {
            Log.e(TAG, "Error closing socket: ${ioException.message}")
        } finally {
            telemetryJob?.cancel()
            telemetryJob = null
            _telemetryFlow.value = TelemetryData()
            bluetoothSocket = null
            connectedDevice = null
        }
    }

    /**
     * Launch a background listener that continuously reads telemetry data from the active socket.
     *
     * @param socket Socket connected to the Pololu robot.
     */
    private fun startTelemetryListener(socket: BluetoothSocket) {
        telemetryJob?.cancel()
        telemetryJob = telemetryScope.launch {
            try {
                val bufferedReader = socket.inputStream.bufferedReader()
                while (isActive) {
                    val line = bufferedReader.readLine() ?: break
                    processTelemetryLine(line)
                }
            } catch (ioException: IOException) {
                Log.e(TAG, "Telemetry stream error: ${ioException.message}")
            } catch (throwable: Throwable) {
                Log.e(TAG, "Unexpected telemetry error: ${throwable.message}")
            } finally {
                _telemetryFlow.value = TelemetryData()
            }
        }
    }

    /**
     * Parse a raw telemetry line and update the shared state with the latest values.
     *
     * @param line Raw line received from the robot, containing key-value pairs.
     */
    private fun processTelemetryLine(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            return
        }

        val tokens = trimmed.split(';', ',', '|')
        var distance: Float? = null
        var light: Float? = null
        var temperature: Float? = null

        for (token in tokens) {
            val parts = token.split(':', '=')
            if (parts.size != 2) continue

            val key = parts[0].trim().lowercase()
            val value = parts[1].trim()
            val numericValue = value.toFloatOrNull()

            when (key) {
                "distance", "dist", "d" -> {
                    distance = numericValue?.let { max(0f, it) }
                }
                "light", "ldr", "l" -> {
                    light = numericValue?.let { valueNumber ->
                        min(100f, max(0f, valueNumber))
                    }
                }
                "temperature", "temp", "t" -> {
                    temperature = numericValue
                }
            }
        }

        if (distance == null && light == null && temperature == null) {
            return
        }

        val currentTelemetry = _telemetryFlow.value
        val updatedTelemetry = currentTelemetry.copy(
            distanceCm = distance ?: currentTelemetry.distanceCm,
            lightPercentage = light ?: currentTelemetry.lightPercentage,
            temperatureCelsius = temperature ?: currentTelemetry.temperatureCelsius
        )

        _telemetryFlow.value = updatedTelemetry
    }
}

