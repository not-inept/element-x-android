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

sealed interface LabsEvents {
    data class ToggleFeature(val feature: FeatureUiModel) : LabsEvents
    data class SetBubblesEnabled(val enabled: Boolean) : LabsEvents
    data object ShowBubbleModeDialog : LabsEvents
    data object DismissBubbleModeDialog : LabsEvents
    data class SetBubbleMode(val mode: BubbleMode) : LabsEvents
    data object TestBubbleNotification : LabsEvents
    data object RequestOverlayPermission : LabsEvents
    data object RefreshOverlayPermission : LabsEvents
}
