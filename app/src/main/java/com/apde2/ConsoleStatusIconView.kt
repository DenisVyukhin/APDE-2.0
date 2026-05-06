package com.apde2

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

class ConsoleStatusIconView(
   context: Context,
   private val theme: EditorTheme,
   private val mode: Int
) : AbstractComposeView(context) {
   companion object {
      const val MODE_SUCCESS = 1
      const val MODE_ERROR = 2
      const val MODE_INFO = 3
   }

   init {
      minimumWidth = dpToPx(24)
      minimumHeight = dpToPx(24)
   }

   @Composable
   override fun Content() {
      val tint = when (mode) {
         MODE_SUCCESS -> Color(theme.accent)
         MODE_ERROR -> Color(theme.error)
         else -> Color(theme.codeAccent)
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
                  else -> R.drawable.info_i_24
               }
            ),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
         )
      }
   }

   private fun dpToPx(value: Int): Int {
      return (value * resources.displayMetrics.density).toInt()
   }
}
