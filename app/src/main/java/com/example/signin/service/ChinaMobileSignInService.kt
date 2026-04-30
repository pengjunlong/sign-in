package com.example.signin.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 中国移动 App 自动签到无障碍服务
 *
 * 工作流程：
 * 1. 监听中国移动 App 的窗口/内容变化事件
 * 2. 检测开屏广告 → 点击"跳过"按钮
 * 3. 进入首页后 → 点击左上角"签到"入口
 * 4. 进入签到页后 → 点击"签到"按钮完成签到
 */
class ChinaMobileSignInService : AccessibilityService() {

    companion object {
        private const val TAG = "ChinaMobileSignIn"

        /** 中国移动 App 包名 */
        private const val CM_PACKAGE = "com.greenpoint.android.mc10086.activity"

        /** 两次操作之间的最小间隔（毫秒），防止重复触发 */
        private const val ACTION_INTERVAL_MS = 1500L

        /** 跳过广告最多尝试次数 */
        private const val MAX_SKIP_AD_ATTEMPTS = 12

        /** 等待首页/签到页最多尝试次数 */
        private const val MAX_WAIT_ATTEMPTS = 20
    }

    // -------------------------------------------------------
    // 状态机
    // -------------------------------------------------------
    private enum class State {
        SKIP_AD,          // 尝试跳过开屏广告
        WAIT_HOME,        // 等待进入首页
        CLICK_SIGN_ENTRY, // 点击左上角签到入口
        WAIT_SIGN_PAGE,   // 等待签到页面加载
        CLICK_SIGN_BTN,   // 点击签到页中的签到按钮
        DONE              // 完成
    }

    private var state = State.SKIP_AD
    private var attempts = 0
    private var lastActionTime = 0L

    private val handler = Handler(Looper.getMainLooper())

