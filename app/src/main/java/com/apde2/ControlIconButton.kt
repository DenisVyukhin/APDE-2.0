package com.apde2

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.unit.dp

class ControlIconButton(
   context: Context,
   private val theme: EditorTheme,
   mode: Int
) : AbstractComposeView(context) {
   companion object {
      const val MODE_PLAY = 1
      const val MODE_STOP = 2
      const val MODE_MENU = 3
      const val MODE_MORE = 4
      const val MODE_ADD = 5
      const val MODE_CLEAR = 6
      const val MODE_UNDO = 7
      const val MODE_REDO = 8
      const val MODE_ADD_FOLDER = 9
      const val MODE_ADD_FILE = 10
      const val MODE_BACK = 11
   }

   private var iconMode by mutableIntStateOf(mode)

   init {
      minimumWidth = dpToPx(48)
      minimumHeight = dpToPx(48)
   }

   fun setMode(mode: Int) {
      iconMode = mode
   }

   @Composable
   override fun Content() {
      Box(
         modifier = Modifier.fillMaxSize(),
         contentAlignment = Alignment.Center
      ) {
         Box(
            modifier = Modifier
               .size(40.dp)
               .border(
                  BorderStroke(1.dp, Color(theme.border)),
                  RoundedCornerShape(10.dp)
               ),
            contentAlignment = Alignment.Center
         ) {
            Icon(
               imageVector = when (iconMode) {
                  MODE_PLAY -> Icons.Rounded.PlayArrow
                  MODE_STOP -> Icons.Rounded.Stop
                  MODE_MENU -> Icons.Rounded.Menu
                  MODE_MORE -> Icons.Rounded.MoreHoriz
                  MODE_ADD -> Icons.Rounded.Add
                  MODE_CLEAR -> Icons.Rounded.Close
                  MODE_UNDO -> Icons.AutoMirrored.Rounded.Undo
                  MODE_REDO -> Icons.AutoMirrored.Rounded.Redo
                  MODE_ADD_FOLDER -> Icons.Rounded.CreateNewFolder
                  MODE_ADD_FILE -> Icons.Rounded.NoteAdd
                  MODE_BACK -> Icons.AutoMirrored.Rounded.ArrowBack
                  else -> Icons.Rounded.MoreHoriz
               },
               contentDescription = null,
               tint = when (iconMode) {
                  MODE_PLAY -> Color(theme.play)
                  MODE_STOP -> Color(theme.stop)
                  MODE_ADD -> Color(theme.accent)
                  MODE_ADD_FOLDER -> Color(theme.accent)
                  MODE_ADD_FILE -> Color(theme.accent)
                  else -> Color(theme.textMuted)
               },
               modifier = Modifier.size(iconSize())
            )
         }
      }
   }

   @Composable
   private fun iconSize() = when (iconMode) {
      MODE_PLAY -> 20.dp
      MODE_STOP -> 18.dp
      MODE_MORE -> 22.dp
      else -> 21.dp
   }

   private fun dpToPx(value: Int): Int {
      return (value * resources.displayMetrics.density).toInt()
   }
}
