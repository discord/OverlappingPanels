package com.discord.sampleapp

import android.app.Activity
import android.os.Build
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import com.discord.panels.OverlappingPanelsLayout
import com.discord.panels.PanelState
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@Config(sdk = [Build.VERSION_CODES.P])
@LooperMode(LooperMode.Mode.PAUSED)
@RunWith(RobolectricTestRunner::class)
class OverlappingPanelsIntegrationTest {

  private lateinit var activityController: ActivityController<Activity>
  private lateinit var activity: Activity

  private lateinit var overlappingPanelsLayout: OverlappingPanelsLayout

  @Before
  fun setUp() {
    activityController = Robolectric.buildActivity(Activity::class.java)
    activity = activityController.get()

    val root = LayoutInflater.from(activity)
      .inflate(
        R.layout.main_activity /* resource */,
        null /* root */
      )

    overlappingPanelsLayout = root.findViewById(R.id.overlapping_panels) as OverlappingPanelsLayout
    overlappingPanelsLayout.layout(0 /* l */, 0 /* t */, 480 /* r */, 480 /* b */)
  }

  @Test
  fun swipeRightOpensStartPanel() {
    var startPanelState: PanelState? = null
    var endPanelState: PanelState? = null

    overlappingPanelsLayout
      .registerStartPanelStateListeners(object :
        OverlappingPanelsLayout.PanelStateListener {
        override fun onPanelStateChange(panelState: PanelState) {
          startPanelState = panelState
        }
      })

    overlappingPanelsLayout
      .registerEndPanelStateListeners(object :
        OverlappingPanelsLayout.PanelStateListener {
        override fun onPanelStateChange(panelState: PanelState) {
          endPanelState = panelState
        }
      })

    dispatchSwipeRightGesture(overlappingPanelsLayout)
    runPendingAnimations()

    Truth.assertThat(startPanelState).isEqualTo(
      PanelState.Opened(
        isLocked = false
      )
    )

    Truth.assertThat(endPanelState).isEqualTo(
      PanelState.Closed
    )
  }

  @Test
  fun swipeLeftOpensStartPanel() {
    var startPanelState: PanelState? = null
    var endPanelState: PanelState? = null

    overlappingPanelsLayout
      .registerStartPanelStateListeners(object :
        OverlappingPanelsLayout.PanelStateListener {
        override fun onPanelStateChange(panelState: PanelState) {
          startPanelState = panelState
        }
      })

    overlappingPanelsLayout
      .registerEndPanelStateListeners(object :
        OverlappingPanelsLayout.PanelStateListener {
        override fun onPanelStateChange(panelState: PanelState) {
          endPanelState = panelState
        }
      })

    dispatchSwipeLeftGesture(overlappingPanelsLayout)
    runPendingAnimations()

    Truth.assertThat(startPanelState).isEqualTo(
      PanelState.Closed
    )

    Truth.assertThat(endPanelState).isEqualTo(
      PanelState.Opened(
        isLocked = false
      )
    )
  }

  @Test
  fun openStartPanelOpensStartPanel() {
    var startPanelState: PanelState? = null

    overlappingPanelsLayout
      .registerStartPanelStateListeners(object :
        OverlappingPanelsLayout.PanelStateListener {
        override fun onPanelStateChange(panelState: PanelState) {
          startPanelState = panelState
        }
      })

    overlappingPanelsLayout.openStartPanel()
    runPendingAnimations()

    Truth.assertThat(startPanelState).isEqualTo(PanelState.Opened(isLocked = false))
  }

  @Test
  fun openEndPanelOpensEndPanel() {
    var endPanelState: PanelState? = null

    overlappingPanelsLayout
      .registerEndPanelStateListeners(object :
        OverlappingPanelsLayout.PanelStateListener {
        override fun onPanelStateChange(panelState: PanelState) {
          endPanelState = panelState
        }
      })

    overlappingPanelsLayout.openEndPanel()
    runPendingAnimations()

    Truth.assertThat(endPanelState).isEqualTo(PanelState.Opened(isLocked = false))
  }

