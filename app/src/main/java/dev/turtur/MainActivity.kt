package dev.turtur

import android.Manifest
import android.content.Intent
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
import dev.turtur.radio.SosListener
import dev.turtur.sos.SosNotifier
import dev.turtur.ui.TurturApp
import dev.turtur.ui.TurturViewModel
import dev.turtur.ui.theme.TurturTheme

class MainActivity : ComponentActivity() {
    private val vm: TurturViewModel by viewModels()
    private var cameraGranted by mutableStateOf(false)

    private val bluetoothPerms = if (Build.VERSION.SDK_INT >= 31) {
        buildList {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private val btLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        vm.startRadioIfPossible()
        SosListener.sync(this)
    }

    private val camLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> cameraGranted = granted }

    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        vm.onLocationResult()
        SosListener.sync(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        btLauncher.launch(bluetoothPerms)
        if (savedInstanceState == null) routeIntent(intent)
        setContent {
            val state by vm.ui.collectAsState()
            TurturTheme {
                TurturApp(
                    state = state,
                    vm = vm,
                    cameraGranted = cameraGranted,
                    onNeedCamera = { camLauncher.launch(Manifest.permission.CAMERA) },
                    onNeedLocation = {
                        locationLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        vm.startRadioIfPossible()
    }

    private fun routeIntent(intent: Intent?) {
        val screen = intent?.getStringExtra(SosNotifier.EXTRA_SCREEN) ?: return
        vm.openFromIntent(screen, intent.getStringExtra(SosNotifier.EXTRA_BEACON))
    }
}
