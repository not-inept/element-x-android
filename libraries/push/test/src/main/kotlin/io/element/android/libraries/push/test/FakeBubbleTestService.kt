/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.test

import io.element.android.libraries.push.api.bubble.BubbleTestService

class FakeBubbleTestService(
    private val showTestBubbleLambda: suspend () -> Unit = {}
) : BubbleTestService {
    override suspend fun showTestBubble() {
        showTestBubbleLambda()
    }
}
