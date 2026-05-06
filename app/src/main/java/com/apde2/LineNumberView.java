package com.apde2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.util.TypedValue;
import android.view.View;

final class LineNumberView extends View {
   private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
   private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
   private final EditorTheme theme;
   private CodeEditorView editor;
   private int lineCount = 1;

   LineNumberView(Context context, EditorTheme theme) {
      super(context);
      this.theme = theme;
      setBackgroundColor(theme.background);
      paint.setColor(theme.textMuted);
      paint.setTextAlign(Paint.Align.RIGHT);
      paint.setTextSize(sp(15));
      paint.setTypeface(AppFonts.code(context));
      dividerPaint.setColor(theme.border);
      dividerPaint.setStrokeWidth(dp(1));
   }

   void attachEditor(CodeEditorView editor) {
      this.editor = editor;
      invalidate();
   }

   void setLineCount(int lineCount) {
      this.lineCount = Math.max(1, lineCount);
      invalidate();
   }

   void setFontSizeSp(int sizeSp) {
      paint.setTextSize(sp(sizeSp));
      invalidate();
   }

   int desiredWidth() {
      int digits = Math.max(2, String.valueOf(Math.max(1, lineCount)).length());
      float textWidth = paint.measureText(repeat('8', digits));
      return Math.round(textWidth + dp(18));
   }

   @Override
   protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      if (editor == null || editor.getLayout() == null) {
         drawFallback(canvas);
         return;
      }

      Layout layout = editor.getLayout();
      int scrollY = editor.getScrollY();
      int firstLine = layout.getLineForVertical(scrollY);
      int lastLine = layout.getLineForVertical(scrollY + editor.getHeight());
      float right = getWidth() - dp(10);

      for (int i = firstLine; i <= lastLine && i < lineCount; i++) {
         float baseline = layout.getLineBaseline(i) + editor.getTotalPaddingTop() - scrollY;
         canvas.drawText(String.valueOf(i + 1), right, baseline, paint);
      }
      drawDivider(canvas);
   }

   private void drawFallback(Canvas canvas) {
      float baseline = dp(16);
      float lineHeight = paint.getFontSpacing();
      float right = getWidth() - dp(10);
      for (int i = 1; i <= lineCount; i++) {
         canvas.drawText(String.valueOf(i), right, baseline, paint);
         baseline += lineHeight;
      }
      drawDivider(canvas);
   }

   private void drawDivider(Canvas canvas) {
      float x = getWidth() - 1f;
      canvas.drawLine(x, 0f, x, getHeight(), dividerPaint);
   }

   private int dp(int value) {
      return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
   }

   private float sp(int value) {
      return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
   }

   private String repeat(char value, int count) {
      StringBuilder builder = new StringBuilder(count);
      for (int i = 0; i < count; i++) {
         builder.append(value);
      }
      return builder.toString();
   }
}
