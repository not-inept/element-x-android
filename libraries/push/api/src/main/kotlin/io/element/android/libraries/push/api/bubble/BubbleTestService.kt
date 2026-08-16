/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.api.bubble

/**
 * Service to trigger test bubble notifications for debugging purposes.
 */
interface BubbleTestService {
    /**
     * Shows a test bubble notification.
     * This creates a simple notification with bubble metadata to test the bubble feature.
     */
    suspend fun showTestBubble()
}
