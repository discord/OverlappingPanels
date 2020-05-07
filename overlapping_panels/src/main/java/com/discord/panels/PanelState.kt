package com.discord.panels

sealed class PanelState {
  object Opening : PanelState()
  data class Opened(val isLocked: Boolean) : PanelState()
  object Closing : PanelState()
  object Closed : PanelState()
}
