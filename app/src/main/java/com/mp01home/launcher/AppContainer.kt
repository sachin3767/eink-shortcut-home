package com.mp01home.launcher

import android.app.Application
import com.mp01home.launcher.data.apps.AppLauncher
import com.mp01home.launcher.data.apps.DockDefaults
import com.mp01home.launcher.data.apps.InstalledAppsRepository
import com.mp01home.launcher.data.db.AppDatabase
import com.mp01home.launcher.data.settings.SettingsRepository
import com.mp01home.launcher.data.weather.WeatherCacheRepository
import com.mp01home.launcher.data.weather.WeatherRepository
import com.mp01home.launcher.data.weather.WeatherSyncable
import com.mp01home.launcher.mail.NotificationHandler
import com.mp01home.launcher.sync.SyncScheduler
import com.mp01home.launcher.sync.SyncStatusRepository
import com.mp01home.launcher.sync.Syncable
import com.mp01home.launcher.util.EinkRefreshController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manual dependency container, one per process, created in [MP01LauncherApp.onCreate].
 * Get it with `context.appContainer` (or `(application as MP01LauncherApp).container`).
 *
 * Feature agents: declare your repositories in YOUR reserved block below (keep edits inside
 * it so parallel changes merge cleanly) and register syncables / notification handlers from
 * an `init { }` block in the same place. Kotlin runs property initialisers and init blocks
 * top to bottom, so only depend on things declared above your block.
 */
class AppContainer(val app: Application) {

    /** Process-lifetime scope for fire-and-forget work (never cancelled). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ---- Registries (must stay above the feature blocks) ----------------------------------

    private val _syncables = mutableListOf<Syncable>()
    /** Everything [com.mp01home.launcher.sync.SyncWorker] runs, in registration order. */
    val syncables: List<Syncable> get() = _syncables.toList()
    fun registerSyncable(syncable: Syncable) {
        _syncables += syncable
    }

    private val _notificationHandlers = mutableListOf<NotificationHandler>()
    /** Everything [com.mp01home.launcher.mail.MailNotificationListener] fans out to. */
    val notificationHandlers: List<NotificationHandler> get() = _notificationHandlers.toList()
    fun registerNotificationHandler(handler: NotificationHandler) {
        _notificationHandlers += handler
    }

    // ---- Core ------------------------------------------------------------------------------

    val settingsRepository = SettingsRepository(app)
    val database: AppDatabase by lazy { AppDatabase.build(app) }
    val installedAppsRepository = InstalledAppsRepository(app)
    val appLauncher = AppLauncher(app)
    val einkRefresh = EinkRefreshController()
    val syncStatusRepository = SyncStatusRepository(app)
    val syncScheduler = SyncScheduler(app)

    // ---- Weather (EXT-1, OpenWeatherMap) --------------------------------------------------

    val weatherRepository = WeatherRepository()
    val weatherCacheRepository = WeatherCacheRepository(app)
    val weatherSyncable = WeatherSyncable(settingsRepository, weatherRepository, weatherCacheRepository)

    init {
        registerSyncable(weatherSyncable)
    }

    // =========================================================================================
    // FEATURE REPOSITORIES — one reserved block per feature. Edit only your own block.
    // =========================================================================================

    // ---- [calendar] CAL-* ------------------------------------------------------------------
    // Fully-qualified names keep this block free of shared import edits.
    val calendarRepository = com.mp01home.launcher.calendar.CalendarRepository(
        context = app,
        daoProvider = { database.cachedEventDao() }, // lazy: no DB open in Application.onCreate
        settingsRepository = settingsRepository,
        syncStatusRepository = syncStatusRepository,
        prefsRepository = com.mp01home.launcher.calendar.CalendarPrefsRepository(app)
    )

    init {
        registerSyncable(calendarRepository)
        // Launches the selection/prefs watchers and the provider ContentObserver (no I/O here).
        calendarRepository.start(appScope)
    }

    // ---- [end calendar] ---------------------------------------------------------------------

    // ---- [tasks] TASK-* ----------------------------------------------------------------------
    val tasksAuth = com.mp01home.launcher.tasks.GoogleTasksAuth(app)
    val tasksRepository = com.mp01home.launcher.tasks.TasksRepository(
        app, { database }, tasksAuth, settingsRepository, syncScheduler, appScope
    )
    /** TASK-3/4 actions for other features (quick capture, Top 3, reviews). */
    val tasksActions: com.mp01home.launcher.tasks.TasksActions get() = tasksRepository

    init {
        registerSyncable(tasksRepository)
        appScope.launch { tasksAuth.load() }
    }

    // ---- [end tasks] ------------------------------------------------------------------------