    // -------------------------------------------------------
    // 无障碍服务回调
    // -------------------------------------------------------

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg != CM_PACKAGE) return

        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        // 节流：避免在极短时间内重复处理
        val now = System.currentTimeMillis()
        if (now - lastActionTime < ACTION_INTERVAL_MS) return

        Log.d(TAG, "事件触发 state=$state attempts=$attempts")
        dispatch()
    }

    override fun onInterrupt() {
        Log.w(TAG, "无障碍服务被中断")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "无障碍服务已连接，开始监听中国移动 App")
        state = State.SKIP_AD
        attempts = 0
    }

    // -------------------------------------------------------
    // 状态机分发
    // -------------------------------------------------------

    private fun dispatch() {
        when (state) {
            State.SKIP_AD          -> handleSkipAd()
            State.WAIT_HOME        -> handleWaitHome()
            State.CLICK_SIGN_ENTRY -> handleClickSignEntry()
            State.WAIT_SIGN_PAGE   -> handleWaitSignPage()
            State.CLICK_SIGN_BTN   -> handleClickSignBtn()
            State.DONE             -> Log.i(TAG, "✅ 签到流程已完成！")
        }
    }

    // -------------------------------------------------------
    // Step 1：跳过开屏广告
    // -------------------------------------------------------

    private fun handleSkipAd() {
        if (attempts >= MAX_SKIP_AD_ATTEMPTS) {
            Log.d(TAG, "跳过广告达到上限，直接进入等待首页")
            transitionTo(State.WAIT_HOME)
            return
        }

        val root = rootInActiveWindow ?: run {
            scheduleRetry(800)
            return
        }

        val skipped = tryClickByTexts(root, "跳过", "跳过广告", "SKIP", "skip") ||
                tryClickContaining(root, "跳过") ||
                tryClickByDescription(root, "跳过")

        root.recycle()

        if (skipped) {
            Log.i(TAG, "✅ 已点击跳过广告按钮")
            lastActionTime = System.currentTimeMillis()
            // 广告跳过后等待 2s 再检测首页
            handler.postDelayed({ transitionTo(State.WAIT_HOME) }, 2000)
        } else {
            attempts++
            Log.d(TAG, "未找到跳过按钮，第 $attempts 次重试")
            scheduleRetry(1000)
        }
    }

    // -------------------------------------------------------
    // Step 2：等待首页加载
    // -------------------------------------------------------

    private fun handleWaitHome() {
        if (attempts >= MAX_WAIT_ATTEMPTS) {
            Log.w(TAG, "等待首页超时，强制尝试点击签到入口")
            transitionTo(State.CLICK_SIGN_ENTRY)
            return
        }

        val root = rootInActiveWindow ?: run {
            attempts++
            scheduleRetry(800)
            return
        }

        // 首页特征：能找到"签到"相关文字节点
        val homeDetected = findNodeByTexts(root, "签到", "每日签到", "积分签到") != null
        root.recycle()

        if (homeDetected) {
            Log.i(TAG, "✅ 检测到首页，准备点击签到入口")
            transitionTo(State.CLICK_SIGN_ENTRY)
        } else {
            attempts++
            scheduleRetry(1000)
        }
    }

    // -------------------------------------------------------
    // Step 3：点击左上角签到入口
    // -------------------------------------------------------

    private fun handleClickSignEntry() {
        if (attempts >= MAX_WAIT_ATTEMPTS) {
            Log.w(TAG, "签到入口点击达到上限，放弃")
            return
        }

        val root = rootInActiveWindow ?: run {
            attempts++
            scheduleRetry(800)
            return
        }

        // 中国移动首页左上角签到入口，常见文字/描述
        val clicked = tryClickByTexts(root, "签到", "每日签到", "积分签到", "签到有礼") ||
                tryClickByDescription(root, "签到") ||
                tryClickByDescription(root, "每日签到")

        root.recycle()

        if (clicked) {
            Log.i(TAG, "✅ 已点击签到入口")
            lastActionTime = System.currentTimeMillis()
            handler.postDelayed({ transitionTo(State.WAIT_SIGN_PAGE) }, 2000)
        } else {
            attempts++
            Log.d(TAG, "未找到签到入口，第 $attempts 次重试")
            scheduleRetry(1200)
        }
    }

    // -------------------------------------------------------
    // Step 4：等待签到页面加载
    // -------------------------------------------------------

    private fun handleWaitSignPage() {
        if (attempts >= MAX_WAIT_ATTEMPTS) {
            Log.w(TAG, "等待签到页超时，强制尝试点击签到按钮")
            transitionTo(State.CLICK_SIGN_BTN)
            return
        }

        val root = rootInActiveWindow ?: run {
            attempts++
            scheduleRetry(800)
            return
        }

        // 签到页特征：页面中出现可点击的"签到"按钮（通常文字为"签到"或"立即签到"）
        val signPageDetected = findNodeByTexts(root, "立即签到", "签到领积分", "点击签到") != null ||
                findClickableNodeByText(root, "签到") != null

        root.recycle()

        if (signPageDetected) {
            Log.i(TAG, "✅ 检测到签到页面，准备点击签到按钮")
            transitionTo(State.CLICK_SIGN_BTN)
        } else {
            attempts++
            scheduleRetry(1000)
        }
    }

    // -------------------------------------------------------
    // Step 5：点击签到页中的签到按钮
    // -------------------------------------------------------

    private fun handleClickSignBtn() {
        if (attempts >= MAX_WAIT_ATTEMPTS) {
            Log.w(TAG, "签到按钮点击达到上限，放弃")
            return
        }

        val root = rootInActiveWindow ?: run {
            attempts++
            scheduleRetry(800)
            return
        }

        // 签到按钮常见文字
        val clicked = tryClickByTexts(root, "立即签到", "签到领积分", "点击签到") ||
                tryClickClickableByText(root, "签到") ||
                tryClickByDescription(root, "立即签到") ||
                tryClickByDescription(root, "签到")

        root.recycle()

        if (clicked) {
            Log.i(TAG, "✅ 签到成功！")
            lastActionTime = System.currentTimeMillis()
            state = State.DONE
        } else {
            attempts++
            Log.d(TAG, "未找到签到按钮，第 $attempts 次重试")
            scheduleRetry(1200)
        }
    }

    // -------------------------------------------------------
    // 工具方法
    // -------------------------------------------------------

    /** 切换状态并重置尝试次数 */
    private fun transitionTo(newState: State) {
        Log.d(TAG, "状态切换: $state → $newState")
        state = newState
        attempts = 0
    }

    /** 延迟后重新触发 dispatch */
    private fun scheduleRetry(delayMs: Long) {
        handler.postDelayed({ dispatch() }, delayMs)
    }

    /** 精确文字匹配，点击第一个找到的节点，返回是否成功 */
    private fun tryClickByTexts(root: AccessibilityNodeInfo, vararg texts: String): Boolean {
        for (text in texts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    if (node.text?.toString() == text || node.text?.toString()?.contains(text) == true) {
                        if (performClick(node)) return true
                    }
                }
            }
        }
        return false
    }

    /** 文字包含匹配，点击第一个找到的节点 */
    private fun tryClickContaining(root: AccessibilityNodeInfo, keyword: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByText(keyword)
        if (!nodes.isNullOrEmpty()) {
            for (node in nodes) {
                if (performClick(node)) return true
            }
        }
        return false
    }

    /** contentDescription 匹配，点击第一个找到的节点 */
    private fun tryClickByDescription(root: AccessibilityNodeInfo, desc: String): Boolean {
        return findNodeByDescription(root, desc)?.let { performClick(it) } ?: false
    }

    /** 找到可点击的文字节点并点击（用于区分签到页 vs 入口） */
    private fun tryClickClickableByText(root: AccessibilityNodeInfo, text: String): Boolean {
        return findClickableNodeByText(root, text)?.let { performClick(it) } ?: false
    }

    /** 找到 text 精确匹配或包含的节点 */
    private fun findNodeByTexts(root: AccessibilityNodeInfo, vararg texts: String): AccessibilityNodeInfo? {
        for (text in texts) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (!nodes.isNullOrEmpty()) return nodes[0]
        }
        return null
    }

    /** 找到 contentDescription 匹配的节点（递归遍历） */
    private fun findNodeByDescription(node: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString()?.contains(desc) == true) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByDescription(child, desc)
            if (result != null) return result
        }
        return null
    }

    /** 找到 text 匹配且可点击的节点（签到页中的签到按钮通常是 Button，具有 isClickable） */
    private fun findClickableNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = node.findAccessibilityNodeInfosByText(text)
        if (!nodes.isNullOrEmpty()) {
            // 优先返回可点击节点
            nodes.firstOrNull { it.isClickable }?.let { return it }
            // 否则向上找可点击的父节点
            nodes.firstOrNull()?.let { return findClickableParent(it) }
        }
        return null
    }

    /** 向上查找最近的可点击祖先节点 */
    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }

    /**
     * 执行点击操作：
     * 优先使用 performAction(ACTION_CLICK)，若节点不可点击则通过手势模拟点击
     */
    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        // 1. 节点本身可点击
        if (node.isClickable) {
            val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (success) return true
        }

        // 2. 向上找可点击父节点
        val clickable = findClickableParent(node)
        if (clickable != null && clickable.isClickable) {
            if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }

        // 3. 通过手势模拟点击节点中心点
        return gestureClick(node)
    }

    /** 通过手势模拟点击节点中心坐标 */
    private fun gestureClick(node: AccessibilityNodeInfo): Boolean {
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) return false

        val cx = rect.centerX().toFloat()
        val cy = rect.centerY().toFloat()

        val path = Path().apply { moveTo(cx, cy) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 100)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return dispatchGesture(gesture, null, null)
    }
}

