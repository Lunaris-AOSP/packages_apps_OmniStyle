/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.omnirom.omnistyle.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.omnirom.omnistyle.R
import org.omnirom.omnistyle.data.GalleryImage
import org.omnirom.omnistyle.data.GalleryKind
import org.omnirom.omnistyle.data.GallerySource

@Composable
internal fun GalleryHeading(kind: GalleryKind) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (kind == GalleryKind.Headers) R.string.browse_header else R.string.browse_walls),
                style = MaterialTheme.typography.displaySmallEmphasized,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(if (kind == GalleryKind.Headers) R.string.headers_description else R.string.wallpapers_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            Modifier.size(76.dp).clip(MaterialShapes.Cookie9Sided.toShape())
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (kind == GalleryKind.Headers) Icons.Default.Palette else Icons.Default.Wallpaper,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
internal fun SourceButtons(source: GallerySource, enabled: Boolean, onSource: (GallerySource) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        GallerySource.entries.forEachIndexed { index, entry ->
            val active = source == entry
            val shape = RoundedCornerShape(
                topStart = if (index == 0) 28.dp else 8.dp,
                bottomStart = if (index == 0) 28.dp else 8.dp,
                topEnd = if (index == 1) 28.dp else 8.dp,
                bottomEnd = if (index == 1) 28.dp else 8.dp,
            )
            Button(
                onClick = { onSource(entry) },
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp).semantics { selected = active },
                shapes = ButtonDefaults.shapes(shape = shape, pressedShape = RoundedCornerShape(16.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(
                    if (active) Icons.Default.Check else if (entry == GallerySource.Local) Icons.Default.Collections else Icons.Default.CloudQueue,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (entry == GallerySource.Local) R.string.wallpaper_location_local else R.string.wallpaper_location_remote),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}

@Composable
internal fun GalleryCard(
    image: GalleryImage,
    kind: GalleryKind,
    applied: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "image scale",
    )
    val corner by animateFloatAsState(
        if (pressed) 18f else 28f,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "image corner",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .semantics { selected = applied },
        interactionSource = interaction,
        shape = RoundedCornerShape(corner.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(if (kind == GalleryKind.Headers) 4f else 0.75f)) {
                GalleryImageContent(image.previewUri, stringResource(R.string.preview_image, image.name), Modifier.fillMaxSize())
                if (applied) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(Icons.Default.Check, stringResource(R.string.applied_header),
                            modifier = Modifier.padding(6.dp).size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(image.name, style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (image.creator.isNotBlank()) {
                    Text(image.creator, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
