package com.apde2;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;

final class UiStyles {
   private static final int KIND_ROUNDED = 1;
   private static final int KIND_ROUNDED_STROKE = 2;
   private static final int KIND_TOP_ROUNDED = 3;
   private static final int KIND_TOP_ROUNDED_STROKE = 4;
   private static final int KIND_TOP_ROUNDED_SIDE_STROKE = 5;

   private UiStyles() {
   }

   static void rounded(View view, int color, float radius) {
      setThemedBackground(view, new ThemedBackground(KIND_ROUNDED, color, 0, 0, radius));
   }

   static void roundedStroke(View view, int color, int strokeColor, int strokeWidth, float radius) {
      setThemedBackground(view, new ThemedBackground(KIND_ROUNDED_STROKE, color, strokeColor, strokeWidth, radius));
   }

   static void topRounded(View view, int color, float radius) {
      setThemedBackground(view, new ThemedBackground(KIND_TOP_ROUNDED, color, 0, 0, radius));
   }

   static void topRoundedStroke(View view, int color, int strokeColor, int strokeWidth, float radius) {
      setThemedBackground(view, new ThemedBackground(KIND_TOP_ROUNDED_STROKE, color, strokeColor, strokeWidth, radius));
   }

   static void topRoundedSideStroke(View view, int color, int strokeColor, int strokeWidth, float radius) {
      setThemedBackground(view, new ThemedBackground(KIND_TOP_ROUNDED_SIDE_STROKE, color, strokeColor, strokeWidth, radius));
   }

   static boolean remapThemeBackground(View view, EditorTheme previousTheme, EditorTheme nextTheme) {
      if (!(view.getTag() instanceof ThemedBackground)) {
         return false;
      }
      ThemedBackground background = (ThemedBackground) view.getTag();
      background.color = remapThemeColor(background.color, previousTheme, nextTheme);
      background.strokeColor = remapThemeColor(background.strokeColor, previousTheme, nextTheme);
      applyThemedBackground(view, background);
      return true;
   }

   private static void setThemedBackground(View view, ThemedBackground background) {
      view.setTag(background);
      applyThemedBackground(view, background);
   }

   private static void applyThemedBackground(View view, ThemedBackground background) {
      if (background.kind == KIND_ROUNDED) {
         applyRounded(view, background.color, background.radius);
      } else if (background.kind == KIND_ROUNDED_STROKE) {
         applyRoundedStroke(view, background.color, background.strokeColor, background.strokeWidth, background.radius);
      } else if (background.kind == KIND_TOP_ROUNDED) {
         applyTopRounded(view, background.color, background.radius);
      } else if (background.kind == KIND_TOP_ROUNDED_STROKE) {
         applyTopRoundedStroke(view, background.color, background.strokeColor, background.strokeWidth, background.radius);
      } else if (background.kind == KIND_TOP_ROUNDED_SIDE_STROKE) {
         view.setBackground(new TopRoundedSideStrokeDrawable(background.color, background.strokeColor, background.strokeWidth, background.radius));
      }
   }

   private static int remapThemeColor(int color, EditorTheme previousTheme, EditorTheme nextTheme) {
      if (color == previousTheme.background) return nextTheme.background;
      if (color == previousTheme.surface) return nextTheme.surface;
      if (color == previousTheme.surfaceSoft) return nextTheme.surfaceSoft;
      if (color == previousTheme.border) return nextTheme.border;
      if (sameRgb(color, previousTheme.border)) return withAlpha(nextTheme.border, Color.alpha(color));
      if (color == previousTheme.scrim) return nextTheme.scrim;
      if (color == previousTheme.text) return nextTheme.text;
      if (color == previousTheme.textMuted) return nextTheme.textMuted;
      if (color == previousTheme.accent) return nextTheme.accent;
      if (color == previousTheme.codeAccent) return nextTheme.codeAccent;
      if (color == previousTheme.error) return nextTheme.error;
      if (color == previousTheme.play) return nextTheme.play;
      if (color == previousTheme.stop) return nextTheme.stop;
      return color;
   }

