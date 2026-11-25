package com.kokouliss.closetanything.core.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.WriteBatch
import com.google.firebase.firestore.snapshots
import com.kokouliss.closetanything.core.domain.model.Household
import com.kokouliss.closetanything.core.domain.model.InviteCode
import com.kokouliss.closetanything.core.domain.model.JoinResult
import com.kokouliss.closetanything.core.domain.repository.HouseholdRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

internal class FirestoreHouseholdRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : HouseholdRepository {

    private val households get() = db.collection(Collections.HOUSEHOLDS)
    private val invites get() = db.collection(Collections.INVITES)

    override fun observeHouseholds(): Flow<List<Household>> = auth.userFlow().flatMapLatest { user ->
        if (user == null) {
            flowOf(emptyList())
        } else {
            households.whereArrayContains(Fields.MEMBER_IDS, user.uid).snapshots().map { snapshot ->
                snapshot.documents.mapNotNull { it.toHousehold() }.sortedBy { it.name.lowercase() }
            }
        }
    }

    override fun observeInvitations(): Flow<List<Household>> = auth.userFlow().flatMapLatest { user ->
        val email = user?.verifiedEmail
        if (email == null) {
            flowOf(emptyList())
        } else {
            households.whereArrayContains(Fields.INVITED_EMAILS, email).snapshots().map { snapshot ->
                snapshot.documents.mapNotNull { it.toHousehold() }
            }
        }
    }

    override fun observeHousehold(householdId: String): Flow<Household?> =
        households.document(householdId).snapshots()
            .map { it.toHousehold()?.takeIf { household -> household.members.any { m -> m.id == auth.currentUser?.uid } } }
            // Being removed from a household ends the listener with "permission denied".
            .catch { e -> if (e is FirebaseFirestoreException) emit(null) else throw e }

    override suspend fun createHousehold(name: String) {
        val user = auth.requireUser()
        val household = households.document()
        val code = InviteCode.generate()
        db.batch()
            .set(
                household,
                mapOf(
                    Fields.NAME to name,
                    Fields.OWNER_ID to user.uid,
                    Fields.MEMBER_IDS to listOf(user.uid),
                    Fields.MEMBERS to mapOf(user.uid to mapOf(Fields.NAME to user.memberName)),
                    Fields.INVITE_CODE to code,
                    Fields.INVITED_EMAILS to emptyList<String>(),
                    Fields.CREATED_AT to FieldValue.serverTimestamp(),
                ),
            )
            .set(invites.document(code), mapOf(Fields.HOUSEHOLD_ID to household.id))
            .commit()
            .logFailure("create household")
    }

    override suspend fun renameHousehold(householdId: String, name: String) {
        households.document(householdId).update(Fields.NAME, name).logFailure("rename household")
    }

    override suspend fun joinWithCode(code: String): JoinResult {
        val user = auth.requireUser()
        val normalized = InviteCode.normalize(code)
        if (!InviteCode.isValid(normalized)) return JoinResult.InvalidCode

        return withTimeoutOrNull(JOIN_TIMEOUT_MS) {
            try {
                // Filtered on the device: a second server-side filter would need a composite index.
                val mine = households.whereArrayContains(Fields.MEMBER_IDS, user.uid).get().await()
                if (mine.documents.any { it.getString(Fields.INVITE_CODE) == normalized }) {
                    return@withTimeoutOrNull JoinResult.AlreadyMember
                }
                val householdId = invites.document(normalized).get().await().getString(Fields.HOUSEHOLD_ID)
                    ?: return@withTimeoutOrNull JoinResult.InvalidCode
                // The rules only accept a new member entry that carries the household's current invite code.
                households.document(householdId).update(
                    FieldPath.of(Fields.MEMBER_IDS), FieldValue.arrayUnion(user.uid),
                    FieldPath.of(Fields.MEMBERS, user.uid),
                    mapOf(Fields.NAME to user.memberName, Fields.INVITE_CODE to normalized),
                ).await()
                JoinResult.Joined
            } catch (e: FirebaseFirestoreException) {
                when (e.code) {
                    // The code was reset or the household was deleted after the invite was looked up.
                    Code.PERMISSION_DENIED, Code.NOT_FOUND -> JoinResult.InvalidCode
                    else -> JoinResult.Unavailable
                }
            }
        } ?: JoinResult.Unavailable
    }

    override suspend fun acceptInvitation(householdId: String) {
        val user = auth.requireUser()
        // Invitations are only listed for a verified email, so there is nothing to accept without one.
        val email = user.verifiedEmail ?: return
        households.document(householdId).update(
            FieldPath.of(Fields.MEMBER_IDS), FieldValue.arrayUnion(user.uid),
            FieldPath.of(Fields.MEMBERS, user.uid), mapOf(Fields.NAME to user.memberName),
            FieldPath.of(Fields.INVITED_EMAILS), FieldValue.arrayRemove(email),
        ).logFailure("accept invitation")
    }

