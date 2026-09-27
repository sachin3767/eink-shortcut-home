package com.mp01home.launcher.keep

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.util.Log
import com.mp01home.launcher.data.apps.AppLauncher

/**
 * KEEP-1: one-tap "new note" / "new checklist" in Google Keep (no consumer API, decision D2).
 *
 * Order of attempts:
 * 1. Keep's own app shortcut via LauncherApps (works while we are the default launcher).
 * 2. Note only: `ACTION_SEND` text/plain to Keep (opens Keep's share-to-note editor).
 * 3. Launch Keep.
 */
class KeepActions(private val context: Context, private val appLauncher: AppLauncher) {

    fun isInstalled(): Boolean = context.packageManager.getLaunchIntentForPackage(KEEP_PACKAGE) != null

    fun newNote(): Boolean = create(KeepItemKind.NOTE)

    fun newList(): Boolean = create(KeepItemKind.LIST)

    /** Share arbitrary text into a new Keep note (usable by quick capture, KEEP-2). */
    fun shareText(text: String, title: String? = null): Boolean {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .setPackage(KEEP_PACKAGE)
            .putExtra(Intent.EXTRA_TEXT, text)
        if (!title.isNullOrBlank()) intent.putExtra(Intent.EXTRA_SUBJECT, title)
        return appLauncher.startActivitySafely(intent)
    }

    private fun create(kind: KeepItemKind): Boolean {
        if (startShortcut(kind)) return true
        if (kind == KeepItemKind.NOTE && shareText("")) return true
        return appLauncher.launch(KEEP_PACKAGE)
    }

    private fun startShortcut(kind: KeepItemKind): Boolean = try {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        if (launcherApps == null || !launcherApps.hasShortcutHostPermission()) {
            false
        } else {
            val query = LauncherApps.ShortcutQuery()
                .setPackage(KEEP_PACKAGE)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            val user = Process.myUserHandle()
            val shortcuts = launcherApps.getShortcuts(query, user).orEmpty()
            val candidates = shortcuts.filter { it.isEnabled }.map {
                KeepShortcutCandidate(id = it.id, label = it.shortLabel?.toString())
            }
            val picked = KeepShortcutMatcher.pick(kind, candidates)
            if (picked == null) {
                false
            } else {
                launcherApps.startShortcut(KEEP_PACKAGE, picked.id, null, null, user)
                true
            }
        }
    } catch (e: Exception) {
        // SecurityException (not default launcher), IllegalStateException (user locked), …
        Log.w(TAG, "Keep shortcut failed", e)
        false
    }

    companion object {
        const val KEEP_PACKAGE = "com.google.android.keep"
        private const val TAG = "KeepActions"
    }
}
