package com.example.control_rm_v1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.control_rm_v1.ui.navigation.ControlRMApp
import com.example.control_rm_v1.ui.theme.Control_RM_V1Theme
import com.example.control_rm_v1.ui.viewmodel.BluetoothViewModel

/**
 * Root activity that hosts the Compose navigation graph.
 */
class MainActivity : ComponentActivity() {

    private val bluetoothViewModel: BluetoothViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Control_RM_V1Theme(darkTheme = true) {
                ControlRMApp(bluetoothViewModel = bluetoothViewModel)
            }
        }
    }
}

