package dev.turtur

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import dev.turtur.ui.TurturApp
import dev.turtur.ui.TurturViewModel
import dev.turtur.ui.theme.TurturTheme

class MainActivity : ComponentActivity() {
    private val vm: TurturViewModel by viewModels()
    private var cameraGranted by mutableStateOf(false)

    private val bluetoothPerms = if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private val btLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        vm.startRadioIfPossible()
    }

    private val camLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> cameraGranted = granted }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        btLauncher.launch(bluetoothPerms)
        setContent {
            val state by vm.ui.collectAsState()
            TurturTheme {
                TurturApp(
                    state = state,
                    vm = vm,
                    cameraGranted = cameraGranted,
                    onNeedCamera = { camLauncher.launch(Manifest.permission.CAMERA) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.startRadioIfPossible()
    }
}
