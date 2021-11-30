package com.discord.panels

import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.UiThread
import com.discord.panels.PanelsChildGestureRegionObserver.GestureRegionsListener
import java.lang.ref.WeakReference

/**
 * Use [PanelsChildGestureRegionObserver] to register child gesture regions that should handle
 * their own horizontal scrolls rather than defaulting to [OverlappingPanelsLayout] handling
 * the horizontal scrolls as panel-swipe gestures.
 *
 * Example usage:
 * 1) Use [PanelsChildGestureRegionObserver.Provider.get] to get an Activity-scoped instance of
 * [PanelsChildGestureRegionObserver]
 * 2) Register the child gesture region View with the [register].
 * 3) In the parent of [OverlappingPanelsLayout], e.g. in a Fragment or Activity, implement
 * [GestureRegionsListener], and add the listener via [addGestureRegionsUpdateListener]
 * 4) Inside [GestureRegionsListener.onGestureRegionsUpdate], pass the child gesture regions to
 * [OverlappingPanelsLayout].
 * 5) Remember to unregister the child gesture region with [unregister] in appropriate Android
 * lifecycle methods.
 */
class PanelsChildGestureRegionObserver : View.OnLayoutChangeListener {

  interface GestureRegionsListener {
    fun onGestureRegionsUpdate(gestureRegions: List<Rect>)
  }

  private val viewIdToGestureRegionMap = mutableMapOf<Int, Rect>()
  private val viewIdToListenerMap = mutableMapOf<Int, ViewTreeObserver.OnScrollChangedListener>()
  private val gestureRegionsListeners = mutableSetOf<GestureRegionsListener>()

  override fun onLayoutChange(
    view: View,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    oldLeft: Int,
    oldTop: Int,
    oldRight: Int,
    oldBottom: Int
  ) {
    if (!viewIdToListenerMap.keys.contains(view.id)) {
      return
    }
    val coordinates = intArrayOf(0, 0)
    view.getLocationInWindow(coordinates)

    val x = coordinates[0]
    val y = coordinates[1]

    val absoluteRight = x + right
    val absoluteBottom = y + bottom

    viewIdToGestureRegionMap[view.id] = Rect(
      x,
      y,
      absoluteRight,
      absoluteBottom
    )

    publishGestureRegionsUpdate()
  }

  @UiThread
  fun register(view: View) {
    if (viewIdToListenerMap.contains(view.id)) {
      Log.w(
        javaClass.simpleName,
        "failed to register view with ID ${view.id}. already registered"
      )
      return
    }

    view.addOnLayoutChangeListener(this)

    val listener = ViewTreeObserver.OnScrollChangedListener {
      onLayoutChange(
        view = view,
        left = view.left,
        top = view.top,
        right = view.right,
        bottom = view.bottom,
        oldLeft = 0,
        oldTop = 0,
        oldRight = 0,
        oldBottom = 0
      )
    }

    view.viewTreeObserver.addOnScrollChangedListener(listener)
    viewIdToListenerMap[view.id] = listener
  }

  /**
   * Stop publishing gesture region updates based on layout and scroll changes to android.view.View
   */
  @UiThread
  fun unregister(view: View) {
    viewIdToListenerMap.remove(view.id)?.let {
      view.viewTreeObserver.removeOnScrollChangedListener(it)
    }

    view.removeOnLayoutChangeListener(this)

    viewIdToGestureRegionMap.remove(view.id)
    publishGestureRegionsUpdate()
  }

  /**
   * Add [gestureRegionsListener] to this [PanelsChildGestureRegionObserver]. This method notifies
   * that listener as soon as it adds the listener. That listener will continue to get future
   * updates from layout changes on child gesture regions.
   */
  @UiThread
  fun addGestureRegionsUpdateListener(gestureRegionsListener: GestureRegionsListener) {
    val gestureRegions = viewIdToGestureRegionMap.values.toList()
    gestureRegionsListener.onGestureRegionsUpdate(gestureRegions)
    gestureRegionsListeners.add(gestureRegionsListener)
  }

  /**
   * Remove [gestureRegionsListener] from the set of listeners to notify.
   */
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
