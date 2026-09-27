package com.mp01home.launcher.ui.navigation

/** Navigation routes (MainActivity's NavHost). All transitions are disabled (EINK-1). */
object Routes {
    const val HOME = "home"
    const val APP_DRAWER = "app_drawer"
    const val SETTINGS = "settings"

    /** PLAN-1 — ui/review/MorningReviewScreen.kt */
    const val MORNING_REVIEW = "morning_review"

    /** PLAN-2 — ui/review/EveningReviewScreen.kt */
    const val EVENING_REVIEW = "evening_review"

    /** FOC-2 — ui/focus/FocusScreen.kt */
    const val FOCUS = "focus"
}
