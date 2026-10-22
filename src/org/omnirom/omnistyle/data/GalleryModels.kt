/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package org.omnirom.omnistyle.data

enum class GalleryKind { Headers, Wallpapers }
enum class GallerySource { Local, Online }
enum class GallerySort { Name, Creator, Collection }

data class GalleryCollection(val key: String, val label: String)

data class GalleryImage(
    val id: String,
    val name: String,
    val creator: String = "",
    val collection: String,
    val previewUri: String,
    val fullUri: String = previewUri,
    val packageName: String? = null,
    val resourceName: String? = null,
)

data class GalleryCatalog(
    val images: List<GalleryImage> = emptyList(),
    val collections: List<GalleryCollection> = emptyList(),
)
