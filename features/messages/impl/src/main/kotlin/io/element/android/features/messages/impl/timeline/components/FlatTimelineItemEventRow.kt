/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.timeline.TimelineEvent
import io.element.android.features.messages.impl.timeline.TimelineRoomInfo
import io.element.android.features.messages.impl.timeline.aTimelineItemEvent
import io.element.android.features.messages.impl.timeline.aTimelineItemReactions
import io.element.android.features.messages.impl.timeline.components.layout.ContentAvoidingLayoutData
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.TimelineItemGroupPosition
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemImageContent
import io.element.android.features.messages.impl.timeline.model.event.isEdited
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemTextContent
import io.element.android.features.messages.impl.timeline.protection.TimelineProtectionState
import io.element.android.libraries.designsystem.colors.AvatarColorsProvider
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.modifiers.onKeyboardContextMenuAction
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.LocalMessageLayoutMode
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.event.MessageShield
import io.element.android.libraries.matrix.ui.messages.reply.InReplyToDetailsProvider
import io.element.android.libraries.matrix.ui.messages.sender.SenderName
import io.element.android.libraries.matrix.ui.messages.sender.SenderNameMode
import io.element.android.libraries.preferences.api.store.MessageLayoutMode
import io.element.android.libraries.testtags.TestTags
import io.element.android.libraries.testtags.testTag
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.libraries.ui.utils.a11y.isTalkbackActive

/** Horizontal inset shared by the header row and the message body. */
internal val FLAT_ROW_HORIZONTAL_PADDING = 16.dp

/** Gap between the avatar and the sender name. */
private val FLAT_AVATAR_SPACING = 8.dp

/**
 * How far the message body is indented, so that it lines up under the sender name
 * rather than under the avatar.
 */
internal val FLAT_CONTENT_INDENT = AvatarSize.TimelineSender.dp + FLAT_AVATAR_SPACING

/**
 * Media has no bubble constraining it any more, so cap it explicitly. Text is
 * deliberately left unconstrained: running the full width is the point of this layout.
 */
private const val FLAT_MEDIA_WIDTH_RATIO = 0.72f

/**
 * The "classic" message layout: no bubbles, everything aligned to the start.
 *
 * Each new run of messages from a sender opens with a header line carrying the avatar and
 * (colour-coded) display name at the start and the timestamp at the end. Subsequent messages
 * in the same run are bare, indented to line up under the name. Own messages are rendered
 * exactly like everyone else's — there is deliberately no `isMine` branching here, which is
 * what distinguishes this from [TimelineItemEventRow]'s bubble layout.
 */
