/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.impl.notifications

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.graphics.toArgb
import coil3.ImageLoader
import dev.zacsweers.metro.Inject
import io.element.android.appconfig.NotificationConfig
import io.element.android.features.enterprise.api.EnterpriseService
import io.element.android.libraries.core.log.logger.LoggerTag
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import io.element.android.libraries.preferences.api.store.SessionPreferencesStoreFactory
import io.element.android.libraries.push.api.bubble.BubbleMode
import io.element.android.libraries.push.api.notifications.NotificationIdProvider
import io.element.android.libraries.push.impl.chathead.ChatHeadService
import io.element.android.libraries.push.impl.notifications.factories.NotificationAccountParams
import io.element.android.libraries.push.impl.notifications.factories.NotificationCreator
import io.element.android.libraries.push.impl.notifications.model.FallbackNotifiableEvent
import io.element.android.libraries.push.impl.notifications.model.InviteNotifiableEvent
import io.element.android.libraries.push.impl.notifications.model.NotifiableEvent
import io.element.android.libraries.push.impl.notifications.model.NotifiableMessageEvent
import io.element.android.libraries.push.impl.notifications.model.NotifiableRingingCallEvent
import io.element.android.libraries.push.impl.notifications.model.SimpleNotifiableEvent
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.File

private val loggerTag = LoggerTag("NotificationRenderer", LoggerTag.NotificationLoggerTag)

@Inject
class NotificationRenderer(
    @ApplicationContext private val context: Context,
    private val notificationDisplayer: NotificationDisplayer,
    private val notificationDataFactory: NotificationDataFactory,
    private val enterpriseService: EnterpriseService,
    private val sessionStore: SessionStore,
    private val sessionPreferencesStoreFactory: SessionPreferencesStoreFactory,
) {
    suspend fun render(
        currentUser: MatrixUser,
        useCompleteNotificationFormat: Boolean,
        eventsToProcess: List<NotifiableEvent>,
        imageLoader: ImageLoader,
    ) {
        val color = enterpriseService.brandColorsFlow(currentUser.userId).first()?.toArgb()
            ?: NotificationConfig.NOTIFICATION_ACCENT_COLOR
        val numberOfAccounts = sessionStore.numberOfSessions()
        val notificationAccountParams = NotificationAccountParams(
            user = currentUser,
            color = color,
            showSessionId = numberOfAccounts > 1,
        )
        val groupedEvents = eventsToProcess.groupByType()
        val roomNotifications = notificationDataFactory.toNotifications(groupedEvents.roomEvents, imageLoader, notificationAccountParams)
        val invitationNotifications = notificationDataFactory.toNotifications(groupedEvents.invitationEvents, notificationAccountParams)
        val simpleNotifications = notificationDataFactory.toNotifications(groupedEvents.simpleEvents, notificationAccountParams)
        val fallbackNotifications = notificationDataFactory.toNotifications(groupedEvents.fallbackEvents, notificationAccountParams)
        val summaryNotification = notificationDataFactory.createSummaryNotification(
            roomNotifications = roomNotifications,
            invitationNotifications = invitationNotifications,
            simpleNotifications = simpleNotifications,
            fallbackNotifications = fallbackNotifications,
            notificationAccountParams = notificationAccountParams,
        )

        // Remove summary first to avoid briefly displaying it after dismissing the last notification
        if (summaryNotification == SummaryNotification.Removed) {
            Timber.tag(loggerTag.value).d("Removing summary notification")
            notificationDisplayer.cancelNotification(
                tag = null,
                id = NotificationIdProvider.getSummaryNotificationId(currentUser.userId)
            )
        }

        // Check if chat heads are enabled
        @Suppress("OPT_IN_USAGE")
        val sessionPreferencesStore = sessionPreferencesStoreFactory.get(currentUser.userId, GlobalScope)
        val bubblesEnabled = sessionPreferencesStore.areBubblesEnabled().firstOrNull() ?: false
        val bubbleMode = sessionPreferencesStore.getBubbleMode().map { modeString ->
            BubbleMode.entries.find { it.name == modeString } ?: BubbleMode.DMS_ONLY
        }.firstOrNull() ?: BubbleMode.DMS_ONLY
        val canDrawOverlays = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

        roomNotifications.forEach { notificationData ->
            val tag = NotificationCreator.messageTag(
                roomId = notificationData.roomId,
                threadId = notificationData.threadId
            )
            notificationDisplayer.showNotification(
                tag = tag,
                id = NotificationIdProvider.getRoomMessagesNotificationId(currentUser.userId),
                notification = notificationData.notification
            )

            // Show chat head if enabled and matches criteria
            if (bubblesEnabled && canDrawOverlays) {
                val shouldShowChatHead = when (bubbleMode) {
                    BubbleMode.ALL -> true
                    BubbleMode.DMS_ONLY -> notificationData.isDm
                    BubbleMode.SELECTED -> false // TODO: Implement selected rooms
                }

                if (shouldShowChatHead) {
                    Timber.tag(loggerTag.value).d("Showing chat head for room ${notificationData.roomId}")
                    val avatarBitmap = notificationData.senderAvatarPath?.let { path ->
                        try {
                            BitmapFactory.decodeFile(path)
                        } catch (e: Exception) {
                            Timber.tag(loggerTag.value).w(e, "Failed to load avatar from $path")
                            null
                        }
                    }
                    ChatHeadService.addConversation(
                        context = context,
                        roomId = notificationData.roomId.value,
                        sessionId = notificationData.sessionId.value,
                        name = notificationData.senderName ?: "Chat",
                        avatar = avatarBitmap,
                        lastMessage = notificationData.summaryLine.toString(),
                        unreadCount = notificationData.messageCount,
                    )
                }
            }
        }

        invitationNotifications.forEach { notificationData ->
            if (useCompleteNotificationFormat) {
                Timber.tag(loggerTag.value).d("Updating invitation notification ${notificationData.tag}")
                notificationDisplayer.showNotification(
                    tag = notificationData.tag,
                    id = NotificationIdProvider.getRoomInvitationNotificationId(currentUser.userId),
                    notification = notificationData.notification
                )
            }
        }

        simpleNotifications.forEach { notificationData ->
            if (useCompleteNotificationFormat) {
                Timber.tag(loggerTag.value).d("Updating simple notification ${notificationData.tag}")
                notificationDisplayer.showNotification(
                    tag = notificationData.tag,
                    id = NotificationIdProvider.getRoomEventNotificationId(currentUser.userId),
                    notification = notificationData.notification
                )
            }
        }

        // Show only the first fallback notification
        if (fallbackNotifications.isNotEmpty()) {
            Timber.tag(loggerTag.value).d("Showing fallback notification")
            notificationDisplayer.showNotification(
                tag = "FALLBACK",
                id = NotificationIdProvider.getFallbackNotificationId(currentUser.userId),
                notification = fallbackNotifications.first().notification
            )
        }

        // Update summary last to avoid briefly displaying it before other notifications
        if (summaryNotification is SummaryNotification.Update) {
            Timber.tag(loggerTag.value).d("Updating summary notification")
            notificationDisplayer.showNotification(
                tag = null,
                id = NotificationIdProvider.getSummaryNotificationId(currentUser.userId),
                notification = summaryNotification.notification
            )
        }
    }
}

