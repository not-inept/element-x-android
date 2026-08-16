/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.preferences.api.store

/**
 * How message events are laid out in the timeline.
 */
enum class MessageLayoutMode {
    /** Each message sits in a rounded bubble; own messages are aligned to the end. */
    BUBBLES,

    /**
     * No bubbles. Every message is aligned to the start, under a header line
     * carrying the sender avatar and name on the start side and the timestamp
     * on the end side. Own messages look exactly like everyone else's.
     */
    FLAT;

    companion object {
        val Default: MessageLayoutMode = BUBBLES

        /** Parse a stored value, falling back to [Default] for anything unrecognised. */
        fun fromStorage(value: String?): MessageLayoutMode = entries.find { it.name == value } ?: Default
    }
}
