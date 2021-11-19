package com.discord.sampleapp

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.discord.panels.OverlappingPanelsLayout
import com.discord.panels.PanelState
import com.discord.panels.PanelsChildGestureRegionObserver
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.reactivex.rxjava3.disposables.Disposable

class MainActivity : AppCompatActivity(),
  PanelsChildGestureRegionObserver.GestureRegionsListener {

  private lateinit var viewModel: MainViewModel
  private var viewStateDisposable: Disposable? = null

  private lateinit var overlappingPanels: OverlappingPanelsLayout
  private lateinit var openStartPanelButton: View
  private lateinit var horizontalScrollItemsContainer: View
  private lateinit var showToastButton: View

  private lateinit var tabLayout: TabLayout
  private lateinit var viewPager: ViewPager2

  private lateinit var centerPanelMainLayout: ViewGroup
  private lateinit var viewPagerLayout: ViewGroup

  private val adapter = ViewPagerAdapter(this)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main_activity)
    setSupportActionBar(findViewById(R.id.toolbar))
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
    PanelsChildGestureRegionObserver.Provider.get().register(horizontalScrollItemsContainer)

    // This button helps verify the accuracy of
    // OverlappingPanelsLayout#isTouchingCenterPanelWhileSidePanelOpen()
    showToastButton = findViewById(R.id.show_toast_button)
    showToastButton.setOnClickListener {
      Toast.makeText(
        this,
        "clicked button in start panel",
        Toast.LENGTH_LONG
      ).show()
    }

    viewModel = ViewModelProvider(
      this,
      MainViewModel.Factory()
    ).get(MainViewModel::class.java)

    centerPanelMainLayout = findViewById(R.id.center_panel_main_layout)
    viewPagerLayout = findViewById(R.id.view_pager_layout)
    viewPager = findViewById(R.id.view_pager)
    tabLayout = findViewById(R.id.tabs)

    viewPager.apply {
      adapter = this@MainActivity.adapter
      addOnLayoutChangeListener(PanelsChildGestureRegionObserver.Provider.get())
    }

    TabLayoutMediator(tabLayout, viewPager) { tab, position ->
      tab.text = "Text $position"
    }.attach()

    tabLayout.apply {
      addOnLayoutChangeListener(PanelsChildGestureRegionObserver.Provider.get())
    }
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.menu_activity, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.view_pager_menu_item -> {
        val showViewPager = !viewPagerLayout.isVisible
        viewPagerLayout.isVisible = showViewPager
        if (showViewPager) {
          PanelsChildGestureRegionObserver.Provider.get().register(viewPagerLayout)
        } else {
          PanelsChildGestureRegionObserver.Provider.get().unregister(viewPagerLayout)
        }

        centerPanelMainLayout.isVisible = !centerPanelMainLayout.isVisible
      }
    }
    return super.onOptionsItemSelected(item)
  }

  override fun onResume() {
    super.onResume()

    // Save the panel state in the view model, so we can restore the panel state after
    // a device rotation.
    overlappingPanels
      .registerStartPanelStateListeners(object : OverlappingPanelsLayout.PanelStateListener {
        override fun onPanelStateChange(panelState: PanelState) {
          viewModel.onStartPanelStateChange(panelState)
        }
      })

    overlappingPanels
      .registerEndPanelStateListeners(object : OverlappingPanelsLayout.PanelStateListener {
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

    PanelsChildGestureRegionObserver.Provider.get().unregister(horizontalScrollItemsContainer)
    PanelsChildGestureRegionObserver.Provider.get().unregister(viewPager)
    PanelsChildGestureRegionObserver.Provider.get().unregister(tabLayout)
  }

  override fun onGestureRegionsUpdate(gestureRegions: List<Rect>) {
    overlappingPanels.setChildGestureRegions(gestureRegions)
  }

  private fun handleViewState(viewState: MainViewModel.ViewState) {
    overlappingPanels.handleStartPanelState(viewState.startPanelState)
    overlappingPanels.handleEndPanelState(viewState.endPanelState)
  }
}

class ViewPagerAdapter(
  activity: FragmentActivity
) : FragmentStateAdapter(activity) {

  override fun getItemCount() = 7

  override fun createFragment(position: Int) = MainFragment().apply {
    this.position = position
  }
}

class MainFragment : Fragment() {
  private val colors = listOf(
    R.color.one,
    R.color.two,
    R.color.three,
    R.color.four,
    R.color.five,
    R.color.six,
    R.color.seven
  )

  var position: Int = 0

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val view = inflater.inflate(R.layout.fragment_main, container, true)
    val colorView = view.findViewById<View>(R.id.color)
    colorView.setBackgroundColor(ContextCompat.getColor(requireContext(), colors[position]))
    return view
  }
}
