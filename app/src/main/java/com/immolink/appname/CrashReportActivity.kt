package com.immolink.appname

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Écran volontairement construit avec des vues Android natives (pas de Compose,
 * pas d'AppCompat) pour qu'il puisse s'afficher même si le crash initial vient
 * d'un problème de thème ou d'inflation Compose.
 */
class CrashReportActivity : Activity() {

    companion object {
        const val EXTRA_REPORT = "report"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val report = intent.getStringExtra(EXTRA_REPORT) ?: "Aucun rapport disponible."

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 90, 40, 40)
            setBackgroundColor(Color.WHITE)
        }

        root.addView(TextView(this).apply {
            text = "⚠️ ImmoLink a rencontré une erreur"
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#123D73"))
            setPadding(0, 0, 0, 20)
        })

        root.addView(TextView(this).apply {
            text = "Copiez ce rapport ci-dessous et envoyez-le pour correction :"
            textSize = 14f
            setPadding(0, 0, 0, 20)
        })

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        val reportView = TextView(this).apply {
            text = report
            textSize = 12f
            setTextIsSelectable(true)
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#F0F0F0"))
        }
        scroll.addView(reportView)
        root.addView(scroll)

        val buttonBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 30, 0, 0)
            gravity = Gravity.END
        }

        buttonBar.addView(Button(this).apply {
            text = "Copier le rapport"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Rapport d'erreur ImmoLink", report))
                Toast.makeText(this@CrashReportActivity, "Rapport copié !", Toast.LENGTH_SHORT).show()
            }
        })

        buttonBar.addView(Button(this).apply {
            text = "Fermer"
            setOnClickListener { finish() }
        })

        root.addView(buttonBar)
        setContentView(root)
    }
}
