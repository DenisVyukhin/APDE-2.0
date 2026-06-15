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

final class ColorFieldView extends View implements ThemeAware {
   interface Listener {
      void onValueChanged(float saturation, float value);
   }

   private final Paint saturationPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
   private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
   private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
   private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
   private final Paint thumbStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
   private final RectF rect = new RectF();

   private float hue = 120f;
   private float saturation = 0.66f;
   private float value = 0.73f;
   private Listener listener;

   ColorFieldView(Context context, EditorTheme theme) {
      super(context);
      strokePaint.setStyle(Paint.Style.STROKE);
      thumbPaint.setStyle(Paint.Style.STROKE);
      thumbPaint.setStrokeWidth(dp(2));
      thumbStrokePaint.setStyle(Paint.Style.STROKE);
      thumbStrokePaint.setStrokeWidth(dp(4));
      applyTheme(theme);
   }

   @Override
   public void applyTheme(EditorTheme theme) {
      strokePaint.setColor(theme.colorPickerBorder);
      thumbPaint.setColor(theme.colorPickerThumb);
      thumbStrokePaint.setColor(theme.colorPickerThumbStroke);
      invalidate();
   }

   void setListener(Listener listener) {
      this.listener = listener;
   }

   void setHue(float hue) {
      this.hue = hue;
      invalidate();
   }

   void setSelection(float saturation, float value) {
      this.saturation = clamp01(saturation);
      this.value = clamp01(value);
      invalidate();
   }

   @Override
   protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      rect.set(0, 0, getWidth(), getHeight());

      int hueColor = Color.HSVToColor(new float[] {hue, 1f, 1f});
      Shader saturationShader = new LinearGradient(0, 0, getWidth(), 0, Color.WHITE, hueColor, Shader.TileMode.CLAMP);
      Shader valueShader = new LinearGradient(0, 0, 0, getHeight(), Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP);
      saturationPaint.setShader(saturationShader);
      valuePaint.setShader(valueShader);

      float radius = dp(10);
      canvas.drawRoundRect(rect, radius, radius, saturationPaint);
      canvas.drawRoundRect(rect, radius, radius, valuePaint);
      canvas.drawRoundRect(rect, radius, radius, strokePaint);

      float cx = saturation * getWidth();
      float cy = (1f - value) * getHeight();
      canvas.drawCircle(cx, cy, dp(10), thumbStrokePaint);
      canvas.drawCircle(cx, cy, dp(10), thumbPaint);
   }

   @Override
   public boolean onTouchEvent(MotionEvent event) {
      if (event.getAction() == MotionEvent.ACTION_DOWN
         || event.getAction() == MotionEvent.ACTION_MOVE
         || event.getAction() == MotionEvent.ACTION_UP) {
         updateSelection(event.getX(), event.getY(), true);
         return true;
      }
      return super.onTouchEvent(event);
   }

   private void updateSelection(float x, float y, boolean notify) {
      saturation = clamp01(x / Math.max(1f, getWidth()));
      value = 1f - clamp01(y / Math.max(1f, getHeight()));
      invalidate();
      if (notify && listener != null) {
         listener.onValueChanged(saturation, value);
      }
   }

   private float clamp01(float value) {
      return Math.max(0f, Math.min(1f, value));
   }

   private float dp(int value) {
      return value * getResources().getDisplayMetrics().density;
   }
}
