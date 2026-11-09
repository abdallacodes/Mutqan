package com.example.mutqan.domain.sync

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

/** The signed-in Google account. */
data class Account(val uid: String, val email: String?, val name: String?)

/**
 * Sign-in with Google through Firebase Auth. It works only when the app was built with the Firebase project's
 * `google-services.json`; without it [isConfigured] is false and nothing else here is touched.
 */
class AccountManager(context: Context) {
    private val appContext = context.applicationContext

    /** The OAuth web client id that google-services generates from `google-services.json`, if there is one. */
    private val webClientId: String? = appContext.resources
        .getIdentifier("default_web_client_id", "string", appContext.packageName)
        .takeIf { it != 0 }
        ?.let { appContext.getString(it) }

    val isConfigured: Boolean = webClientId != null && FirebaseApp.getApps(appContext).isNotEmpty()

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account

    init {
        if (isConfigured) {
            val auth = FirebaseAuth.getInstance()
            _account.value = auth.currentUser?.toAccount()
            auth.addAuthStateListener { _account.value = it.currentUser?.toAccount() }
        }
    }

    /** Shows Google's account chooser. Returns the ID token of the account picked, or null when the user backs out; throws on a real failure. */
    private suspend fun googleIdToken(activity: Activity): String? {
        val clientId = webClientId ?: return null
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        val credential = try {
            CredentialManager.create(activity).getCredential(activity, request).credential
        } catch (_: GetCredentialCancellationException) {
            return null
        }
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw IllegalStateException("Unexpected credential type")
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }

    /** Shows Google's account chooser. Returns normally when the user backs out; throws on a real failure. */
    suspend fun signIn(activity: Activity) {
        val idToken = googleIdToken(activity) ?: return
        val result = FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        result.user?.let { _account.value = it.toAccount() }
    }

    /**
     * Asks Google again who the user is, before the account is deleted: Firebase deletes only an account that signed in
     * a moment ago, and a deletion should be confirmed by the owner anyway. False when the user backs out; throws when
     * the account picked is not the signed-in one, or on a real failure.
     */
    suspend fun confirmIdentity(activity: Activity): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        val idToken = googleIdToken(activity) ?: return false
        user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
        return true
    }

    /** Deletes the signed-in account itself (its cloud data has to be gone already) and forgets the Google sign-in. */
    suspend fun deleteAccount() {
        if (!isConfigured) return
        FirebaseAuth.getInstance().currentUser?.delete()?.await()
        runCatching { CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest()) }
        _account.value = null
    }

    suspend fun signOut() {
        if (!isConfigured) return
        FirebaseAuth.getInstance().signOut()
        runCatching { CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest()) }
    }

    private fun FirebaseUser.toAccount() = Account(uid, email, displayName)
}