@Composable
internal fun FlatTimelineItemEventRowContent(
    event: TimelineItem.Event,
    timelineMode: Timeline.Mode,
    timelineProtectionState: TimelineProtectionState,
    timelineRoomInfo: TimelineRoomInfo,
    interactionSource: MutableInteractionSource,
    onContentClick: () -> Unit,
    onLongClick: () -> Unit,
    inReplyToClick: () -> Unit,
    onUserDataClick: () -> Unit,
    onReactionClick: (emoji: String) -> Unit,
    onReactionLongClick: (emoji: String) -> Unit,
    onMoreReactionsClick: (event: TimelineItem.Event) -> Unit,
    eventSink: (TimelineEvent.TimelineItemEvent) -> Unit,
    modifier: Modifier = Modifier,
    eventContentView: @Composable (Modifier, (ContentAvoidingLayoutData) -> Unit) -> Unit,
) {
    // Unlike TimelineItem.Event.showSenderInformation, this deliberately ignores `isMine`:
    // in this layout own messages carry a header too, because nothing else identifies them.
    // It also ignores `isDm` — the timestamp lives on the header, so dropping the header in
    // DMs would drop the timestamp with it.
    val showHeader = event.groupPosition.isNew()
    val isEventPinned = timelineRoomInfo.pinnedEventIds.contains(event.eventId)

    // TimelineEventTimestampView is the only thing in the timeline that draws the send-failure
    // icon, the encryption shield and the "(edited)" marker, and it is the only tap target for
    // the shield dialog. The header carries it, so a message without a header would silently
    // drop all three. Put it back inline for those, and only for those, so the layout stays
    // clean in the common case.
    val needsInlineTimestamp = !showHeader &&
        (event.failedToSend || event.messageShield != null || event.content.isEdited())

    Column(modifier = modifier.fillMaxWidth()) {
        if (showHeader) {
            FlatMessageHeader(
                event = event,
                isEventPinned = isEventPinned,
                onUserDataClick = onUserDataClick,
                eventSink = eventSink,
            )
        }

        // Message body, indented to sit under the sender name.
        val clickableModifier = if (isTalkbackActive()) {
            Modifier
        } else {
            Modifier
                .combinedClickable(
                    onClick = onContentClick,
                    onLongClick = onLongClick,
                    indication = ripple(),
                    interactionSource = interactionSource,
                )
                // Matches MessageEventBubble: opens the action list from a hardware keyboard.
                .onKeyboardContextMenuAction(onLongClick)
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // A message with no header has an empty avatar gutter; put the pinned marker there,
            // so pinning the middle of a run is still visible. The bubble layout gates its pin
            // icon on isEventPinned alone, with no group-position condition.
            if (isEventPinned && !showHeader) {
                Icon(
                    imageVector = CompoundIcons.PinSolid(),
                    contentDescription = stringResource(CommonStrings.common_pinned),
                    tint = ElementTheme.colors.iconTertiary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = FLAT_ROW_HORIZONTAL_PADDING, top = 6.dp)
                        .size(16.dp),
                )
            }
            // Media used to be capped by the bubble's own widthIn; without a bubble it would
            // otherwise stretch the full width of the timeline. widthIn rather than
            // fillMaxWidth(fraction), which would pin small media (files, voice, stickers) to
            // exactly the ratio and leave the clip and tap target hanging in empty space.
            val widthModifier = if (event.content.isMedia) {
                Modifier.widthIn(max = maxWidth * FLAT_MEDIA_WIDTH_RATIO)
            } else {
                Modifier
            }
            MessageEventBubbleContent(
                event = event,
                timelineMode = timelineMode,
                timelineProtectionState = timelineProtectionState,
                onMessageLongClick = onLongClick,
                inReplyToClick = inReplyToClick,
                eventSink = eventSink,
                layoutMode = MessageLayoutMode.FLAT,
                hideTimestamp = !needsInlineTimestamp,
                bubbleModifier = Modifier
                    .testTag(TestTags.messageBubble)
                    .padding(
                        start = FLAT_ROW_HORIZONTAL_PADDING + FLAT_CONTENT_INDENT,
                        end = FLAT_ROW_HORIZONTAL_PADDING,
                    )
                    .then(widthModifier)
                    // Media has no bubble to clip it any more, so round it off here.
                    .clip(RoundedCornerShape(8.dp))
                    .then(clickableModifier),
                eventContentView = eventContentView,
            )
        }

        if (event.reactionsState.reactions.isNotEmpty()) {
            TimelineItemReactionsView(
                reactionsState = event.reactionsState,
                userCanSendReaction = timelineRoomInfo.userHasPermissionToSendReaction,
                // Always lay the reactions out from the start, like every other element of this layout.
                isOutgoing = false,
                onReactionClick = onReactionClick,
                onReactionLongClick = onReactionLongClick,
                onMoreReactionsClick = { onMoreReactionsClick(event) },
                modifier = Modifier.padding(
                    top = 4.dp,
                    start = FLAT_ROW_HORIZONTAL_PADDING + FLAT_CONTENT_INDENT,
                    end = FLAT_ROW_HORIZONTAL_PADDING,
                ),
            )
        }
    }
}

/**
 * The header line opening a run of messages: avatar and display name at the start,
 * timestamp (and the pinned marker, if any) at the end.
 */
