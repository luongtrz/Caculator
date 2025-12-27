package com.example.caculateapp.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.example.caculateapp.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * AuthManager - Manages Firebase Authentication
 * Handles Google Sign-in flow and auth state
 */
class AuthManager(private val context: Context) {
    
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val googleSignInClient: GoogleSignInClient
    
    init {
        // Configure Google Sign-in
        // NOTE: R.string.default_web_client_id is auto-generated from google-services.json
        // If you get "Unresolved reference" error, make sure you:
        // 1. Downloaded real google-services.json from Firebase Console
        // 2. Placed it in app/ folder
        // 3. Synced Gradle
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        
        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }
    
    /**
     * Get current signed-in user
     */
    fun getCurrentUser(): FirebaseUser? = auth.currentUser
    
    /**
     * Check if user is signed in
     */
    fun isSignedIn(): Boolean = getCurrentUser() != null
    
    /**
     * Get Intent for Google Sign-in
     * Use this with ActivityResultLauncher
     */
    fun getSignInIntent(): Intent = googleSignInClient.signInIntent
    
    /**
     * Handle Google Sign-in result
     * Call this from onActivityResult or ActivityResultLauncher callback
     */
    suspend fun handleSignInResult(data: Intent?): AuthResult {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            firebaseAuthWithGoogle(account)
        } catch (e: ApiException) {
            AuthResult.Error("Google sign-in failed: ${e.message}")
        } catch (e: Exception) {
            AuthResult.Error("Sign-in error: ${e.message}")
        }
    }
    
    /**
     * Authenticate with Firebase using Google account
     */
    private suspend fun firebaseAuthWithGoogle(account: GoogleSignInAccount): AuthResult {
        return try {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user
            
            if (user != null) {
                AuthResult.Success(user)
            } else {
                AuthResult.Error("Authentication failed: No user")
            }
        } catch (e: Exception) {
            AuthResult.Error("Firebase auth failed: ${e.message}")
        }
    }
    
    /**
     * Sign out from both Firebase and Google
     */
    suspend fun signOut() {
        auth.signOut()
        googleSignInClient.signOut().await()
    }
    
    /**
     * Add auth state listener
     */
    fun addAuthStateListener(listener: FirebaseAuth.AuthStateListener) {
        auth.addAuthStateListener(listener)
    }
    
    /**
     * Remove auth state listener
     */
    fun removeAuthStateListener(listener: FirebaseAuth.AuthStateListener) {
        auth.removeAuthStateListener(listener)
    }
}

/**
 * Auth result sealed class
 */
sealed class AuthResult {
    data class Success(val user: FirebaseUser) : AuthResult()
    data class Error(val message: String) : AuthResult()
}
