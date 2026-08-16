/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.api.bubble

/**
 * Defines which conversations can appear as Android Bubbles (chat heads).
 * Bubbles are available on Android 11 (API 30) and above.
 */
enum class BubbleMode {
    /**
     * All conversations can appear as bubbles.
     */
    ALL,

    /**
     * Only direct message (1:1) conversations can appear as bubbles.
     */
    DMS_ONLY,

    /**
     * Only user-selected rooms can appear as bubbles.
     * Users mark specific rooms as "bubble enabled" in room settings.
     */
    SELECTED
}
