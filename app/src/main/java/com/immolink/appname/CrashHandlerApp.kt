package com.immolink.appname

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
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

    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
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
            } catch (inner: Throwable) {
                // Si même la capture échoue, on ne fait rien de plus : le
                // processus va se terminer normalement ci-dessous.
            } finally {
                Process.killProcess(Process.myPid())
                Runtime.getRuntime().exit(10)
            }
        }
    }
}
