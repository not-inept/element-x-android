/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomdetails.impl.notificationsettings

import io.element.android.libraries.architecture.AsyncAction
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.room.RoomNotificationMode
import io.element.android.libraries.matrix.api.room.RoomNotificationSettings
import io.element.android.libraries.push.api.bubble.BubbleMode

data class RoomNotificationSettingsState(
    val showUserDefinedSettingStyle: Boolean,
    val roomName: String,
    val roomNotificationSettings: AsyncData<RoomNotificationSettings>,
    val pendingRoomNotificationMode: RoomNotificationMode?,
    val pendingSetDefault: Boolean?,
    val defaultRoomNotificationMode: RoomNotificationMode?,
    val setNotificationSettingAction: AsyncAction<Unit>,
    val restoreDefaultAction: AsyncAction<Unit>,
    val displayMentionsOnlyDisclaimer: Boolean,
    val bubbleSettings: BubbleSettings?,
    val eventSink: (RoomNotificationSettingsEvents) -> Unit
) {
    /**
     * Settings for bubble notifications for this specific room.
     * Only available on Android 11+ (API 30+) when global bubble mode is SELECTED.
     */
    data class BubbleSettings(
        /** Whether bubbles are enabled for this specific room */
        val isEnabled: Boolean,
        /** The global bubble mode - the toggle is only shown when mode is SELECTED */
        val globalMode: BubbleMode,
    )
}

val RoomNotificationSettingsState.displayNotificationMode: RoomNotificationMode? get() {
    return pendingRoomNotificationMode ?: roomNotificationSettings.dataOrNull()?.mode
}

val RoomNotificationSettingsState.displayIsDefault: Boolean? get() {
    return pendingSetDefault ?: roomNotificationSettings.dataOrNull()?.isDefault
}
