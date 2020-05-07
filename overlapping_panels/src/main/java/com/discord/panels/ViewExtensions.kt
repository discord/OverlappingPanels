package com.discord.panels

import android.view.View

fun View.setEnabledAlpha(enabled: Boolean, disabledAlpha: Float = 0.5f) {
  alpha = if (enabled) 1f else disabledAlpha
}
