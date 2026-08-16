/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.push.impl.chathead

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import io.element.android.libraries.designsystem.utils.CommonDrawables
import io.element.android.libraries.push.impl.R

/**
 * Service that manages floating chat head overlays with Facebook Messenger-like experience.
 * Features:
 * - Hub chat head that expands to show conversation list
 * - Multiple individual conversation chat heads
 * - Floating conversation window overlay
 * - Snap to screen edges
 * - Drag to remove zone
 */
class ChatHeadService : Service() {

    private lateinit var windowManager: WindowManager

    // Hub chat head (main Element icon)
    private var hubChatHead: View? = null
    private var hubParams: WindowManager.LayoutParams? = null

    // Expanded conversation list
    private var expandedView: View? = null
    private var expandedParams: WindowManager.LayoutParams? = null
    private var isExpanded = false

    // Individual conversation chat heads
    private val conversationHeads = mutableMapOf<String, ConversationHead>()

    // Floating conversation window
    private var conversationWindow: View? = null
    private var conversationWindowParams: WindowManager.LayoutParams? = null
    private var currentOpenConversation: ConversationData? = null

    // Remove zone
    private var removeZoneView: View? = null
    private var removeZoneParams: WindowManager.LayoutParams? = null

    // Screen dimensions
    private var screenWidth = 0
    private var screenHeight = 0

    // Drag tracking
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    // Conversations data
    private val conversations = mutableListOf<ConversationData>()

    data class ConversationHead(
        val view: View,
        val params: WindowManager.LayoutParams,
        val data: ConversationData
    )

    data class ConversationData(
        val roomId: String,
        val sessionId: String,
        val name: String,
        val avatarBitmap: Bitmap?,
        val lastMessage: String? = null,
        val unreadCount: Int = 0
    )

    // Element-style avatar colors (from Compound design system)
    private val avatarColors = listOf(
        0xFFE9F2FF.toInt() to 0xFF0467DD.toInt(), // Blue
        0xFFE3F7ED.toInt() to 0xFF1B7A4F.toInt(), // Green
        0xFFFCF2D8.toInt() to 0xFF9E6C00.toInt(), // Yellow
        0xFFFFECEC.toInt() to 0xFFCD3654.toInt(), // Red
        0xFFF3EAFD.toInt() to 0xFF7F44B5.toInt(), // Purple
        0xFFE5F5FC.toInt() to 0xFF1277B4.toInt(), // Cyan
        0xFFFFF0E1.toInt() to 0xFFC95807.toInt(), // Orange
        0xFFFFECF5.toInt() to 0xFFB23074.toInt(), // Pink
    )

    private fun getAvatarColors(id: String): Pair<Int, Int> {
        val hash = id.sumOf { it.code } % avatarColors.size
        return avatarColors[hash]
    }

    private fun getInitialLetter(name: String, id: String): String {
        val source = name.takeIf { it.isNotBlank() } ?: id
        var startIndex = 0
        if (source.isNotEmpty() && source[0] in listOf('@', '#', '+', '!')) {
            startIndex = 1.coerceAtMost(source.length - 1)
        }
        return source.getOrNull(startIndex)?.uppercase() ?: "#"
    }

    private fun createInitialAvatarBitmap(name: String, id: String, sizePx: Int): Bitmap {
        val (bgColor, textColor) = getAvatarColors(id)
        val initial = getInitialLetter(name, id)

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw circular background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, bgPaint)

        // Draw initial letter
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = sizePx * 0.45f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val textY = sizePx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(initial, sizePx / 2f, textY, textPaint)

