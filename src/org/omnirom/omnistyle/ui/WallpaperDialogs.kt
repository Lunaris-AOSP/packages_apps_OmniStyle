/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package org.omnirom.omnistyle.ui

import android.app.WallpaperManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.omnirom.omnistyle.R

@Composable
fun WallpaperDialogs(
    visible: Boolean,
    offerCrop: Boolean,
    galleryMissing: Boolean,
    onCropChoice: (Boolean) -> Unit,
    onDestination: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    when {
        galleryMissing -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Wallpaper, null) },
            title = { Text(stringResource(R.string.no_crop_activity_dialog_title)) },
            text = { Text(stringResource(R.string.no_crop_activity_dialog_text)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
        )
        visible && offerCrop -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Crop, null) },
            title = { Text(stringResource(R.string.scrolling_wall_dialog_title)) },
            text = { Text(stringResource(R.string.scrolling_wall_dialog_text)) },
            confirmButton = { TextButton(onClick = { onCropChoice(true) }) { Text(stringResource(R.string.crop_to_screen)) } },
            dismissButton = { TextButton(onClick = { onCropChoice(false) }) { Text(stringResource(R.string.keep_scrolling)) } },
        )
        visible -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Wallpaper, null) },
            title = { Text(stringResource(R.string.wallpaper_type_dialog_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        R.string.wallpaper_type_system to WallpaperManager.FLAG_SYSTEM,
                        R.string.wallpaper_type_lock to WallpaperManager.FLAG_LOCK,
                        R.string.wallpaper_type_both to (WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK),
                    ).forEach { (label, destination) ->
                        FilledTonalButton(onClick = { onDestination(destination) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(label))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