  @Test
  fun closePanelsClosesSidePanels() {
    var startPanelState: PanelState? = null
    var endPanelState: PanelState? = null

    overlappingPanelsLayout
      .registerStartPanelStateListeners(object :
        OverlappingPanelsLayout.PanelStateListener {
        override fun onPanelStateChange(panelState: PanelState) {
          startPanelState = panelState
        }
      })

    overlappingPanelsLayout
      .registerEndPanelStateListeners(object :
        OverlappingPanelsLayout.PanelStateListener {
        override fun onPanelStateChange(panelState: PanelState) {
          endPanelState = panelState
        }
      })

    overlappingPanelsLayout.openStartPanel()
    runPendingAnimations()
    Truth.assertThat(startPanelState).isEqualTo(PanelState.Opened(isLocked = false))
    Truth.assertThat(endPanelState).isEqualTo(PanelState.Closed)

    overlappingPanelsLayout.closePanels()
    runPendingAnimations()
    Truth.assertThat(startPanelState).isEqualTo(PanelState.Closed)
    Truth.assertThat(endPanelState).isEqualTo(PanelState.Closed)
  }

  private fun runPendingAnimations() {
    Shadows.shadowOf(Looper.getMainLooper()).idle()
  }

  private fun dispatchSwipeRightGesture(view: View) {
    val actionDownTime = System.currentTimeMillis()
    val eventDownTime = System.currentTimeMillis()
    val downX = 0f
    val downY = 0f

    // List of meta states found here: developer.android.com/reference/android/view/KeyEvent.html#getMetaState()
    val metaState = 0

    val downMotionEvent = MotionEvent.obtain(
      actionDownTime,
      eventDownTime,
      MotionEvent.ACTION_DOWN,
      downX,
      downY,
      metaState
    )

    val actionMove1Time = System.currentTimeMillis()
    val eventMove1Time = System.currentTimeMillis()
    val actionMove1X = 160f
    val actionMove1Y = 0f
    val move1MotionEvent = MotionEvent.obtain(
      actionMove1Time,
      eventMove1Time,
      MotionEvent.ACTION_MOVE,
      actionMove1X,
      actionMove1Y,
      metaState
    )

    val actionMove2Time = System.currentTimeMillis()
    val eventMove2Time = System.currentTimeMillis()
    val actionMove2X = 320f
    val actionMove2Y = 0f
    val move2MotionEvent = MotionEvent.obtain(
      actionMove2Time,
      eventMove2Time,
      MotionEvent.ACTION_MOVE,
      actionMove2X,
      actionMove2Y,
      metaState
    )

    val actionUpTime = System.currentTimeMillis()
    val eventUpTime = System.currentTimeMillis()
    val actionUpX = 320f
    val actionUpY = 0f
    val upMotionEvent = MotionEvent.obtain(
      actionUpTime,
      eventUpTime,
      MotionEvent.ACTION_MOVE,
      actionUpX,
      actionUpY,
      metaState
    )

    view.dispatchTouchEvent(downMotionEvent)
    view.dispatchTouchEvent(move1MotionEvent)
    view.dispatchTouchEvent(move2MotionEvent)
    view.dispatchTouchEvent(upMotionEvent)
  }

  private fun dispatchSwipeLeftGesture(view: View) {
    val actionDownTime = System.currentTimeMillis()
    val eventDownTime = System.currentTimeMillis()
    val downX = 320f
    val downY = 320f

    // List of meta states found here: developer.android.com/reference/android/view/KeyEvent.html#getMetaState()
    val metaState = 0

    val downMotionEvent = MotionEvent.obtain(
      actionDownTime,
      eventDownTime,
      MotionEvent.ACTION_DOWN,
      downX,
      downY,
      metaState
    )

    val actionMove1Time = System.currentTimeMillis()
    val eventMove1Time = System.currentTimeMillis()
    val actionMove1X = 160f
    val actionMove1Y = 0f
    val move1MotionEvent = MotionEvent.obtain(
      actionMove1Time,
      eventMove1Time,
      MotionEvent.ACTION_MOVE,
      actionMove1X,
      actionMove1Y,
      metaState
    )

    val actionMove2Time = System.currentTimeMillis()
    val eventMove2Time = System.currentTimeMillis()
    val actionMove2X = 0f
    val actionMove2Y = 0f
    val move2MotionEvent = MotionEvent.obtain(
      actionMove2Time,
      eventMove2Time,
      MotionEvent.ACTION_MOVE,
      actionMove2X,
      actionMove2Y,
      metaState
    )

    val actionUpTime = System.currentTimeMillis()
    val eventUpTime = System.currentTimeMillis()
    val actionUpX = 0f
    val actionUpY = 0f
    val upMotionEvent = MotionEvent.obtain(
      actionUpTime,
      eventUpTime,
      MotionEvent.ACTION_MOVE,
      actionUpX,
      actionUpY,
      metaState
    )

    view.dispatchTouchEvent(downMotionEvent)
    view.dispatchTouchEvent(move1MotionEvent)
    view.dispatchTouchEvent(move2MotionEvent)
    view.dispatchTouchEvent(upMotionEvent)
  }
}