    override suspend fun declineInvitation(householdId: String) {
        val email = auth.requireUser().verifiedEmail ?: return
        households.document(householdId)
            .update(Fields.INVITED_EMAILS, FieldValue.arrayRemove(email))
            .logFailure("decline invitation")
    }

    override suspend fun inviteEmail(householdId: String, email: String) {
        households.document(householdId)
            .update(Fields.INVITED_EMAILS, FieldValue.arrayUnion(email))
            .logFailure("invite email")
    }

    override suspend fun cancelEmailInvite(householdId: String, email: String) {
        households.document(householdId)
            .update(Fields.INVITED_EMAILS, FieldValue.arrayRemove(email))
            .logFailure("cancel email invite")
    }

    override suspend fun removeMember(household: Household, memberId: String) {
        val newCode = InviteCode.generate()
        db.batch()
            .update(
                households.document(household.id),
                FieldPath.of(Fields.MEMBER_IDS), FieldValue.arrayRemove(memberId),
                FieldPath.of(Fields.MEMBERS, memberId), FieldValue.delete(),
                FieldPath.of(Fields.INVITE_CODE), newCode,
            )
            .replaceInvite(household, newCode)
            .commit()
            .logFailure("remove member")
    }

    override suspend fun resetInviteCode(household: Household) {
        val newCode = InviteCode.generate()
        db.batch()
            .update(households.document(household.id), Fields.INVITE_CODE, newCode)
            .replaceInvite(household, newCode)
            .commit()
            .logFailure("reset invite code")
    }

    override suspend fun leaveHousehold(householdId: String) {
        val user = auth.requireUser()
        households.document(householdId).update(
            FieldPath.of(Fields.MEMBER_IDS), FieldValue.arrayRemove(user.uid),
            FieldPath.of(Fields.MEMBERS, user.uid), FieldValue.delete(),
        ).logFailure("leave household")
    }

    override suspend fun deleteHousehold(household: Household) {
        // shortcut: the rooms, spaces and things are deleted from the phone, which only knows the ones
        // it has in its cache while offline. Move this to a Cloud Function if the app ever gets a billing account.
        val home = households.document(household.id)
        try {
            db.deleteThings(home.collection(Collections.THINGS).get().await().documents.map { it.reference })
            val places = home.collection(Collections.SPACES).get().await().documents + home.collection(Collections.ROOMS).get().await().documents
            places.chunked(400).forEach { chunk ->
                db.batch().also { batch -> chunk.forEach { batch.delete(it.reference) } }.commit().logFailure("delete rooms and spaces")
            }
        } catch (e: FirebaseFirestoreException) {
            // Offline with nothing cached: the household itself still goes.
        }
        // Last, because the rules let members delete what is under the household only while it exists.
        db.batch()
            .delete(invites.document(household.inviteCode))
            .delete(home)
            .commit()
            .logFailure("delete household")
    }

    /** The households the signed-in user is in, read once from the server. */
    internal suspend fun loadMyHouseholds(): List<Household> =
        households.whereArrayContains(Fields.MEMBER_IDS, auth.requireUser().uid).get().await().documents.mapNotNull { it.toHousehold() }

    /** Shows [name] for the signed-in user in the member list of each of their households. */
    internal fun renameMe(name: String) {
        val uid = auth.requireUser().uid
        households.whereArrayContains(Fields.MEMBER_IDS, uid).get()
            .addOnSuccessListener { mine ->
                mine.documents.forEach {
                    it.reference.update(FieldPath.of(Fields.MEMBERS, uid, Fields.NAME), name).logFailure("rename the member")
                }
            }
            .logFailure("find the households to rename the member in")
    }

    /**
     * Takes the signed-in user out of [household] for good, waiting for the server: a member leaves, the
     * owner hands the household to its next member, and the last person in it deletes it.
     */
    internal suspend fun leaveForGood(household: Household) {
        val uid = auth.requireUser().uid
        val next = household.members.firstOrNull { it.id != uid }
        when {
            next == null -> db.batch()
                .delete(invites.document(household.inviteCode))
                .delete(households.document(household.id))
                .commit().await()
            household.ownerId == uid -> households.document(household.id).update(
                FieldPath.of(Fields.OWNER_ID), next.id,
                FieldPath.of(Fields.MEMBER_IDS), FieldValue.arrayRemove(uid),
                FieldPath.of(Fields.MEMBERS, uid), FieldValue.delete(),
            ).await()
            else -> households.document(household.id).update(
                FieldPath.of(Fields.MEMBER_IDS), FieldValue.arrayRemove(uid),
                FieldPath.of(Fields.MEMBERS, uid), FieldValue.delete(),
            ).await()
        }
    }

    private fun WriteBatch.replaceInvite(household: Household, newCode: String): WriteBatch =
        delete(invites.document(household.inviteCode))
            .set(invites.document(newCode), mapOf(Fields.HOUSEHOLD_ID to household.id))

    private companion object {
        const val JOIN_TIMEOUT_MS = 20_000L
    }
}