   private static boolean sameRgb(int left, int right) {
      return Color.red(left) == Color.red(right)
         && Color.green(left) == Color.green(right)
         && Color.blue(left) == Color.blue(right);
   }

   private static int withAlpha(int color, int alpha) {
      return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
   }

   private static void applyRounded(View view, int color, float radius) {
      GradientDrawable drawable = new GradientDrawable();
      drawable.setColor(color);
      drawable.setCornerRadius(radius);
      view.setBackground(drawable);
   }

   private static void applyRoundedStroke(View view, int color, int strokeColor, int strokeWidth, float radius) {
      GradientDrawable drawable = new GradientDrawable();
      drawable.setColor(color);
      drawable.setStroke(strokeWidth, strokeColor);
      drawable.setCornerRadius(radius);
      view.setBackground(drawable);
   }

   private static void applyTopRounded(View view, int color, float radius) {
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

   private static void applyTopRoundedStroke(View view, int color, int strokeColor, int strokeWidth, float radius) {
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

   private static final class ThemedBackground {
      private final int kind;
      private int color;
      private int strokeColor;
      private final int strokeWidth;
      private final float radius;

      private ThemedBackground(int kind, int color, int strokeColor, int strokeWidth, float radius) {
         this.kind = kind;
         this.color = color;
         this.strokeColor = strokeColor;
         this.strokeWidth = strokeWidth;
         this.radius = radius;
      }
   }

   private static final class TopRoundedSideStrokeDrawable extends Drawable {
      private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      private final Path fillPath = new Path();
      private final Path strokePath = new Path();
      private final float radius;
      private final float strokeWidth;

      TopRoundedSideStrokeDrawable(int color, int strokeColor, int strokeWidth, float radius) {
         this.radius = radius;
         this.strokeWidth = strokeWidth;
         fillPaint.setStyle(Paint.Style.FILL);
         fillPaint.setColor(color);
         strokePaint.setStyle(Paint.Style.STROKE);
         strokePaint.setStrokeWidth(strokeWidth);
         strokePaint.setColor(strokeColor);
      }

      @Override
      public void draw(Canvas canvas) {
         float width = getBounds().width();
         float height = getBounds().height();
         if (width <= 0f || height <= 0f) {
            return;
         }

         float r = Math.min(radius, width * 0.5f);
         fillPath.reset();
         fillPath.moveTo(0f, height);
         fillPath.lineTo(0f, r);
         fillPath.quadTo(0f, 0f, r, 0f);
         fillPath.lineTo(width - r, 0f);
         fillPath.quadTo(width, 0f, width, r);
         fillPath.lineTo(width, height);
         fillPath.close();
         canvas.drawPath(fillPath, fillPaint);

         float halfStroke = strokeWidth * 0.5f;
         float right = width - halfStroke;
         float top = halfStroke;
         float left = halfStroke;
         float strokeRadius = Math.max(0f, r - halfStroke);
         strokePath.reset();
         strokePath.moveTo(left, height);
         strokePath.lineTo(left, top + strokeRadius);
         strokePath.quadTo(left, top, left + strokeRadius, top);
         strokePath.lineTo(right - strokeRadius, top);
         strokePath.quadTo(right, top, right, top + strokeRadius);
         strokePath.lineTo(right, height);
         canvas.drawPath(strokePath, strokePaint);
      }

      @Override
      public void setAlpha(int alpha) {
         fillPaint.setAlpha(alpha);
         strokePaint.setAlpha(alpha);
         invalidateSelf();
      }

      @Override
      public void setColorFilter(ColorFilter colorFilter) {
         fillPaint.setColorFilter(colorFilter);
         strokePaint.setColorFilter(colorFilter);
         invalidateSelf();
      }

      @Override
      public int getOpacity() {
         return PixelFormat.TRANSLUCENT;
      }
   }
}
