package com.apde2

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.dp

class FileIconView(
   context: Context,
   private val theme: EditorTheme,
   mode: Int
) : AbstractComposeView(context) {
   companion object {
      const val MODE_FILE = 1
      const val MODE_FOLDER_CLOSED = 2
      const val MODE_FOLDER_OPEN = 3
      const val MODE_CHEVRON_RIGHT = 4
      const val MODE_CHEVRON_DOWN = 5
   }

   private var iconMode by mutableIntStateOf(mode)

   fun setMode(mode: Int) {
      iconMode = mode
   }

   @Composable
   override fun Content() {
      Box(
         modifier = Modifier.fillMaxSize(),
         contentAlignment = Alignment.Center
      ) {
         Icon(
            imageVector = when (iconMode) {
               MODE_FOLDER_CLOSED -> Icons.Rounded.Folder
               MODE_FOLDER_OPEN -> Icons.Rounded.FolderOpen
               MODE_CHEVRON_RIGHT -> Icons.Rounded.KeyboardArrowRight
               MODE_CHEVRON_DOWN -> Icons.Rounded.ExpandMore
               else -> Icons.Rounded.Description
            },
            contentDescription = null,
            tint = Color(
               when (iconMode) {
                  MODE_FOLDER_OPEN -> theme.accent
                  MODE_FOLDER_CLOSED -> theme.codeAccent
                  MODE_FILE -> theme.textMuted
                  else -> theme.textMuted
               }
            ),
            modifier = Modifier.size(
               when (iconMode) {
                  MODE_CHEVRON_RIGHT, MODE_CHEVRON_DOWN -> 18.dp
                  else -> 20.dp
               }
            )
         )
      }
   }
}
