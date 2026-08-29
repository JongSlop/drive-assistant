package dev.smto.driveassistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.smto.driveassistant.ui.AssistantViewModel
import dev.smto.driveassistant.ui.ConversationScreen
import dev.smto.driveassistant.ui.SettingsScreen
import dev.smto.driveassistant.ui.SettingsViewModel
import dev.smto.driveassistant.ui.theme.DriveAssistantTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DriveAssistantTheme {
                val nav = rememberNavController()

                var micGranted by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED,
                    )
                }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { result ->
                    micGranted = result[Manifest.permission.RECORD_AUDIO] == true || micGranted
                }

                NavHost(navController = nav, startDestination = "chat") {
                    composable("chat") {
                        val vm: AssistantViewModel = viewModel()
                        ConversationScreen(
                            vm = vm,
                            micGranted = micGranted,
                            onRequestMic = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.RECORD_AUDIO,
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            },
                            onOpenSettings = { nav.navigate("settings") },
                        )
                    }
                    composable("settings") {
                        val vm: SettingsViewModel = viewModel()
                        SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}
