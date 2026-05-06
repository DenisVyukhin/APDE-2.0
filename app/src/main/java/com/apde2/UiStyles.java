package com.apde2;

import android.graphics.drawable.GradientDrawable;
import android.view.View;

final class UiStyles {
   private UiStyles() {
   }

   static void rounded(View view, int color, float radius) {
      GradientDrawable drawable = new GradientDrawable();
      drawable.setColor(color);
      drawable.setCornerRadius(radius);
      view.setBackground(drawable);
   }

   static void roundedStroke(View view, int color, int strokeColor, int strokeWidth, float radius) {
      GradientDrawable drawable = new GradientDrawable();
      drawable.setColor(color);
      drawable.setStroke(strokeWidth, strokeColor);
      drawable.setCornerRadius(radius);
      view.setBackground(drawable);
   }

   static void topRounded(View view, int color, float radius) {
      GradientDrawable drawable = new GradientDrawable();
      drawable.setColor(color);
      drawable.setCornerRadii(new float[] {
         radius, radius,
         radius, radius,
         0f, 0f,
         0f, 0f
      });
      view.setBackground(drawable);
   }

   static void topRoundedStroke(View view, int color, int strokeColor, int strokeWidth, float radius) {
      GradientDrawable drawable = new GradientDrawable();
      drawable.setColor(color);
      drawable.setStroke(strokeWidth, strokeColor);
      drawable.setCornerRadii(new float[] {
         radius, radius,
         radius, radius,
         0f, 0f,
         0f, 0f
      });
      view.setBackground(drawable);
   }
}
