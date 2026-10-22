/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package org.omnirom.omnistyle.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.TypedValue
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.omnirom.omnistyle.HeaderUtils
import org.omnirom.omnistyle.NetworkUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class GalleryRepository(private val context: Context) {
    private companion object {
        val fileMutex = Mutex()
    }

    suspend fun load(kind: GalleryKind, source: GallerySource, filter: String?): GalleryCatalog =
        withContext(Dispatchers.IO) {
            when {
                source == GallerySource.Online -> loadOnline(kind, filter)
                kind == GalleryKind.Headers -> loadHeaders(filter)
                else -> loadWallpapers()
            }
        }

    private fun loadHeaders(filter: String?): GalleryCatalog {
        val packages = context.packageManager
        val packs = linkedMapOf<String, GalleryCollection>()
        if (filter == "theme") {
            val key = "${context.packageName}/${context.packageName}.theme"
            packs[key] = GalleryCollection(key, context.getString(org.omnirom.omnistyle.R.string.theme_collection))
        } else {
            for (action in listOf("org.omnirom.DaylightHeaderPack", "org.omnirom.DaylightHeaderPack1")) {
                for (match in packages.queryIntentActivities(Intent(action), 0)) {
                    val activity = match.activityInfo
                    val key = if (action.endsWith("1")) "${activity.packageName}/${activity.name}"
                        else activity.packageName
                    packs[key] = GalleryCollection(key, activity.loadLabel(packages).toString())
                }
            }
        }
        val images = mutableListOf<GalleryImage>()
        for (pack in packs.values.sortedBy { it.label.lowercase() }) {
            runCatching {
                val packageName = pack.key.substringBefore('/')
                val resources = packages.getResourcesForApplication(packageName)
                val headers = mutableListOf<HeaderUtils.DaylightHeaderInfo>()
                val creator = HeaderUtils.loadHeaders(resources, pack.key.substringAfter('/', "").ifEmpty { null }, headers).orEmpty()
                for (header in headers.distinctBy { it.mImage }) {
                    val resourceId = resources.getIdentifier(header.mImage, "drawable", packageName)
                    if (resourceId == 0) continue
                    images += GalleryImage(
                        id = "$packageName/${header.mImage}/${pack.key}",
                        name = header.mName?.takeIf { it.isNotBlank() } ?: readableName(header.mImage),
                        creator = creator,
                        collection = pack.key,
                        previewUri = "android.resource://$packageName/drawable/${header.mImage}",
                        packageName = packageName,
                        resourceName = header.mImage,
                    )
                }
            }
        }
        return GalleryCatalog(images, packs.values.sortedBy { it.label.lowercase() })
    }

    private fun loadWallpapers(): GalleryCatalog {
        val images = mutableListOf<GalleryImage>()
        context.assets.open("wallpapers.xml").use { stream ->
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(stream, "UTF-8")
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "wallpaper") {
                    val image = parser.getAttributeValue(null, "image")
                    val id = image?.let { context.resources.getIdentifier(it, "drawable", context.packageName) } ?: 0
                    if (id != 0 && image != null) {
                        images += GalleryImage(
                            id = "${context.packageName}/$image",
                            name = parser.getAttributeValue(null, "name")?.takeIf { it.isNotBlank() } ?: readableName(image),
                            creator = parser.getAttributeValue(null, "creator").orEmpty(),
                            collection = parser.getAttributeValue(null, "tag").orEmpty(),
                            previewUri = "android.resource://${context.packageName}/drawable/$image",
                            packageName = context.packageName,
                            resourceName = image,
                        )
                    }
                }
                parser.next()
            }
        }
        return GalleryCatalog(images.distinctBy { it.id }, collectionsFor(images))
    }

    private fun loadOnline(kind: GalleryKind, filter: String?): GalleryCatalog {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true) {
            throw IOException(context.getString(org.omnirom.omnistyle.R.string.no_network_message))
        }
        val images = OnlineGallery(NetworkUtils::downloadUrlMemoryAsString).load(kind, filter)
        return GalleryCatalog(images, collectionsFor(images))
    }

    private fun collectionsFor(images: List<GalleryImage>) = images.map { it.collection }
        .filter { it.isNotBlank() }.distinct().sorted()
        .map { GalleryCollection(it, readableName(it)) }

    suspend fun prepareWallpaper(image: GalleryImage): Uri = withContext(Dispatchers.IO) {
        FileProvider.getUriForFile(context, "${context.packageName}.files", imageFile(image))
    }

    suspend fun applyHeader(image: GalleryImage) = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        currentCoroutineContext().ensureActive()
        if (image.packageName != null) {
            check(Settings.System.putString(resolver, Settings.System.STATUS_BAR_CUSTOM_HEADER_IMAGE,
                "${image.packageName}/${image.resourceName}"))
            check(Settings.System.putString(resolver, Settings.System.STATUS_BAR_CUSTOM_HEADER_PROVIDER, "static"))
        } else {
            val saved = saveImage(image)
            val path = resolver.query(saved, arrayOf(MediaStore.Images.Media.DATA), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: throw IOException("Saved header has no file path")
            currentCoroutineContext().ensureActive()
            check(Settings.System.putString(resolver, Settings.System.STATUS_BAR_FILE_HEADER_IMAGE, path))
            check(Settings.System.putString(resolver, Settings.System.STATUS_BAR_CUSTOM_HEADER_PROVIDER, "file"))
        }
    }

    suspend fun saveImage(image: GalleryImage): Uri = withContext(Dispatchers.IO) {
        val file = imageFile(image)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        val mime = options.outMimeType ?: "image/png"
        val extension = when (mime) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${image.resourceName ?: image.id.substringAfterLast('/').substringBeforeLast('.')}.$extension")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/OmniStyle")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        currentCoroutineContext().ensureActive()
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Unable to create image")
        try {
            file.inputStream().use { source ->
                val output = resolver.openOutputStream(uri) ?: throw IOException("Unable to save image")
                output.use { source.copyTo(it) }
            }
            currentCoroutineContext().ensureActive()
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            check(resolver.update(uri, values, null, null) > 0)
            uri
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    private suspend fun imageFile(image: GalleryImage): File = fileMutex.withLock {
        val directory = File(context.cacheDir, "wallpapers")
        check(directory.isDirectory || directory.mkdirs())
        val local = image.packageName != null
        val version = image.packageName?.let {
            "${context.packageManager.getPackageInfo(it, 0).lastUpdateTime}/${context.resources.configuration}"
        }.orEmpty()
        val key = MessageDigest.getInstance("SHA-256").digest("${image.fullUri}/$version".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val resource = if (local) galleryResource(context, Uri.parse(image.fullUri)) else null
        val resourcePath = resource?.let { (resources, id) ->
            TypedValue().also { resources.getValue(id, it, true) }.string?.toString()
        }
        val extension = if (local) resourcePath?.substringAfterLast('.')?.takeUnless { it == "xml" } ?: "png"
            else image.fullUri.substringAfterLast('.').lowercase()
        val file = File(directory, "$key.$extension")
        if (!validImage(file)) {
            val temporary = File.createTempFile(key, ".part", directory)
            try {
                if (local) {
                    val uri = Uri.parse(image.fullUri)
                    val (resources, id) = checkNotNull(resource)
                    if (resourcePath?.endsWith(".xml") == true) {
                        val bitmap = decodeGalleryResource(context, uri, 2160, 3840)
                        try {
                            temporary.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                        } finally {
                            bitmap.recycle()
                        }
                    } else {
                        resources.openRawResource(id).use { source ->
                            temporary.outputStream().use { source.copyTo(it) }
                        }
                    }
                } else if (!NetworkUtils.downloadUrlFile(image.fullUri, temporary)) {
                    throw IOException("Image download failed")
                }
                currentCoroutineContext().ensureActive()
                if (!validImage(temporary)) throw IOException("Invalid image download")
                check(temporary.renameTo(file))
            } finally {
                temporary.delete()
            }
        }
        file
    }
}

private fun validImage(file: File): Boolean {
    if (!file.isFile || file.length() == 0L) return false
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    return options.outWidth > 0 && options.outHeight > 0
}

private fun galleryResource(context: Context, uri: Uri): Pair<Resources, Int> {
    val packageName = uri.authority ?: throw IOException("Image package missing")
    val resources = context.packageManager.getResourcesForApplication(packageName)
    val path = uri.pathSegments
    val id = when (path.size) {
        1 -> path[0].toIntOrNull() ?: 0
        2 -> resources.getIdentifier(path[1], path[0], packageName)
        else -> 0
    }
    if (id == 0) throw IOException("Image resource missing")
    return resources to id
}

internal fun decodeGalleryResource(context: Context, uri: Uri, targetWidth: Int, targetHeight: Int): Bitmap {
    require(targetWidth > 0 && targetHeight > 0)
    val (resources, id) = galleryResource(context, uri)
    val value = TypedValue()
    resources.getValue(id, value, true)
    if (value.string?.endsWith(".xml") != true) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false; inSampleSize = 1 }
        BitmapFactory.decodeResource(resources, id, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) throw IOException("Invalid image resource")
        // Keep enough pixels in both dimensions for the preview's center crop.
        while (options.outWidth / (options.inSampleSize * 2) >= targetWidth &&
            options.outHeight / (options.inSampleSize * 2) >= targetHeight) {
            options.inSampleSize *= 2
        }
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeResource(resources, id, options) ?: throw IOException("Unable to decode image")
    }
    val drawable = resources.getDrawable(id, null)
    val width = drawable.intrinsicWidth.coerceAtLeast(1)
    val height = drawable.intrinsicHeight.coerceAtLeast(1)
    val scale = minOf(targetWidth.toFloat() / width, targetHeight.toFloat() / height)
    val bitmap = Bitmap.createBitmap((width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, bitmap.width, bitmap.height)
    drawable.draw(Canvas(bitmap))
    return bitmap
}

private fun readableName(name: String) = name.replace('_', ' ').replace('-', ' ')
    .replaceFirstChar { it.titlecase() }
