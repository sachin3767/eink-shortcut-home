package com.mp01home.launcher.distraction

import android.content.Context
import android.content.Intent
import com.mp01home.launcher.data.apps.AppLauncher
import com.mp01home.launcher.data.apps.LaunchInterceptor
import com.mp01home.launcher.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * DIS-1 / DIS-2 launch interceptor, installed as [AppLauncher.interceptor].
 *
 * For friction-flagged apps, or apps whose daily limit is reached, it opens
 * [FrictionGateActivity] instead of the app. The gate calls
 * `appLauncher.launch(pkg, bypassInterceptor = true)` once the pause + reason are done.
 * The flag/limit sets are mirrored into memory because [intercept] is synchronous.
 */
class DistractionGate(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val limitsRepository: AppLimitsRepository,
    private val usageRepository: UsageStatsRepository,
    private val appLauncher: AppLauncher
) : LaunchInterceptor {

    @Volatile private var frictionApps: Set<String> = emptySet()
    @Volatile private var limits: Map<String, Int> = emptyMap()

    /** Starts mirroring settings; call once (AppContainer init). */
    fun start(scope: CoroutineScope) {
        scope.launch {
            settingsRepository.settings.map { it.frictionApps }.distinctUntilChanged().collect { frictionApps = it }
        }
        scope.launch {
            limitsRepository.limits.collect { limits = it }
        }
    }

    override fun intercept(packageName: String): Boolean {
        val friction = frictionApps
        val currentLimits = limits
        if (packageName !in friction && (currentLimits[packageName] ?: 0) <= 0) return false
        val used = if ((currentLimits[packageName] ?: 0) > 0) usageRepository.usageTodayFor(packageName) else null
        return when (val decision = decideGate(packageName, friction, currentLimits, used)) {
            GateDecision.Allow -> false
            GateDecision.Friction -> showGate(packageName, null)
            is GateDecision.LimitReached -> showGate(packageName, decision)
        }
    }

    private fun showGate(packageName: String, limit: GateDecision.LimitReached?): Boolean {
        val intent = Intent(context, FrictionGateActivity::class.java)
            .putExtra(FrictionGateActivity.EXTRA_PACKAGE, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        if (limit != null) {
            intent.putExtra(FrictionGateActivity.EXTRA_LIMIT_MINUTES, limit.limitMinutes)
            intent.putExtra(FrictionGateActivity.EXTRA_USED_MILLIS, limit.usedMillis)
        }
        // If the gate itself cannot start, don't block the user: fall through to a normal launch.
        return appLauncher.startActivitySafely(intent)
    }
}
