package com.discord.panels

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * This ViewGroup assumes that there are exactly three child views. The first one
 * should be the start panel, the second should be the center panel, and the third
 * should be the end panel.
 *
 * If there are not exactly three child views, OverlappingPanelsLayout will throw
 * an exception.
 */
open class OverlappingPanelsLayout : FrameLayout {

  interface PanelStateListener {
    fun onPanelStateChange(panelState: PanelState)
  }

  enum class Panel {
    START,
    CENTER,
    END
  }

  enum class LockState {
    OPEN,
    CLOSE,
    UNLOCKED
  }

  enum class SwipeDirection {
    LEFT,
    RIGHT
  }

  private var isLeftToRight: Boolean = true
  private var scrollingSlopPx: Float = 0f
  private var homeGestureFromBottomThreshold: Float = 0f
  private var minFlingPxPerSecond: Float = 0f
  private var velocityTracker: VelocityTracker? = null
  private var nonFullScreenSidePanelWidth: Int = 0

  private var isScrollingHorizontally = false
  private var wasActionDownOnClosedCenterPanel = false

  // We detect the home system gesture in the ACTION_DOWN of onInterceptTouchEvent.
  // We use isHomeSystemGesture to check whether we should handle events in onTouchEvent.
  private var isHomeSystemGesture = false

  private var xFromInterceptActionDown: Float = 0f
  private var yFromInterceptActionDown: Float = 0f

  // difference between the center panel position and the ACTION_DOWN
  // event position
  private var centerPanelDiffX: Float = 0f

  private var startPanelOpenedCenterPanelX: Float = Float.MIN_VALUE
  private var endPanelOpenedCenterPanelX: Float = Float.MAX_VALUE

  private var centerPanelXAnimator: ValueAnimator? = null

  private val startPanelStateListeners = arrayListOf<PanelStateListener>()
  private val endPanelStateListeners = arrayListOf<PanelStateListener>()

  private var selectedPanel: Panel = Panel.CENTER

  private var startPanelLockState: LockState = LockState.UNLOCKED
  private var endPanelLockState: LockState = LockState.UNLOCKED

  private var startPanelState: PanelState = PanelState.Closed
  private var endPanelState: PanelState = PanelState.Closed

  private var useFullWidthForStartPanel: Boolean = false

  private var pendingUpdate: (() -> Unit)? = null

  // Sometimes we need to make a decision based on the expected end X of an animation
  // before the animation completes. For example, if we open the right panel in portrait
  // mode and then rotate the device, the center panel will start animating toward the left
  // to open the right panel. Before that animation completes, we need to save the end X
  // value of the animation. While the animation is in progress, the width of the right panel
  // may get reset. After the width resets, we check if the end X of the current animation
  // is the Opened state based on the previous width. If it is, then we start a new animation
  // to update the center panel X based on the new right panel width.
  private var centerPanelAnimationEndX = 0f

  // If a touch event happens inside this childGestureRegion, we should not intercept it,
  // and we should not make the panels horizontally scroll. Instead, we should let the child
  // views handle this touch event.
  private var childGestureRegions = emptyList<Rect>()

  private var swipeDirection: SwipeDirection? = null

  private val isSystemGestureNavigationPossible: Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

  private lateinit var startPanel: View
  private lateinit var centerPanel: View
  private lateinit var endPanel: View

  constructor(context: Context) : super(context)

  constructor(context: Context, attrs: AttributeSet? = null) : super(context, attrs) {
    initialize(attrs)
  }

  constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : super(
    context,
    attrs,
    defStyleAttr
  ) {
    initialize(attrs)
  }

  private fun initialize(attrs: AttributeSet?) {
    val locale = LocaleProvider.getPrimaryLocale(context)
    isLeftToRight = TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_LTR
    scrollingSlopPx = resources.getDimension(R.dimen.overlapping_panels_scroll_slop)
    homeGestureFromBottomThreshold =
      resources.getDimension(R.dimen.overlapping_panels_home_gesture_from_bottom_threshold)
    minFlingPxPerSecond = resources.getDimension(R.dimen.overlapping_panels_min_fling_dp_per_second)

    val portraitModeWidth =
      if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
        resources.displayMetrics.widthPixels
      } else {
        resources.displayMetrics.heightPixels
      }

