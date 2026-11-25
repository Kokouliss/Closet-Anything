package com.kokouliss.closetanything.feature.household

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.kokouliss.closetanything.core.domain.model.Room
import com.kokouliss.closetanything.core.domain.model.Space
import com.kokouliss.closetanything.core.domain.model.Thing
import com.kokouliss.closetanything.core.ui.R as CoreR
import com.kokouliss.closetanything.core.ui.component.AppIcon
import com.kokouliss.closetanything.core.ui.component.AppIcons
import com.kokouliss.closetanything.core.ui.component.BackButton
import com.kokouliss.closetanything.core.ui.component.ConfirmDialog
import com.kokouliss.closetanything.core.ui.component.EmptyState
import com.kokouliss.closetanything.core.ui.component.LoadingCrossfade
import com.kokouliss.closetanything.core.ui.component.PopIn
import com.kokouliss.closetanything.core.ui.component.TextInputDialog
import com.kokouliss.closetanything.core.ui.component.destructiveMenuItemColors
import com.kokouliss.closetanything.core.ui.component.pageWidth
import com.kokouliss.closetanything.core.ui.theme.ClosetAnythingTheme

private enum class SpaceDialog { None, Rename, Delete }

@Composable
internal fun SpaceRoute(viewModel: SpaceViewModel, onOpen: (NavKey) -> Unit, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (uiState is SpaceUiState.Gone) {
        LaunchedEffect(Unit) { onBack() }
    }
    SpaceScreen(
        uiState = uiState,
        onBack = onBack,
        onOpen = onOpen,
        onRenameSpace = viewModel::renameSpace,
        onDeleteSpace = viewModel::deleteSpace,
    )
}

@Composable
internal fun SpaceScreen(
    uiState: SpaceUiState,
    onBack: () -> Unit,
    onOpen: (NavKey) -> Unit,
    onRenameSpace: (String) -> Unit,
    onDeleteSpace: () -> Unit,
) {
    var dialog by rememberSaveable { mutableStateOf(SpaceDialog.None) }
    val ready = uiState as? SpaceUiState.Ready
    val closeDialog = { dialog = SpaceDialog.None }
    val title = ready?.title.orEmpty()

    Scaffold(
        modifier = Modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (ready != null && ready.path.isNotEmpty()) Hint(ready.path.asPath(), maxLines = 1)
                    }
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    val box = ready?.box
                    when {
                        ready == null -> Unit
                        // A box is a thing: its name, photo, place and deletion are edited like any other.
                        box != null -> IconButton(onClick = { onOpen(ThingKey(ready.householdId, thingId = box.id)) }) {
                            AppIcon(AppIcons.Edit, stringResource(R.string.feature_household_edit_box))
                        }
                        else -> SpaceMenu(onSelect = { dialog = it })
                    }
                },
            )
        },
        floatingActionButton = {
            PopIn(ready != null && ready.things.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { ready?.let { onOpen(ThingKey(it.householdId, spaceId = it.space.id, boxId = it.box?.id)) } },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { AppIcon(AppIcons.Add, null) },
                    text = { Text(stringResource(R.string.feature_household_add_thing)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).pageWidth()) {
            LoadingCrossfade(ready == null) {
                if (ready == null) return@LoadingCrossfade
                if (ready.things.isEmpty()) {
                    EmptyState(
                        icon = if (ready.box != null) AppIcons.Box else AppIcons.Image,
                        title = stringResource(R.string.feature_household_things_empty_title),
                        body = stringResource(
                            if (ready.box != null) R.string.feature_household_box_empty_body else R.string.feature_household_things_empty_body,
                        ),
                    ) {
                        Button(onClick = { onOpen(ThingKey(ready.householdId, spaceId = ready.space.id, boxId = ready.box?.id)) }) {
                            Text(stringResource(R.string.feature_household_add_thing))
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
                        items(ready.things, key = { it.id }) { thing ->
                            ThingRow(
                                thing = thing,
                                modifier = Modifier.animateItem(),
                                onClick = {
                                    if (thing.isBox) {
                                        onOpen(SpaceKey(ready.householdId, ready.space.id, boxId = thing.id))
                                    } else {
                                        onOpen(ThingKey(ready.householdId, thingId = thing.id))
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    when (dialog) {
        SpaceDialog.None -> Unit
        SpaceDialog.Rename -> TextInputDialog(
            title = stringResource(R.string.feature_household_rename_space),
            label = stringResource(R.string.feature_household_space_name),
            confirmLabel = stringResource(CoreR.string.core_ui_save),
            initialValue = ready?.space?.name.orEmpty(),
            onConfirm = {
                onRenameSpace(it)
                closeDialog()
            },
            onDismiss = closeDialog,
        )
        SpaceDialog.Delete -> ConfirmDialog(
            title = stringResource(R.string.feature_household_delete_space),
            text = stringResource(R.string.feature_household_delete_space_text),
            confirmLabel = stringResource(CoreR.string.core_ui_delete),
            onConfirm = {
                closeDialog()
                onDeleteSpace()
            },
            onDismiss = closeDialog,
        )
    }
}

/** A thing as one row: its photo or icon, its name with the quantity, and its note or tags. */
@Composable
internal fun ThingRow(thing: Thing, onClick: () -> Unit, modifier: Modifier = Modifier, supporting: String? = null) {
    val details = supporting ?: thing.note?.takeIf { it.isNotBlank() } ?: thing.tags.takeIf { it.isNotEmpty() }?.joinToString(", ")
    ListItem(
        leadingContent = { Thumbnail(thing) },
        headlineContent = {
            Text(
                if (thing.quantity > 1) stringResource(R.string.feature_household_name_times, thing.name, thing.quantity) else thing.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = details?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun SpaceMenu(onSelect: (SpaceDialog) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val select = { target: SpaceDialog ->
        expanded = false
        onSelect(target)
    }
    Box {
        IconButton(onClick = { expanded = true }) {
            AppIcon(AppIcons.MoreVert, stringResource(CoreR.string.core_ui_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_household_rename_space)) },
                leadingIcon = { AppIcon(AppIcons.Edit, null) },
                onClick = { select(SpaceDialog.Rename) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_household_delete_space)) },
                leadingIcon = { AppIcon(AppIcons.Delete, null) },
                colors = destructiveMenuItemColors(),
                onClick = { select(SpaceDialog.Delete) },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun SpaceScreenPreview() {
    ClosetAnythingTheme {
        SpaceScreen(
            uiState = SpaceUiState.Ready(
                householdId = "h1",
                room = Room("r1", "My room"),
                space = Space("s1", "r1", "Under the bed"),
                box = null,
                things = listOf(
                    Thing("b1", "Jewellery box", "r1", "s1", null, true, null, 1, emptyList(), null, "Panos", 0),
                    Thing("t1", "Nike shoes", "r1", "s1", null, false, "Size 42, the black ones", 1, emptyList(), null, "Panos", 0),
                    Thing("t2", "Wool socks", "r1", "s1", null, false, null, 6, listOf("winter"), null, "Maria", 0),
                ),
            ),
            onBack = {},
            onOpen = {},
            onRenameSpace = {},
            onDeleteSpace = {},
        )
    }
}
