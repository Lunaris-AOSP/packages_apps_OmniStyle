/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package org.omnirom.omnistyle.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.axion.compose.scaffold.ExpressiveBackButton
import org.omnirom.omnistyle.GalleryState
import org.omnirom.omnistyle.R
import org.omnirom.omnistyle.data.GalleryImage
import org.omnirom.omnistyle.data.GalleryKind
import org.omnirom.omnistyle.data.GallerySort
import org.omnirom.omnistyle.data.GallerySource

@Composable
fun GalleryScreen(
    state: GalleryState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onSource: (GallerySource) -> Unit,
    onCollection: (String) -> Unit,
    onSort: (GallerySort) -> Unit,
    onRetry: () -> Unit,
    onSelect: (GalleryImage?) -> Unit,
    onApply: (GalleryImage) -> Unit,
    onSave: (GalleryImage) -> Unit,
) {
    val grid = key(state.source, state.collection) { rememberLazyGridState() }
    val showTitle by remember(grid) { derivedStateOf { grid.firstVisibleItemIndex > 0 } }
    val images = remember(state.catalog, state.collection, state.sort) { state.visibleImages }
    val motion = MaterialTheme.motionScheme
    var sortMenu by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { if (state.selected == null) SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(showTitle, enter = fadeIn(motion.fastEffectsSpec()), exit = fadeOut(motion.fastEffectsSpec())) {
                        Text(
                            stringResource(if (state.kind == GalleryKind.Headers) R.string.browse_header else R.string.browse_walls),
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = { ExpressiveBackButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) },
                actions = {
                    Box(Modifier.padding(end = 12.dp)) {
                        FilledTonalIconButton(onClick = { sortMenu = true }, enabled = !state.busy,
                            shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.sort_images))
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            GallerySort.entries.forEach { entry ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(sortLabel(entry))) },
                                    leadingIcon = { if (entry == state.sort) Icon(Icons.Default.Check, null) },
                                    onClick = { onSort(entry); sortMenu = false },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (state.kind == GalleryKind.Headers) 320.dp else 148.dp),
                state = grid,
                modifier = Modifier.align(Alignment.TopCenter).widthIn(max = 1100.dp).fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "heading", span = { GridItemSpan(maxLineSpan) }) { GalleryHeading(state.kind) }
                if (!state.onlineOnly) {
                    item(key = "source", span = { GridItemSpan(maxLineSpan) }) {
                        SourceButtons(state.source, !state.busy, onSource)
                    }
                }
                if (state.catalog.collections.isNotEmpty()) {
                    item(key = "collections", span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.collection_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp).semantics { heading() })
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item(key = "all") {
                                    FilterChip(
                                        selected = state.collection.isEmpty(),
                                        onClick = { onCollection("") },
                                        label = { Text(stringResource(R.string.all_collections)) },
                                        enabled = !state.busy,
                                        leadingIcon = { if (state.collection.isEmpty()) Icon(Icons.Default.Check, null, Modifier.size(18.dp)) },
                                    )
                                }
                                items(state.catalog.collections, key = { "collection:${it.key}" }) { collection ->
                                    FilterChip(
                                        selected = state.collection == collection.key,
                                        onClick = { onCollection(collection.key) },
                                        label = { Text(collection.label) },
                                        enabled = !state.busy,
                                        leadingIcon = { if (state.collection == collection.key) Icon(Icons.Default.Check, null, Modifier.size(18.dp)) },
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "count", span = { GridItemSpan(maxLineSpan) }) {
                    AnimatedVisibility(!state.loading && !state.failed && images.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(pluralStringResource(R.plurals.collection_size, images.size, images.size),
                                style = MaterialTheme.typography.titleSmallEmphasized,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                    }
                }
                when {
                    state.loading -> item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            LoadingIndicator(Modifier.size(64.dp))
                            Text(stringResource(R.string.loading_images), color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                    }
                    state.failed || images.isEmpty() -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        GalleryEmptyState(state.failed, onRetry,
                            if (!state.onlineOnly && state.source == GallerySource.Local) ({ onSource(GallerySource.Online) }) else null)
                    }
                    else -> items(images, key = { it.id }) { image ->
                        GalleryCard(
                            image = image,
                            kind = state.kind,
                            applied = state.kind == GalleryKind.Headers && image.packageName != null &&
                                state.appliedHeader == "${image.packageName}/${image.resourceName}",
                            enabled = !state.busy,
                            onClick = { onSelect(image) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = motion.fastEffectsSpec(),
                                placementSpec = motion.defaultSpatialSpec(),
                                fadeOutSpec = motion.fastEffectsSpec(),
                            ),
                        )
                    }
                }
            }
        }
    }
    state.selected?.let { image ->
        GalleryPreviewSheet(image, state.kind, state.allowApply, state.busy, state.action, snackbarHostState,
            onDismiss = { onSelect(null) }, onApply = { onApply(image) }, onSave = { onSave(image) })
    }
}

@Composable
private fun GalleryEmptyState(failed: Boolean, onRetry: () -> Unit, onExplore: (() -> Unit)?) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(if (failed) Icons.Default.CloudOff else Icons.Default.Collections, null,
            modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(if (failed) R.string.load_collection_failed else R.string.empty_collection_title),
            style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center)
        Text(stringResource(if (failed) R.string.load_collection_failed_message else R.string.empty_collection_message),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        if (failed || onExplore != null) {
            Button(onClick = if (failed) onRetry else onExplore ?: onRetry) {
                Text(stringResource(if (failed) R.string.retry else R.string.wallpaper_location_remote))
            }
        }
    }
}

private fun sortLabel(sort: GallerySort) = when (sort) {
    GallerySort.Name -> R.string.sort_by_default_menu
    GallerySort.Creator -> R.string.sort_by_creator_menu
    GallerySort.Collection -> R.string.sort_by_tag_menu
}
