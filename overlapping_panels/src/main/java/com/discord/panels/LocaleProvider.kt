package com.discord.panels

import android.content.Context
import androidx.core.os.ConfigurationCompat
import java.util.Locale

/**
 * To provide a different locale to determine the panels layout direction, call [setProvider] before
 * instantiating [OverlappingPanelsLayout]
 */
object LocaleProvider {

  private var provider: (Context) -> Locale = { context: Context ->
    ConfigurationCompat.getLocales(context.resources.configuration)[0]
  }

  fun getPrimaryLocale(context: Context): Locale = provider.invoke(context)

  fun setProvider(provider: (Context) -> Locale) {
    this.provider = provider
  }
}
