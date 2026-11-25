package com.kokouliss.closetanything.feature.household

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import com.kokouliss.closetanything.core.domain.model.Room
import com.kokouliss.closetanything.core.domain.model.Space
import com.kokouliss.closetanything.core.domain.model.Thing
import com.kokouliss.closetanything.core.domain.model.locationIn
import com.kokouliss.closetanything.core.domain.model.search
import com.kokouliss.closetanything.core.domain.repository.PlaceRepository
import com.kokouliss.closetanything.core.domain.repository.ThingRepository
import com.kokouliss.closetanything.core.ui.component.AppIcon
import com.kokouliss.closetanything.core.ui.component.AppIcons
import com.kokouliss.closetanything.core.ui.component.BackButton
import com.kokouliss.closetanything.core.ui.component.EmptyState
import com.kokouliss.closetanything.core.ui.component.LoadingBox
import com.kokouliss.closetanything.core.ui.component.pageWidth
import com.kokouliss.closetanything.core.ui.theme.ClosetAnythingTheme
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** A thing that matches, with where it is. */
internal data class SearchHit(val thing: Thing, val path: List<String>)

@HiltViewModel(assistedFactory = SearchViewModel.Factory::class)
internal class SearchViewModel @AssistedInject constructor(
    @Assisted private val key: SearchKey,
    placeRepository: PlaceRepository,
    thingRepository: ThingRepository,
) : ViewModel() {

    val householdId: String get() = key.householdId

    val query = MutableStateFlow("")

    /** Null until the household's things have arrived once. */
    val hits: StateFlow<List<SearchHit>?> = combine(
        query,
        placeRepository.observeRooms(householdId).catch { emit(emptyList()) },
        placeRepository.observeSpaces(householdId).catch { emit(emptyList()) },
        thingRepository.observeThings(householdId).catch { emit(emptyList()) },
    ) { words, rooms, spaces, things ->
        things.search(words).map { SearchHit(it, it.locationIn(rooms, spaces, things)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @AssistedFactory
    interface Factory {
        fun create(key: SearchKey): SearchViewModel
    }
}

@Composable
internal fun SearchRoute(viewModel: SearchViewModel, onOpen: (NavKey) -> Unit, onBack: () -> Unit) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val hits by viewModel.hits.collectAsStateWithLifecycle()
    SearchScreen(
        query = query,
        hits = hits,
        onQueryChange = { viewModel.query.value = it },
        onOpenHit = { hit ->
            val thing = hit.thing
            // A box opens on its contents; anything else on its own page.
            onOpen(if (thing.isBox) SpaceKey(viewModel.householdId, thing.spaceId, boxId = thing.id) else ThingKey(viewModel.householdId, thingId = thing.id))
        },
        onBack = onBack,
    )
}

@Composable
internal fun SearchScreen(
    query: String,
    /** Null while the things are still loading. */
    hits: List<SearchHit>?,
    onQueryChange: (String) -> Unit,
    onOpenHit: (SearchHit) -> Unit,
    onBack: () -> Unit,
) {
    val title = stringResource(R.string.feature_household_search)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Scaffold(
        modifier = Modifier.semantics { paneTitle = title },
        topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        Column(Modifier.padding(padding).pageWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.feature_household_search_hint)) },
                singleLine = true,
                trailingIcon = if (query.isEmpty()) {
                    null
                } else {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            AppIcon(AppIcons.Close, stringResource(R.string.feature_household_search_clear))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).focusRequester(focusRequester),
            )
            when {
                query.isBlank() -> Unit
                hits == null -> LoadingBox()
                hits.isEmpty() -> EmptyState(
                    icon = AppIcons.Search,
                    title = stringResource(R.string.feature_household_search_nothing),
                    body = stringResource(R.string.feature_household_search_nothing_body, query.trim()),
                )
                else -> LazyColumn {
                    items(hits, key = { it.thing.id }) { hit ->
                        ThingRow(hit.thing, onClick = { onOpenHit(hit) }, supporting = hit.path.asPath())
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun SearchScreenPreview() {
    val room = Room("r1", "My room")
    val space = Space("s1", "r1", "Under the bed")
    val box = Thing("b1", "Jewellery box", "r1", "s1", null, true, null, 1, emptyList(), null, "Panos", 0)
    val ring = Thing("t1", "Ruby ring", "r1", "s1", "b1", false, null, 1, emptyList(), null, "Panos", 0)
    ClosetAnythingTheme {
        SearchScreen(
            query = "ring",
            hits = listOf(SearchHit(ring, ring.locationIn(listOf(room), listOf(space), listOf(box)))),
            onQueryChange = {},
            onOpenHit = {},
            onBack = {},
        )
    }
}
