package com.kokouliss.closetanything.feature.household

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.kokouliss.closetanything.core.domain.model.Household
import com.kokouliss.closetanything.core.domain.model.InviteCode
import com.kokouliss.closetanything.core.domain.model.JoinResult
import com.kokouliss.closetanything.core.domain.model.Member
import com.kokouliss.closetanything.core.ui.R as CoreR
import com.kokouliss.closetanything.core.ui.component.AppIcon
import com.kokouliss.closetanything.core.ui.component.AppIcons
import com.kokouliss.closetanything.core.ui.component.EmptyState
import com.kokouliss.closetanything.core.ui.component.InitialAvatar
import com.kokouliss.closetanything.core.ui.component.LoadingCrossfade
import com.kokouliss.closetanything.core.ui.component.PopIn
import com.kokouliss.closetanything.core.ui.component.TextInputDialog
import com.kokouliss.closetanything.core.ui.component.UndoSnackbar
import com.kokouliss.closetanything.core.ui.component.pageWidth
import com.kokouliss.closetanything.core.ui.theme.ClosetAnythingTheme
import kotlinx.coroutines.launch

private enum class HouseholdsDialog { None, Create, Join }

@Composable
internal fun HouseholdsRoute(
    onOpen: (NavKey) -> Unit,
    onOpenProfile: () -> Unit,
    notice: String?,
    onNoticeShown: () -> Unit,
    inviteCode: String?,
    onInviteHandled: () -> Unit,
    viewModel: HouseholdsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val showMessage = { message: Int ->
        scope.launch { snackbarHostState.showSnackbar(resources.getString(message)) }
    }

    // An invite link opened the app, so join right away. The result arrives like any other join.
    LaunchedEffect(inviteCode) {
        if (inviteCode != null) {
            viewModel.joinWithCode(inviteCode)
            onInviteHandled()
        }
    }

    // One household and nothing to decide: straight into it, once per visit to this screen.
    var autoOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState) {
        val ready = uiState as? HouseholdsUiState.Ready ?: return@LaunchedEffect
        if (!autoOpened && inviteCode == null && ready.households.size == 1 && ready.invitations.isEmpty()) {
            autoOpened = true
            onOpen(HouseholdKey(ready.households.single().id))
        }
    }

    UndoSnackbar(viewModel.declines, snackbarHostState) {
        resources.getString(R.string.feature_household_invitation_declined, it.name)
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            scope.launch { snackbarHostState.showSnackbar(notice) }
            onNoticeShown()
        }
    }

    HouseholdsScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onOpenHousehold = { onOpen(HouseholdKey(it)) },
        onCreateHousehold = viewModel::createHousehold,
        onJoinWithCode = viewModel::joinWithCode,
        onJoinShown = viewModel::joinShown,
        onScanCode = {
            // The camera screen comes from Google Play services, so the app needs no camera permission.
            val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
            GmsBarcodeScanning.getClient(context, options).startScan()
                .addOnSuccessListener { barcode ->
                    val code = barcode.rawValue?.let(InviteCode::fromLink)
                    if (code != null) viewModel.joinWithCode(code) else showMessage(R.string.feature_household_scan_not_invite)
                }
                // Closing the scanner is reported as a cancel, not as a failure.
                .addOnFailureListener { showMessage(R.string.feature_household_scan_failed) }
        },
        onAcceptInvitation = viewModel::acceptInvitation,
        onDeclineInvitation = viewModel::declineInvitation,
        onOpenProfile = onOpenProfile,
        onRetry = viewModel::retry,
    )
}