        return bitmap
    }

    private fun getAvatarBitmap(conversation: ConversationData, sizePx: Int): Bitmap {
        return conversation.avatarBitmap ?: createInitialAvatarBitmap(
            conversation.name,
            conversation.roomId,
            sizePx
        )
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        updateScreenDimensions()
    }

    private fun updateScreenDimensions() {
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_HUB -> {
                showHubChatHead()
            }
            ACTION_ADD_CONVERSATION -> {
                val roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: return START_NOT_STICKY
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: return START_NOT_STICKY
                val name = intent.getStringExtra(EXTRA_NAME) ?: "Chat"
                val avatarBytes = intent.getByteArrayExtra(EXTRA_AVATAR)
                val lastMessage = intent.getStringExtra(EXTRA_LAST_MESSAGE)
                val unreadCount = intent.getIntExtra(EXTRA_UNREAD_COUNT, 0)

                val avatarBitmap = avatarBytes?.let {
                    BitmapFactory.decodeByteArray(it, 0, it.size)
                }

                addConversation(ConversationData(
                    roomId = roomId,
                    sessionId = sessionId,
                    name = name,
                    avatarBitmap = avatarBitmap,
                    lastMessage = lastMessage,
                    unreadCount = unreadCount
                ))
            }
            ACTION_HIDE_ALL -> {
                hideAllAndStop()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chat Heads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Floating chat head notifications"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): Notification {
        val stopIntent = Intent(this, ChatHeadService::class.java).apply {
            action = ACTION_HIDE_ALL
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Chat heads active")
            .setContentText("Tap to close all chat heads")
            .setSmallIcon(CommonDrawables.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(stopPendingIntent)
            .build()
    }

    private fun showHubChatHead() {
        if (hubChatHead != null) return

        startForeground(NOTIFICATION_ID, createForegroundNotification())

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        hubChatHead = inflater.inflate(R.layout.chat_head_view, null)

        // Use app icon
        val avatarView = hubChatHead?.findViewById<ImageView>(R.id.chat_head_avatar)
        val appIcon = getAppLauncherIcon()
        if (appIcon != null) {
            avatarView?.setImageDrawable(appIcon)
        } else {
            avatarView?.setImageResource(CommonDrawables.ic_notification)
        }

        // Show unread badge if there are conversations
        updateHubBadge()

        val layoutFlag = getWindowLayoutFlag()
        val chatHeadSize = (64 * resources.displayMetrics.density).toInt()

        hubParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        setupHubTouchListener(chatHeadSize)

        try {
            windowManager.addView(hubChatHead, hubParams)
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun setupHubTouchListener(chatHeadSize: Int) {
        hubChatHead?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = hubParams?.x ?: 0
                    initialY = hubParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY

                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        isDragging = true
                        showRemoveZone()
                        // Close expanded view if dragging
                        if (isExpanded) {
                            hideExpandedView()
                        }
                    }

                    hubParams?.x = initialX + deltaX.toInt()
                    hubParams?.y = initialY + deltaY.toInt()
                    windowManager.updateViewLayout(hubChatHead, hubParams)

                    updateRemoveZoneHighlight(hubParams?.y ?: 0, chatHeadSize)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    hideRemoveZone()

                    if (isInRemoveZone(hubParams?.y ?: 0, chatHeadSize)) {
                        hideAllAndStop()
                        return@setOnTouchListener true
                    }

                    if (!isDragging) {
                        // Tap - toggle expanded view
                        toggleExpandedView()
                    } else {
                        snapToEdge(hubParams, hubChatHead, chatHeadSize)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    hideRemoveZone()
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleExpandedView() {
        if (isExpanded) {
            hideExpandedView()
        } else {
            showExpandedView()
        }
    }

    private fun showExpandedView() {
        if (expandedView != null || conversations.isEmpty()) return

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        expandedView = inflater.inflate(R.layout.chat_head_expanded_view, null)

        // Setup close button
        expandedView?.findViewById<ImageView>(R.id.close_button)?.setOnClickListener {
            hideExpandedView()
        }

        // Populate conversation list
        val listContainer = expandedView?.findViewById<LinearLayout>(R.id.conversation_list)
        conversations.forEach { conversation ->
            val itemView = createConversationListItem(conversation)
            listContainer?.addView(itemView)
        }

        val layoutFlag = getWindowLayoutFlag()

        // Position next to hub
        val hubX = hubParams?.x ?: 0
        val hubY = hubParams?.y ?: 0

        expandedParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = hubX + (64 * resources.displayMetrics.density).toInt()
            y = hubY
        }

        try {
            windowManager.addView(expandedView, expandedParams)
            isExpanded = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createConversationListItem(conversation: ConversationData): View {
        val itemSize = (56 * resources.displayMetrics.density).toInt()
        val margin = (4 * resources.displayMetrics.density).toInt()

        val container = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(itemSize, itemSize).apply {
                setMargins(0, margin, 0, margin)
            }
        }

        val avatarSize = (48 * resources.displayMetrics.density).toInt()
        val avatarBitmap = getAvatarBitmap(conversation, avatarSize)

        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            clipToOutline = true
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(avatarBitmap)
        }
        container.addView(imageView)

        // Add unread badge if needed
        if (conversation.unreadCount > 0) {
            val badge = TextView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    (20 * resources.displayMetrics.density).toInt(),
                    (20 * resources.displayMetrics.density).toInt(),
                    Gravity.TOP or Gravity.END
                )
                setBackgroundResource(R.drawable.chat_head_badge_background)
                gravity = Gravity.CENTER
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 10f
                text = if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString()
            }
            container.addView(badge)
        }

        container.setOnClickListener {
            hideExpandedView()
            showConversationAsChatHead(conversation)
        }

        return container
    }

    private fun hideExpandedView() {
        expandedView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // View might not be attached
            }
            expandedView = null
        }
        isExpanded = false
    }

    private fun showConversationAsChatHead(conversation: ConversationData) {
        // If already showing this conversation as a head, just open the window
        if (conversationHeads.containsKey(conversation.roomId)) {
            openConversationWindow(conversation)
            return
        }

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.chat_head_view, null)

        val chatHeadSize = (64 * resources.displayMetrics.density).toInt()
        val avatarSize = (48 * resources.displayMetrics.density).toInt()
        val avatarBitmap = getAvatarBitmap(conversation, avatarSize)

        val avatarView = view.findViewById<ImageView>(R.id.chat_head_avatar)
        avatarView.setImageBitmap(avatarBitmap)

        val layoutFlag = getWindowLayoutFlag()

        // Position below hub
        val hubY = hubParams?.y ?: 200
        val offset = (conversationHeads.size + 1) * (chatHeadSize + 10)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = hubY + offset
        }

        setupConversationHeadTouchListener(view, params, conversation, chatHeadSize)

        try {
            windowManager.addView(view, params)
            conversationHeads[conversation.roomId] = ConversationHead(view, params, conversation)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupConversationHeadTouchListener(
        view: View,
        params: WindowManager.LayoutParams,
        conversation: ConversationData,
        chatHeadSize: Int
    ) {
        var headInitialX = 0
        var headInitialY = 0
        var headTouchX = 0f
        var headTouchY = 0f
        var headDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    headInitialX = params.x
                    headInitialY = params.y
                    headTouchX = event.rawX
                    headTouchY = event.rawY
                    headDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - headTouchX
                    val deltaY = event.rawY - headTouchY

                    if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                        headDragging = true
                        showRemoveZone()
                    }

                    params.x = headInitialX + deltaX.toInt()
                    params.y = headInitialY + deltaY.toInt()
                    windowManager.updateViewLayout(view, params)

                    updateRemoveZoneHighlight(params.y, chatHeadSize)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    hideRemoveZone()

                    if (isInRemoveZone(params.y, chatHeadSize)) {
                        removeConversationHead(conversation.roomId)
                        return@setOnTouchListener true
                    }

                    if (!headDragging) {
                        openConversationWindow(conversation)
                    } else {
                        snapToEdge(params, view, chatHeadSize)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    hideRemoveZone()
                    true
                }
                else -> false
            }
        }
    }

    private fun removeConversationHead(roomId: String) {
        conversationHeads[roomId]?.let { head ->
            try {
                windowManager.removeView(head.view)
            } catch (e: Exception) {
                // View might not be attached
            }
            conversationHeads.remove(roomId)
        }
    }

    private fun openConversationWindow(conversation: ConversationData) {
        // Close existing window if open
        closeConversationWindow()

        currentOpenConversation = conversation

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        conversationWindow = inflater.inflate(R.layout.chat_head_conversation_window, null)

        // Setup header with avatar
        val avatarSize = (36 * resources.displayMetrics.density).toInt()
        val headerAvatarBitmap = getAvatarBitmap(conversation, avatarSize)
        val avatarView = conversationWindow?.findViewById<ImageView>(R.id.conversation_avatar)
        avatarView?.setImageBitmap(headerAvatarBitmap)

        conversationWindow?.findViewById<TextView>(R.id.conversation_name)?.apply {
            text = conversation.name
            setTextColor(0xFFFFFFFF.toInt())
        }

        // Setup buttons
        conversationWindow?.findViewById<ImageView>(R.id.minimize_button)?.setOnClickListener {
            closeConversationWindow()
        }

        conversationWindow?.findViewById<ImageView>(R.id.close_conversation_button)?.setOnClickListener {
            closeConversationWindow()
            removeConversationHead(conversation.roomId)
        }

        // Setup send button
        val messageInput = conversationWindow?.findViewById<EditText>(R.id.message_input)
        conversationWindow?.findViewById<ImageView>(R.id.send_button)?.setOnClickListener {
            val message = messageInput?.text?.toString()
            if (!message.isNullOrBlank()) {
                // TODO: Actually send the message via Matrix client
                addMessageToWindow(message, true)
                messageInput.text.clear()
            }
        }

        // Add placeholder message
        if (conversation.lastMessage != null) {
            addMessageToWindow(conversation.lastMessage, false)
        }

        val layoutFlag = getWindowLayoutFlag()
        val windowWidth = (screenWidth * 0.9).toInt()
        val windowHeight = (400 * resources.displayMetrics.density).toInt()

        conversationWindowParams = WindowManager.LayoutParams(
            windowWidth,
            windowHeight,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        try {
            windowManager.addView(conversationWindow, conversationWindowParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun addMessageToWindow(message: String, isOutgoing: Boolean) {
        val container = conversationWindow?.findViewById<LinearLayout>(R.id.messages_container) ?: return

        val messageView = TextView(this).apply {
            text = message
            textSize = 14f
            setPadding(
                (12 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt()
            )

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(
                    if (isOutgoing) (48 * resources.displayMetrics.density).toInt() else 0,
                    (4 * resources.displayMetrics.density).toInt(),
                    if (isOutgoing) 0 else (48 * resources.displayMetrics.density).toInt(),
                    (4 * resources.displayMetrics.density).toInt()
                )
                gravity = if (isOutgoing) Gravity.END else Gravity.START
            }
            layoutParams = params

            // Create rounded background programmatically
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 16 * resources.displayMetrics.density
                if (isOutgoing) {
                    // Element green for outgoing messages
                    setColor(0xFF0DBD8B.toInt())
                } else {
                    // Dark gray for incoming messages (matching dark theme)
                    setColor(0xFF2E3238.toInt())
                }
            }
            background = drawable

            // White text for both in dark theme
            setTextColor(0xFFFFFFFF.toInt())
        }

        container.addView(messageView)

        // Scroll to bottom
        conversationWindow?.findViewById<ScrollView>(R.id.messages_scroll)?.post {
            conversationWindow?.findViewById<ScrollView>(R.id.messages_scroll)?.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun closeConversationWindow() {
        conversationWindow?.let {
            try {
                // Hide keyboard first
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(it.windowToken, 0)
                windowManager.removeView(it)
            } catch (e: Exception) {
                // View might not be attached
            }
            conversationWindow = null
        }
        currentOpenConversation = null
    }

    private fun addConversation(conversation: ConversationData) {
        // Check if already exists, update if so
        val existingIndex = conversations.indexOfFirst { it.roomId == conversation.roomId }
        if (existingIndex >= 0) {
            conversations[existingIndex] = conversation
        } else {
            conversations.add(0, conversation) // Add to front
        }

        // Show hub if not visible
        if (hubChatHead == null) {
            showHubChatHead()
        } else {
            updateHubBadge()
        }
    }

    private fun updateHubBadge() {
        val totalUnread = conversations.sumOf { it.unreadCount }
        val badge = hubChatHead?.findViewById<TextView>(R.id.chat_head_badge)
        if (totalUnread > 0) {
            badge?.visibility = View.VISIBLE
            badge?.text = if (totalUnread > 99) "99+" else totalUnread.toString()
        } else {
            badge?.visibility = View.GONE
        }
    }

    private fun getAppLauncherIcon(): Drawable? {
        return try {
            val icon = packageManager.getApplicationIcon(packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && icon is android.graphics.drawable.AdaptiveIconDrawable) {
                icon.foreground
            } else {
                icon
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getWindowLayoutFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun showRemoveZone() {
        if (removeZoneView != null) return

        removeZoneView = FrameLayout(this).apply {
            setBackgroundColor(0x80FF0000.toInt())
            val closeIcon = ImageView(context).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setColorFilter(0xFFFFFFFF.toInt())
            }
            val iconSize = (48 * resources.displayMetrics.density).toInt()
            addView(closeIcon, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))
        }

        val layoutFlag = getWindowLayoutFlag()
        val removeZoneHeight = (80 * resources.displayMetrics.density).toInt()

        removeZoneParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            removeZoneHeight,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        try {
            windowManager.addView(removeZoneView, removeZoneParams)
        } catch (e: Exception) {
            removeZoneView = null
        }
    }

    private fun hideRemoveZone() {
        removeZoneView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // View might not be attached
            }
            removeZoneView = null
        }
    }

    private fun updateRemoveZoneHighlight(chatHeadY: Int, chatHeadSize: Int) {
        val isNearRemoveZone = isInRemoveZone(chatHeadY, chatHeadSize)
        removeZoneView?.apply {
            setBackgroundColor(if (isNearRemoveZone) 0xE0FF0000.toInt() else 0x80FF0000.toInt())
            scaleX = if (isNearRemoveZone) 1.1f else 1.0f
            scaleY = if (isNearRemoveZone) 1.1f else 1.0f
        }
    }

    private fun isInRemoveZone(chatHeadY: Int, chatHeadSize: Int): Boolean {
        val removeZoneHeight = (80 * resources.displayMetrics.density).toInt()
        val removeZoneTop = screenHeight - removeZoneHeight
        val chatHeadBottom = chatHeadY + chatHeadSize
        return chatHeadBottom >= removeZoneTop
    }

    private fun snapToEdge(params: WindowManager.LayoutParams?, view: View?, chatHeadSize: Int) {
        if (params == null || view == null) return

        val currentX = params.x
        val centerX = currentX + chatHeadSize / 2

        val targetX = if (centerX < screenWidth / 2) 0 else screenWidth - chatHeadSize

        ValueAnimator.ofInt(currentX, targetX).apply {
            duration = 200
            interpolator = OvershootInterpolator(0.8f)
            addUpdateListener { animator ->
                params.x = animator.animatedValue as Int
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (e: Exception) {
                    cancel()
                }
            }
            start()
        }
    }

    private fun hideAllAndStop() {
        hideExpandedView()
        closeConversationWindow()
        hideRemoveZone()

        // Remove all conversation heads
        conversationHeads.values.forEach { head ->
            try {
                windowManager.removeView(head.view)
            } catch (e: Exception) {
                // View might not be attached
            }
        }
        conversationHeads.clear()

        // Remove hub
        hubChatHead?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // View might not be attached
            }
            hubChatHead = null
        }

        conversations.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideAllAndStop()
    }

    companion object {
        const val ACTION_SHOW_HUB = "io.element.android.ACTION_SHOW_HUB"
        const val ACTION_ADD_CONVERSATION = "io.element.android.ACTION_ADD_CONVERSATION"
        const val ACTION_HIDE_ALL = "io.element.android.ACTION_HIDE_ALL"

        const val EXTRA_NAME = "extra_name"
        const val EXTRA_AVATAR = "extra_avatar"
        const val EXTRA_ROOM_ID = "extra_room_id"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_LAST_MESSAGE = "extra_last_message"
        const val EXTRA_UNREAD_COUNT = "extra_unread_count"

        private const val CHANNEL_ID = "chat_heads_channel"
        private const val NOTIFICATION_ID = 9999

        fun showHub(context: Context) {
            val intent = Intent(context, ChatHeadService::class.java).apply {
                action = ACTION_SHOW_HUB
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun addConversation(
            context: Context,
            roomId: String,
            sessionId: String,
            name: String,
            avatar: Bitmap? = null,
            lastMessage: String? = null,
            unreadCount: Int = 0,
        ) {
            val intent = Intent(context, ChatHeadService::class.java).apply {
                action = ACTION_ADD_CONVERSATION
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_NAME, name)
                putExtra(EXTRA_LAST_MESSAGE, lastMessage)
                putExtra(EXTRA_UNREAD_COUNT, unreadCount)
                avatar?.let {
                    val stream = java.io.ByteArrayOutputStream()
                    it.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    putExtra(EXTRA_AVATAR, stream.toByteArray())
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        // Legacy method for compatibility
        fun showChatHead(
            context: Context,
            name: String,
            avatar: Bitmap? = null,
            roomId: String? = null,
            sessionId: String? = null,
        ) {
            if (roomId != null && sessionId != null) {
                addConversation(context, roomId, sessionId, name, avatar, null, 1)
            } else {
                showHub(context)
            }
        }

        fun hideAll(context: Context) {
            val intent = Intent(context, ChatHeadService::class.java).apply {
                action = ACTION_HIDE_ALL
            }
            context.startService(intent)
        }
    }
}
