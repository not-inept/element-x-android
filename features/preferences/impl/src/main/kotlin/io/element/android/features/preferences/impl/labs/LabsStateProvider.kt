/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.labs

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.designsystem.icons.CompoundDrawables
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.featureflag.ui.model.FeatureUiModel
import io.element.android.libraries.push.api.bubble.BubbleMode
import kotlinx.collections.immutable.toImmutableList

internal class LabsStateProvider : PreviewParameterProvider<LabsState> {
    override val values: Sequence<LabsState>
        get() = sequenceOf(
            aLabsState(features = aFeatureList()),
            aLabsState(features = aFeatureList(), isApplyingChanges = true),
            aLabsState(features = aFeatureList(), bubbleSettings = aBubbleSettings(isEnabled = false)),
            aLabsState(features = aFeatureList(), bubbleSettings = aBubbleSettings(isEnabled = true)),
        )
}

internal fun aLabsState(
    features: List<FeatureUiModel> = emptyList(),
    isApplyingChanges: Boolean = false,
    bubbleSettings: LabsState.BubbleSettings? = aBubbleSettings(),
) = LabsState(
    features = features.toImmutableList(),
    isApplyingChanges = isApplyingChanges,
    bubbleSettings = bubbleSettings,
    eventSink = {},
)

internal fun aBubbleSettings(
    isEnabled: Boolean = false,
    mode: BubbleMode = BubbleMode.DMS_ONLY,
    showModeDialog: Boolean = false,
    overlayPermissionGranted: Boolean = true,
) = LabsState.BubbleSettings(
    isEnabled = isEnabled,
    mode = mode,
    showModeDialog = showModeDialog,
    overlayPermissionGranted = overlayPermissionGranted,
)

internal fun aFeatureList() = listOf(
    FeatureUiModel(
        key = "feature_1",
        title = "Feature 1",
        description = "This is a description of feature 1.",
        isEnabled = true,
        icon = IconSource.Resource(CompoundDrawables.ic_compound_threads),
    ),
    FeatureUiModel(
        key = "feature_2",
        title = "Feature 2",
        description = "This is a description of feature 2.",
        isEnabled = false,
        icon = IconSource.Resource(CompoundDrawables.ic_compound_video_call),
    )
)