    // ---- [mail] MAIL-* -----------------------------------------------------------------------
    // Also hosts KEEP-1 actions and the FOC-4 digest (both notification/intent based).
    val mailRepository = com.mp01home.launcher.mail.MailRepository(
        database.mailNotificationDao(), syncStatusRepository, appScope
    )
    val mailActions = com.mp01home.launcher.mail.MailActions(app, appLauncher)
    val keepActions = com.mp01home.launcher.keep.KeepActions(app, appLauncher)
    val digestPrefs = com.mp01home.launcher.digest.DigestPrefs(app)
    val digestRepository = com.mp01home.launcher.digest.DigestRepository(
        app, database.digestNotificationDao(), settingsRepository, syncStatusRepository, digestPrefs, appScope
    )

    init {
        registerNotificationHandler(mailRepository)
        registerNotificationHandler(digestRepository)
    }

    // ---- [end mail] -------------------------------------------------------------------------

    // ---- [capture] CAP-*, KEEP-* -------------------------------------------------------------

    // ---- [end capture] ----------------------------------------------------------------------

    // ---- [focus] FOC-*, DIS-* ----------------------------------------------------------------
    // Fully-qualified names keep the shared import list untouched (parallel-merge friendly).

    val focusStateStore = com.mp01home.launcher.focus.FocusStateStore(app)
    /** FOC-1 Daily Top 3; also for PLAN-1/2 review screens (`setTop3(date, items)`). */
    val topPriorityRepository = com.mp01home.launcher.focus.TopPriorityRepository({ database }, focusStateStore)
    val dndController = com.mp01home.launcher.focus.DndController(app, focusStateStore)
    /** FOC-2/3 Pomodoro timer (persisted end time + WorkManager phase-end worker). */
    val focusTimerRepository = com.mp01home.launcher.focus.FocusTimerRepository(
        app, settingsRepository, focusStateStore, dndController, com.mp01home.launcher.focus.FocusNotifier(app)
    )
    /** DIS-2 per-app daily limits; DIS-2/3 usage stats. */
    val appLimitsRepository = com.mp01home.launcher.distraction.AppLimitsRepository(app)
    val usageStatsRepository = com.mp01home.launcher.distraction.UsageStatsRepository(app)
    /** DIS-1/DIS-2 friction gate, installed as the AppLauncher interceptor. */
    val distractionGate = com.mp01home.launcher.distraction.DistractionGate(
        app, settingsRepository, appLimitsRepository, usageStatsRepository, appLauncher
    )
    /** EXT-2 habit tracker (habits package, owned by the focus agent). */
    val habitRepository = com.mp01home.launcher.habits.HabitRepository { database }

    init {
        appLauncher.interceptor = distractionGate
        distractionGate.start(appScope)
        // Catch up a timer that ended while the process was dead (async, no I/O on this thread).
        appScope.launch { focusTimerRepository.reconcile() }
    }

    // ---- [end focus] ------------------------------------------------------------------------

    // ---- [planning] PLAN-* -------------------------------------------------------------------

    // ---- [end planning] ---------------------------------------------------------------------

    // ---- [extras] EXT-2/3, UX-5, CORE-7 ------------------------------------------------------
    // Fully-qualified names keep the shared import list untouched (parallel-merge friendly).

    /** EXT-3 reading apps, UX-5 sleep-screen toggle, UX-6 vendor refresh override. */
    val extrasStore = com.mp01home.launcher.eink.ExtrasStore(app)
    /** UX-5: renders agenda/tasks/Top 3 to the lock-screen wallpaper after each sync + on screen-off. */
    val sleepScreenUpdater = com.mp01home.launcher.eink.SleepScreenUpdater(
        app, { database }, settingsRepository, extrasStore, appScope
    )

    init {
        // Registered after the calendar/tasks syncables so it renders freshly synced data.
        registerSyncable(sleepScreenUpdater)
        sleepScreenUpdater.registerScreenOffTrigger()
        // UX-6: vendor full-refresh hooks follow the override setting (AUTO = Build.MANUFACTURER).
        appScope.launch {
            extrasStore.prefs
                .map { it.vendorRefreshMode }
                .distinctUntilChanged()
                .collect { einkRefresh.vendorHooks = com.mp01home.launcher.eink.VendorRefresh.hooksFor(it) }
        }
    }

    // ---- [end extras]-----------------------------------------------------------------------

    // =========================================================================================

    /** Called once from [MP01LauncherApp.onCreate]. */
    fun start() {
        appScope.launch {
            settingsRepository.ensureDockInitialized {
                withContext(Dispatchers.IO) { DockDefaults.resolve(app.packageManager) }
            }
        }
        appScope.launch {
            settingsRepository.settings
                .map { it.fullRefreshEveryN }
                .distinctUntilChanged()
                .collect { einkRefresh.fullRefreshEveryN = it }
        }
        syncScheduler.bindPeriodic(settingsRepository.settings, appScope)
        syncScheduler.registerScreenOnTrigger()
    }
}
