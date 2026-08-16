/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.labs

import io.element.android.libraries.featureflag.ui.model.FeatureUiModel
import io.element.android.libraries.push.api.bubble.BubbleMode
import kotlinx.collections.immutable.ImmutableList

data class LabsState(
    val features: ImmutableList<FeatureUiModel>,
    val isApplyingChanges: Boolean,
    val bubbleSettings: BubbleSettings?,
    val eventSink: (LabsEvents) -> Unit,
) {
    data class BubbleSettings(
        val isEnabled: Boolean,
        val mode: BubbleMode,
        val showModeDialog: Boolean,
        val overlayPermissionGranted: Boolean,
    )
}
