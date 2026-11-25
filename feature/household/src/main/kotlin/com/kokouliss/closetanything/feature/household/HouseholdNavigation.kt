package com.kokouliss.closetanything.feature.household

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The signed-in start screen: the user's households and pending invitations. */
@Serializable
data object HouseholdsKey : NavKey

/** One household: its rooms with their spaces, its members and invites. */
@Serializable
data class HouseholdKey(val householdId: String) : NavKey

/** The things in one space, or, with [boxId], inside one box of that space. */
@Serializable
data class SpaceKey(val householdId: String, val spaceId: String, val boxId: String? = null) : NavKey

/** One thing to look at and edit, or, with [thingId] null, a new thing for [spaceId] (inside [boxId] when set). */
@Serializable
data class ThingKey(val householdId: String, val thingId: String? = null, val spaceId: String? = null, val boxId: String? = null) : NavKey

/** Finds things by name, note or tag across the household. */
@Serializable
data class SearchKey(val householdId: String) : NavKey

fun EntryProviderScope<NavKey>.householdEntries(
    /** Opens the given screen on top. */
    onOpen: (NavKey) -> Unit,
    onOpenProfile: () -> Unit,
    onBack: () -> Unit,
    /** The household was deleted or access to it ended: the screen closes and [message] goes on the one below. */
    onGone: (message: String) -> Unit,
    /** A message from a screen that closed itself, shown once by the screen now on top. */
    notice: String?,
    onNoticeShown: () -> Unit,
    /** The code of an invite link that opened the app. The households screen joins with it. */
    inviteCode: String?,
    onInviteHandled: () -> Unit,
) {
    entry<HouseholdsKey> {
        HouseholdsRoute(
            onOpen = onOpen,
            onOpenProfile = onOpenProfile,
            notice = notice,
            onNoticeShown = onNoticeShown,
            inviteCode = inviteCode,
            onInviteHandled = onInviteHandled,
        )
    }
    entry<HouseholdKey> { key ->
        HouseholdRoute(
            viewModel = hiltViewModel<HouseholdViewModel, HouseholdViewModel.Factory> { it.create(key) },
            onOpen = onOpen,
            onBack = onBack,
            onGone = onGone,
            notice = notice,
            onNoticeShown = onNoticeShown,
        )
    }
    entry<SpaceKey> { key ->
        SpaceRoute(
            viewModel = hiltViewModel<SpaceViewModel, SpaceViewModel.Factory> { it.create(key) },
            onOpen = onOpen,
            onBack = onBack,
        )
    }
    entry<ThingKey> { key ->
        ThingRoute(
            viewModel = hiltViewModel<ThingViewModel, ThingViewModel.Factory> { it.create(key) },
            onBack = onBack,
        )
    }
    entry<SearchKey> { key ->
        SearchRoute(
            viewModel = hiltViewModel<SearchViewModel, SearchViewModel.Factory> { it.create(key) },
            onOpen = onOpen,
            onBack = onBack,
        )
    }
}
