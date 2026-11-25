package com.kokouliss.closetanything.feature.household

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.kokouliss.closetanything.core.domain.model.Household
import com.kokouliss.closetanything.core.domain.model.InviteCode
import com.kokouliss.closetanything.core.domain.model.Member
import com.kokouliss.closetanything.core.domain.model.Room
import com.kokouliss.closetanything.core.domain.model.Space
import com.kokouliss.closetanything.core.ui.R as CoreR
import com.kokouliss.closetanything.core.ui.component.AppIcon
import com.kokouliss.closetanything.core.ui.component.AppIcons
import com.kokouliss.closetanything.core.ui.component.BackButton
import com.kokouliss.closetanything.core.ui.component.ConfirmDialog
import com.kokouliss.closetanything.core.ui.component.EmptyState
import com.kokouliss.closetanything.core.ui.component.LoadingCrossfade
import com.kokouliss.closetanything.core.ui.component.PopIn
import com.kokouliss.closetanything.core.ui.component.TextInputDialog
import com.kokouliss.closetanything.core.ui.component.UndoSnackbar
import com.kokouliss.closetanything.core.ui.component.destructiveMenuItemColors
import com.kokouliss.closetanything.core.ui.component.pageWidth
import com.kokouliss.closetanything.core.ui.theme.ClosetAnythingTheme
import kotlinx.coroutines.launch

private enum class HouseholdDialog { None, NewRoom, Rename, Leave, Delete, ResetCode }

@Composable
internal fun HouseholdRoute(
    viewModel: HouseholdViewModel,
    onOpen: (NavKey) -> Unit,
    onBack: () -> Unit,
    onGone: (message: String) -> Unit,
    notice: String?,
    onNoticeShown: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (uiState is HouseholdUiState.Gone) {
        LaunchedEffect(Unit) { onGone(resources.getString(R.string.feature_household_gone)) }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            scope.launch { snackbarHostState.showSnackbar(notice) }
            onNoticeShown()
        }
    }
    UndoSnackbar(viewModel.cancelledInvites, snackbarHostState) {
        resources.getString(R.string.feature_household_invite_cancelled, it)
    }

    HouseholdScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onOpen = onOpen,
        onCreateRoom = viewModel::createRoom,
        onRenameRoom = viewModel::renameRoom,
        onDeleteRoom = viewModel::deleteRoom,
        onCreateSpace = viewModel::createSpace,
        onRenameHousehold = viewModel::renameHousehold,
        onShareCode = { household ->
            val text = resources.getString(
                R.string.feature_household_share_text,
                household.name,
                household.inviteCode,
                InviteCode.link(household.inviteCode),
            )
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, null))
        },
        onResetCode = viewModel::resetInviteCode,
        onInviteEmail = viewModel::inviteEmail,
        onCancelEmailInvite = viewModel::cancelEmailInvite,
        onRemoveMember = viewModel::removeMember,
        onLeaveHousehold = viewModel::leaveHousehold,
        onDeleteHousehold = viewModel::deleteHousehold,
    )
}

