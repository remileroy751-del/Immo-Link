package com.immolink.appname

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import co.opensi.kkiapay.uikit.Kkiapay
import co.opensi.kkiapay.uikit.SdkConfig
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Application personnalisée : capture TOUTE exception non gérée (sur n'importe
 * quel thread, y compris pendant l'initialisation de la première Activity) et
 * affiche un écran de rapport d'erreur copiable au lieu de laisser le système
 * fermer l'application silencieusement.
 */
class CrashHandlerApp : Application() {

    // attachBaseContext() s'exécute AVANT onCreate() : c'est le tout premier
    // point d'entrée disponible dans le cycle de vie de l'Application. On y
    // installe le capteur d'erreurs le plus tôt possible, avant même
    // l'initialisation de tout SDK tiers (Kkiapay, etc.).
    override fun attachBaseContext(base: android.content.Context?) {
        super.attachBaseContext(base)
        installCrashHandler()
    }

    override fun onCreate() {
        super.onCreate()

        // Initialisation du SDK Kkiapay ICI, dans Application.onCreate(),
        // conformément à la documentation officielle (et non plus dans
        // MainActivity, ce qui était non conforme et probablement à l'origine
        // du crash au lancement).
        runCatching {
            Kkiapay.init(
                applicationContext,
                KkiapayConfig.PUBLIC_KEY,
                SdkConfig(themeColor = R.color.colorPrimary, enableSandbox = false)
            )
        }.onFailure { showReport(it, Thread.currentThread()) }
    }

    private fun installCrashHandler() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                showReport(throwable, thread)
            } catch (inner: Throwable) {
                // Si même l'affichage du rapport échoue, on abandonne
                // silencieusement : le processus va se terminer ci-dessous.
            } finally {
                Process.killProcess(Process.myPid())
                Runtime.getRuntime().exit(10)
            }
        }
    }

    private fun showReport(throwable: Throwable, thread: Thread) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))

        val report = buildString {
            appendLine("=== RAPPORT D'ERREUR IMMOLINK ===")
            appendLine("Date        : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRANCE).format(Date())}")
            appendLine("Appareil    : ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android     : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Thread      : ${thread.name}")
            appendLine()
            appendLine("--- STACK TRACE ---")
            append(sw.toString())
        }

        val intent = Intent(applicationContext, CrashReportActivity::class.java).apply {
            putExtra(CrashReportActivity.EXTRA_REPORT, report)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        applicationContext.startActivity(intent)
    }
}
