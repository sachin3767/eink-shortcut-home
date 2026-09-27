package com.mp01home.launcher.tasks

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.mp01home.launcher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Sign-in failed or the token could not be obtained without user interaction. */
class TasksAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * TASK-1: Google Sign-In with the `https://www.googleapis.com/auth/tasks` scope.
 *
 * - Sign-in uses play-services-auth [GoogleSignIn] with the "Web application" OAuth client id from
 *   `BuildConfig.GOOGLE_OAUTH_WEB_CLIENT_ID` (local.properties). The same Cloud project also needs an
 *   "Android" OAuth client for this package name + signing SHA-1.
 * - Access tokens come from [GoogleAuthUtil.getToken] on a background thread; Play services caches
 *   and refreshes them, so no token is persisted by the app.
 * - Only the signed-in account name is stored, in [EncryptedSharedPreferences].
 *
 * Construction is cheap (no I/O); call [load] once from a background coroutine.
 */
class GoogleTasksAuth(private val context: Context) {

    val clientId: String = BuildConfig.GOOGLE_OAUTH_WEB_CLIENT_ID

    /** False when no OAuth client id is configured at build time: sign-in is impossible. */
    val isConfigured: Boolean get() = clientId.isNotBlank()

    private val _accountName = MutableStateFlow<String?>(null)
    /** Signed-in Google account (email), or null. */
    val accountName: StateFlow<String?> = _accountName.asStateFlow()

    private val _consentIntent = MutableStateFlow<Intent?>(null)
    /**
     * Set when Google needs the user to (re-)grant the tasks scope. Launch it from an Activity,
     * then sync again.
     */
    val consentIntent: StateFlow<Intent?> = _consentIntent.asStateFlow()

    @Volatile
    private var loaded = false

    private val prefs: SharedPreferences? by lazy { openPrefs() }

    val isSignedIn: Boolean get() = _accountName.value != null

    /** Reads the stored account (disk + keystore work: call off the main thread). Idempotent. */
    suspend fun load(): Unit = withContext(Dispatchers.IO) {
        if (loaded) return@withContext
        _accountName.value = prefs?.getString(KEY_ACCOUNT, null)
        loaded = true
    }

    fun signInOptions(): GoogleSignInOptions {
        val builder = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(TASKS_SCOPE))
        if (isConfigured) builder.requestIdToken(clientId)
        return builder.build()
    }

    /** Intent for `rememberLauncherForActivityResult(StartActivityForResult())`. */
    fun signInIntent(): Intent = GoogleSignIn.getClient(context, signInOptions()).signInIntent

    /**
     * Completes sign-in from the activity result data. Returns the account email.
     * @throws TasksAuthException with a readable message on failure/cancel.
     */
    suspend fun completeSignIn(data: Intent?): String = withContext(Dispatchers.IO) {
        val account: GoogleSignInAccount? = try {
            GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
        } catch (e: ApiException) {
            throw TasksAuthException(describeStatus(e.statusCode), e)
        } catch (e: Exception) {
            throw TasksAuthException("Sign-in failed: ${e.message}", e)
        }
        if (account == null) throw TasksAuthException("Sign-in cancelled")
        val name = account.email ?: account.account?.name
            ?: throw TasksAuthException("Google account has no email")
        prefs?.edit()?.putString(KEY_ACCOUNT, name)?.apply()
        _accountName.value = name
        _consentIntent.value = null
        loaded = true
        name
    }

    /** Signs out of Google Sign-In and forgets the stored account. */
    suspend fun signOut(): Unit = withContext(Dispatchers.IO) {
        runCatching { GoogleSignIn.getClient(context, signInOptions()).signOut() }
        prefs?.edit()?.remove(KEY_ACCOUNT)?.apply()
        _accountName.value = null
        _consentIntent.value = null
    }

    /**
     * An OAuth access token for the tasks scope. Blocking Play-services call, run on IO.
     * @throws TasksAuthException when not signed in or user consent is needed (see [consentIntent])
     * @throws java.io.IOException on network trouble (retry later)
     */
    suspend fun accessToken(): String = withContext(Dispatchers.IO) {
        if (!loaded) load()
        val name = _accountName.value ?: throw TasksAuthException("Not signed in")
        try {
            GoogleAuthUtil.getToken(context, Account(name, ACCOUNT_TYPE), "oauth2:$TASKS_SCOPE").also {
                _consentIntent.value = null
            }
        } catch (e: UserRecoverableAuthException) {
            _consentIntent.value = e.intent
            throw TasksAuthException("Google Tasks access needs to be granted", e)
        } catch (e: GoogleAuthException) {
            throw TasksAuthException("Google auth error: ${e.message}", e)
        }
    }

    /** Drops a rejected token from Play services' cache so the next [accessToken] is fresh. */
    suspend fun invalidate(token: String): Unit = withContext(Dispatchers.IO) {
        try {
            GoogleAuthUtil.clearToken(context, token)
        } catch (e: Exception) {
            Log.w(TAG, "clearToken failed", e)
        }
    }

    private fun openPrefs(): SharedPreferences? = try {
        createEncryptedPrefs()
    } catch (e: Exception) {
        // Keystore/prefs corruption (e.g. restored from backup): start over rather than crash.
        Log.w(TAG, "encrypted prefs unreadable; resetting", e)
        context.deleteSharedPreferences(PREFS_NAME)
        try {
            createEncryptedPrefs()
        } catch (e2: Exception) {
            Log.e(TAG, "encrypted prefs unavailable", e2)
            null
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences = EncryptedSharedPreferences.create(
        PREFS_NAME,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun describeStatus(code: Int): String = when (code) {
        12501 -> "Sign-in cancelled"                        // SIGN_IN_CANCELLED
        12500 -> "Sign-in failed (check OAuth client setup)" // SIGN_IN_FAILED
        10 -> "Developer error: OAuth client id / SHA-1 mismatch"
        7 -> "No network connection"
        else -> "Sign-in failed (code $code)"
    }

    companion object {
        const val TASKS_SCOPE = "https://www.googleapis.com/auth/tasks"
        private const val ACCOUNT_TYPE = "com.google"
        private const val PREFS_NAME = "mp01_tasks_secure"
        private const val KEY_ACCOUNT = "account_name"
        private const val TAG = "GoogleTasksAuth"
    }
}
