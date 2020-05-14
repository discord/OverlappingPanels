package com.discord.sampleapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.discord.panels.PanelState
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject

class MainViewModel : ViewModel() {

  data class ViewState(
    val startPanelState: PanelState,
    val endPanelState: PanelState
  )

  private val viewStateSubject: BehaviorSubject<ViewState> =
    BehaviorSubject.createDefault<ViewState>(
      ViewState(
        startPanelState = PanelState.Closed,
        endPanelState = PanelState.Closed
      )
    )

  fun observeViewState(): Observable<ViewState> = viewStateSubject

  fun onStartPanelStateChange(panelState: PanelState) {
    val viewState = viewStateSubject.value
    viewStateSubject.onNext(viewState.copy(startPanelState = panelState))
  }

  fun onEndPanelStateChange(panelState: PanelState) {
    val viewState = viewStateSubject.value
    viewStateSubject.onNext(viewState.copy(endPanelState = panelState))
  }

  class Factory() : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
      return MainViewModel() as T
    }
  }
}
