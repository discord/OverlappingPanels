package com.discord.sampleappcompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.discord.panels.OverlappingPanels
import com.discord.panels.rememberOverlappingPanelsState
import com.discord.sampleappcompose.component.PanelColumn
import com.discord.sampleappcompose.component.PanelHeaderText
import com.discord.sampleappcompose.component.ScrollItem
import com.discord.sampleappcompose.ui.theme.OverlappingPanelsTheme
import kotlinx.coroutines.launch

@ExperimentalMaterialApi
class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      OverlappingPanelsTheme {
        val panelState = rememberOverlappingPanelsState()
        val coroutineScope = rememberCoroutineScope()
        Scaffold(
          topBar = {
            TopAppBar(
              backgroundColor = MaterialTheme.colors.secondary
            ) {
              Text(
                text = stringResource(R.string.app_name),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
              )
            }
          },
          backgroundColor = MaterialTheme.colors.surface
        ) {
          OverlappingPanels(
            modifier = Modifier.fillMaxSize(),
            panelsState = panelState,
            panelStart = {
              Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colors.background,
                onClick = {
                  coroutineScope.launch {
                    panelState.closePanel()
                  }
                }
              ) {
                PanelColumn(
                  modifier = Modifier.fillMaxSize()
                ) {
                  PanelHeaderText(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    text = stringResource(R.string.start_panel_name)
                  )
                }
              }
            },
            panelCenter = {
              Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colors.background
              ) {
                PanelColumn(
                  modifier = Modifier.fillMaxSize()
                ) {
                  PanelHeaderText(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    text = stringResource(R.string.center_panel_name)
                  )
                  Text(text = stringResource(R.string.swipe_gesture_instructions))
                  Spacer(Modifier.weight(1f))
                  Text(text = stringResource(R.string.open_panel_programmatically_instructions))
                  Button(
                    onClick = {
                      coroutineScope.launch {
                        panelState.openStartPanel()
                      }
                    }
                  ) {
                    Text(text = stringResource(R.string.open_start_panel_button_text))
                  }
                  Button(
                    onClick = {
                      coroutineScope.launch {
                        panelState.openEndPanel()
                      }
                    }
                  ) {
                    Text(text = stringResource(R.string.open_end_panel_button_text))
                  }
                  Spacer(Modifier.weight(1f))
                  Text(text = stringResource(R.string.child_gesture_region_instructions))
                  LazyRow {
                    items(10) { index ->
                      ScrollItem(
                        modifier = Modifier.padding(4.dp),
                        itemNumber = index + 1
                      )
                    }
                  }
                  Spacer(Modifier.height(48.dp))
                }
              }
            },
            panelEnd = {
              Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colors.background,
              ) {
                PanelColumn(
                  modifier = Modifier.fillMaxSize()
                ) {
                  PanelHeaderText(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    text = stringResource(R.string.end_panel_name)
                  )
                }
              }
            }
          )
        }
      }
    }
  }
}
