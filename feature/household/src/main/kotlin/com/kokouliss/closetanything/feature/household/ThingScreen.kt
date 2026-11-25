package com.kokouliss.closetanything.feature.household

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kokouliss.closetanything.core.domain.model.ImageData
import com.kokouliss.closetanything.core.domain.model.MAX_NAME_LENGTH
import com.kokouliss.closetanything.core.domain.model.MAX_NOTE_LENGTH
import com.kokouliss.closetanything.core.domain.model.MAX_QUANTITY
import com.kokouliss.closetanything.core.domain.model.Thing
import com.kokouliss.closetanything.core.domain.model.ThingDraft
import com.kokouliss.closetanything.core.domain.model.parseTags
import com.kokouliss.closetanything.core.ui.R as CoreR
import com.kokouliss.closetanything.core.ui.component.AppIcon
import com.kokouliss.closetanything.core.ui.component.AppIcons
import com.kokouliss.closetanything.core.ui.component.BackButton
import com.kokouliss.closetanything.core.ui.component.ConfirmDialog
import com.kokouliss.closetanything.core.ui.component.LoadingCrossfade
import com.kokouliss.closetanything.core.ui.component.destructiveMenuItemColors
import com.kokouliss.closetanything.core.ui.component.pageWidth
import com.kokouliss.closetanything.core.ui.theme.ClosetAnythingTheme
import kotlinx.coroutines.launch

@Composable
internal fun ThingRoute(viewModel: ThingViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val photo by viewModel.photo.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    if (uiState is ThingUiState.Gone) {
        LaunchedEffect(Unit) { onBack() }
    }
    ThingScreen(
        uiState = uiState,
        photo = photo,
        onBack = onBack,
        onSave = { draft, isBox, photoUri ->
            scope.launch {
                viewModel.save(draft, isBox, photoUri)
                onBack()
            }
        },
        onSetPhoto = viewModel::setPhoto,
        onRemovePhoto = viewModel::removePhoto,
        onMove = viewModel::move,
        onDelete = {
            viewModel.delete()
            onBack()
        },
    )
}

@Composable
internal fun ThingScreen(
    uiState: ThingUiState,
    /** The stored full-size photo of an existing thing, once loaded. */
    photo: ImageData?,
    onBack: () -> Unit,
    /** [photoUri] is the picture chosen for a new thing, attached once it exists. */
    onSave: (draft: ThingDraft, isBox: Boolean, photoUri: String?) -> Unit,
    onSetPhoto: (photoUri: String) -> Unit,
    onRemovePhoto: () -> Unit,
    onMove: (Destination) -> Unit,
    onDelete: () -> Unit,
) {
    val ready = uiState as? ThingUiState.Ready
    val isNew = ready?.thing == null
    val title = when {
        ready == null -> ""
        isNew -> stringResource(R.string.feature_household_add_thing)
        else -> stringResource(R.string.feature_household_edit_thing)
    }
    var showMove by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.semantics { paneTitle = title },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (ready != null) Hint(ready.path.asPath(), maxLines = 1)
                    }
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (ready?.thing != null) {
                        ThingMenu(onMove = { showMove = true }, onDelete = { confirmDelete = true })
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).pageWidth()) {
            LoadingCrossfade(ready == null) {
                if (ready == null) return@LoadingCrossfade
                ThingForm(ready, photo, onSave, onSetPhoto, onRemovePhoto)
            }
        }
    }

    if (ready != null && showMove) {
        MoveSheet(
            destinations = ready.destinations,
            onDismiss = { showMove = false },
            onMove = {
                showMove = false
                onMove(it)
            },
        )
    }
    val thing = ready?.thing
    if (thing != null && confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.feature_household_delete_thing, thing.name),
            text = stringResource(if (thing.isBox) R.string.feature_household_delete_box_text else R.string.feature_household_delete_thing_text),
            confirmLabel = stringResource(CoreR.string.core_ui_delete),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** The fields of a thing. They start from the stored thing and are kept while the screen is open. */
