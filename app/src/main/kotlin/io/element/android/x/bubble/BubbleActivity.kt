/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.x.bubble

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import io.element.android.libraries.deeplink.api.DeepLinkCreator
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.x.MainActivity

/**
 * Activity used for Android Bubble notifications.
 *
 * This activity is marked with the required attributes for Android Bubbles:
 * - android:allowEmbedded="true" - Allows embedding in bubble window
 * - android:resizeableActivity="true" - Allows the activity to be resized
 * - android:documentLaunchMode="always" - Creates new document for each bubble
 *
 * When launched from a bubble, it opens the conversation in the main app.
 */
class BubbleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionIdValue = intent.getStringExtra(EXTRA_SESSION_ID)
        val roomIdValue = intent.getStringExtra(EXTRA_ROOM_ID)

        if (sessionIdValue != null && roomIdValue != null) {
            // Launch MainActivity with the room deep link
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = createDeepLink(sessionIdValue, roomIdValue).toUri()
                // Bring the main app to front if already running
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(mainIntent)
        }

        // Close this activity after launching the main app
        finish()
    }

    private fun createDeepLink(sessionId: String, roomId: String): String {
        // Match the format from DefaultDeepLinkCreator
        return buildString {
            append("$DEEP_LINK_SCHEME://$DEEP_LINK_HOST/")
            append(sessionId)
            append("/")
            append(roomId)
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "bubble_session_id"
        const val EXTRA_ROOM_ID = "bubble_room_id"

        private const val DEEP_LINK_SCHEME = "elementx"
        private const val DEEP_LINK_HOST = "open"

        /**
         * Creates an intent to launch this activity for a specific conversation.
         */
        fun createIntent(
            context: Context,
            sessionId: SessionId,
            roomId: RoomId,
        ): Intent {
            return Intent(context, BubbleActivity::class.java).apply {
                putExtra(EXTRA_SESSION_ID, sessionId.value)
                putExtra(EXTRA_ROOM_ID, roomId.value)
            }
        }
    }
}