    val marginBetweenPanels =
      resources.getDimension(R.dimen.overlapping_panels_margin_between_panels)
    val visibleWidthOfClosedCenterPanel =
      resources.getDimension(R.dimen.overlapping_panels_closed_center_panel_visible_width)

    nonFullScreenSidePanelWidth =
      (portraitModeWidth - marginBetweenPanels - visibleWidthOfClosedCenterPanel).toInt()

    val styledAttrs = context.obtainStyledAttributes(
      attrs,
      R.styleable.OverlappingPanelsLayout,
      0,
      0
    )

    try {
      val maxSidePanelNonFullScreenWidth =
        styledAttrs.getDimension(
          R.styleable.OverlappingPanelsLayout_maxSidePanelNonFullScreenWidth,
          Int.MAX_VALUE.toFloat()
        ).toInt()

      nonFullScreenSidePanelWidth = min(nonFullScreenSidePanelWidth, maxSidePanelNonFullScreenWidth)
    } finally {
      styledAttrs.recycle()
    }
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)

    // OverlappingPanelsLayout expects exactly three child views where each child view
    // is a panel. If there are not exactly three child views, OverlappingPanelsLayout
    // will throw an exception from the lateinit var panel views not getting initialized.
    if (childCount == 3 && !::centerPanel.isInitialized) {
      initPanels()
    }
  }

  /*
   * This method only returns whether we want to intercept the motion.
   * If we return true, onTouchEvent will be called and we do the actual
   * view translations there.
   *
   * https://developer.android.com/training/gestures/viewgroup.html
   */
  override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
    return when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        isScrollingHorizontally = false
        wasActionDownOnClosedCenterPanel = isTouchingCenterPanelWhileSidePanelOpen(event)
        centerPanelDiffX = centerPanel.x - event.rawX

        xFromInterceptActionDown = event.x
        yFromInterceptActionDown = event.y

        val yDiffFromBottomOfDisplay =
          abs(yFromInterceptActionDown - resources.displayMetrics.heightPixels)
        isHomeSystemGesture = yDiffFromBottomOfDisplay < homeGestureFromBottomThreshold &&
            isSystemGestureNavigationPossible

        if (velocityTracker == null) {
          velocityTracker = VelocityTracker.obtain()
          velocityTracker?.addMovement(event)
        } else {
          velocityTracker?.clear()
        }

        wasActionDownOnClosedCenterPanel
      }
      MotionEvent.ACTION_MOVE -> {
        if (isScrollingHorizontally) {
          true
        } else {

          // If the horizontally distance in the MotionEvent is more than
          // the scroll slop, and if the horizontal distance is greater than
          // the vertical distance, start the horizontal scroll for the panels.
          val xDiff = calculateDistanceX(startX = xFromInterceptActionDown, event = event)
          val yDiff = calculateDistanceY(startY = yFromInterceptActionDown, event = event)
          val isTouchingChildGestureRegion = isTouchingChildGestureRegion(event)

          if (abs(xDiff) > scrollingSlopPx &&
            abs(xDiff) > abs(yDiff) &&
            !isTouchingChildGestureRegion
          ) {
            isScrollingHorizontally = true
            true
          } else {
            false
          }
        }
      }
      MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
        velocityTracker?.recycle()
        velocityTracker = null
        isScrollingHorizontally || wasActionDownOnClosedCenterPanel
      }
      else -> {
        // When the center panel is closed (but still visible at the edges of the screen)
        // intercept all touch events so that the user cannot interact with the child views of
        // the center panel.
        wasActionDownOnClosedCenterPanel
      }
    }
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (isHomeSystemGesture) {
      return false
    }

    return when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        true
      }
      MotionEvent.ACTION_MOVE -> {
        if (isTouchingChildGestureRegion(event)) {
          return false
        }

        val xDiff = calculateDistanceX(startX = xFromInterceptActionDown, event = event)

        if (abs(xDiff) > scrollingSlopPx) {
          if (swipeDirection == null) {
            swipeDirection = if (xDiff > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
          }
        }

        velocityTracker?.addMovement(event)

        if (shouldHandleActionMoveEvent(event)) {
          translateCenterPanel(event)
        }

        true
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
        val isClosedCenterPanelClick =
          wasActionDownOnClosedCenterPanel &&
              abs(event.x - xFromInterceptActionDown) < scrollingSlopPx &&
              !isScrollingHorizontally

        if (isClosedCenterPanelClick) {
          closePanels()
        } else {
          // If we're not treating this as a click, then assume it's a horizontal scroll.
          velocityTracker?.addMovement(event)
          snapOpenOrClose(event)
        }

        wasActionDownOnClosedCenterPanel = false
        isScrollingHorizontally = false
        swipeDirection = null

        true
      }
      else -> {
        false
      }
    }
  }

  fun openStartPanel() {
    openStartPanel(isFling = false)
  }

  fun openEndPanel() {
    openEndPanel(isFling = false)
  }

  fun closePanels() {
    closePanels(isFling = false)
  }

  fun setStartPanelLockState(lockState: LockState) {
    startPanelLockState = lockState
    if (lockState == LockState.OPEN) {
      openStartPanel()
    }
  }

  fun setEndPanelLockState(lockState: LockState) {
    endPanelLockState = lockState
    if (lockState == LockState.OPEN) {
      openEndPanel()
    }
  }

  fun setStartPanelUseFullPortraitWidth(useFullPortraitWidth: Boolean) {
    useFullWidthForStartPanel = useFullPortraitWidth
    resetStartPanelWidth()
  }

  fun setChildGestureRegions(childGestureRegions: List<Rect>) {
    this.childGestureRegions = childGestureRegions
  }

  private fun resetStartPanelWidth() {
    if (::startPanel.isInitialized) {
      val layoutParams = startPanel.layoutParams
      layoutParams.width = if (useFullWidthForStartPanel) {
        ViewGroup.LayoutParams.MATCH_PARENT
      } else {
        nonFullScreenSidePanelWidth
      }
      startPanel.layoutParams = layoutParams
    }
  }

  private fun resetEndPanelWidth() {
    val layoutParams = endPanel.layoutParams
    layoutParams.width = nonFullScreenSidePanelWidth
    endPanel.layoutParams = layoutParams
  }

  fun registerStartPanelStateListeners(vararg panelStateListenerArgs: PanelStateListener) {
    for (panelStateListener in panelStateListenerArgs) {
      startPanelStateListeners.add(panelStateListener)
    }
  }

  fun registerEndPanelStateListeners(vararg panelStateListenerArgs: PanelStateListener) {
    for (panelStateListener in panelStateListenerArgs) {
      endPanelStateListeners.add(panelStateListener)
    }
  }

  fun getSelectedPanel(): Panel = selectedPanel

  /**
   * Diff [startPanelState] with the actual drawer state before opening or closing panels.
   * This allows us to keep the drawer state the same across configuration changes without
   * an infinite loop of syncing updates between the view and the store.
   */
  fun handleStartPanelState(startPanelState: PanelState) {
    val previousStartPanelState = this.startPanelState
    when {
      (startPanelState is PanelState.Opened &&
          previousStartPanelState !is PanelState.Opened) -> {
        openStartPanel()
      }

      (startPanelState is PanelState.Closed &&
          previousStartPanelState is PanelState.Opened) -> {
        closePanels()
      }
    }

    this.startPanelState = startPanelState
  }

  /**
   * Diff [endPanelState] with the actual drawer state before opening or closing panels.
   * This allows us to keep the drawer state the same across configuration changes without
   * an infinite loop of syncing updates between the view and the store.
   */
  fun handleEndPanelState(endPanelState: PanelState) {
    val previousEndPanelState = this.endPanelState

    when {
      (endPanelState is PanelState.Opened &&
          previousEndPanelState !is PanelState.Opened) -> {
        openEndPanel()
      }

      (endPanelState is PanelState.Closed &&
          previousEndPanelState is PanelState.Opened) -> {
        closePanels()
      }
    }

    this.endPanelState = endPanelState
  }

  private fun openPanel(panel: Panel) {
    when (panel) {
      Panel.START -> openStartPanel(isFling = false)
      Panel.END -> openEndPanel(isFling = false)
      Panel.CENTER -> closePanels(isFling = false)
    }
  }

  private fun openStartPanel(isFling: Boolean = false) {

    // This can get called before onLayout() where centerPanel gets initialized.
    // If that happens, save the pendingUpdate for after centerPanel gets initialized
    if (!::centerPanel.isInitialized) {
      pendingUpdate = { openStartPanel(isFling) }
      return
    }

    if (startPanelLockState == LockState.OPEN) {
      updateCenterPanelX(x = startPanelOpenedCenterPanelX)
    } else {
      updateCenterPanelXWithAnimation(
        x = startPanelOpenedCenterPanelX,
        isFling = isFling,
        animationDurationMs = SIDE_PANEL_OPEN_DURATION_MS
      )
    }
  }

  private fun openEndPanel(isFling: Boolean = false) {

    // This can get called before onLayout() where centerPanel gets initialized.
    // If that happens, save the pendingUpdate for after centerPanel gets initialized
    if (!::centerPanel.isInitialized) {
      pendingUpdate = { openEndPanel(isFling) }
      return
    }

    updateCenterPanelXWithAnimation(
      x = endPanelOpenedCenterPanelX,
      isFling = isFling,
      animationDurationMs = SIDE_PANEL_OPEN_DURATION_MS
    )
  }

  private fun closePanels(isFling: Boolean = false) {

    // This can get called before onLayout() where centerPanel gets initialized.
    // If that happens, save the pendingUpdate for after centerPanel gets initialized
    if (!::centerPanel.isInitialized) {
      pendingUpdate = { closePanels(isFling) }
      return
    }

    updateCenterPanelXWithAnimation(
      x = 0f,
      isFling = isFling,
      animationDurationMs = SIDE_PANEL_CLOSE_DURATION_MS
    )
  }

  /**
   * Snap to one of the following states:
   * - center panel horizontally centered (side panels closed)
   * - left panel open
   * - right panel open
   */
  private fun snapOpenOrClose(event: MotionEvent) {
    val targetedX = getTargetedX(event)

    // Setting [units] to 1000 provides pixels per second.
    velocityTracker?.computeCurrentVelocity(1000 /* units */)
    val pxPerSecond = velocityTracker?.xVelocity ?: Float.MIN_VALUE
    val isFling = abs(pxPerSecond) > minFlingPxPerSecond
    val isDirectionStartToEnd = if (isLeftToRight) pxPerSecond > 0 else pxPerSecond < 0

    if (isFling) {
      if (isDirectionStartToEnd) {
        if (selectedPanel == Panel.END) {
          closePanels(isFling = true)
          return
        } else if (selectedPanel == Panel.CENTER) {
          openStartPanel(isFling = true)
          return
        }
      } else {
        if (selectedPanel == Panel.START) {
          closePanels(isFling = true)
          return
        } else if (selectedPanel == Panel.CENTER) {
          openEndPanel(isFling = true)
          return
        }
      }
    }

    val maxCenterPanelX = max(startPanelOpenedCenterPanelX, endPanelOpenedCenterPanelX)
    val minCenterPanelX = min(startPanelOpenedCenterPanelX, endPanelOpenedCenterPanelX)

    // Select the panel state based on the x position of the up or cancel event.
    when {
      targetedX > maxCenterPanelX / 2 -> {
        openPanel(getLeftPanel())
      }
      targetedX < minCenterPanelX / 2 -> {
        openPanel(getRightPanel())
      }
      else -> {
        closePanels()
      }
    }
  }

  private fun getLeftPanel() = if (isLeftToRight) Panel.START else Panel.END

  private fun getRightPanel() = if (isLeftToRight) Panel.END else Panel.START

  private fun getLeftPanelLockState() =
    if (isLeftToRight) startPanelLockState else endPanelLockState

  private fun getRightPanelLockState() =
    if (isLeftToRight) endPanelLockState else startPanelLockState

  private fun translateCenterPanel(event: MotionEvent) {
    val targetedX = getTargetedX(event)
    val normalizedX = getNormalizedX(targetedX)
    updateCenterPanelX(normalizedX)
  }

  /**
   * Return an x value that is within the bounds of the center panel's
   * allowed horizontal translation range.
   */
  private fun getNormalizedX(targetedX: Float): Float {
    if (startPanelLockState == LockState.OPEN) {
      return startPanelOpenedCenterPanelX
    } else if (endPanelLockState == LockState.OPEN) {
      return endPanelOpenedCenterPanelX
    }

    val maxX = when {
      (getLeftPanelLockState() == LockState.CLOSE) ||
          (selectedPanel == Panel.CENTER && swipeDirection == SwipeDirection.LEFT) -> 0f
      else -> max(startPanelOpenedCenterPanelX, endPanelOpenedCenterPanelX)
    }

    val minX = when {
      (getRightPanelLockState() == LockState.CLOSE) ||
          (selectedPanel == Panel.CENTER && swipeDirection == SwipeDirection.RIGHT) -> 0f
      else -> min(startPanelOpenedCenterPanelX, endPanelOpenedCenterPanelX)
    }

    return when {
      targetedX > maxX -> {
        maxX
      }
      targetedX < minX -> {
        minX
      }
      else -> {
        targetedX
      }
    }
  }

  /**
   * This method is necessary because users can't keep their fingers perfectly still on
   * the phone screen. If the user is trying to hold a finger in one place, but the finger actually
   * deviates to the left or right by a pixel, it will trigger a drawer state change between
   * Opening and Closing. We generally want the drawer state to stay the same in this situation,
   * so we only handle the ACTION_MOVE event if it is either one of the fully opened or closed
   * positions or if it the targeted x position is greater than 1dp different from the current
   * x position.
   */
  private fun shouldHandleActionMoveEvent(event: MotionEvent): Boolean {
    val targetedX = getTargetedX(event)
    val normalizedX = getNormalizedX(targetedX)
    val greaterThanMinChange = abs(normalizedX - centerPanel.x) > resources.displayMetrics.density

    return normalizedX == 0f ||
        normalizedX == startPanelOpenedCenterPanelX ||
        normalizedX == endPanelOpenedCenterPanelX ||
        greaterThanMinChange
  }

  private fun getTargetedX(event: MotionEvent): Float = event.rawX + centerPanelDiffX

  private fun calculateDistanceX(startX: Float, event: MotionEvent): Float = event.x - startX

  private fun calculateDistanceY(startY: Float, event: MotionEvent): Float = event.y - startY

  /**
   * [animationDurationMs] should generally be 250ms for opening and 200ms for closing
   * according to https://material.io/design/motion/speed.html#duration
   */
  private fun updateCenterPanelXWithAnimation(
    x: Float,
    isFling: Boolean = false,
    animationDurationMs: Long = SIDE_PANEL_OPEN_DURATION_MS
  ) {
    val previousX = centerPanel.x
    centerPanelXAnimator?.cancel()

    val normalizedX = getNormalizedX(targetedX = x)
    centerPanelAnimationEndX = normalizedX

    if (isFling) {
      centerPanelXAnimator = ValueAnimator.ofFloat(previousX, normalizedX).apply {
        // https://material.io/design/motion/speed.html#easing
        // Use the suggested interpolator for Decelerated Easing
        interpolator = LinearOutSlowInInterpolator()
        duration = animationDurationMs
      }
      centerPanelXAnimator?.addUpdateListener { animator ->
        updateCenterPanelX(animator.animatedValue as Float)
      }
    } else {
      centerPanelXAnimator = ValueAnimator.ofFloat(previousX, normalizedX).apply {
        // https://material.io/design/motion/speed.html#easing
        // Use the suggested interpolator for Standard Easing
        interpolator = FastOutSlowInInterpolator()
        duration = animationDurationMs
      }
      centerPanelXAnimator?.addUpdateListener { animator ->
        updateCenterPanelX(animator.animatedValue as Float)
      }
    }

    centerPanelXAnimator?.start()
  }

  private fun updateCenterPanelX(x: Float) {
    val previousX = centerPanel.x
    centerPanel.x = x
    handleCenterPanelX(previousX, x)
  }

  /**
   * Call this method anytime the x position of the center panel changes, so we can
   * notify listeners of drawer state changes.
   */
  private fun handleCenterPanelX(previousX: Float, x: Float) {
    startPanel.visibility =
      if ((isLeftToRight && centerPanel.x > 0) || (!isLeftToRight && centerPanel.x < 0)) {
        View.VISIBLE
      } else {
        View.INVISIBLE
      }
    endPanel.visibility =
      if ((isLeftToRight && centerPanel.x < 0) || (!isLeftToRight && centerPanel.x > 0)) {
        View.VISIBLE
      } else {
        View.INVISIBLE
      }

    when (x) {
      0f -> {
        selectedPanel = Panel.CENTER
      }
      startPanelOpenedCenterPanelX -> {
        selectedPanel = Panel.START
      }
      endPanelOpenedCenterPanelX -> {
        selectedPanel = Panel.END
      }
    }

    val isCenterPanelClosed = x == endPanelOpenedCenterPanelX || x == startPanelOpenedCenterPanelX
    centerPanel.setEnabledAlpha(enabled = !isCenterPanelClosed, disabledAlpha = 0.5f)

    val isCenterPanelInRestingState = x == 0f || isCenterPanelClosed
    centerPanel.elevation = if (isCenterPanelInRestingState) {
      0f
    } else {
      resources.getDimension(R.dimen.overlapping_panels_center_panel_non_resting_elevation)
    }

    startPanelState = getStartPanelState(previousX, x)
    for (leftPanelStateListener in startPanelStateListeners) {
      leftPanelStateListener.onPanelStateChange(startPanelState)
    }

    endPanelState = getEndPanelState(previousX, x)
    for (rightPanelStateListener in endPanelStateListeners) {
      rightPanelStateListener.onPanelStateChange(endPanelState)
    }
  }

  private fun getStartPanelState(previousX: Float, x: Float): PanelState {
    val isLockedOpen = startPanelLockState == LockState.OPEN
    return when {
      isLeftToRight && x <= 0F -> PanelState.Closed
      !isLeftToRight && x >= 0f -> PanelState.Closed
      x == startPanelOpenedCenterPanelX -> PanelState.Opened(isLocked = isLockedOpen)
      isLeftToRight && x > previousX -> PanelState.Opening
      !isLeftToRight && x < previousX -> PanelState.Opening
      else -> PanelState.Closing
    }
  }

  private fun getEndPanelState(previousX: Float, x: Float): PanelState {
    val isLockedOpen = endPanelLockState == LockState.OPEN
    return when {
      isLeftToRight && x >= 0F -> PanelState.Closed
      !isLeftToRight && x <= 0f -> PanelState.Closed
      x == endPanelOpenedCenterPanelX -> PanelState.Opened(isLocked = isLockedOpen)
      isLeftToRight && x < previousX -> PanelState.Opening
      !isLeftToRight && x > previousX -> PanelState.Opening
      else -> PanelState.Closing
    }
  }

  private fun initPanels() {
    startPanel = getChildAt(0)
    centerPanel = getChildAt(1)
    endPanel = getChildAt(2)

    startPanel.visibility = View.INVISIBLE
    startPanel.elevation = 0f

    centerPanel.visibility = View.VISIBLE
    centerPanel.elevation = 0f

    endPanel.visibility = View.INVISIBLE
    endPanel.elevation = 0f

    // OverlappingPanelsLayout controls the widths of the side panel views depending
    // on state like useFullPortraitWidthForStartPanel and the portrait mode width
    // of the device.
    resetStartPanelWidth()
    resetEndPanelWidth()

    // Get the min and max x values for the center panel based on the initial widths.
    handleStartPanelWidthUpdate()
    handleEndPanelWidthUpdate()

    // Apply the pending update once after the child views are available and
    // after we've handled the initial side panel widths.
    pendingUpdate?.invoke()
    pendingUpdate = null

    // If the side panel sizes change (e.g. when the left panel starts full screen for a new
    // user and then becomes non-full-screen after the user has channels or guilds), then
    // recalculate the min and max x values.
    startPanel.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
      if (isLeftToRight && right != oldRight) {
        handleStartPanelWidthUpdate()
      } else if (!isLeftToRight && left != oldLeft) {
        handleStartPanelWidthUpdate()
      }
    }
    endPanel.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
      if (isLeftToRight && left != oldLeft) {
        handleEndPanelWidthUpdate()
      } else if (!isLeftToRight && right != oldRight) {
        handleEndPanelWidthUpdate()
      }
    }
  }

  private fun isTouchingCenterPanelWhileSidePanelOpen(event: MotionEvent): Boolean {
    val rawX = event.rawX
    val centerPanelX = centerPanel.x

    val maxCenterPanelX = max(startPanelOpenedCenterPanelX, endPanelOpenedCenterPanelX)
    val minCenterPanelX = min(startPanelOpenedCenterPanelX, endPanelOpenedCenterPanelX)
    val centerPanelRightEdgeXWhenRightPanelFullyOpen = minCenterPanelX + centerPanel.width

    val isTouchingCenterPanelWithLeftPanelOpen = rawX > maxCenterPanelX
    val isTouchingCenterPanelWithRightPanelOpen =
      rawX < centerPanelRightEdgeXWhenRightPanelFullyOpen
    val isLeftPanelFullyOpen = centerPanelX == maxCenterPanelX
    val isRightPanelFullyOpen = centerPanelX == minCenterPanelX

    return (isLeftPanelFullyOpen && isTouchingCenterPanelWithLeftPanelOpen) ||
        (isRightPanelFullyOpen && isTouchingCenterPanelWithRightPanelOpen)
  }

  private fun isTouchingChildGestureRegion(event: MotionEvent): Boolean {
    val rawX = event.rawX
    val rawY = event.rawY

    childGestureRegions.forEach { childGestureRegion ->
      val isInXRange = rawX >= childGestureRegion.left && rawX <= childGestureRegion.right

      // https://stackoverflow.com/questions/11483345/how-do-android-screen-coordinates-work
      // Y value 0 represents the top of the screen, so we need to check that the Y value is
      // greater than the top and less than the bottom.
      val isInYRange = rawY <= childGestureRegion.bottom && rawY >= childGestureRegion.top

      val isInChildGestureRegion = isInXRange && isInYRange

      if (isInChildGestureRegion) {
        return true
      }
    }

    return false
  }

  private fun handleStartPanelWidthUpdate() {
    val previousStartPanelOpenedCenterPanelX = startPanelOpenedCenterPanelX
    val marginBetweenPanels =
      resources.getDimension(R.dimen.overlapping_panels_margin_between_panels)
    startPanelOpenedCenterPanelX = startPanel.width + marginBetweenPanels
    startPanelOpenedCenterPanelX =
      if (isLeftToRight) startPanelOpenedCenterPanelX else -startPanelOpenedCenterPanelX

    // If the start panel was in a fully opened state based on the previous startPanelOpenedCenterPanelX,
    // then translate the center panel to the new startPanelOpenedCenterPanelX
    if (centerPanel.x == previousStartPanelOpenedCenterPanelX ||
      centerPanelAnimationEndX == previousStartPanelOpenedCenterPanelX
    ) {
      openStartPanel()
    }
  }

  private fun handleEndPanelWidthUpdate() {
    val previousEndPanelOpenedCenterPanelX = endPanelOpenedCenterPanelX
    val marginBetweenPanels =
      resources.getDimension(R.dimen.overlapping_panels_margin_between_panels)
    endPanelOpenedCenterPanelX = -(endPanel.width + marginBetweenPanels)
    endPanelOpenedCenterPanelX = if (isLeftToRight) {
      endPanelOpenedCenterPanelX
    } else {
      -endPanelOpenedCenterPanelX
    }

    // If the end panel was in a fully opened state based on the previous endPanelOpenedCenterPanelX,
    // then translate the center panel to the new endPanelOpenedCenterPanelX
    if (
      centerPanel.x == previousEndPanelOpenedCenterPanelX ||
      centerPanelAnimationEndX == previousEndPanelOpenedCenterPanelX
    ) {
      openEndPanel()
    }
  }

  companion object {
    private const val SIDE_PANEL_CLOSE_DURATION_MS = 200L
    private const val SIDE_PANEL_OPEN_DURATION_MS = 250L
  }
}
