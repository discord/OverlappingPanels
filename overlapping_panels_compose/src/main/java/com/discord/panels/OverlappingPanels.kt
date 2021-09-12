package com.discord.panels

import android.content.res.Configuration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeableState
import androidx.compose.material.swipeable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val animationDuration = 200

private val MarginBetweenPanels = 16.dp

enum class OverlappingPanelsValue {
  OpenRight, OpenLeft, Closed
}

@ExperimentalMaterialApi
class OverlappingPanelsState(
  initialValue: OverlappingPanelsValue,
  confirmStateChange: (OverlappingPanelsValue) -> Boolean = { true },
) {

  val swipeableState = SwipeableState(
    initialValue = initialValue,
    animationSpec = spring(),
    confirmStateChange = confirmStateChange
  )

  val currentValue
    get() = swipeableState.currentValue

  val offset
    get() = swipeableState.offset

  val isPanelClosed
    get() = currentValue == OverlappingPanelsValue.Closed

  val isEndPanelOpen
    get() = currentValue == OverlappingPanelsValue.OpenRight

  val isStartPanelOpen
    get() = currentValue == OverlappingPanelsValue.OpenLeft

  suspend fun closePanel() {
    swipeableState.animateTo(OverlappingPanelsValue.Closed)
  }

  suspend fun openEndPanel() {
    swipeableState.animateTo(OverlappingPanelsValue.OpenRight)
  }

  suspend fun openStartPanel() {
    swipeableState.animateTo(OverlappingPanelsValue.OpenLeft)
  }

  companion object {

    fun Saver(confirmStateChange: (OverlappingPanelsValue) -> Boolean) =
      Saver<OverlappingPanelsState, OverlappingPanelsValue>(
        save = { it.currentValue },
        restore = { OverlappingPanelsState(it, confirmStateChange) }
      )

  }
}

@ExperimentalMaterialApi
@Composable
fun rememberOverlappingPanelsState(
  initialValue: OverlappingPanelsValue = OverlappingPanelsValue.Closed,
  confirmStateChange: (OverlappingPanelsValue) -> Boolean = { true },
): OverlappingPanelsState {
  return rememberSaveable(saver = OverlappingPanelsState.Saver(confirmStateChange)) {
    OverlappingPanelsState(initialValue, confirmStateChange)
  }
}

@ExperimentalMaterialApi
@Composable
fun OverlappingPanels(
  modifier: Modifier = Modifier,
  panelsState: OverlappingPanelsState = rememberOverlappingPanelsState(initialValue = OverlappingPanelsValue.Closed),
  panelStart: @Composable BoxScope.() -> Unit,
  panelCenter: @Composable BoxScope.() -> Unit,
  panelEnd: @Composable BoxScope.() -> Unit,
) {
  val resources = LocalContext.current.resources

  val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

  BoxWithConstraints(modifier.fillMaxSize()) {
    val fraction =
      if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
        0.85f
      } else {
        0.45f
      }

    val offsetValue = (constraints.maxWidth * fraction) + MarginBetweenPanels.value

    val centerPanelAlpha by animateFloatAsState(
      targetValue = if (
        panelsState.offset.value == offsetValue ||
        panelsState.offset.value == -offsetValue
      ) 0.7f else 1f,
      animationSpec = tween(animationDuration)
    )

    val elevation by animateDpAsState(
      targetValue = if (
        panelsState.offset.value == offsetValue ||
        panelsState.offset.value == -offsetValue
      ) 0.dp else 8.dp,
      animationSpec = tween(animationDuration)
    )

    val anchors = mapOf(
      offsetValue to OverlappingPanelsValue.OpenLeft,
      0f to OverlappingPanelsValue.Closed,
      -offsetValue to OverlappingPanelsValue.OpenRight
    )

    Box(
      modifier = Modifier
        .fillMaxSize()
        .swipeable(
          state = panelsState.swipeableState,
          orientation = Orientation.Horizontal,
          velocityThreshold = 400.dp,
          anchors = anchors,
          reverseDirection = !isLtr,
          resistance = null
        )
    ) {
      Box(
        modifier = Modifier
          .fillMaxHeight()
          .fillMaxWidth(fraction)
          .align(if (isLtr) Alignment.CenterStart else Alignment.CenterEnd)
          .alpha(if ((isLtr && panelsState.offset.value > 0f) || (!isLtr && panelsState.offset.value < 0f)) 1f else 0f),
        content = if (isLtr) panelStart else panelEnd
      )
      Box(
        modifier = Modifier
          .fillMaxHeight()
          .fillMaxWidth(fraction)
          .align(if (isLtr) Alignment.CenterEnd else Alignment.CenterStart)
          .alpha(if ((isLtr && panelsState.offset.value < 0f) || (!isLtr && panelsState.offset.value > 0f)) 1f else 0f),
        content = if (isLtr) panelEnd else panelStart
      )
      Box(
        modifier = Modifier
          .fillMaxSize()
          .align(Alignment.Center)
          .alpha(centerPanelAlpha)
          .offset {
            IntOffset(
              x = panelsState.offset.value.roundToInt(),
              y = 0
            )
          }
          .shadow(elevation),
        content = panelCenter
      )
    }
  }
}