@Composable
private fun ThingForm(
    state: ThingUiState.Ready,
    photo: ImageData?,
    onSave: (draft: ThingDraft, isBox: Boolean, photoUri: String?) -> Unit,
    onSetPhoto: (photoUri: String) -> Unit,
    onRemovePhoto: () -> Unit,
) {
    val thing = state.thing
    var name by rememberSaveable { mutableStateOf(thing?.name.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(thing?.note.orEmpty()) }
    var quantity by rememberSaveable { mutableStateOf(thing?.quantity?.toString() ?: "1") }
    var tags by rememberSaveable { mutableStateOf(thing?.tags?.joinToString(", ").orEmpty()) }
    var isBox by rememberSaveable { mutableStateOf(false) }
    // A picture chosen for a new thing, kept here until the thing exists.
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val captureUri = remember { captureUri(context) }
    val onPhotoPicked = { uri: Uri? ->
        if (uri != null) {
            if (thing == null) pendingPhoto = uri.toString() else onSetPhoto(uri.toString())
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        onPhotoPicked(captureUri.takeIf { saved })
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), onPhotoPicked)
    val takePhoto = {
        try {
            camera.launch(captureUri)
        } catch (e: ActivityNotFoundException) {
            // No camera app: the gallery button is right next to it.
        }
    }
    val choosePhoto = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    val count = quantity.trim().toIntOrNull()
    val draft = ThingDraft(name.trim(), note.trim().takeIf { it.isNotEmpty() }, count ?: 1, parseTags(tags))
    val stored = thing?.let { ThingDraft(it.name, it.note, it.quantity, it.tags) }
    val canSave = draft.name.isNotEmpty() && count != null && count in 1..MAX_QUANTITY && (stored == null || draft != stored)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PhotoBox(
            bitmap = if (thing == null) rememberBitmap(pendingPhoto) else rememberBitmap(photo ?: thing.thumbnail),
            hasPhoto = if (thing == null) pendingPhoto != null else thing.thumbnail != null,
            onTakePhoto = takePhoto,
            onChoosePhoto = choosePhoto,
            onRemovePhoto = { if (thing == null) pendingPhoto = null else onRemovePhoto() },
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_NAME_LENGTH) },
            label = { Text(stringResource(R.string.feature_household_thing_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(MAX_NOTE_LENGTH) },
            label = { Text(stringResource(R.string.feature_household_thing_note)) },
            supportingText = { Text(stringResource(R.string.feature_household_thing_note_hint)) },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = quantity,
            onValueChange = { quantity = it.filter(Char::isDigit).take(3) },
            label = { Text(stringResource(R.string.feature_household_thing_quantity)) },
            isError = count == null || count < 1,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = tags,
            onValueChange = { tags = it.take(TAGS_FIELD_LENGTH) },
            label = { Text(stringResource(R.string.feature_household_thing_tags)) },
            supportingText = { Text(stringResource(R.string.feature_household_thing_tags_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.canBeBox) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(role = Role.Switch) { isBox = !isBox }
                    .semantics { toggleableState = if (isBox) ToggleableState.On else ToggleableState.Off }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(stringResource(R.string.feature_household_is_box))
                    Hint(stringResource(R.string.feature_household_is_box_hint))
                }
                Switch(checked = isBox, onCheckedChange = null)
            }
        }
        Button(
            onClick = { onSave(draft, isBox, pendingPhoto) },
            enabled = canSave,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(if (thing == null) R.string.feature_household_add else CoreR.string.core_ui_save))
        }
    }
}

// Ten tags of thirty letters, with their commas.
private const val TAGS_FIELD_LENGTH = 340

/** The photo at the top of the form, or the buttons to take or choose one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhotoBox(
    bitmap: ImageBitmap?,
    hasPhoto: Boolean,
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap,
                    contentDescription = stringResource(R.string.feature_household_photo),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else if (!hasPhoto) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppIcon(AppIcons.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Hint(stringResource(R.string.feature_household_photo_hint))
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onTakePhoto) {
                AppIcon(AppIcons.Camera, null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.feature_household_take_photo))
            }
            OutlinedButton(onClick = onChoosePhoto) {
                AppIcon(AppIcons.Image, null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.feature_household_choose_photo))
            }
            if (hasPhoto) {
                TextButton(onClick = onRemovePhoto) { Text(stringResource(R.string.feature_household_remove_photo)) }
            }
        }
    }
}

@Composable
private fun MoveSheet(destinations: List<Destination>, onDismiss: () -> Unit, onMove: (Destination) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceBright,
    ) {
        SectionTitle(stringResource(R.string.feature_household_move_to), Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        if (destinations.isEmpty()) {
            Hint(stringResource(R.string.feature_household_move_nowhere), Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
        }
        LazyColumn {
            items(destinations, key = { "${it.spaceId}/${it.boxId}" }) { destination ->
                ListItem(
                    leadingContent = { AppIcon(if (destination.boxId != null) AppIcons.Box else AppIcons.Location, null) },
                    headlineContent = { Text(destination.path.asPath(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.clickable(role = Role.Button) { onMove(destination) },
                )
            }
        }
    }
}

@Composable
private fun ThingMenu(onMove: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            AppIcon(AppIcons.MoreVert, stringResource(CoreR.string.core_ui_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_household_move_to)) },
                leadingIcon = { AppIcon(AppIcons.Location, null) },
                onClick = {
                    expanded = false
                    onMove()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(CoreR.string.core_ui_delete)) },
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

@PreviewLightDark
@Composable
private fun ThingScreenPreview() {
    ClosetAnythingTheme {
        ThingScreen(
            uiState = ThingUiState.Ready(
                thing = Thing("t1", "Nike shoes", "r1", "s1", null, false, "Size 42, the black ones", 1, listOf("winter"), null, "Panos", 0),
                roomId = "r1",
                spaceId = "s1",
                boxId = null,
                path = listOf("My room", "Under the bed"),
                destinations = emptyList(),
                canBeBox = false,
            ),
            photo = null,
            onBack = {},
            onSave = { _, _, _ -> },
            onSetPhoto = {},
            onRemovePhoto = {},
            onMove = {},
            onDelete = {},
        )
    }
}
