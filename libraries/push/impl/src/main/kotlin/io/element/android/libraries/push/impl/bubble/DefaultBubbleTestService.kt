/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.impl.bubble

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.Settings
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.api.room.isDm
import io.element.android.libraries.matrix.api.roomlist.LatestEventValue
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.push.api.bubble.BubbleTestService
import io.element.android.libraries.push.impl.chathead.ChatHeadService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Service for showing floating chat head overlays with real DM conversations.
 * Uses SYSTEM_ALERT_WINDOW permission to draw over other apps.
 */
@ContributesBinding(SessionScope::class)
@Inject
class DefaultBubbleTestService(
    @ApplicationContext private val context: Context,
    private val matrixClient: MatrixClient,
) : BubbleTestService {

    override suspend fun showTestBubble() {
        // Check if we have overlay permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Timber.d("Cannot show chat heads - overlay permission not granted")
            return
        }

        // Get all room summaries and filter for DMs
        val roomSummaries = matrixClient.roomListService.allRooms.summaries.first()
        val dmRooms = roomSummaries
            .filter { it.info.isDm }
            .sortedByDescending { it.latestEventTimestamp ?: 0 }
            .take(10) // Limit to 10 most recent DMs

        Timber.d("Found ${dmRooms.size} DM rooms for chat heads")

        if (dmRooms.isEmpty()) {
            // No DMs, just show the hub
            ChatHeadService.showHub(context)
            return
        }

        // Load avatars in parallel
        coroutineScope {
            val conversationsWithAvatars = dmRooms.map { roomSummary ->
                async {
                    val roomInfo = roomSummary.info
                    val roomId = roomInfo.id.value
                    val name = roomInfo.name ?: "Chat"

                    // Get avatar URL - prefer hero avatar for DMs, fall back to room avatar
                    val avatarUrl = roomInfo.heroes.firstOrNull()?.avatarUrl
                        ?: roomInfo.avatarUrl

                    // Load avatar bitmap
                    val avatarBitmap = avatarUrl?.let { url ->
                        loadAvatarBitmap(url)
                    }

                    // Get last message preview
                    val lastMessage = when (val event = roomSummary.latestEvent) {
                        is LatestEventValue.Remote -> (event.content as? MessageContent)?.body
                        is LatestEventValue.Local -> (event.content as? MessageContent)?.body
                        else -> null
                    }

                    // Calculate unread count
                    val unreadCount = roomInfo.numUnreadNotifications.toInt()

                    ConversationInfo(
                        roomId = roomId,
                        name = name,
                        avatarBitmap = avatarBitmap,
                        lastMessage = lastMessage,
                        unreadCount = unreadCount
                    )
                }
            }.awaitAll()

            // Add all conversations to chat head service
            conversationsWithAvatars.forEach { info ->
                Timber.d("Adding DM: ${info.name} (roomId: ${info.roomId}, unread: ${info.unreadCount}, hasAvatar: ${info.avatarBitmap != null})")

                ChatHeadService.addConversation(
                    context = context,
                    roomId = info.roomId,
                    sessionId = matrixClient.sessionId.value,
                    name = info.name,
                    avatar = info.avatarBitmap,
                    lastMessage = info.lastMessage,
                    unreadCount = info.unreadCount,
                )
            }
        }
    }

    private suspend fun loadAvatarBitmap(avatarUrl: String): Bitmap? {
        return try {
            val mediaSource = MediaSource(url = avatarUrl)
            val result = matrixClient.matrixMediaLoader.loadMediaThumbnail(
                source = mediaSource,
                width = 128,
                height = 128
            )
            result.getOrNull()?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load avatar: $avatarUrl")
            null
        }
    }

    private data class ConversationInfo(
        val roomId: String,
        val name: String,
        val avatarBitmap: Bitmap?,
        val lastMessage: String?,
        val unreadCount: Int
    )
}
