package com.example.control_rm_v1.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.control_rm_v1.data.TelemetryData
import com.example.control_rm_v1.ui.viewmodel.BluetoothViewModel
import com.example.control_rm_v1.ui.viewmodel.MainControlViewModel
import com.example.control_rm_v1.ui.viewmodel.MainControlViewModel.MovementDirection
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Main control screen for the Pololu robot.
 * 
 * This screen provides:
 * - Real-time telemetry display (distance, light, temperature) at the top
 * - Virtual joystick control in the center for robot movement
 * - Disconnect button at the bottom to return to Bluetooth connection screen
 * 
 * The joystick sends Bluetooth commands:
 * - "F" = Forward (up)
 * - "B" = Backward (down)
 * - "L" = Left
 * - "R" = Right
 * - "S" = Stop (when joystick returns to center)
 * 
 * Telemetry is continuously read from the Bluetooth InputStream and parsed
 * from the format: D:123,L:45,T:27.8
 *
 * @param viewModel ViewModel that manages robot commands and telemetry state.
 * @param onNavigateBackToConnection Callback to return to the Bluetooth connection screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainControlScreen(
    viewModel: MainControlViewModel,
    onNavigateBackToConnection: () -> Unit
) {
    // Collect connection state and telemetry from ViewModel
    val connectionState by viewModel.connectionUiState.collectAsState()
    val telemetryState by viewModel.telemetryState.collectAsState()
    val isConnected = connectionState.connectionState == BluetoothViewModel.ConnectionState.CONNECTED

    // Reset movement state when screen is disposed to ensure robot stops
    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetMovementState()
        }
    }

    Scaffold(
        containerColor = Color(0xFF101010),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Control del Robot",
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
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Telemetry section at the top
            TelemetryDisplay(telemetryState = telemetryState)

            // 2. Joystick in the center (only visible when connected)
            if (isConnected) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    VirtualJoystick(
                        onDirectionChanged = { direction ->
                            // Send movement command via ViewModel
                            viewModel.sendMovement(direction)
                        },
                        onStop = {
                            // Send stop command when joystick returns to center
                            viewModel.stopMovement()
                        }
                    )
                }
            } else {
                // Show connection status when not connected
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "No conectado",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFF44336),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = connectionState.statusMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFB0BEC5)
                            )
                        }
                    }
                }
            }

            // 3. Disconnect button at the bottom
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                onClick = {
                    viewModel.disconnect()
                    onNavigateBackToConnection()
                }
            ) {
                Text(
                    text = "Desconectar y volver",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Displays real-time telemetry data received from the Arduino robot.
 * 
 * Parses and displays:
 * - Distance in centimeters (from D:123)
 * - Light percentage (from L:45)
 * - Temperature in Celsius (from T:27.8)
 * 
 * Values are updated in real-time using StateFlow from the Bluetooth repository.
 *
 * @param telemetryState Current telemetry data from the robot.
 */
@Composable
private fun TelemetryDisplay(telemetryState: TelemetryData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Telemetría en tiempo real",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Distance telemetry
                TelemetryItem(
                    label = "Distancia",
                    value = telemetryState.distanceCm?.let { "${it.toInt()} cm" } ?: "-- cm",
                    modifier = Modifier.weight(1f)
                )
                
                // Light telemetry
                TelemetryItem(
                    label = "Luz",
                    value = telemetryState.lightPercentage?.let { "${it.toInt()} %" } ?: "-- %",
                    modifier = Modifier.weight(1f)
                )
                
                // Temperature telemetry
                TelemetryItem(
                    label = "Temperatura",
                    value = telemetryState.temperatureCelsius?.let { 
                        String.format(Locale.US, "%.1f °C", it) 
                    } ?: "-- °C",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Individual telemetry item displaying a label and formatted value.
 *
 * @param label Label text for the telemetry value (e.g., "Distancia", "Luz", "Temperatura").
 * @param value Formatted value to display (e.g., "123 cm", "45 %", "27.8 °C").
 * @param modifier Modifier for layout customization.
 */
@Composable
private fun TelemetryItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFB0BEC5)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = Color(0xFF4CAF50),
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Virtual joystick control for robot movement.
 * 
 * Uses pointerInput with detectDragGestures to detect user drag gestures.
 * Converts drag position to directional commands:
 * - Up (negative Y) = Forward (F)
 * - Down (positive Y) = Backward (B)
 * - Left (negative X) = Left (L)
 * - Right (positive X) = Right (R)
 * - Center = Stop (S)
 * 
 * The joystick has a circular boundary and the knob is constrained within it.
 * A threshold of 30% of the maximum radius is used to prevent accidental activations.
 *
 * @param onDirectionChanged Callback invoked when the joystick direction changes.
 * @param onStop Callback invoked when the joystick returns to center position.
 */
@Composable
private fun VirtualJoystick(
    onDirectionChanged: (MovementDirection) -> Unit,
    onStop: () -> Unit
) {
    val joystickSize = 280.dp
    val knobSize = 80.dp
    val density = LocalDensity.current
    
    // State for joystick position and direction
    var baseRadiusPx by remember { mutableFloatStateOf(0f) }
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    var currentDirection by remember { mutableStateOf<MovementDirection?>(null) }

    Box(
        modifier = Modifier
            .size(joystickSize)
            .clip(CircleShape)
            .background(Color(0xFF1E1E1E))
            .border(width = 4.dp, color = Color(0xFF4CAF50), shape = CircleShape)
            .onGloballyPositioned { layoutCoordinates ->
                // Calculate the base radius when layout is positioned
                baseRadiusPx = min(layoutCoordinates.size.width, layoutCoordinates.size.height) / 2f
            }
            .pointerInput(Unit) {
                // Use detectDragGestures to handle drag input
                detectDragGestures(
                    onDragStart = { 
                        // Drag started - no action needed
                    },
                    onDragEnd = {
                        // Reset to center and send stop command
                        knobOffset = Offset.Zero
                        if (currentDirection != null) {
                            currentDirection = null
                            onStop()
                        }
                    },
                    onDragCancel = {
                        // Reset to center and send stop command on cancel
                        knobOffset = Offset.Zero
                        if (currentDirection != null) {
                            currentDirection = null
                            onStop()
                        }
                    }
                ) { change, dragAmount ->
                    change.consume()
                    
                    // Calculate maximum allowed radius (base radius minus knob radius)
                    val knobRadiusPx = with(density) { knobSize.toPx() / 2f }
                    val maxRadius = (baseRadiusPx - knobRadiusPx).coerceAtLeast(0f)
                    
                    // Calculate new position
                    val newOffset = Offset(
                        x = (knobOffset.x + dragAmount.x).coerceIn(-maxRadius, maxRadius),
                        y = (knobOffset.y + dragAmount.y).coerceIn(-maxRadius, maxRadius)
                    )
                    
                    // Constrain to circular boundary
                    val distance = sqrt(newOffset.x * newOffset.x + newOffset.y * newOffset.y)
                    val constrainedOffset = if (distance > maxRadius) {
                        // Scale down to fit within circle
                        val scale = maxRadius / distance
                        Offset(newOffset.x * scale, newOffset.y * scale)
                    } else {
                        newOffset
                    }
                    
                    knobOffset = constrainedOffset
                    
                    // Determine direction based on position
                    val threshold = maxRadius * 0.3f // 30% threshold for activation
                    val magnitude = sqrt(
                        constrainedOffset.x * constrainedOffset.x + 
                        constrainedOffset.y * constrainedOffset.y
                    )
                    
                    val newDirection = if (magnitude < threshold) {
                        // Within threshold = center position = STOP
                        null
                    } else {
                        // Determine primary direction based on axis dominance
                        when {
                            abs(constrainedOffset.x) > abs(constrainedOffset.y) -> {
                                // Horizontal movement dominates
                                if (constrainedOffset.x > 0) {
                                    MovementDirection.RIGHT
                                } else {
                                    MovementDirection.LEFT
                                }
                            }
                            else -> {
                                // Vertical movement dominates
                                if (constrainedOffset.y > 0) {
                                    MovementDirection.BACKWARD
                                } else {
                                    MovementDirection.FORWARD
                                }
                            }
                        }
                    }
                    
                    // Only send command if direction changed
                    if (newDirection != currentDirection) {
                        currentDirection = newDirection
                        if (newDirection == null) {
                            // Center position = stop
                            onStop()
                        } else {
                            // Send movement command
                            onDirectionChanged(newDirection)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Center crosshair lines for visual reference
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            // Horizontal center line
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color(0xFF2C2C2C))
            )
            // Vertical center line
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(Color(0xFF2C2C2C))
            )
        }
        
        // Joystick knob that moves with drag gestures
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = knobOffset.x.roundToInt(),
                        y = knobOffset.y.roundToInt()
                    )
                }
                .size(knobSize)
                .clip(CircleShape)
                .background(Color(0xFF4CAF50))
                .border(width = 3.dp, color = Color(0xFF81C784), shape = CircleShape)
        )
    }
}
