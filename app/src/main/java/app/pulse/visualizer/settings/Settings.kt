package app.pulse.visualizer.settings

enum class StyleKind(val label: String) {
    BARS("Barras"), RADIAL("Radial"), WAVES("Ondas"),

    /** Pagina da letra sincronizada no lugar do visualizador. */
    LYRICS("Letra"),
}

enum class SourceMode(val label: String) { AUTO("Automático"), SYSTEM("Sistema"), MIC("Microfone") }

enum class ArtMode(val label: String) { ALBUM("Capa"), ARTIST("Artista"), NONE("Nenhuma") }

/**
 * Ligacao Bluetooth entre dois Pulse: o do celular envia musica, capa e letra;
 * o da central multimidia recebe (ela nao tem internet nem a sessao de midia do Android Auto).
 */
enum class LinkMode(val label: String) { OFF("Desligado"), SEND("Enviar"), RECEIVE("Receber") }

data class Settings(
    /** Mostra titulo e artista da musica que esta tocando. */
    val nowPlaying: Boolean = true,
    val artMode: ArtMode = ArtMode.ALBUM,
    /** Imagem ocupando toda a lateral, se fundindo com o fundo (estilo Spicy Lyrics). */
    val artFull: Boolean = false,
    /** Fundo desfocado feito da imagem, com o visualizador em tons claros. */
    val artBackground: Boolean = true,
    /** Permite buscar foto do artista / capa na internet (Deezer). */
    val onlineArt: Boolean = true,
    /** Botoes voltar / tocar-pausar / pular embaixo da musica. */
    val mediaControls: Boolean = true,
    /** Na letra, preenche cada palavra/silaba enquanto e cantada (quando ha esses tempos). */
    val wordByWord: Boolean = true,
    /** Ajuste da sincronia da letra em ms: positivo adianta a letra, negativo atrasa. */
    val lyricsOffsetMs: Int = 0,
    val style: StyleKind = StyleKind.BARS,
    /** Minutos entre trocas automaticas de estilo; 0 desliga. */
    val autoRotateMin: Int = 0,
    val palette: Int = 0,
    /** true: as cores acompanham a frequencia; false: degrade pela intensidade. */
    val spectrumColor: Boolean = true,
    val sensitivity: Float = 1f,
    val smoothness: Float = 0.5f,
    val pulse: Float = 0.6f,
    val barCount: Int = 40,
    val mirror: Boolean = true,
    val peaks: Boolean = true,
    val reflection: Boolean = true,
    val thickness: Float = 0.6f,
    val ambient: Boolean = true,
    val clock: Boolean = false,
    val nightDim: Boolean = false,
    val keepScreenOn: Boolean = true,
    val source: SourceMode = SourceMode.AUTO,
    val linkMode: LinkMode = LinkMode.OFF,
    /** Endereco Bluetooth da central para onde o celular envia (modo Enviar). */
    val linkDevice: String = "",
) {
    companion object {
        val ROTATE_OPTIONS = listOf(0, 1, 3, 5)
    }
}

/** Limite do ajuste de sincronia da letra (3 s para cada lado). */
const val LYRICS_OFFSET_MAX = 3000
