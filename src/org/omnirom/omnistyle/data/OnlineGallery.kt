/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package org.omnirom.omnistyle.data

import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class OnlineGallery(private val download: (String) -> String?) {
    fun load(kind: GalleryKind, filter: String?): List<GalleryImage> {
        val query = filter?.trim()?.takeUnless { it.isEmpty() || it == "theme" }
        try {
            val images = loadWallhaven(kind, query)
            if (images.isNotEmpty() || query != null) return images
        } catch (error: Exception) {
            if (query != null || (error !is IOException && error !is JSONException)) throw error
        }
        return loadPicsum(kind).ifEmpty { throw IOException("Unable to load image directory") }
    }

    private fun loadWallhaven(kind: GalleryKind, query: String?): List<GalleryImage> {
        val url = buildString {
            append("https://wallhaven.cc/api/v1/search?categories=110&purity=100")
            append("&sorting=toplist&topRange=1M")
            if (kind == GalleryKind.Headers) {
                append("&atleast=1920x1080&ratios=landscape")
            } else {
                append("&atleast=1080x1920")
            }
            if (query != null) append("&q=${URLEncoder.encode(query, "UTF-8")}")
        }
        val array = JSONObject(download(url) ?: throw IOException("Unable to load Wallhaven"))
            .getJSONArray("data")
        val images = mutableListOf<GalleryImage>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.text("id")
            if (!id.matches(Regex("[a-z0-9]{6}")) || item.text("purity") != "sfw") continue
            val width = item.optInt("dimension_x")
            val height = item.optInt("dimension_y")
            if (width <= 0 || height <= 0 || (kind == GalleryKind.Headers && width < height)) continue
            if (item.text("file_type") !in listOf("image/jpeg", "image/png", "image/webp")) continue
            val full = imageUrl(item.text("path"), "w.wallhaven.cc") ?: continue
            val preview = imageUrl(item.optJSONObject("thumbs")?.text("large").orEmpty(), "th.wallhaven.cc")
                ?: continue
            images += GalleryImage(
                id = "wallhaven/$id",
                name = "Wallhaven $id",
                collection = query ?: item.text("category").ifEmpty { "general" },
                previewUri = preview,
                fullUri = full,
            )
        }
        return images.distinctBy { it.id }
    }

    private fun loadPicsum(kind: GalleryKind): List<GalleryImage> {
        val json = download("https://picsum.photos/v2/list?page=1&limit=100")
            ?: throw IOException("Unable to load Picsum")
        val array = JSONArray(json)
        val images = mutableListOf<GalleryImage>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.text("id")
            if (!id.matches(Regex("[0-9]+"))) continue
            val width = item.optInt("width")
            val height = item.optInt("height")
            if (width <= 0 || height <= 0 || (kind == GalleryKind.Headers && width < height)) continue
            val fullWidth: Int
            val fullHeight: Int
            if (kind == GalleryKind.Headers) {
                fullHeight = minOf(width / 4, height, 640)
                if (fullHeight == 0) continue
                fullWidth = fullHeight * 4
            } else {
                val scale = minOf(1.0, 3840.0 / maxOf(width, height))
                fullWidth = (width * scale).toInt().coerceAtLeast(1)
                fullHeight = (height * scale).toInt().coerceAtLeast(1)
            }
            val previewScale = minOf(1.0, 800.0 / maxOf(fullWidth, fullHeight))
            val previewWidth = (fullWidth * previewScale).toInt().coerceAtLeast(1)
            val previewHeight = (fullHeight * previewScale).toInt().coerceAtLeast(1)
            val base = "https://picsum.photos/id/$id"
            images += GalleryImage(
                id = "picsum/${kind.name}/$id",
                name = "Picsum $id",
                creator = item.text("author"),
                collection = "picsum",
                previewUri = "$base/$previewWidth/$previewHeight.jpg",
                fullUri = "$base/$fullWidth/$fullHeight.jpg",
            )
        }
        return images.distinctBy { it.id }
    }

    private fun imageUrl(value: String, host: String): String? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        return value.takeIf { uri.scheme == "https" && uri.host == host && uri.rawUserInfo == null }
    }
}

private fun JSONObject.text(key: String): String =
    if (isNull(key)) "" else optString(key).trim()
