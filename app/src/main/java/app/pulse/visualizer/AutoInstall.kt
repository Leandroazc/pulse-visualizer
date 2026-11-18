package app.pulse.visualizer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Faz o Pulse aparecer no Android Auto sem conta de desenvolvedor na Play Store.
 *
 * O Android Auto so lista um app do tipo Car App Library (template) se o instalador registrado for
 * a Play Store. No Android 11+ ele olha antes quem iniciou a instalacao: instalado pelo adb, o
 * iniciador e o shell e o app e recusado mesmo com "-i com.android.vending". Aqui o proprio Pulse
 * pede ao instalador do sistema para se reinstalar dizendo que a origem e a Play Store (o mesmo
 * que o KingInstaller faz). O app e os ajustes continuam iguais.
 */
object AutoInstall {
    const val PLAY_STORE = "com.android.vending"

    enum class State { READY, NEEDS_REINSTALL, UNKNOWN }

    /** READY quando a instalacao atual ja passa no Android Auto. */
    fun state(context: Context): State {
        if (Build.VERSION.SDK_INT < 30) return State.UNKNOWN
        val info = runCatching { context.packageManager.getInstallSourceInfo(context.packageName) }.getOrNull()
            ?: return State.UNKNOWN
        val initiator = info.initiatingPackageName
        val ok = info.installingPackageName == PLAY_STORE && initiator != null && initiator != "com.android.shell"
        return if (ok) State.READY else State.NEEDS_REINSTALL
    }

    /** Abre o instalador do sistema com o proprio APK do Pulse; na primeira vez pede a permissao. */
    fun reinstall(context: Context) {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        val dir = File(context.cacheDir, "auto").apply { mkdirs() }
        val apk = File(dir, "Pulse.apk")
        File(context.applicationInfo.sourceDir).copyTo(apk, overwrite = true)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        @Suppress("DEPRECATION")
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, PLAY_STORE)
            .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        context.startActivity(intent)
    }
}