@Composable
internal fun HouseholdsScreen(
    uiState: HouseholdsUiState,
    snackbarHostState: SnackbarHostState,
    onOpenHousehold: (String) -> Unit,
    onCreateHousehold: (String) -> Unit,
    onJoinWithCode: (String) -> Unit,
    /** The result of a join has been shown; the next state is Idle. */
    onJoinShown: () -> Unit,
    onScanCode: () -> Unit,
    onAcceptInvitation: (String) -> Unit,
    onDeclineInvitation: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onRetry: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf(HouseholdsDialog.None) }
    val ready = uiState as? HouseholdsUiState.Ready
    val title = stringResource(R.string.feature_household_title)
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    val join = ready?.join
    LaunchedEffect(join) {
        if (join !is JoinState.Done) return@LaunchedEffect
        // A code that did not work stays in the dialog, with the reason under the field.
        if (dialog == HouseholdsDialog.Join && join.result.failed) return@LaunchedEffect
        if (dialog == HouseholdsDialog.Join) dialog = HouseholdsDialog.None
        onJoinShown()
        scope.launch { snackbarHostState.showSnackbar(resources.getString(join.result.message)) }
    }

    Scaffold(
        modifier = Modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                actions = {
                    if (ready != null) {
                        IconButton(onClick = onScanCode) {
                            AppIcon(AppIcons.QrCodeScanner, stringResource(R.string.feature_household_scan_code))
                        }
                        IconButton(onClick = { dialog = HouseholdsDialog.Join }) {
                            AppIcon(AppIcons.GroupAdd, stringResource(R.string.feature_household_join_with_code))
                        }
                        // The avatar itself is decorative, so the button carries the name.
                        val profileLabel = stringResource(R.string.feature_household_account)
                        IconButton(
                            onClick = onOpenProfile,
                            modifier = Modifier.semantics { contentDescription = profileLabel },
                        ) {
                            InitialAvatar(ready.userName, size = 32.dp)
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            PopIn(ready != null && (ready.households.isNotEmpty() || ready.invitations.isNotEmpty())) {
                ExtendedFloatingActionButton(
                    onClick = { dialog = HouseholdsDialog.Create },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { AppIcon(AppIcons.Add, null) },
                    text = { Text(stringResource(R.string.feature_household_new_household)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).pageWidth()) {
            LoadingCrossfade(uiState == HouseholdsUiState.Loading) {
                when (uiState) {
                    HouseholdsUiState.Loading -> Unit
                    HouseholdsUiState.Error -> EmptyState(
                        icon = AppIcons.Group,
                        title = stringResource(R.string.feature_household_error_title),
                        body = stringResource(CoreR.string.core_ui_error_generic),
                    ) {
                        Button(onClick = onRetry) { Text(stringResource(R.string.feature_household_retry)) }
                    }
                    is HouseholdsUiState.Ready -> {
                        if (uiState.households.isEmpty() && uiState.invitations.isEmpty()) {
                            EmptyState(
                                icon = AppIcons.Group,
                                title = stringResource(R.string.feature_household_empty_title),
                                body = stringResource(R.string.feature_household_empty_body),
                            ) {
                                Button(onClick = { dialog = HouseholdsDialog.Create }) {
                                    Text(stringResource(R.string.feature_household_new_household))
                                }
                                OutlinedButton(onClick = { dialog = HouseholdsDialog.Join }) {
                                    Text(stringResource(R.string.feature_household_join_with_code))
                                }
                                TextButton(onClick = onScanCode) {
                                    Text(stringResource(R.string.feature_household_scan_code))
                                }
                            }
                        } else {
                            HouseholdList(uiState, onOpenHousehold, onAcceptInvitation, onDeclineInvitation)
                        }
                        if (uiState.join == JoinState.Joining) {
                            val joining = stringResource(R.string.feature_household_joining)
                            LinearProgressIndicator(
                                Modifier.fillMaxWidth().semantics {
                                    contentDescription = joining
                                    liveRegion = LiveRegionMode.Polite
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    when (dialog) {
        HouseholdsDialog.None -> Unit
        HouseholdsDialog.Create -> TextInputDialog(
            title = stringResource(R.string.feature_household_new_household),
            label = stringResource(R.string.feature_household_name),
            supportingText = stringResource(R.string.feature_household_name_hint),
            confirmLabel = stringResource(R.string.feature_household_create),
            onConfirm = {
                onCreateHousehold(it)
                dialog = HouseholdsDialog.None
            },
            onDismiss = { dialog = HouseholdsDialog.None },
        )
        HouseholdsDialog.Join -> TextInputDialog(
            title = stringResource(R.string.feature_household_join_title),
            label = stringResource(R.string.feature_household_invite_code),
            confirmLabel = stringResource(R.string.feature_household_join),
            capitalization = KeyboardCapitalization.Characters,
            maxLength = 12,
            isBusy = join == JoinState.Joining,
            errorText = (join as? JoinState.Done)?.result?.takeIf { it.failed }?.let { stringResource(it.message) },
            onConfirm = onJoinWithCode,
            onDismiss = {
                dialog = HouseholdsDialog.None
                onJoinShown()
            },
        )
    }
}

@Composable
private fun HouseholdList(
    state: HouseholdsUiState.Ready,
    onOpenHousehold: (String) -> Unit,
    onAcceptInvitation: (String) -> Unit,
    onDeclineInvitation: (String) -> Unit,
) {
    LazyColumn(
        // Bottom room so the last card is not hidden behind the floating button.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.invitations, key = { "invite-${it.id}" }) { household ->
            InvitationCard(household, onAccept = { onAcceptInvitation(household.id) }, onDecline = { onDeclineInvitation(household.id) })
        }
        items(state.households, key = { it.id }) { household ->
            ElevatedCard(
                onClick = { onOpenHousehold(household.id) },
                modifier = Modifier.animateItem().fillMaxWidth().semantics { role = Role.Button },
            ) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { AppIcon(AppIcons.Group, null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = {
                        Text(household.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(pluralStringResource(R.plurals.feature_household_member_count, household.members.size, household.members.size))
                    },
                )
            }
        }
    }
}

private val JoinResult.failed: Boolean
    get() = this == JoinResult.InvalidCode || this == JoinResult.Unavailable

private val JoinResult.message: Int
    get() = when (this) {
        JoinResult.Joined -> R.string.feature_household_join_done
        JoinResult.AlreadyMember -> R.string.feature_household_join_already_member
        JoinResult.InvalidCode -> R.string.feature_household_join_invalid_code
        JoinResult.Unavailable -> R.string.feature_household_join_unavailable
    }

@Composable
private fun InvitationCard(household: Household, onAccept: () -> Unit, onDecline: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.feature_household_invitation_title, household.name),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                pluralStringResource(R.plurals.feature_household_member_count, household.members.size, household.members.size),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                // Every card has these two buttons, so each one names its household for a screen reader.
                val declineLabel = stringResource(R.string.feature_household_decline_household, household.name)
                val acceptLabel = stringResource(R.string.feature_household_accept_household, household.name)
                TextButton(onClick = onDecline, modifier = Modifier.semantics { contentDescription = declineLabel }) {
                    Text(stringResource(R.string.feature_household_decline))
                }
                Button(onClick = onAccept, modifier = Modifier.semantics { contentDescription = acceptLabel }) {
                    Text(stringResource(R.string.feature_household_accept))
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun HouseholdsScreenPreview() {
    val home = Household("1", "Home", "u1", "ABCD2345", listOf(Member("u1", "Panos"), Member("u2", "Maria")), emptyList())
    ClosetAnythingTheme {
        HouseholdsScreen(
            uiState = HouseholdsUiState.Ready(
                userName = "Panos",
                households = listOf(home),
                invitations = listOf(home.copy(id = "2", name = "Flat in Chania")),
                join = JoinState.Idle,
            ),
            snackbarHostState = SnackbarHostState(),
            onOpenHousehold = {},
            onCreateHousehold = {},
            onJoinWithCode = {},
            onJoinShown = {},
            onScanCode = {},
            onAcceptInvitation = {},
            onDeclineInvitation = {},
            onOpenProfile = {},
            onRetry = {},
        )
    }
}
