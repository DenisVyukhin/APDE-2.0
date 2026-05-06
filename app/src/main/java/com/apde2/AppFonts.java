package com.apde2;

import android.content.Context;
import android.graphics.Typeface;

final class AppFonts {
   private AppFonts() {
   }

   static Typeface code(Context context) {
      try {
         return context.getResources().getFont(R.font.jetbrains_mono);
      } catch (RuntimeException ignored) {
         return Typeface.MONOSPACE;
      }
   }
}
