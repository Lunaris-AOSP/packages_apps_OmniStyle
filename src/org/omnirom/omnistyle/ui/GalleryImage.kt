/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.omnirom.omnistyle.ui

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.omnirom.omnistyle.R
import org.omnirom.omnistyle.data.decodeGalleryResource

@Composable
internal fun GalleryImageContent(uri: String, description: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val parsed = remember(uri) { Uri.parse(uri) }
    var measuredSize by remember { mutableStateOf(IntSize.Zero) }
    val requestSize = remember(measuredSize) {
        if (measuredSize.width > 0 && measuredSize.height > 0) {
            val scale = minOf(1f, 1200f / measuredSize.width, 1600f / measuredSize.height)
            IntSize((measuredSize.width * scale).toInt().coerceAtLeast(1),
                (measuredSize.height * scale).toInt().coerceAtLeast(1))
        } else {
            IntSize.Zero
        }
    }
    var bitmap by remember(uri, requestSize, configuration) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(uri, requestSize, configuration) { mutableStateOf(false) }
    var retry by remember(uri) { mutableIntStateOf(0) }
    val target = remember(uri, requestSize, configuration) {
        object : Target {
            override fun onBitmapLoaded(image: Bitmap, from: Picasso.LoadedFrom) { bitmap = image; failed = false }
            override fun onBitmapFailed(errorDrawable: Drawable?) { failed = true }
            override fun onPrepareLoad(placeHolderDrawable: Drawable?) { failed = false }
        }
    }
    LaunchedEffect(uri, requestSize, configuration, retry) {
        if (parsed.scheme == "android.resource" && requestSize.width > 0 && requestSize.height > 0) {
            try {
                bitmap = withContext(Dispatchers.IO) {
                    decodeGalleryResource(context, parsed, requestSize.width, requestSize.height)
                }
                failed = false
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failed = true
            }
        }
    }
    DisposableEffect(uri, requestSize, configuration, retry) {
        val picasso = Picasso.with(context)
        if (parsed.scheme != "android.resource" && requestSize.width > 0 && requestSize.height > 0) {
            val request = picasso.load(parsed).resize(requestSize.width, requestSize.height).onlyScaleDown().centerCrop()
            if (retry > 0) request.networkPolicy(com.squareup.picasso.NetworkPolicy.NO_CACHE)
            request.into(target)
        }
        onDispose { picasso.cancelRequest(target) }
    }
    Box(modifier.onSizeChanged { measuredSize = it }.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center) {
        val loaded = bitmap
        when {
            loaded != null -> Image(loaded.asImageBitmap(), description, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.BrokenImage, stringResource(R.string.image_preview_failed), Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { retry++; failed = false }, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.image_preview_retry))
                }
            }
            else -> LoadingIndicator(Modifier.size(40.dp))
        }
    }
}
