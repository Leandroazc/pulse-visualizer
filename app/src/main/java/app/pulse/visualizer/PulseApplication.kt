package app.pulse.visualizer

import android.app.Application
import app.pulse.visualizer.lyrics.LyricsDiskCache
import app.pulse.visualizer.lyrics.LyricsFetcher
import java.io.File

/** Prepara o que vale para o app inteiro (tela do celular, Android Auto e envio para a central). */
class PulseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LyricsFetcher.disk = LyricsDiskCache(File(cacheDir, "lyrics"))
    }
}
