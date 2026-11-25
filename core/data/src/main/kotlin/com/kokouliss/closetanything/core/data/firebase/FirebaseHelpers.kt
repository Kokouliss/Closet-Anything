package com.kokouliss.closetanything.core.data.firebase

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.tasks.await

internal fun FirebaseAuth.userFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.IdTokenListener { trySend(it.currentUser) }
    addIdTokenListener(listener)
    awaitClose { removeIdTokenListener(listener) }
}.distinctUntilChangedBy { user -> user?.let { Triple(it.uid, it.displayName, it.isEmailVerified) } }

internal fun FirebaseAuth.requireUser(): FirebaseUser = checkNotNull(currentUser) { "No user is signed in" }

/** The name other members see next to this person's things. */
internal val FirebaseUser.memberName: String
    get() = displayName?.takeIf { it.isNotBlank() } ?: email?.substringBefore('@') ?: "?"

/** The address email invites are matched against. The security rules require it to be verified. */
internal val FirebaseUser.verifiedEmail: String?
    get() = email?.lowercase()?.takeIf { isEmailVerified }

/** The document as this device has it, or null when it has none. Never goes to the network, so it costs nothing. */
internal suspend fun DocumentReference.cached(): DocumentSnapshot? =
    try {
        get(Source.CACHE).await().takeIf { it.exists() }
    } catch (e: FirebaseFirestoreException) {
        null
    }

/**
 * Offline-first writes are not awaited: Firestore applies them on the device at once and syncs
 * later. A rejection (for example by the security rules) arrives much later, so it is only logged.
 */
internal fun Task<*>.logFailure(action: String) {
    addOnFailureListener { Log.w("ClosetAnythingSync", "Could not $action", it) }
}
