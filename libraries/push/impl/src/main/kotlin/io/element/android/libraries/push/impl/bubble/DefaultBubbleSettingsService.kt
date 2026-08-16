/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.impl.bubble

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import io.element.android.libraries.push.api.bubble.BubbleMode
import io.element.android.libraries.push.api.bubble.BubbleSettingsService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@ContributesBinding(SessionScope::class)
class DefaultBubbleSettingsService(
    private val sessionPreferencesStore: SessionPreferencesStore,
) : BubbleSettingsService {

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    override fun isBubblesSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    override fun getBubbleMode(): Flow<BubbleMode> {
        return sessionPreferencesStore.getBubbleMode().map { modeName ->
            try {
                BubbleMode.valueOf(modeName)
            } catch (e: IllegalArgumentException) {
                BubbleMode.DMS_ONLY
            }
        }
    }

    override suspend fun setBubbleMode(mode: BubbleMode) {
        sessionPreferencesStore.setBubbleMode(mode.name)
    }

    override fun shouldBubble(roomId: RoomId, isDm: Boolean): Flow<Boolean> {
        if (!isBubblesSupported()) {
            return flowOf(false)
        }

        return combine(
            getBubbleMode(),
            isBubbleEnabledForRoom(roomId)
        ) { mode, isRoomEnabled ->
            when (mode) {
                BubbleMode.ALL -> true
                BubbleMode.DMS_ONLY -> isDm
                BubbleMode.SELECTED -> isRoomEnabled
            }
        }
    }

    override fun isBubbleEnabledForRoom(roomId: RoomId): Flow<Boolean> {
        return sessionPreferencesStore.isBubbleEnabledForRoom(roomId.value)
    }

    override suspend fun setBubbleEnabledForRoom(roomId: RoomId, enabled: Boolean) {
        sessionPreferencesStore.setBubbleEnabledForRoom(roomId.value, enabled)
    }
}
