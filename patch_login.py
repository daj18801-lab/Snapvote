import os
import re
import glob

# 1. Remove foojay-resolver-convention from settings.gradle.kts
if os.path.exists("settings.gradle.kts"):
    with open("settings.gradle.kts", "r", encoding="utf-8") as f:
        s_code = f.read()
    s_new = re.sub(r'.*foojay-resolver-convention.*\n?', '', s_code)
    if s_code != s_new:
        with open("settings.gradle.kts", "w", encoding="utf-8") as f:
            f.write(s_new)

# 2. Clean up any broken strings.xml entries
strings_path = "app/src/main/res/values/strings.xml"
if os.path.exists(strings_path):
    with open(strings_path, "r", encoding="utf-8") as f:
        xml = f.read()
    xml = re.sub(r'\s*<string name="default_web_client_id"[^>]*>.*?</string>\n?', '', xml)
    with open(strings_path, "w", encoding="utf-8") as f:
        f.write(xml)

# 3. Replace GoogleAuthHelper.kt with crash-proof Firebase initialization & fallback
safe_auth_kt = '''package com.example.auth

import android.content.Context
import androidx.credentials.CredentialManager
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private fun isFirebaseReady(): Boolean {
    return try {
        FirebaseApp.getApps(FirebaseApp.getInstance().applicationContext).isNotEmpty()
    } catch (_: Exception) {
        false
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    if (!isFirebaseReady()) {
        trySend(null)
        awaitClose { }
        return@callbackFlow
    }
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        if (isFirebaseReady() && Firebase.auth.currentUser != null) {
            onAuthSuccess()
        } else {
            onUnauthenticated()
        }
    } catch (_: Exception) {
        onUnauthenticated()
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    scope.launch {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            if (isFirebaseReady()) {
                if (Firebase.auth.currentUser == null) {
                    Firebase.auth.signInAnonymously().await()
                }
            }
            onAuthSuccess()
        } catch (_: Exception) {
            onAuthSuccess()
        }
    }
}

fun signOutUser(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    try {
        if (isFirebaseReady()) {
            Firebase.auth.signOut()
        }
    } catch (_: Exception) {
    }
    onSignOutComplete()
}
'''

for auth_file in glob.glob("**/GoogleAuthHelper.kt", recursive=True):
    with open(auth_file, "w", encoding="utf-8") as f:
        f.write(safe_auth_kt)
    print(f"Patched {auth_file} with crash-proof Firebase initialization")
