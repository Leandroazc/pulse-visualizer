package app.pulse.visualizer.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("pulse_settings")

/**
 * Numero salvo de cada estilo. Fixo, para nao embaralhar os ajustes ja gravados quando um estilo
 * sai: "WAVES" (Ondas) foi removido e o codigo 2 continua reservado para ele.
 */
private val STYLE_CODES = listOf("BARS", "RADIAL", "WAVES", "LYRICS")

class SettingsStore(private val context: Context) {
    private object K {
        val style = intPreferencesKey("style")
        val rotate = intPreferencesKey("rotate")
        val palette = intPreferencesKey("palette")
        val spectrumColor = booleanPreferencesKey("spectrum_color")
        val sensitivity = floatPreferencesKey("sensitivity")
        val smoothness = floatPreferencesKey("smoothness")
        val pulse = floatPreferencesKey("pulse")
        val barCount = intPreferencesKey("bar_count")
        val mirror = booleanPreferencesKey("mirror")
        val peaks = booleanPreferencesKey("peaks")
        val reflection = booleanPreferencesKey("reflection")
        val thickness = floatPreferencesKey("thickness")
        val ambient = booleanPreferencesKey("ambient")
        val clock = booleanPreferencesKey("clock")
        val keepOn = booleanPreferencesKey("keep_on")
        val source = intPreferencesKey("source")
        val nowPlaying = booleanPreferencesKey("now_playing")
        val artMode = intPreferencesKey("art_mode")
        val artBackground = booleanPreferencesKey("art_background")
        val onlineArt = booleanPreferencesKey("online_art")
        val mediaControls = booleanPreferencesKey("media_controls")
        val artFull = booleanPreferencesKey("art_full")
        val wordByWord = booleanPreferencesKey("word_by_word")
        val lyricsOffset = intPreferencesKey("lyrics_offset_ms")
        val linkMode = intPreferencesKey("link_mode")
        val linkDevice = stringPreferencesKey("link_device")
    }

    val settings: Flow<Settings> = context.dataStore.data.map(::read)

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val s = transform(read(p))
            p[K.style] = STYLE_CODES.indexOf(s.style.name)
            p[K.rotate] = s.autoRotateMin
            p[K.palette] = s.palette
            p[K.spectrumColor] = s.spectrumColor
            p[K.sensitivity] = s.sensitivity
            p[K.smoothness] = s.smoothness
            p[K.pulse] = s.pulse
            p[K.barCount] = s.barCount
            p[K.mirror] = s.mirror
            p[K.peaks] = s.peaks
            p[K.reflection] = s.reflection
            p[K.thickness] = s.thickness
            p[K.ambient] = s.ambient
            p[K.clock] = s.clock
            p[K.keepOn] = s.keepScreenOn
            p[K.source] = s.source.ordinal
            p[K.nowPlaying] = s.nowPlaying
            p[K.artMode] = s.artMode.ordinal
            p[K.artBackground] = s.artBackground
            p[K.onlineArt] = s.onlineArt
            p[K.mediaControls] = s.mediaControls
            p[K.artFull] = s.artFull
            p[K.wordByWord] = s.wordByWord
            p[K.lyricsOffset] = s.lyricsOffsetMs
            p[K.linkMode] = s.linkMode.ordinal
            p[K.linkDevice] = s.linkDevice
        }
    }

    private fun read(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            nowPlaying = p[K.nowPlaying] ?: d.nowPlaying,
            artMode = ArtMode.entries.getOrElse(p[K.artMode] ?: d.artMode.ordinal) { d.artMode },
            artBackground = p[K.artBackground] ?: d.artBackground,
            onlineArt = p[K.onlineArt] ?: d.onlineArt,
            mediaControls = p[K.mediaControls] ?: d.mediaControls,
            artFull = p[K.artFull] ?: d.artFull,
            wordByWord = p[K.wordByWord] ?: d.wordByWord,
            lyricsOffsetMs = (p[K.lyricsOffset] ?: d.lyricsOffsetMs).coerceIn(-LYRICS_OFFSET_MAX, LYRICS_OFFSET_MAX),
            // Codigo antigo de um estilo que saiu (Ondas) volta para Barras.
            style = StyleKind.entries.firstOrNull { it.name == STYLE_CODES.getOrNull(p[K.style] ?: -1) } ?: d.style,
            autoRotateMin = p[K.rotate] ?: d.autoRotateMin,
            palette = (p[K.palette] ?: d.palette).coerceIn(0, Palettes.all.lastIndex),
            spectrumColor = p[K.spectrumColor] ?: d.spectrumColor,
            sensitivity = p[K.sensitivity] ?: d.sensitivity,
            smoothness = p[K.smoothness] ?: d.smoothness,
            pulse = p[K.pulse] ?: d.pulse,
            barCount = (p[K.barCount] ?: d.barCount).coerceIn(16, 64),
            mirror = p[K.mirror] ?: d.mirror,
            peaks = p[K.peaks] ?: d.peaks,
            reflection = p[K.reflection] ?: d.reflection,
            thickness = p[K.thickness] ?: d.thickness,
            ambient = p[K.ambient] ?: d.ambient,
            clock = p[K.clock] ?: d.clock,
            keepScreenOn = p[K.keepOn] ?: d.keepScreenOn,
            source = SourceMode.entries.getOrElse(p[K.source] ?: d.source.ordinal) { d.source },
            linkMode = LinkMode.entries.getOrElse(p[K.linkMode] ?: d.linkMode.ordinal) { d.linkMode },
            linkDevice = p[K.linkDevice] ?: d.linkDevice,
        )
    }
}
