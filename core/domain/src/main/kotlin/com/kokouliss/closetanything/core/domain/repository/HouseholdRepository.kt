package com.kokouliss.closetanything.core.domain.repository

import com.kokouliss.closetanything.core.domain.model.Household
import com.kokouliss.closetanything.core.domain.model.JoinResult
import kotlinx.coroutines.flow.Flow

interface HouseholdRepository {
    /** Households the signed-in user is a member of. */
    fun observeHouseholds(): Flow<List<Household>>

    /** Households that invited the signed-in user's email address and are waiting for an answer. */
    fun observeInvitations(): Flow<List<Household>>

    /** Emits null once the household is deleted or the user is no longer a member. */
    fun observeHousehold(householdId: String): Flow<Household?>

    suspend fun createHousehold(name: String)

    suspend fun renameHousehold(householdId: String, name: String)

    /** Needs a connection: a household cannot be read before its server accepts the new member. */
    suspend fun joinWithCode(code: String): JoinResult

    suspend fun acceptInvitation(householdId: String)

    suspend fun declineInvitation(householdId: String)

    /** [email] must already be normalized with `normalizeEmail`. */
    suspend fun inviteEmail(householdId: String, email: String)

    suspend fun cancelEmailInvite(householdId: String, email: String)

    /** Owner only. Also resets the invite code so the removed person cannot rejoin with the old one. */
    suspend fun removeMember(household: Household, memberId: String)

    /** Owner only. The old code stops working. */
    suspend fun resetInviteCode(household: Household)

    suspend fun leaveHousehold(householdId: String)

    /** Owner only. Deletes its rooms, spaces, things and photos as well. */
    suspend fun deleteHousehold(household: Household)
}
