package com.example.control_rm_v1.ui.viewmodel

import android.app.Application
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.control_rm_v1.data.BluetoothRepository
import com.example.control_rm_v1.data.TelemetryData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel responsible for orchestrating Bluetooth actions and exposing UI state.
 */
class BluetoothViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothManager: BluetoothManager =
        application.getSystemService(BluetoothManager::class.java)
            ?: throw IllegalStateException("BluetoothManager not available on this device")

    private val repository: BluetoothRepository = BluetoothRepository(bluetoothManager)

    private val _uiState = MutableStateFlow(BluetoothConnectionUiState())
    val uiState: StateFlow<BluetoothConnectionUiState> = _uiState.asStateFlow()
    val telemetryState: StateFlow<TelemetryData> = repository.telemetryFlow

    private var lastConnectedDeviceAddress: String? = null

    init {
        refreshBluetoothState()
    }

    /**
     * Refresh the adapter status and paired devices list.
     */
    fun refreshBluetoothState() {
        val isEnabled = repository.initializeBluetooth()
        if (isEnabled) {
            loadPairedDevices()
        } else {
            repository.closeConnection()
        }

        _uiState.update { current ->
            val status = buildStatusMessage(
                connectionState = if (isEnabled) current.connectionState else ConnectionState.DISCONNECTED,
                isBluetoothEnabled = isEnabled,
                deviceName = current.connectedDeviceName
            )
            current.copy(
                isBluetoothEnabled = isEnabled,
                connectionState = if (isEnabled) current.connectionState else ConnectionState.DISCONNECTED,
                connectedDeviceName = if (isEnabled) current.connectedDeviceName else null,
                pairedDevices = if (isEnabled) current.pairedDevices else emptyList(),
                statusMessage = status,
                errorMessage = if (isEnabled) null else "Bluetooth desactivado"
            )
        }
    }

    /**
     * Load the bonded devices using the repository.
     */
    fun loadPairedDevices() {
        viewModelScope.launch(Dispatchers.IO) {
            val devices = repository.getPairedDevices().map { device ->
                PairedDeviceUiModel(
                    name = device.name ?: "Dispositivo desconocido",
                    address = device.address
                )
            }
            _uiState.update { current -> current.copy(pairedDevices = devices) }
        }
    }

    /**
     * Attempt to enable Bluetooth programmatically (best effort).
     */
    fun enableBluetoothDirectly() {
        val requested = repository.enableBluetooth()
        if (!requested) {
            _uiState.update { current ->
                current.copy(errorMessage = "No se pudo solicitar la activación de Bluetooth")
            }
        }
    }

    /**
     * Disable Bluetooth and update the UI state accordingly.
     */
    fun disableBluetooth() {
        repository.disableBluetooth()
        _uiState.update { current ->
            current.copy(
                isBluetoothEnabled = false,
                connectionState = ConnectionState.DISCONNECTED,
                connectedDeviceName = null,
                pairedDevices = emptyList(),
                statusMessage = "Bluetooth desactivado"
            )
        }
    }

    /**
     * Connect to a selected device.
     *
     * @param deviceAddress MAC address for the selected device.
     */
    fun connectToDevice(deviceAddress: String) {
        lastConnectedDeviceAddress = deviceAddress
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    connectionState = ConnectionState.CONNECTING,
                    statusMessage = "Conectando...",
                    errorMessage = null
                )
            }

            val success = repository.connectToDevice(deviceAddress)
            if (success) {
                val deviceName = repository.getConnectedDeviceName()
                _uiState.update { current ->
                    current.copy(
                        connectionState = ConnectionState.CONNECTED,
                        connectedDeviceName = deviceName,
                        statusMessage = buildStatusMessage(
                            connectionState = ConnectionState.CONNECTED,
                            isBluetoothEnabled = true,
                            deviceName = deviceName
                        )
                    )
                }
            } else {
                _uiState.update { current ->
                    current.copy(
                        connectionState = ConnectionState.DISCONNECTED,
                        statusMessage = buildStatusMessage(
                            connectionState = ConnectionState.DISCONNECTED,
                            isBluetoothEnabled = current.isBluetoothEnabled,
                            deviceName = null
                        ),
                        errorMessage = "Error al conectar con el dispositivo"
                    )
                }
            }
        }
    }

    /**
     * Disconnect the current socket if present.
     */
    fun disconnect() {
        repository.closeConnection()
        _uiState.update { current ->
            current.copy(
                connectionState = ConnectionState.DISCONNECTED,
                connectedDeviceName = null,
                statusMessage = buildStatusMessage(
                    connectionState = ConnectionState.DISCONNECTED,
                    isBluetoothEnabled = current.isBluetoothEnabled,
                    deviceName = null
                )
            )
        }
    }

    /**
     * Attempt to reconnect using the last successful device address.
     * If there is no previous device or Bluetooth is disabled, an error message is published.
     */
    fun retryLastConnection() {
        val address = lastConnectedDeviceAddress
        if (address.isNullOrBlank()) {
            _uiState.update { current ->
                current.copy(errorMessage = "No hay un dispositivo previo para reconectar")
            }
            return
        }

        if (!repository.isBluetoothEnabled()) {
            _uiState.update { current ->
                current.copy(errorMessage = "Activa Bluetooth antes de reconectar")
            }
            return
        }

        connectToDevice(address)
    }

    /**
     * Send a command to the connected robot.
     *
     * @param command Command identifier (F, B, L, R, S).
     */
    fun sendCommand(command: String) {
        if (_uiState.value.connectionState != ConnectionState.CONNECTED) {
            _uiState.update { current ->
                current.copy(errorMessage = "No hay conexión con el robot")
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val success = repository.sendCommand(command)
            if (!success) {
                _uiState.update { current ->
                    current.copy(errorMessage = "Error al enviar comando")
                }
            }
        }
    }

    /**
     * Clear the currently visible error message.
     */
    fun clearError() {
        _uiState.update { current -> current.copy(errorMessage = null) }
    }

    private fun buildStatusMessage(
        connectionState: ConnectionState,
        isBluetoothEnabled: Boolean,
        deviceName: String?
    ): String {
        return when (connectionState) {
            ConnectionState.CONNECTING -> "Conectando..."
            ConnectionState.CONNECTED -> if (deviceName.isNullOrBlank()) {
                "Bluetooth conectado"
            } else {
                "Conectado a $deviceName"
            }
            ConnectionState.ERROR -> "Bluetooth con errores"
            ConnectionState.DISCONNECTED -> if (isBluetoothEnabled) {
                "Bluetooth activado"
            } else {
                "Bluetooth desactivado"
            }
        }
    }

    /**
     * Representation of the Bluetooth connection lifecycle.
     */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }
}

/**
 * Immutable UI model for paired Bluetooth devices.
 */
data class PairedDeviceUiModel(
    val name: String,
    val address: String
)

/**
 * Complete state required to render the Bluetooth connection screen.
 */
data class BluetoothConnectionUiState(
    val isBluetoothEnabled: Boolean = false,
    val connectionState: BluetoothViewModel.ConnectionState = BluetoothViewModel.ConnectionState.DISCONNECTED,
    val pairedDevices: List<PairedDeviceUiModel> = emptyList(),
    val connectedDeviceName: String? = null,
    val statusMessage: String = "Bluetooth desactivado",
    val errorMessage: String? = null
)