@Composable
internal fun HouseholdScreen(
    uiState: HouseholdUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpen: (NavKey) -> Unit,
    onCreateRoom: (String) -> Unit,
    onRenameRoom: (roomId: String, name: String) -> Unit,
    onDeleteRoom: (roomId: String) -> Unit,
    onCreateSpace: (roomId: String, name: String) -> Unit,
    onRenameHousehold: (String) -> Unit,
    onShareCode: (Household) -> Unit,
    onResetCode: () -> Unit,
    onInviteEmail: (String) -> Unit,
    onCancelEmailInvite: (String) -> Unit,
    onRemoveMember: (memberId: String) -> Unit,
    onLeaveHousehold: () -> Unit,
    onDeleteHousehold: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf(HouseholdDialog.None) }
    var showMembers by rememberSaveable { mutableStateOf(false) }
    var memberToRemove by rememberSaveable { mutableStateOf<String?>(null) }
    // The room a dialog is about, one at a time.
    var newSpaceIn by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val ready = uiState as? HouseholdUiState.Ready
    val closeDialog = { dialog = HouseholdDialog.None }

    val title = ready?.household?.name.orEmpty()
    Scaffold(
        modifier = Modifier.semantics { paneTitle = title },
        // The open sheet covers this host, so it shows its own copy of the snackbar instead.
        snackbarHost = { if (!showMembers) SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (ready != null) {
                        IconButton(onClick = { onOpen(SearchKey(ready.household.id)) }) {
                            AppIcon(AppIcons.Search, stringResource(R.string.feature_household_search))
                        }
                        IconButton(onClick = { showMembers = true }) {
                            AppIcon(AppIcons.Group, stringResource(R.string.feature_household_members))
                        }
                        HouseholdMenu(isOwner = ready.isOwner, onSelect = { dialog = it })
                    }
                },
            )
        },
        floatingActionButton = {
            PopIn(ready != null && ready.rooms.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { dialog = HouseholdDialog.NewRoom },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { AppIcon(AppIcons.Add, null) },
                    text = { Text(stringResource(R.string.feature_household_new_room)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).pageWidth()) {
            LoadingCrossfade(ready == null) {
                if (ready == null) return@LoadingCrossfade
                if (ready.rooms.isEmpty()) {
                    EmptyState(
                        icon = AppIcons.Box,
                        title = stringResource(R.string.feature_household_rooms_empty_title),
                        body = stringResource(R.string.feature_household_rooms_empty_body),
                    ) {
                        Button(onClick = { dialog = HouseholdDialog.NewRoom }) {
                            Text(stringResource(R.string.feature_household_new_room))
                        }
                    }
                } else {
                    RoomList(
                        state = ready,
                        onOpenSpace = { onOpen(SpaceKey(ready.household.id, it)) },
                        onAddSpace = { newSpaceIn = it },
                        onRenameRoom = { renaming = it },
                        onDeleteRoom = { deleting = it },
                    )
                }
            }
        }
    }

    if (ready != null && showMembers) {
        MembersSheet(
            state = ready,
            snackbarHostState = snackbarHostState,
            onDismiss = { showMembers = false },
            onShareCode = { onShareCode(ready.household) },
            onResetCode = { dialog = HouseholdDialog.ResetCode },
            onInviteEmail = onInviteEmail,
            onCancelEmailInvite = onCancelEmailInvite,
            onRemoveMember = { memberToRemove = it },
        )
    }

    when (dialog) {
        HouseholdDialog.None -> Unit
        HouseholdDialog.NewRoom -> TextInputDialog(
            title = stringResource(R.string.feature_household_new_room),
            label = stringResource(R.string.feature_household_room_name),
            supportingText = stringResource(R.string.feature_household_room_name_hint),
            confirmLabel = stringResource(R.string.feature_household_create),
            onConfirm = {
                onCreateRoom(it)
                closeDialog()
            },
            onDismiss = closeDialog,
        )
        HouseholdDialog.Rename -> TextInputDialog(
            title = stringResource(R.string.feature_household_rename_household),
            label = stringResource(R.string.feature_household_name),
            confirmLabel = stringResource(CoreR.string.core_ui_save),
            initialValue = ready?.household?.name.orEmpty(),
            onConfirm = {
                onRenameHousehold(it)
                closeDialog()
            },
            onDismiss = closeDialog,
        )
        HouseholdDialog.Leave -> ConfirmDialog(
            title = stringResource(R.string.feature_household_leave_household),
            text = stringResource(R.string.feature_household_leave_confirm_text),
            confirmLabel = stringResource(R.string.feature_household_leave),
            onConfirm = {
                closeDialog()
                onLeaveHousehold()
            },
            onDismiss = closeDialog,
        )
        HouseholdDialog.Delete -> ConfirmDialog(
            title = stringResource(R.string.feature_household_delete_household),
            text = stringResource(R.string.feature_household_delete_confirm_text),
            confirmLabel = stringResource(CoreR.string.core_ui_delete),
            onConfirm = {
                closeDialog()
                onDeleteHousehold()
            },
            onDismiss = closeDialog,
        )
        HouseholdDialog.ResetCode -> ConfirmDialog(
            title = stringResource(R.string.feature_household_reset_code_confirm_title),
            text = stringResource(R.string.feature_household_reset_code_confirm_text),
            confirmLabel = stringResource(R.string.feature_household_reset_code),
            onConfirm = {
                closeDialog()
                onResetCode()
            },
            onDismiss = closeDialog,
        )
    }

    val roomForNewSpace = ready?.rooms?.firstOrNull { it.id == newSpaceIn }
    if (roomForNewSpace != null) {
        TextInputDialog(
            title = stringResource(R.string.feature_household_new_space_in, roomForNewSpace.name),
            label = stringResource(R.string.feature_household_space_name),
            supportingText = stringResource(R.string.feature_household_space_name_hint),
            confirmLabel = stringResource(R.string.feature_household_create),
            onConfirm = {
                onCreateSpace(roomForNewSpace.id, it)
                newSpaceIn = null
            },
            onDismiss = { newSpaceIn = null },
        )
    }
    val roomToRename = ready?.rooms?.firstOrNull { it.id == renaming }
    if (roomToRename != null) {
        TextInputDialog(
            title = stringResource(R.string.feature_household_rename_room),
            label = stringResource(R.string.feature_household_room_name),
            confirmLabel = stringResource(CoreR.string.core_ui_save),
            initialValue = roomToRename.name,
            onConfirm = {
                onRenameRoom(roomToRename.id, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
    val roomToDelete = ready?.rooms?.firstOrNull { it.id == deleting }
    if (roomToDelete != null) {
        ConfirmDialog(
            title = stringResource(R.string.feature_household_delete_room_title, roomToDelete.name),
            text = stringResource(R.string.feature_household_delete_room_text),
            confirmLabel = stringResource(CoreR.string.core_ui_delete),
            onConfirm = {
                deleting = null
                onDeleteRoom(roomToDelete.id)
            },
            onDismiss = { deleting = null },
        )
    }

    val removing = ready?.household?.members?.firstOrNull { it.id == memberToRemove }
    if (removing != null) {
        ConfirmDialog(
            title = stringResource(R.string.feature_household_remove_member, removing.name),
            text = stringResource(R.string.feature_household_remove_member_confirm_text, removing.name),
            confirmLabel = stringResource(R.string.feature_household_remove),
            onConfirm = {
                memberToRemove = null
                onRemoveMember(removing.id)
            },
            onDismiss = { memberToRemove = null },
        )
    }
}

/** The rooms, each followed by its spaces. */
@Composable
private fun RoomList(
    state: HouseholdUiState.Ready,
    onOpenSpace: (spaceId: String) -> Unit,
    onAddSpace: (roomId: String) -> Unit,
    onRenameRoom: (roomId: String) -> Unit,
    onDeleteRoom: (roomId: String) -> Unit,
) {
    LazyColumn(
        // Bottom room so the last row is not hidden behind the floating button.
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
    ) {
        state.rooms.forEach { room ->
            item(key = "room-${room.id}") {
                RoomHeader(
                    room = room,
                    onAddSpace = { onAddSpace(room.id) },
                    onRename = { onRenameRoom(room.id) },
                    onDelete = { onDeleteRoom(room.id) },
                    modifier = Modifier.animateItem(),
                )
            }
            val spaces = state.spaces.filter { it.roomId == room.id }
            if (spaces.isEmpty()) {
                item(key = "empty-${room.id}") {
                    Hint(stringResource(R.string.feature_household_spaces_empty), Modifier.animateItem().padding(horizontal = 16.dp, vertical = 8.dp))
                }
            }
            items(spaces, key = { it.id }) { space ->
                val count = state.thingCounts[space.id] ?: 0
                ListItem(
                    headlineContent = { Text(space.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = if (count == 0) {
                        null
                    } else {
                        { Text(pluralStringResource(R.plurals.feature_household_thing_count, count, count)) }
                    },
                    modifier = Modifier.animateItem().clickable(role = Role.Button) { onOpenSpace(space.id) },
                )
            }
        }
    }
}

@Composable
private fun RoomHeader(room: Room, onAddSpace: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SectionTitle(room.name, Modifier.weight(1f))
        IconButton(onClick = onAddSpace) {
            AppIcon(AppIcons.Add, stringResource(R.string.feature_household_new_space_in, room.name))
        }
        Box {
            IconButton(onClick = { expanded = true }) {
                AppIcon(AppIcons.MoreVert, stringResource(R.string.feature_household_room_options, room.name))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_household_rename_room)) },
                    leadingIcon = { AppIcon(AppIcons.Edit, null) },
                    onClick = {
                        expanded = false
                        onRename()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_household_delete_room)) },
                    leadingIcon = { AppIcon(AppIcons.Delete, null) },
                    colors = destructiveMenuItemColors(),
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun HouseholdMenu(isOwner: Boolean, onSelect: (HouseholdDialog) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val select = { target: HouseholdDialog ->
        expanded = false
        onSelect(target)
    }
    Box {
        IconButton(onClick = { expanded = true }) {
            AppIcon(AppIcons.MoreVert, stringResource(CoreR.string.core_ui_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (isOwner) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_household_rename_household)) },
                    leadingIcon = { AppIcon(AppIcons.Edit, null) },
                    onClick = { select(HouseholdDialog.Rename) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_household_delete_household)) },
                    leadingIcon = { AppIcon(AppIcons.Delete, null) },
                    colors = destructiveMenuItemColors(),
                    onClick = { select(HouseholdDialog.Delete) },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_household_leave_household)) },
                    leadingIcon = { AppIcon(AppIcons.Logout, null) },
                    onClick = { select(HouseholdDialog.Leave) },
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun HouseholdScreenPreview() {
    val home = Household("1", "Home", "u1", "ABCD2345", listOf(Member("u1", "Panos"), Member("u2", "Maria")), emptyList())
    ClosetAnythingTheme {
        HouseholdScreen(
            uiState = HouseholdUiState.Ready(
                household = home,
                rooms = listOf(Room("r1", "My room"), Room("r2", "Storage")),
                spaces = listOf(Space("s1", "r1", "Under the bed"), Space("s2", "r1", "Wardrobe")),
                thingCounts = mapOf("s1" to 3),
                currentUserId = "u1",
            ),
            snackbarHostState = SnackbarHostState(),
            onBack = {},
            onOpen = {},
            onCreateRoom = {},
            onRenameRoom = { _, _ -> },
            onDeleteRoom = {},
            onCreateSpace = { _, _ -> },
            onRenameHousehold = {},
            onShareCode = {},
            onResetCode = {},
            onInviteEmail = {},
            onCancelEmailInvite = {},
            onRemoveMember = {},
            onLeaveHousehold = {},
            onDeleteHousehold = {},
        )
    }
}
