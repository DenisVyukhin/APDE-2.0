package com.apde2

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class ConsoleStatusIconView(
   context: Context,
   theme: EditorTheme,
   private val mode: Int
) : AbstractComposeView(context), ThemeAware {
   companion object {
      const val MODE_SUCCESS = 1
      const val MODE_ERROR = 2
      const val MODE_INFO = 3
      const val MODE_TERMINAL = 4
   }

   private var currentTheme by mutableStateOf(theme)

   init {
      minimumWidth = dpToPx(24)
      minimumHeight = dpToPx(24)
   }

   override fun applyTheme(theme: EditorTheme) {
      currentTheme = theme
   }

   @Composable
   override fun Content() {
      val tint = when (mode) {
         MODE_SUCCESS -> Color(currentTheme.accent)
         MODE_ERROR -> Color(currentTheme.error)
         MODE_TERMINAL -> Color(currentTheme.play)
         else -> Color(currentTheme.codeAccent)
      }

      Box(
         modifier = Modifier
            .size(24.dp)
            .border(BorderStroke(1.dp, tint), RoundedCornerShape(9.dp)),
         contentAlignment = Alignment.Center
      ) {
         Icon(
            painter = painterResource(
               id = when (mode) {
                  MODE_SUCCESS -> R.drawable.check_24
                  MODE_ERROR -> R.drawable.priority_high_24
                  MODE_TERMINAL -> R.drawable.terminal_2_24
                  else -> R.drawable.info_i_24
               }
            ),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize())
         )
      }
   }

   @Composable
   private fun iconSize(): Dp {
      return if (mode == MODE_TERMINAL) 15.dp else 14.dp
   }

   private fun dpToPx(value: Int): Int {
      return (value * resources.displayMetrics.density).toInt()
   }
}
