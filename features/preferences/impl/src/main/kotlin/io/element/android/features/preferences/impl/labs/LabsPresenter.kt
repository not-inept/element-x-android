/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.labs

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import dev.zacsweers.metro.Inject
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.preferences.impl.R
import io.element.android.features.preferences.impl.model.EnabledFeature
import io.element.android.features.preferences.impl.tasks.ClearCacheUseCase
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.ui.model.FeatureUiModel
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import io.element.android.libraries.push.api.bubble.BubbleMode
import io.element.android.libraries.push.api.bubble.BubbleTestService
import io.element.android.services.toolbox.api.strings.StringProvider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Inject
class LabsPresenter(
    @ApplicationContext private val context: Context,
    private val stringProvider: StringProvider,
    private val featureFlagService: FeatureFlagService,
    private val clearCacheUseCase: ClearCacheUseCase,
    private val sessionPreferencesStore: SessionPreferencesStore,
    private val bubbleTestService: BubbleTestService,
) : Presenter<LabsState> {
    @Composable
    override fun present(): LabsState {
        val coroutineScope = rememberCoroutineScope()
        val enabledFeatures = remember {
            mutableStateListOf<EnabledFeature>()
        }
        LaunchedEffect(Unit) {
            featureFlagService.getAvailableFeatures(isInLabs = true)
                .forEach { feature ->
                    enabledFeatures.add(EnabledFeature(feature, featureFlagService.isFeatureEnabled(feature)))
                }
        }
        var isApplyingChanges by remember { mutableStateOf(false) }
        val featureUiModels = createUiModels(enabledFeatures)

        // Bubble/Chat head settings - available on Android 6+ (API 23) for overlay permission
        val isChatHeadsSupported = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.M }
        val bubblesEnabled by sessionPreferencesStore.areBubblesEnabled()
            .collectAsState(initial = false)
        val bubbleMode by remember {
            sessionPreferencesStore.getBubbleMode().map { modeString ->
                BubbleMode.entries.find { it.name == modeString } ?: BubbleMode.DMS_ONLY
            }
        }.collectAsState(initial = BubbleMode.DMS_ONLY)
        var showBubbleModeDialog by remember { mutableStateOf(false) }
        var overlayPermissionGranted by remember { mutableStateOf(false) }

        // Check overlay permission
        fun checkOverlayPermission(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        // Initial permission check
        LaunchedEffect(Unit) {
            overlayPermissionGranted = checkOverlayPermission()
        }

        val bubbleSettings = if (isChatHeadsSupported) {
            LabsState.BubbleSettings(
                isEnabled = bubblesEnabled,
                mode = bubbleMode,
                showModeDialog = showBubbleModeDialog,
                overlayPermissionGranted = overlayPermissionGranted,
            )
        } else {
            null
        }

        fun handleEvent(event: LabsEvents) {
            when (event) {
                is LabsEvents.ToggleFeature -> coroutineScope.launch {
                    val featureIndex = enabledFeatures.indexOfFirst { it.feature.key == event.feature.key }.takeIf { it != -1 } ?: return@launch
                    val enabledFeature = enabledFeatures[featureIndex]
                    val feature = enabledFeature.feature
                    val newValue = enabledFeature.isEnabled.not()
                    if (featureFlagService.setFeatureEnabled(feature, newValue)) {
                        enabledFeatures[featureIndex] = enabledFeatures[featureIndex].copy(isEnabled = newValue)
                        when (feature.key) {
                            FeatureFlags.Threads.key -> {
                                // Threads require a cache clear to recreate the event cache
                                clearCacheUseCase()
                                isApplyingChanges = true
                            }
                        }
                    }
                }
                is LabsEvents.SetBubblesEnabled -> coroutineScope.launch {
                    sessionPreferencesStore.setBubblesEnabled(event.enabled)
                }
                LabsEvents.ShowBubbleModeDialog -> showBubbleModeDialog = true
                LabsEvents.DismissBubbleModeDialog -> showBubbleModeDialog = false
                is LabsEvents.SetBubbleMode -> coroutineScope.launch {
                    sessionPreferencesStore.setBubbleMode(event.mode.name)
                    showBubbleModeDialog = false
                }
                LabsEvents.TestBubbleNotification -> coroutineScope.launch {
                    bubbleTestService.showTestBubble()
                }
                LabsEvents.RequestOverlayPermission -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                }
                LabsEvents.RefreshOverlayPermission -> {
                    overlayPermissionGranted = checkOverlayPermission()
                }
            }
        }
        return LabsState(
            features = featureUiModels,
            isApplyingChanges = isApplyingChanges,
            bubbleSettings = bubbleSettings,
            eventSink = ::handleEvent,
        )
    }

    @Composable
    private fun createUiModels(
        enabledFeatures: SnapshotStateList<EnabledFeature>,
    ): ImmutableList<FeatureUiModel> {
        return enabledFeatures.map { enabledFeature ->
            key(enabledFeature.feature.key) {
                val title = when (enabledFeature.feature) {
                    FeatureFlags.Threads -> stringProvider.getString(R.string.screen_labs_enable_threads)
                    else -> enabledFeature.feature.title
                }
                val description = when (enabledFeature.feature) {
                    FeatureFlags.Threads -> stringProvider.getString(R.string.screen_labs_enable_threads_description)
                    else -> enabledFeature.feature.description
                }
                val icon = when (enabledFeature.feature) {
                    FeatureFlags.Threads -> CompoundIcons.Threads()
                    else -> null
                }
                remember(enabledFeature) {
                    FeatureUiModel(
                        key = enabledFeature.feature.key,
                        title = title,
                        description = description,
                        icon = icon?.let(IconSource::Vector),
                        isEnabled = enabledFeature.isEnabled
                    )
                }
            }
        }.toImmutableList()
    }
}
