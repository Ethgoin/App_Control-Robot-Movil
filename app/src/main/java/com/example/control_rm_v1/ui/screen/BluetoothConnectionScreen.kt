package com.example.control_rm_v1.ui.screen

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.control_rm_v1.ui.viewmodel.BluetoothViewModel

/**
 * Screen that displays paired Bluetooth devices and allows connection to one of them.
 * This screen does not pair devices, it only shows already paired devices.
 *
 * @param viewModel ViewModel that manages Bluetooth state and operations.
 * @param onNavigateToControl Callback executed when connection is successfully established.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothConnectionScreen(
    viewModel: BluetoothViewModel,
    onNavigateToControl: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState(initial = viewModel.uiState.value)
    
    // Local state for connection status
    var connectionState by remember { mutableStateOf(uiState.connectionState) }
    var hasNavigated by remember { mutableStateOf(false) }
    
    // Get BluetoothManager and BluetoothAdapter
    val bluetoothManager = remember {
        context.getSystemService(BluetoothManager::class.java)
    }
    val bluetoothAdapter = remember {
        bluetoothManager?.adapter
    }
    
    // Launcher for enabling Bluetooth via system dialog
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshBluetoothState()
    }
    
    // Launcher for requesting Bluetooth permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            // After permissions granted, enable Bluetooth if needed
            if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled) {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else {
                viewModel.refreshBluetoothState()
            }
        } else {
            Toast.makeText(
                context,
                "Se requieren permisos Bluetooth para continuar",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    // Required permissions based on Android version
    val requiredPermissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            } else {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            } else {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }.toTypedArray()
    }
    
    // Update local connection state when ViewModel state changes
    LaunchedEffect(uiState.connectionState) {
        connectionState = uiState.connectionState
        
        // Navigate to control screen when connected
        if (!hasNavigated && connectionState == BluetoothViewModel.ConnectionState.CONNECTED) {
            hasNavigated = true
            onNavigateToControl()
        }
        
        // Reset navigation flag when disconnected
        if (connectionState == BluetoothViewModel.ConnectionState.DISCONNECTED) {
            hasNavigated = false
        }
    }
    
    // Load paired devices when Bluetooth is enabled
    LaunchedEffect(uiState.isBluetoothEnabled) {
        if (uiState.isBluetoothEnabled) {
            viewModel.loadPairedDevices()
        }
    }
    
    // Show error messages via Toast
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }
    
    Scaffold(
        containerColor = Color(0xFF101010),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Conexión Bluetooth",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF181818)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF101010))
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status text
            StatusText(
                statusMessage = uiState.statusMessage,
                connectionState = connectionState,
                isBluetoothEnabled = uiState.isBluetoothEnabled
            )
            
            // Toggle Bluetooth button
            ToggleBluetoothButton(
                isBluetoothEnabled = uiState.isBluetoothEnabled,
                isConnecting = connectionState == BluetoothViewModel.ConnectionState.CONNECTING,
                onToggle = {
                    if (uiState.isBluetoothEnabled) {
                        // Disable Bluetooth
                        viewModel.disableBluetooth()
                    } else {
                        // Enable Bluetooth - check permissions first
                        val missingPermissions = requiredPermissions.filter { permission ->
                            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
                        }
                        
                        if (missingPermissions.isNotEmpty()) {
                            // Request permissions
                            permissionLauncher.launch(missingPermissions.toTypedArray())
                        } else {
                            // Permissions granted, enable Bluetooth
                            if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled) {
                                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            } else {
                                viewModel.refreshBluetoothState()
                            }
                        }
                    }
                }
            )
            
            // Connection progress indicator
            if (connectionState == BluetoothViewModel.ConnectionState.CONNECTING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(24.dp).height(24.dp),
                        color = Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Conectando...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
            
            // Paired devices list
            PairedDevicesList(
                pairedDevices = uiState.pairedDevices,
                isBluetoothEnabled = uiState.isBluetoothEnabled,
                isConnecting = connectionState == BluetoothViewModel.ConnectionState.CONNECTING,
                onConnect = { deviceAddress ->
                    viewModel.connectToDevice(deviceAddress)
                }
            )
        }
    }
}

/**
 * Displays the current Bluetooth connection status.
 *
 * @param statusMessage Status message from ViewModel.
 * @param connectionState Current connection state.
 * @param isBluetoothEnabled Whether Bluetooth adapter is enabled.
 */
@Composable
private fun StatusText(
    statusMessage: String,
    connectionState: BluetoothViewModel.ConnectionState,
    isBluetoothEnabled: Boolean
) {
    val statusColor = when (connectionState) {
        BluetoothViewModel.ConnectionState.CONNECTED -> Color(0xFF4CAF50) // Green
        BluetoothViewModel.ConnectionState.CONNECTING -> Color(0xFFFFC107) // Amber
        BluetoothViewModel.ConnectionState.ERROR -> Color(0xFFF44336) // Red
        BluetoothViewModel.ConnectionState.DISCONNECTED -> if (isBluetoothEnabled) {
            Color(0xFF90CAF9) // Blue
        } else {
            Color(0xFFFF5722) // Deep Orange
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Text(
            text = statusMessage,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = statusColor,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Button to toggle Bluetooth adapter on/off.
 *
 * @param isBluetoothEnabled Current Bluetooth state.
 * @param isConnecting Whether a connection is in progress.
 * @param onToggle Callback when button is clicked.
 */
@Composable
private fun ToggleBluetoothButton(
    isBluetoothEnabled: Boolean,
    isConnecting: Boolean,
    onToggle: () -> Unit
) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle,
        enabled = !isConnecting
    ) {
        Text(
            text = if (isBluetoothEnabled) {
                "Desactivar Bluetooth"
            } else {
                "Activar Bluetooth"
            }
        )
    }
}

/**
 * Lists all paired Bluetooth devices with connect buttons.
 *
 * @param pairedDevices List of paired devices to display.
 * @param isBluetoothEnabled Whether Bluetooth is enabled.
 * @param isConnecting Whether a connection is in progress.
 * @param onConnect Callback when connect button is clicked for a device.
 */
@Composable
private fun PairedDevicesList(
    pairedDevices: List<com.example.control_rm_v1.ui.viewmodel.PairedDeviceUiModel>,
    isBluetoothEnabled: Boolean,
    isConnecting: Boolean,
    onConnect: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Dispositivos emparejados",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            
            if (!isBluetoothEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Activa Bluetooth para ver los dispositivos emparejados",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB0BEC5)
                    )
                }
            } else if (pairedDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay dispositivos emparejados disponibles",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB0BEC5)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(pairedDevices, key = { it.address }) { device ->
                        PairedDeviceItem(
                            deviceName = device.name,
                            deviceAddress = device.address,
                            isConnecting = isConnecting,
                            onConnect = { onConnect(device.address) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Individual device item card showing name, MAC address, and connect button.
 *
 * @param deviceName Friendly name of the device.
 * @param deviceAddress MAC address of the device.
 * @param isConnecting Whether a connection is in progress.
 * @param onConnect Callback when connect button is clicked.
 */
@Composable
private fun PairedDeviceItem(
    deviceName: String,
    deviceAddress: String,
    isConnecting: Boolean,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF242424)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = deviceAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB0BEC5)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Button(
                onClick = onConnect,
                enabled = !isConnecting
            ) {
                Text(text = "Conectar")
            }
        }
    }
}
