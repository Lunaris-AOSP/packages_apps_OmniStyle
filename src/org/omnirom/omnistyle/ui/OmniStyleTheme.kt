/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package org.omnirom.omnistyle.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.android.axion.compose.preferences.ExpressiveShapes
import com.android.axion.compose.theme.AxionTheme

@Composable
fun OmniStyleTheme(content: @Composable () -> Unit) {
    AxionTheme {
        MaterialTheme(shapes = ExpressiveShapes, content = content)
    }
}
