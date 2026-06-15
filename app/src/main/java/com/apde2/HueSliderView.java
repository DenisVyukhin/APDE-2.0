package com.apde2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

final class HueSliderView extends View implements ThemeAware {
   interface Listener {
      void onHueChanged(float hue);
   }

   private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
   private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
   private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
   private final Paint markerStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
   private final RectF rect = new RectF();
   private float hue = 120f;
   private Listener listener;

   HueSliderView(Context context, EditorTheme theme) {
      super(context);
      strokePaint.setStyle(Paint.Style.STROKE);
      markerStrokePaint.setStyle(Paint.Style.STROKE);
      markerStrokePaint.setStrokeWidth(dp(2));
      applyTheme(theme);
   }

   @Override
   public void applyTheme(EditorTheme theme) {
      strokePaint.setColor(theme.colorPickerBorder);
      markerPaint.setColor(theme.hueSliderMarker);
      markerStrokePaint.setColor(theme.hueSliderMarkerStroke);
      invalidate();
   }

   void setListener(Listener listener) {
      this.listener = listener;
   }

   void setHue(float hue) {
      this.hue = normalizeHue(hue);
      invalidate();
   }

   @Override
   protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      rect.set(0, 0, getWidth(), getHeight());
      fillPaint.setShader(new LinearGradient(
         0, 0, 0, getHeight(),
         new int[] {
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED
         },
         null,
         Shader.TileMode.CLAMP
      ));
      float radius = dp(10);
      canvas.drawRoundRect(rect, radius, radius, fillPaint);
      canvas.drawRoundRect(rect, radius, radius, strokePaint);

      float cy = (hue / 360f) * getHeight();
      float left = dp(3);
      float right = getWidth() - dp(3);
      canvas.drawRoundRect(left, cy - dp(2), right, cy + dp(2), dp(2), dp(2), markerPaint);
      canvas.drawRoundRect(left, cy - dp(2), right, cy + dp(2), dp(2), dp(2), markerStrokePaint);
   }

   @Override
   public boolean onTouchEvent(MotionEvent event) {
      if (event.getAction() == MotionEvent.ACTION_DOWN
         || event.getAction() == MotionEvent.ACTION_MOVE
         || event.getAction() == MotionEvent.ACTION_UP) {
         hue = normalizeHue((event.getY() / Math.max(1f, getHeight())) * 360f);
         invalidate();
         if (listener != null) {
            listener.onHueChanged(hue);
         }
         return true;
      }
      return super.onTouchEvent(event);
   }

   private float normalizeHue(float value) {
      return Math.max(0f, Math.min(360f, value));
   }

   private float dp(int value) {
      return value * getResources().getDisplayMetrics().density;
   }
}
