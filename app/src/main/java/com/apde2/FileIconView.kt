package com.apde2

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class FileIconView(
   context: Context,
   theme: EditorTheme,
   mode: Int
) : AbstractComposeView(context), ThemeAware {
   companion object {
      const val MODE_FILE = 1
      const val MODE_FOLDER_CLOSED = 2
      const val MODE_FOLDER_OPEN = 3
      const val MODE_CHEVRON_RIGHT = 4
      const val MODE_CHEVRON_DOWN = 5
      const val MODE_SKETCH_PROJECT = 6
      const val MODE_FOLDER_CODE = 7
   }

   private var iconMode by mutableIntStateOf(mode)
   private var currentTheme by mutableStateOf(theme)

   fun setMode(mode: Int) {
      iconMode = mode
   }

   override fun applyTheme(theme: EditorTheme) {
      currentTheme = theme
   }

   @Composable
   override fun Content() {
      Box(
         modifier = Modifier.fillMaxSize(),
         contentAlignment = Alignment.Center
      ) {
         if (iconMode == MODE_FOLDER_CODE) {
            FolderCodeIcon()
         } else {
            Icon(
               imageVector = when (iconMode) {
                  MODE_FOLDER_CLOSED -> Icons.Rounded.Folder
                  MODE_FOLDER_OPEN -> Icons.Rounded.FolderOpen
                  MODE_CHEVRON_RIGHT -> Icons.Rounded.KeyboardArrowRight
                  MODE_CHEVRON_DOWN -> Icons.Rounded.ExpandMore
                  MODE_SKETCH_PROJECT -> Icons.Rounded.Code
                  else -> Icons.Rounded.Description
               },
               contentDescription = null,
               tint = Color(
                  when (iconMode) {
                     MODE_FOLDER_OPEN -> currentTheme.accent
                     MODE_FOLDER_CLOSED -> currentTheme.codeAccent
                     MODE_SKETCH_PROJECT -> currentTheme.accent
                     MODE_FILE -> currentTheme.textMuted
                     else -> currentTheme.textMuted
                  }
               ),
               modifier = Modifier.size(iconSize(iconMode))
            )
         }
      }
   }

   private fun iconSize(mode: Int): Dp = when (mode) {
      MODE_CHEVRON_RIGHT, MODE_CHEVRON_DOWN -> 18.dp
      else -> 20.dp
   }

   @Composable
   private fun FolderCodeIcon() {
      Box(
         modifier = Modifier.size(22.dp),
         contentAlignment = Alignment.Center
      ) {
         Icon(
            imageVector = Icons.Rounded.Folder,
            contentDescription = null,
            tint = Color(currentTheme.accent),
            modifier = Modifier.fillMaxSize()
         )
         Icon(
            imageVector = Icons.Rounded.Code,
            contentDescription = null,
            tint = Color(currentTheme.background),
            modifier = Modifier
               .align(Alignment.Center)
               .size(12.dp)
         )
      }
   }
}
