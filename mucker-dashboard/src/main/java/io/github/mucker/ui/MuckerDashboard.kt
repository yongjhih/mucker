package io.github.mucker.ui

import android.content.Context
import android.content.Intent
import io.github.mucker.Mucker

/**
 * Direct programmatic entry point for launching the Mucker in-app WebView dashboard.
 */
object MuckerDashboard {

    /**
     * Launch the in-app Mucker dashboard activity directly.
     */
    fun open(context: Context) {
        val intent = Intent(context, MuckerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(intent)
    }

    /**
     * Show the ongoing notification with the dashboard link.
     */
    fun showNotification(context: Context) {
        MuckerNotification.show(context, Mucker.serverUrl)
    }

    /**
     * Hide the ongoing Mucker notification.
     */
    fun hideNotification(context: Context) {
        MuckerNotification.hide(context)
    }
}
