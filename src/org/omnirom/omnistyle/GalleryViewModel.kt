/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package org.omnirom.omnistyle

import android.app.Application
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.omnirom.omnistyle.data.GalleryCatalog
import org.omnirom.omnistyle.data.GalleryImage
import org.omnirom.omnistyle.data.GalleryKind
import org.omnirom.omnistyle.data.GalleryRepository
import org.omnirom.omnistyle.data.GallerySort
import org.omnirom.omnistyle.data.GallerySource

data class GalleryState(
    val kind: GalleryKind = GalleryKind.Headers,
    val source: GallerySource = GallerySource.Local,
    val catalog: GalleryCatalog = GalleryCatalog(),
    val collection: String = "",
    val sort: GallerySort = GallerySort.Name,
    val loading: Boolean = true,
    val action: GalleryAction? = null,
    val pendingWallpaper: String? = null,
    val failed: Boolean = false,
    val selected: GalleryImage? = null,
    val appliedHeader: String? = null,
    val allowApply: Boolean = false,
    val onlineOnly: Boolean = false,
) {
    val busy: Boolean get() = action != null || pendingWallpaper != null

    val visibleImages: List<GalleryImage>
        get() = catalog.images.filter { collection.isEmpty() || it.collection == collection }
            .sortedWith(compareBy<GalleryImage, String>(String.CASE_INSENSITIVE_ORDER) {
                when (sort) {
                    GallerySort.Name -> it.name
                    GallerySort.Creator -> it.creator
                    GallerySort.Collection -> it.collection
                }
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

enum class GalleryAction { Save, Apply }

sealed interface GalleryEvent {
    data class Message(val resource: Int) : GalleryEvent
}

class GalleryViewModel(application: Application, private val savedState: SavedStateHandle) : AndroidViewModel(application) {
    private val repository = GalleryRepository(application)
    private val mutableState = MutableStateFlow(GalleryState())
    val state = mutableState.asStateFlow()
    private val eventChannel = Channel<GalleryEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private val catalogs = mutableMapOf<GallerySource, GalleryCatalog>()
    private var loadJob: Job? = null
    private var initialized = false
    private var filter: String? = null

    fun initialize(kind: GalleryKind, allowApply: Boolean, onlineOnly: Boolean, filter: String?) {
        if (initialized) return
        initialized = true
        this.filter = filter
        val source = if (onlineOnly) GallerySource.Online
            else GallerySource.entries.find { it.name == savedState.get<String>("source") } ?: GallerySource.Local
        mutableState.value = GalleryState(
            kind = kind,
            source = source,
            collection = savedState["collection"] ?: "",
            sort = GallerySort.entries.find { it.name == savedState.get<String>("sort") } ?: GallerySort.Name,
            allowApply = allowApply,
            onlineOnly = onlineOnly,
            pendingWallpaper = savedState["cropRequest"],
        )
        refreshAppliedHeader()
        load()
    }

    fun setSource(source: GallerySource) {
        if (source == state.value.source || state.value.busy) return
        savedState["source"] = source.name
        savedState["collection"] = ""
        savedState["preview"] = null
        mutableState.update { it.copy(source = source, collection = "", selected = null) }
        load()
    }

    fun setCollection(collection: String) {
        if (state.value.busy) return
        savedState["collection"] = collection
        mutableState.update { it.copy(collection = collection) }
    }

    fun setSort(sort: GallerySort) {
        if (state.value.busy) return
        savedState["sort"] = sort.name
        mutableState.update { it.copy(sort = sort) }
    }

    fun select(image: GalleryImage?) {
        if (!state.value.busy) {
            savedState["preview"] = image?.id
            mutableState.update { it.copy(selected = image) }
        }
    }

    fun load(force: Boolean = false) {
        if (state.value.busy && force) return
        loadJob?.cancel()
        val source = state.value.source
        val cached = catalogs[source].takeUnless { force }
        if (cached != null) {
            mutableState.update { it.copy(catalog = cached, loading = false, failed = false) }
            return
        }
        mutableState.update { it.copy(catalog = GalleryCatalog(), loading = true, failed = false) }
        loadJob = viewModelScope.launch {
            try {
                val activeFilter = if (state.value.kind == GalleryKind.Wallpapers && source == GallerySource.Local) null else filter
                val catalog = repository.load(state.value.kind, source, activeFilter)
                catalogs[source] = catalog
                val collection = state.value.collection.takeIf { key -> catalog.collections.any { it.key == key } }.orEmpty()
                savedState["collection"] = collection
                mutableState.update { it.copy(catalog = catalog, collection = collection, loading = false) }
                val selection = savedState.get<String>("preview")?.let { id -> catalog.images.find { it.id == id } }
                mutableState.update { it.copy(selected = selection) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    fun save(image: GalleryImage) = perform(GalleryAction.Save) {
        repository.saveImage(image)
        eventChannel.send(GalleryEvent.Message(R.string.image_saved))
    }

    fun apply(image: GalleryImage) {
        if (!state.value.allowApply) return
        perform(GalleryAction.Apply) {
            if (state.value.kind == GalleryKind.Headers) {
                repository.applyHeader(image)
                savedState["preview"] = null
                mutableState.update { it.copy(appliedHeader = image.packageName?.let { name -> "$name/${image.resourceName}" }, selected = null) }
                eventChannel.send(GalleryEvent.Message(R.string.custom_header_image_notice))
            } else {
                val uri = repository.prepareWallpaper(image).toString()
                savedState["cropRequest"] = uri
                savedState["preview"] = null
                mutableState.update { it.copy(pendingWallpaper = uri, selected = null) }
            }
        }
    }

    fun completeWallpaperPreparation(uri: Uri) {
        if (state.value.pendingWallpaper != uri.toString()) return
        savedState["cropRequest"] = null
        mutableState.update { it.copy(pendingWallpaper = null) }
    }

    fun refreshAppliedHeader() {
        val resolver = getApplication<Application>().contentResolver
        val image = if (Settings.System.getString(resolver, Settings.System.STATUS_BAR_CUSTOM_HEADER_PROVIDER) == "static") {
            Settings.System.getString(resolver, Settings.System.STATUS_BAR_CUSTOM_HEADER_IMAGE)
        } else null
        mutableState.update { it.copy(appliedHeader = image) }
    }

    private fun perform(action: GalleryAction, operation: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.update { it.copy(action = action) }
        viewModelScope.launch {
            try {
                operation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                eventChannel.send(GalleryEvent.Message(R.string.image_action_failed))
            } finally {
                mutableState.update { it.copy(action = null) }
            }
        }
    }
}
