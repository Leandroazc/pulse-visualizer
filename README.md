# Pulse

Visualizador de música para Android e Android Auto, com visual inspirado no design da Apple.

- **Estilos:** Barras, Radial, Ondas e Letra (letra sincronizada, com destaque palavra por palavra quando disponível).
- **Música tocando:** título, artista, capa ou foto do artista, fundo com as cores da imagem e botões voltar/pausar/pular.
- **Áudio:** áudio do sistema (API `Visualizer`) ou microfone, com troca automática.
- **Android Auto:** a mesma interface do celular, na categoria de navegação (só funciona instalado por fora da loja, com "Fontes desconhecidas" ligado no modo desenvolvedor do Android Auto).

## Compilar

No Windows, `compilar.bat` compila numa cópia local, roda os testes e o lint e gera `Pulse.apk` e as prévias em `previas/`.
O projeto também abre no Android Studio. Kotlin, Jetpack Compose, minSdk 24.

`android-auto-720p.bat` abre o simulador do Android Auto (Desktop Head Unit) em 1280x720.

## Fontes externas

- Letras: [LRCLIB](https://lrclib.net) (só com a página Letra aberta).
- Foto do artista e capa em alta: API pública da Deezer (opção "Buscar imagens na internet").

Código de terceiros: veja [TERCEIROS.md](TERCEIROS.md).
