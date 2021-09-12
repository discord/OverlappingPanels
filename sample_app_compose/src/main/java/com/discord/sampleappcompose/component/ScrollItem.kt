package com.discord.sampleappcompose.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.discord.sampleappcompose.R

@Composable
fun ScrollItem(
  modifier: Modifier = Modifier,
  itemNumber: Int
) {
  Card(
    modifier = modifier,
    backgroundColor = MaterialTheme.colors.surface,
    shape = MaterialTheme.shapes.medium,
    elevation = 0.dp
  ) {
    Text(
      modifier = Modifier.padding(8.dp),
      text = stringResource(R.string.scroll_item, itemNumber)
    )
  }
}