private fun List<NotifiableEvent>.groupByType(): GroupedNotificationEvents {
    val roomEvents: MutableList<NotifiableMessageEvent> = mutableListOf()
    val simpleEvents: MutableList<SimpleNotifiableEvent> = mutableListOf()
    val invitationEvents: MutableList<InviteNotifiableEvent> = mutableListOf()
    val fallbackEvents: MutableList<FallbackNotifiableEvent> = mutableListOf()
    forEach { event ->
        when (event) {
            is InviteNotifiableEvent -> invitationEvents.add(event.castedToEventType())
            is NotifiableMessageEvent -> roomEvents.add(event.castedToEventType())
            is SimpleNotifiableEvent -> simpleEvents.add(event.castedToEventType())
            is FallbackNotifiableEvent -> fallbackEvents.add(event.castedToEventType())
            // Nothing should be done for ringing call events as they're not handled here
            is NotifiableRingingCallEvent -> {}
        }
    }
    return GroupedNotificationEvents(roomEvents, simpleEvents, invitationEvents, fallbackEvents)
}

@Suppress("UNCHECKED_CAST")
private fun <T : NotifiableEvent> NotifiableEvent.castedToEventType(): T = this as T

data class GroupedNotificationEvents(
    val roomEvents: List<NotifiableMessageEvent>,
    val simpleEvents: List<SimpleNotifiableEvent>,
    val invitationEvents: List<InviteNotifiableEvent>,
    val fallbackEvents: List<FallbackNotifiableEvent>,
)
