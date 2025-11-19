package com.example.control_rm_v1.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.control_rm_v1.ui.screen.BluetoothConnectionScreen
import com.example.control_rm_v1.ui.screen.MainControlScreen
import com.example.control_rm_v1.ui.viewmodel.BluetoothViewModel
import com.example.control_rm_v1.ui.viewmodel.MainControlViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost

/**
 * Hosts the Navigation Compose graph for the application.
 *
 * @param bluetoothViewModel Shared ViewModel that coordinates Bluetooth operations.
 * @param navController Optional controller to facilitate previews and tests.
 */
@Composable
fun ControlRMApp(
    bluetoothViewModel: BluetoothViewModel,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = AppDestination.BluetoothConnection
    ) {
        composable(AppDestination.BluetoothConnection) {
            BluetoothConnectionScreen(
                viewModel = bluetoothViewModel,
                onNavigateToControl = {
                    navController.navigate(AppDestination.MainControl) {
                        popUpTo(AppDestination.BluetoothConnection) {
                            inclusive = true
                        }
                    }
                }
            )
        }

        composable(AppDestination.MainControl) {
            val mainControlViewModel: MainControlViewModel = viewModel(
                factory = MainControlViewModel.Factory(bluetoothViewModel)
            )
            MainControlScreen(
                viewModel = mainControlViewModel,
                onNavigateBackToConnection = {
                    navController.navigate(AppDestination.BluetoothConnection) {
                        popUpTo(AppDestination.BluetoothConnection) {
                            inclusive = true
                        }
                    }
                }
            )
        }
    }
}

/**
 * Centralized definition of navigation routes.
 */
object AppDestination {
    const val BluetoothConnection: String = "bluetooth_connection"
    const val MainControl: String = "main_control"
}


