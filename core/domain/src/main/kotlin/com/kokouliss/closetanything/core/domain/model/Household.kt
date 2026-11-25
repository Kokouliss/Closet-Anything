package com.kokouliss.closetanything.core.domain.model

/** The people who share one home's rooms and things: one person, a family or flatmates. */
data class Household(
    val id: String,
    val name: String,
    val ownerId: String,
    /** Anyone who has this code can join. The owner can reset it. */
    val inviteCode: String,
    val members: List<Member>,
    /** Lower-case email addresses that were invited but have not joined yet. */
    val invitedEmails: List<String>,
)

data class Member(
    val id: String,
    val name: String,
)

/** Outcome of trying to join a household with an invite code. */
enum class JoinResult { Joined, AlreadyMember, InvalidCode, Unavailable }

/** The longest name of a household, room, space or thing. The security rules enforce the same. */
const val MAX_NAME_LENGTH = 60