@Composable
private fun FlatMessageHeader(
    event: TimelineItem.Event,
    isEventPinned: Boolean,
    onUserDataClick: () -> Unit,
    eventSink: (TimelineEvent.TimelineItemEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val avatarColors = AvatarColorsProvider.provide(event.senderAvatar.id)
    // No ripple, matching how the bubble layout makes the sender row tappable.
    val nameInteractionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FLAT_ROW_HORIZONTAL_PADDING, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // TimelineItemRow already sets an explicit merged contentDescription carrying the
        // sender name, so hide these the way the bubble layout's MessageSenderInformation
        // does. The timestamp below is deliberately left visible to accessibility: it is
        // what carries the send-failure and encryption-shield indicators.
        Avatar(
            modifier = Modifier
                .clearAndSetSemantics { hideFromAccessibility() }
                .testTag(TestTags.timelineItemSenderAvatar)
                .clip(CircleShape)
                .clickable(
                    interactionSource = nameInteractionSource,
                    indication = null,
                    onClick = onUserDataClick,
                ),
            avatarData = event.senderAvatar,
            avatarType = AvatarType.User,
        )
        Spacer(modifier = Modifier.width(FLAT_AVATAR_SPACING))
        // Takes the remaining width so the timestamp is pushed to the end and a long
        // display name truncates rather than shoving the timestamp off-screen.
        Box(
            modifier = Modifier
                .weight(1f)
                .clearAndSetSemantics { hideFromAccessibility() }
        ) {
            SenderName(
                modifier = Modifier
                    .testTag(TestTags.timelineItemSenderName)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(
                        interactionSource = nameInteractionSource,
                        indication = null,
                        onClick = onUserDataClick,
                    ),
                senderId = event.senderId,
                senderProfile = event.senderProfile,
                senderNameMode = SenderNameMode.Timeline(avatarColors.foreground),
            )
        }
        if (isEventPinned) {
            Icon(
                imageVector = CompoundIcons.PinSolid(),
                contentDescription = stringResource(CommonStrings.common_pinned),
                tint = ElementTheme.colors.iconTertiary,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(16.dp),
            )
        }
        TimelineEventTimestampView(
            event = event,
            eventSink = eventSink,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun FlatTimelineItemEventRowPreview() = ElementPreview {
    // The layout is selected via the CompositionLocal rather than a parameter, so the
    // preview has to provide it the same way ElementThemeApp does at runtime.
    CompositionLocalProvider(LocalMessageLayoutMode provides MessageLayoutMode.FLAT) {
        Column {
            ATimelineItemEventRow(
                event = aTimelineItemEvent(
                    senderDisplayName = "Alice",
                    isMine = false,
                    content = aTimelineItemTextContent(body = "Hey, are we still on for tonight?"),
                    groupPosition = TimelineItemGroupPosition.First,
                ),
            )
            // Same sender: no header, body stays indented under the name above. This one also
            // carries a critical shield, so the timestamp comes back inline — without it the
            // shield would have nowhere to render.
            ATimelineItemEventRow(
                event = aTimelineItemEvent(
                    senderDisplayName = "Alice",
                    isMine = false,
                    content = aTimelineItemTextContent(body = "I can bring dessert."),
                    groupPosition = TimelineItemGroupPosition.Last,
                    timelineItemReactions = aTimelineItemReactions(count = 0),
                    messageShield = MessageShield.UnknownDevice(isCritical = true),
                ),
            )
            // Own message: rendered exactly like the others, header and all.
            ATimelineItemEventRow(
                event = aTimelineItemEvent(
                    senderDisplayName = "Mason",
                    isMine = true,
                    content = aTimelineItemTextContent(
                        body = "A long text which will be displayed on several lines and" +
                            " hopefully can be manually adjusted to test different behaviors."
                    ),
                    groupPosition = TimelineItemGroupPosition.None,
                    timelineItemReactions = aTimelineItemReactions(count = 0),
                ),
            )
            ATimelineItemEventRow(
                event = aTimelineItemEvent(
                    senderDisplayName = "Sender with a super long name that should ellipsize",
                    isMine = false,
                    content = aTimelineItemImageContent(aspectRatio = 2.5f),
                    groupPosition = TimelineItemGroupPosition.None,
                    timelineItemReactions = aTimelineItemReactions(count = 0),
                ),
            )
            // A reply takes CommonLayout's EqualWidthColumn branch, which used to drop the
            // modifier carrying the indent, width cap, clip and tap target.
            ATimelineItemEventRow(
                event = aTimelineItemEvent(
                    senderDisplayName = "Bob",
                    isMine = false,
                    content = aTimelineItemTextContent(body = "Replying to you."),
                    timelineItemReactions = aTimelineItemReactions(count = 0),
                    inReplyTo = InReplyToDetailsProvider().values.first(),
                    groupPosition = TimelineItemGroupPosition.None,
                ),
            )
        }
    }
}
