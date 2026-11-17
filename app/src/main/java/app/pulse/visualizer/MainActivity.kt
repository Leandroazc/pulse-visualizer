package app.pulse.visualizer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.pulse.visualizer.audio.AudioEngine
import app.pulse.visualizer.media.NowPlayingMonitor
import app.pulse.visualizer.settings.SettingsStore
import app.pulse.visualizer.ui.PulseApp

class MainActivity : ComponentActivity() {
    private val engine = AudioEngine()
    private val monitor by lazy { NowPlayingMonitor(applicationContext) }
    private var hasPermission by mutableStateOf(false)
    private var askedOnce = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasPermission = granted
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        hasPermission = micGranted()
        if (!hasPermission) requestMic()

        val store = SettingsStore(applicationContext)
        setContent {
            PulseApp(
                store, engine, monitor, hasPermission,
                onRequestPermission = ::requestMic,
                onMediaAccess = {
                    runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // O usuario pode ter concedido a permissao pela tela de configuracoes do sistema.
        hasPermission = micGranted()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun micGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestMic() {
        // Depois de uma recusa definitiva o dialogo nao aparece mais: abre as configuracoes do app.
        if (askedOnce && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
                )
            }
            return
        }
        askedOnce = true
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
