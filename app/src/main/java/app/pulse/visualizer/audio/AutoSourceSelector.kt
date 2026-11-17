package app.pulse.visualizer.audio

enum class SourceKind { SYSTEM, MIC }

/**
 * No modo automatico as duas fontes rodam juntas. O audio do sistema tem prioridade
 * enquanto houver sinal nele; depois de [holdMs] em silencio, o microfone assume
 * (caso tipico: Bluetooth que nao passa pelo Android).
 */
class AutoSourceSelector(private val holdMs: Long = 3000) {
    private var lastSystemSignal = Long.MIN_VALUE / 2

    fun onSystemFrame(hasSignal: Boolean, nowMs: Long) {
        if (hasSignal) lastSystemSignal = nowMs
    }

    fun active(nowMs: Long, systemAvailable: Boolean = true, micAvailable: Boolean = true): SourceKind = when {
        !systemAvailable -> SourceKind.MIC
        !micAvailable -> SourceKind.SYSTEM
        nowMs - lastSystemSignal <= holdMs -> SourceKind.SYSTEM
        else -> SourceKind.MIC
    }
}
