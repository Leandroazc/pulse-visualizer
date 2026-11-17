package app.pulse.visualizer.car

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.validation.HostValidator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.pulse.visualizer.R
import app.pulse.visualizer.audio.AudioEngine
import app.pulse.visualizer.media.NowPlayingMonitor
import app.pulse.visualizer.settings.SettingsStore
import app.pulse.visualizer.ui.PulseApp

/**
 * Entrada do Pulse no Android Auto. O Android Auto so deixa desenhar livremente na tela
 * para apps de navegacao, entao o Pulse se declara nessa categoria (uso pessoal, fora da Play Store).
 */
class PulseCarService : CarAppService() {
    // Instalado por fora da loja: aceita qualquer host do Android Auto.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent): Screen = PulseCarScreen(carContext)
    }
}

/** A mesma interface do celular (PulseApp), desenhada na tela do carro. */
class PulseCarScreen(ctx: CarContext) : Screen(ctx) {
    private val app = ctx.applicationContext
    private val store = SettingsStore(app)
    private val engine = AudioEngine()
    private val monitor = NowPlayingMonitor(app)

    private var hasPermission by mutableStateOf(micGranted())
    private var settingsToggle by mutableIntStateOf(0)

    private val display = CarDisplay(ctx) { m ->
        // O fundo ocupa a tela toda; o conteudo fica fora dos botoes do Android Auto.
        val padding = with(LocalDensity.current) {
            PaddingValues(start = m.left.toDp(), top = m.top.toDp(), end = m.right.toDp(), bottom = m.bottom.toDp())
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            PulseApp(
                store, engine, monitor, hasPermission,
                onRequestPermission = ::requestMic,
                onMediaAccess = {
                    CarToast.makeText(carContext, "Abra o Pulse no celular para permitir", CarToast.LENGTH_LONG).show()
                },
                settingsToggle = settingsToggle,
                contentPadding = padding,
            )
        }
    }

    init {
        ctx.getCarService(AppManager::class.java).setSurfaceCallback(display)

        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                hasPermission = micGranted()
                display.setStarted(true)
            }

            override fun onStop(owner: LifecycleOwner) = display.setStarted(false)

            override fun onDestroy(owner: LifecycleOwner) = display.destroy()
        })
    }

    private fun micGranted() =
        ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestMic() {
        runCatching {
            carContext.requestPermissions(listOf(Manifest.permission.RECORD_AUDIO)) { granted, _ ->
                hasPermission = Manifest.permission.RECORD_AUDIO in granted
            }
        }.onFailure {
            CarToast.makeText(carContext, "Abra o Pulse no celular para permitir", CarToast.LENGTH_LONG).show()
        }
    }

    override fun onGetTemplate(): Template {
        val settings = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_settings)).build())
            .setOnClickListener { settingsToggle++ }
            .build()
        return NavigationTemplate.Builder()
            .setActionStrip(ActionStrip.Builder().addAction(settings).build())
            // Com o botao de mover, o Android Auto passa os arrastes para o app (trocar estilo, rolar ajustes).
            .setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).build())
            .build()
    }
}
