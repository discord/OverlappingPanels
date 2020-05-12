package com.discord.sampleapp

import android.graphics.Rect
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import com.discord.panels.OverlappingPanelsLayout
import com.discord.panels.PanelState
import com.discord.panels.PanelsChildGestureRegionObserver
import io.reactivex.rxjava3.disposables.Disposable

class MainActivity : AppCompatActivity(),
  PanelsChildGestureRegionObserver.GestureRegionsListener {

  private lateinit var viewModel: MainViewModel
  private var viewStateDisposable: Disposable? = null

  private lateinit var overlappingPanels: OverlappingPanelsLayout
  private lateinit var openStartPanelButton: View
  private lateinit var horizontalScrollItemsContainer: View

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main_activity)
    overlappingPanels = findViewById(R.id.overlapping_panels)

    openStartPanelButton = findViewById(R.id.open_start_panel_button)
    openStartPanelButton.setOnClickListener {

      // Use methods like openStartPanel() to open panels programmatically without swipe
      // gestures.
      overlappingPanels.openStartPanel()
    }

    horizontalScrollItemsContainer = findViewById(R.id.scroll_items_container)

    // To not handle panel gestures on selected child views, e.g. if the child view has its own
    // horizontal scroll handling,
    // 1) Add PanelsChildGestureRegionObserver as an OnLayoutChangeListener on that child view
    // 2) Make the host fragment / activity listen to child gesture region updates (e.g. in
    //    onResume()).
    // 3) Remember to remove the listener (e.g. in onPause() for an Activity), and remove the
    //    child view from PanelsChildGestureRegionObserver.
    //
    // In this example, we're adding the OnLayoutChangeListener to a view in main_activity.xml.
    // This will also work in other cases like child views in Fragments within MainActivity
    // because PanelsChildGestureRegionObserver.Provider.get() returns an Activity-scoped
    // singleton.
    horizontalScrollItemsContainer.addOnLayoutChangeListener(
      PanelsChildGestureRegionObserver.Provider.get()
    )

    viewModel = ViewModelProvider(this,
      MainViewModel.Factory()
    ).get(MainViewModel::class.java)
  }

  override fun onResume() {
    super.onResume()

    // Save the panel state in the view model, so we can restore the panel state after
    // a device rotation.
    overlappingPanels
      .registerStartPanelStateListeners(object :
        OverlappingPanelsLayout.PanelStateListener {
        override fun onPanelStateChange(panelState: PanelState) {
          viewModel.onStartPanelStateChange(panelState)
        }
      })

    overlappingPanels
      .registerEndPanelStateListeners(object :
        OverlappingPanelsLayout.PanelStateListener {
        override fun onPanelStateChange(panelState: PanelState) {
          viewModel.onEndPanelStateChange(panelState)
        }
      })

    // Apply reactive updates to the OverlappingPanels state to handle use cases like:
    // 1) Restoring panel state after a device rotation
    // 2) Opening a panel in response to business logic from classes that don't have
    //    access to views.
    viewStateDisposable = viewModel.observeViewState()
      .subscribe { viewState ->
        handleViewState(viewState)
      }

    PanelsChildGestureRegionObserver.Provider.get().addGestureRegionsUpdateListener(this)
  }

  override fun onPause() {
    super.onPause()
    viewStateDisposable?.dispose()

    PanelsChildGestureRegionObserver.Provider.get().addGestureRegionsUpdateListener(this)
  }

  override fun onDestroy() {
    super.onDestroy()

    PanelsChildGestureRegionObserver.Provider.get().remove(horizontalScrollItemsContainer.id)
  }

  override fun onGestureRegionsUpdate(gestureRegions: List<Rect>) {
    overlappingPanels.setChildGestureRegions(gestureRegions)
  }

  private fun handleViewState(viewState: MainViewModel.ViewState) {
    overlappingPanels.handleStartPanelState(viewState.startPanelState)
    overlappingPanels.handleEndPanelState(viewState.endPanelState)
  }
}
