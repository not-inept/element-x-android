/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.impl.notifications.bubble

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.core.app.NotificationCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import coil3.ImageLoader
import dev.zacsweers.metro.Inject
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.push.api.bubble.BubbleMode
import io.element.android.libraries.push.api.notifications.NotificationBitmapLoader
import io.element.android.libraries.push.impl.intent.IntentProvider
import io.element.android.services.toolbox.api.systemclock.SystemClock

/**
 * Factory for creating Android Bubble notification metadata.
 *
 * Bubbles are available on Android 11 (API 30) and above, allowing
 * conversations to appear as floating windows.
 *
 * Supports three bubble modes:
 * - ALL: All conversations can bubble
 * - DMS_ONLY: Only direct messages bubble (default)
 * - SELECTED: Only user-selected rooms bubble
 */
@Inject
class BubbleMetadataFactory(
    @ApplicationContext private val context: Context,
    private val intentProvider: IntentProvider,
    private val bitmapLoader: NotificationBitmapLoader,
    private val clock: SystemClock,
) {
    /**
     * Returns true if the device supports Android Bubbles (API 30+).
     */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    fun isBubblesSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    }

    /**
     * Creates BubbleMetadata for a notification if bubbles are supported and enabled.
     *
     * @param sessionId The session/user ID
     * @param roomId The room ID for the conversation
     * @param roomName Display name of the room
     * @param roomAvatarUrl URL to the room's avatar image
     * @param isDm Whether this is a direct message room
     * @param imageLoader Coil image loader for loading the avatar
     * @param bubbleMode The user's bubble mode preference
     * @param isRoomBubbleEnabled Whether bubbles are enabled for this specific room (for SELECTED mode)
     * @return BubbleMetadata if bubbles should be shown, null otherwise
     */
    suspend fun createBubbleMetadata(
        sessionId: SessionId,
        roomId: RoomId,
        roomName: String,
        roomAvatarUrl: String?,
        isDm: Boolean,
        imageLoader: ImageLoader,
        bubbleMode: BubbleMode = BubbleMode.DMS_ONLY,
        isRoomBubbleEnabled: Boolean = false,
    ): NotificationCompat.BubbleMetadata? {
        // Check if bubbles are supported (API 30+)
        if (!isBubblesSupported()) {
            return null
        }

        // Check if bubbles should be shown based on mode
        val shouldShowBubble = when (bubbleMode) {
            BubbleMode.ALL -> true
            BubbleMode.DMS_ONLY -> isDm
            BubbleMode.SELECTED -> isRoomBubbleEnabled
        }

        if (!shouldShowBubble) {
            return null
        }

        return createBubbleMetadataInternal(
            sessionId = sessionId,
            roomId = roomId,
            roomName = roomName,
            roomAvatarUrl = roomAvatarUrl,
            imageLoader = imageLoader,
        )
    }

    private suspend fun createBubbleMetadataInternal(
        sessionId: SessionId,
        roomId: RoomId,
        roomName: String,
        roomAvatarUrl: String?,
        imageLoader: ImageLoader,
    ): NotificationCompat.BubbleMetadata {
        // Create the bubble intent
        val bubbleIntent = intentProvider.getBubbleIntent(sessionId, roomId)
        val bubblePendingIntent = PendingIntent.getActivity(
            context,
            clock.epochMillis().toInt(),
            bubbleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        // Load the room avatar for the bubble icon
        val icon = loadBubbleIcon(
            roomId = roomId,
            roomName = roomName,
            roomAvatarUrl = roomAvatarUrl,
            imageLoader = imageLoader,
        )

        // Build the bubble metadata
        return NotificationCompat.BubbleMetadata.Builder(bubblePendingIntent, icon)
            // Set the desired height for the expanded bubble
            .setDesiredHeight(BUBBLE_HEIGHT_DP)
            // Don't auto-expand - let the user choose to tap
            .setAutoExpandBubble(false)
            // Don't suppress the notification - show both bubble and notification
            .setSuppressNotification(false)
            .build()
    }

    private suspend fun loadBubbleIcon(
        roomId: RoomId,
        roomName: String,
        roomAvatarUrl: String?,
        imageLoader: ImageLoader,
    ): IconCompat {
        val iconSize = ShortcutManagerCompat.getIconMaxWidth(context)
        val avatarData = AvatarData(
            id = roomId.value,
            name = roomName,
            url = roomAvatarUrl,
            size = AvatarSize.RoomDetailsHeader,
        )

        val bitmap = bitmapLoader.getRoomBitmap(
            avatarData = avatarData,
            imageLoader = imageLoader,
            targetSize = iconSize.toLong(),
        )

        return if (bitmap != null) {
            IconCompat.createWithBitmap(bitmap)
        } else {
            // Fallback to app icon if avatar loading fails
            IconCompat.createWithResource(context, io.element.android.libraries.designsystem.R.drawable.ic_notification)
        }
    }

    companion object {
        // Default bubble height in DP
        private const val BUBBLE_HEIGHT_DP = 600
    }
}
