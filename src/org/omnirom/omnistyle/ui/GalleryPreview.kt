/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package org.omnirom.omnistyle.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Date
import org.omnirom.omnistyle.R
import org.omnirom.omnistyle.GalleryAction
import org.omnirom.omnistyle.data.GalleryImage
import org.omnirom.omnistyle.data.GalleryKind

@Composable
internal fun GalleryPreviewSheet(
    image: GalleryImage,
    kind: GalleryKind,
    allowApply: Boolean,
    busy: Boolean,
    action: GalleryAction?,
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    onSave: () -> Unit,
) {
    val currentBusy by rememberUpdatedState(busy)
    val confirmChange = remember { { value: SheetValue -> value != SheetValue.Hidden || !currentBusy } }
    val sheet = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = confirmChange,
    )
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheet,
        sheetGesturesEnabled = !busy,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !busy),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(image.name, style = MaterialTheme.typography.headlineSmallEmphasized,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
                    if (image.creator.isNotBlank()) {
                        Text(image.creator, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                FilledTonalIconButton(onClick = onDismiss, enabled = !busy, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Default.Close, stringResource(R.string.close_preview))
                }
            }
            Text(stringResource(if (kind == GalleryKind.Headers) R.string.header_preview_label else R.string.wallpaper_preview_label),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (kind == GalleryKind.Headers) HeaderPreview(image) else WallpaperPreview(image)
            if (allowApply) {
                Button(
                    onClick = onApply,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                    shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(28.dp), pressedShape = RoundedCornerShape(16.dp)),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                ) {
                    if (action == GalleryAction.Apply) LoadingIndicator(Modifier.size(24.dp)) else Icon(Icons.Default.Check, null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(if (kind == GalleryKind.Headers) R.string.apply_header else R.string.apply_wallpaper),
                        style = MaterialTheme.typography.titleMediumEmphasized)
                }
            }
            FilledTonalButton(
                onClick = onSave,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(28.dp), pressedShape = RoundedCornerShape(16.dp)),
            ) {
                if (action == GalleryAction.Save) LoadingIndicator(Modifier.size(24.dp)) else Icon(Icons.Default.Download, null)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.save_image), style = MaterialTheme.typography.labelLargeEmphasized)
            }
            SnackbarHost(snackbarHostState)
        }
    }
}

@Composable
private fun HeaderPreview(image: GalleryImage) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            GalleryImageContent(image.fullUri, image.name, Modifier.fillMaxWidth().aspectRatio(4f))
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PreviewTile(Icons.Default.Wifi, stringResource(R.string.preview_wifi), true, Modifier.weight(1f))
                PreviewTile(Icons.Default.Bluetooth, stringResource(R.string.preview_bluetooth), false, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PreviewTile(icon: ImageVector, label: String, active: Boolean, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(if (active) 28.dp else 16.dp),
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun WallpaperPreview(image: GalleryImage) {
    val context = LocalContext.current
    val time = remember { DateFormat.getTimeFormat(context).format(Date()) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.widthIn(max = 240.dp).fillMaxWidth().aspectRatio(0.5f).clip(RoundedCornerShape(32.dp))) {
            GalleryImageContent(image.fullUri, image.name, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent))))
            Text(time, style = MaterialTheme.typography.displayMediumEmphasized,
                color = Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(top = 44.dp))
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).width(72.dp).height(4.dp)
                .clip(RoundedCornerShape(50)).background(Color.White))
        }
    }
}
