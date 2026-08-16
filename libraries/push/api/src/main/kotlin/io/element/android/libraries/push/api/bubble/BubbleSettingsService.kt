/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.api.bubble

import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.flow.Flow

/**
 * Service for managing Android Bubble (chat head) settings.
 * Bubbles allow conversations to appear as floating windows.
 */
interface BubbleSettingsService {
    /**
     * Returns true if the device supports Android Bubbles (API 30+).
     */
    fun isBubblesSupported(): Boolean

    /**
     * Returns the current bubble mode setting.
     */
    fun getBubbleMode(): Flow<BubbleMode>

    /**
     * Sets the bubble mode setting.
     */
    suspend fun setBubbleMode(mode: BubbleMode)

    /**
     * Checks if bubbles are enabled for a specific room based on current settings.
     *
     * @param roomId The room to check
     * @param isDm Whether the room is a direct message (1:1) conversation
     * @return Flow that emits true if the room should show as a bubble
     */
    fun shouldBubble(roomId: RoomId, isDm: Boolean): Flow<Boolean>

    /**
     * Gets whether a specific room has bubble notifications enabled.
     * Only used when [BubbleMode.SELECTED] is active.
     */
    fun isBubbleEnabledForRoom(roomId: RoomId): Flow<Boolean>

    /**
     * Sets whether a specific room should show as a bubble.
     * Only applies when [BubbleMode.SELECTED] is active.
     */
    suspend fun setBubbleEnabledForRoom(roomId: RoomId, enabled: Boolean)
}
