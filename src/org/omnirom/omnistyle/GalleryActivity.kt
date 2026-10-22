/*
 * Copyright (C) 2024-2026 Lunaris AOSP
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package org.omnirom.omnistyle

import android.app.WallpaperManager
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.omnirom.omnistyle.data.GalleryKind
import org.omnirom.omnistyle.ui.GalleryScreen
import org.omnirom.omnistyle.ui.OmniStyleTheme
import org.omnirom.omnistyle.ui.WallpaperDialogs

abstract class GalleryActivity : ComponentActivity() {
    protected abstract val kind: GalleryKind
    protected open val allowApply = false
    protected open val onlineOnly = false
    protected open fun collectionFilter(): String? = null

    private val viewModel: GalleryViewModel by viewModels()
    private val snackbar = SnackbarHostState()
    private var cropUri by mutableStateOf<Uri?>(null)
    private var offerCrop by mutableStateOf(false)
    private var fitToScreen by mutableStateOf(true)
    private var galleryMissing by mutableStateOf(false)
    private val cropActivity = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        viewModel.initialize(kind, allowApply, onlineOnly, collectionFilter())
        cropUri = savedInstanceState?.getString("cropUri")?.let(Uri::parse)
        offerCrop = savedInstanceState?.getBoolean("offerCrop") ?: false
        fitToScreen = savedInstanceState?.getBoolean("fitToScreen") ?: true
        galleryMissing = savedInstanceState?.getBoolean("galleryMissing") ?: false
        setContent {
            OmniStyleTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                GalleryScreen(
                    state = state,
                    snackbarHostState = snackbar,
                    onBack = { finish() },
                    onSource = viewModel::setSource,
                    onCollection = viewModel::setCollection,
                    onSort = viewModel::setSort,
                    onRetry = { viewModel.load(force = true) },
                    onSelect = viewModel::select,
                    onApply = { image ->
                        if (kind == GalleryKind.Wallpapers && cropIntent().resolveActivity(packageManager) == null) {
                            viewModel.select(null)
                            galleryMissing = true
                        } else {
                            viewModel.apply(image)
                        }
                    },
                    onSave = viewModel::save,
                )
                WallpaperDialogs(
                    visible = cropUri != null,
                    offerCrop = offerCrop,
                    galleryMissing = galleryMissing,
                    onCropChoice = { fit -> fitToScreen = fit; offerCrop = false },
                    onDestination = ::launchCrop,
                    onDismiss = { cropUri = null; galleryMissing = false },
                )
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is GalleryEvent.Message -> snackbar.showSnackbar(getString(event.resource))
                        }
                    }
                }
                launch {
                    viewModel.state.map { it.pendingWallpaper }.distinctUntilChanged().filterNotNull().collect { request ->
                        val uri = Uri.parse(request)
                        if (cropUri != uri) prepareCrop(uri)
                        viewModel.completeWallpaperPreparation(uri)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAppliedHeader()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("cropUri", cropUri?.toString())
        outState.putBoolean("offerCrop", offerCrop)
        outState.putBoolean("fitToScreen", fitToScreen)
        outState.putBoolean("galleryMissing", galleryMissing)
        super.onSaveInstanceState(outState)
    }

    private fun cropIntent() = Intent().setComponent(ComponentName(
        "com.android.gallery3d", "com.android.gallery3d.filtershow.crop.CropActivity",
    ))

    private suspend fun prepareCrop(uri: Uri) {
        if (cropIntent().resolveActivity(packageManager) == null) {
            viewModel.select(null)
            galleryMissing = true
            return
        }
        try {
            val dimensions = withContext(Dispatchers.IO) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                check(options.outWidth > 0 && options.outHeight > 0)
                options.outWidth.toFloat() / options.outHeight
            }
            val bounds = windowManager.maximumWindowMetrics.bounds
            val displayRatio = bounds.width().toFloat() / bounds.height()
            fitToScreen = true
            offerCrop = kotlin.math.abs(dimensions - displayRatio) > 0.1f
            viewModel.select(null)
            cropUri = uri
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            snackbar.showSnackbar(getString(R.string.image_action_failed))
        }
    }

    private fun launchCrop(wallpaperType: Int) {
        val uri = cropUri ?: return
        val bounds = windowManager.maximumWindowMetrics.bounds
        val wallpaper = WallpaperManager.getInstance(this)
        val width = if (fitToScreen) bounds.width() else wallpaper.desiredMinimumWidth.coerceAtLeast(bounds.width())
        val height = if (fitToScreen) bounds.height() else wallpaper.desiredMinimumHeight.coerceAtLeast(bounds.height())
        val intent = cropIntent()
            .setDataAndType(uri, "image/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = ClipData.newRawUri("Wallpaper", uri) }
            .putExtra(CropExtras.KEY_OUTPUT_X, width)
            .putExtra(CropExtras.KEY_OUTPUT_Y, height)
            .putExtra(CropExtras.KEY_ASPECT_X, width)
            .putExtra(CropExtras.KEY_ASPECT_Y, height)
            .putExtra(CropExtras.KEY_SPOTLIGHT_X, bounds.width().toFloat() / width)
            .putExtra(CropExtras.KEY_SPOTLIGHT_Y, bounds.height().toFloat() / height)
            .putExtra(CropExtras.KEY_SCALE, true)
            .putExtra(CropExtras.KEY_SCALE_UP_IF_NEEDED, true)
            .putExtra(CropExtras.KEY_SET_AS_WALLPAPER, true)
            .putExtra(CropExtras.KEY_WALLPAPER_TYPE, wallpaperType)
        try {
            cropActivity.launch(intent)
            cropUri = null
        } catch (error: Exception) {
            galleryMissing = true
            cropUri = null
        }
    }
}

open class BrowseHeaderActivity : GalleryActivity() {
    override val kind = GalleryKind.Headers
}

class PickHeaderActivity : BrowseHeaderActivity() {
    override val allowApply = true
}

class BrowseThemeHeaderActivity : GalleryActivity() {
    override val kind = GalleryKind.Headers
    override val allowApply = true
    override fun collectionFilter() = "theme"
}

class BrowseWallsActivity : GalleryActivity() {
    override val kind = GalleryKind.Wallpapers
    override val allowApply = true
    override fun collectionFilter() = intent.getStringExtra("sortWallpapers")
}

class BrowseWallsFilterActivity : GalleryActivity() {
    override val kind = GalleryKind.Wallpapers
    override val allowApply = true
    override val onlineOnly = true
    override fun collectionFilter() = intent.getStringExtra("filter") ?: "theme"
}
