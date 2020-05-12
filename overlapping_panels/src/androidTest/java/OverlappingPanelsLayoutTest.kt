package com.discord.panels

import android.app.Activity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import com.discord.panels.test.R
import com.google.common.truth.Truth
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
class OverlappingPanelsLayoutTest {

  private lateinit var activityController: ActivityController<Activity>
  private lateinit var activity: Activity

  private lateinit var overlappingPanelsLayout: OverlappingPanelsLayout

  @Before
  fun setUp() {
    activityController = Robolectric.buildActivity(Activity::class.java)
    activity = activityController.get()

    overlappingPanelsLayout =
      LayoutInflater.from(activity).inflate(
        R.layout.overlapping_panels_test_layout,
        null
      ) as OverlappingPanelsLayout
  }

  @Test
  fun toName() {
    val startPanelStates = mutableListOf<PanelState>()

    overlappingPanelsLayout
      .registerStartPanelStateListeners(object : OverlappingPanelsLayout.PanelStateListener {
        override fun onPanelStateChange(panelState: PanelState) {
          startPanelStates.add(panelState)
        }
      })

    dispatchSwipeRightGesture(overlappingPanelsLayout)

    Truth.assertThat(startPanelStates.size).isEqualTo(1)
    Truth.assertThat(startPanelStates[0]).isEqualTo(PanelState.Opened(isLocked = false))
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
}
