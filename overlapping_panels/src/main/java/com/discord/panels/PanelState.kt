package com.discord.panels

sealed class PanelState {
  object Opening : PanelState()
  object Opened : PanelState()
  object Closing : PanelState()
  object Closed : PanelState()
}
