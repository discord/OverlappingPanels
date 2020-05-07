package com.discord.panels

import android.graphics.Rect
import android.view.View
import androidx.annotation.UiThread
import java.lang.ref.WeakReference

class PanelsChildGestureRegionObserver : View.OnLayoutChangeListener {

  interface GestureRegionsListener {
    fun onGestureRegionsUpdate(gestureRegions: List<Rect>)
  }

  private var viewIdToGestureRegionMap = mutableMapOf<Int, Rect>()
  private var gestureRegionsListeners = mutableSetOf<GestureRegionsListener>()

  override fun onLayoutChange(
    view: View?,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    oldLeft: Int,
    oldTop: Int,
    oldRight: Int,
    oldBottom: Int
  ) {
    if (view != null) {
      val coordinates = intArrayOf(0, 0)
      view.getLocationOnScreen(coordinates)

      val x = coordinates[0]
      val y = coordinates[1]

      val absoluteLeft = x + left
      val absoluteTop = y + top
      val absoluteRight = x + right
      val absoluteBottom = y + bottom

      viewIdToGestureRegionMap[view.id] = Rect(
          absoluteLeft,
          absoluteTop,
          absoluteRight,
          absoluteBottom
      )

      publishGestureRegionsUpdate()
    }
  }

  @UiThread
  fun remove(viewId: Int) {
    viewIdToGestureRegionMap.remove(viewId)
    publishGestureRegionsUpdate()
  }

  @UiThread
  fun addGestureRegionsUpdateListener(gestureRegionsListener: GestureRegionsListener) {
    val gestureRegions = viewIdToGestureRegionMap.values.toList()
    gestureRegionsListener.onGestureRegionsUpdate(gestureRegions)
    gestureRegionsListeners.add(gestureRegionsListener)
  }

  @UiThread
  fun removeGestureRegionsUpdateListener(gestureRegionsListener: GestureRegionsListener) {
    gestureRegionsListeners.remove(gestureRegionsListener)
  }

  private fun publishGestureRegionsUpdate() {
    val gestureRegions = viewIdToGestureRegionMap.values.toList()
    gestureRegionsListeners.forEach { gestureRegionsListener ->
      gestureRegionsListener.onGestureRegionsUpdate(gestureRegions)
    }
  }

  object Provider {

    private var observerWeakRef = WeakReference<PanelsChildGestureRegionObserver>(null)

    // This is a lazily instantiated singleton. There is at most one instance of
    // PanelsChildGestureRegionObserver at a time. If an Activity creates this, all other calls to get()
    // from child views will return the same instance. If the Activity and all other references to this
    // get destroyed, then observerWeakRef will hold onto null, and the next call to get() will create
    // a new instance.
    @JvmStatic
    @UiThread
    fun get(): PanelsChildGestureRegionObserver {
      val previousObserver = observerWeakRef.get()

      return if (previousObserver == null) {
        val observer = PanelsChildGestureRegionObserver()
        observerWeakRef = WeakReference(observer)
        observer
      } else {
        previousObserver
      }
    }
  }
}
