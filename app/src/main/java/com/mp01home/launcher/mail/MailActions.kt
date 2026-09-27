package com.mp01home.launcher.mail

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mp01home.launcher.data.apps.AppLauncher

/** MAIL-3: open inbox / compose. Gmail first, any mail app as fallback. */
class MailActions(private val context: Context, private val appLauncher: AppLauncher) {

    private fun installedGmail(): String? = GmailNotificationParser.GMAIL_PACKAGES.firstOrNull { pkg ->
        context.packageManager.getLaunchIntentForPackage(pkg) != null
    }

    /** Opens Gmail (through the DIS-1 launch path) or the default e-mail app. */
    fun openInbox(): Boolean {
        installedGmail()?.let { if (appLauncher.launch(it)) return true }
        val generic = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_EMAIL)
        return appLauncher.startActivitySafely(generic)
    }

    /** New message: `mailto:` to Gmail if installed, otherwise to whatever handles mailto. */
    fun compose(): Boolean {
        val mailto = Uri.parse("mailto:")
        installedGmail()?.let { pkg ->
            if (appLauncher.startActivitySafely(Intent(Intent.ACTION_SENDTO, mailto).setPackage(pkg))) return true
        }
        return appLauncher.startActivitySafely(Intent(Intent.ACTION_SENDTO, mailto))
    }
}
