package com.apde2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.util.TypedValue;
import android.view.View;

final class LineNumberView extends View implements ThemeAware {
   private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
   private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
   private EditorTheme theme;
   private CodeEditorView editor;
   private int lineCount = 1;
   private int cachedDigitCount = -1;
   private int cachedDesiredWidth = 0;
   private float dividerStrokeWidth;
   private float horizontalPadding;

   LineNumberView(Context context, EditorTheme theme) {
      super(context);
      this.theme = theme;
      setBackgroundColor(theme.background);
      setWillNotDraw(false);
      paint.setColor(theme.textMuted);
      paint.setTextAlign(Paint.Align.RIGHT);
      paint.setTextSize(sp(15));
      paint.setTypeface(AppFonts.code(context));
      dividerPaint.setColor(theme.border);
      dividerStrokeWidth = dp(1);
      dividerPaint.setStrokeWidth(dividerStrokeWidth);
      horizontalPadding = dp(10);
   }

   @Override
   public void applyTheme(EditorTheme theme) {
      this.theme = theme;
      setBackgroundColor(theme.background);
      paint.setColor(theme.textMuted);
      dividerPaint.setColor(theme.border);
      invalidate();
   }

   void attachEditor(CodeEditorView editor) {
      if (this.editor == editor) return;
      this.editor = editor;
      if (editor != null) {
         editor.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY != oldScrollY) {
               invalidate();
            }
         });
         editor.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot || r - l != or - ol) {
               invalidate();
            }
         });
      }
      invalidate();
   }

   void setLineCount(int lineCount) {
      int safe = Math.max(1, lineCount);
      if (this.lineCount == safe) return;
      this.lineCount = safe;
      cachedDigitCount = -1;
      invalidate();
   }

   void setFontSizeSp(int sizeSp) {
      paint.setTextSize(sp(sizeSp));
      cachedDigitCount = -1;
      invalidate();
   }

   int desiredWidth() {
      int digits = Math.max(2, String.valueOf(Math.max(1, lineCount)).length());
      if (digits == cachedDigitCount && cachedDesiredWidth > 0) {
         return cachedDesiredWidth;
      }
      float textWidth = paint.measureText(repeat('8', digits));
      cachedDigitCount = digits;
      cachedDesiredWidth = Math.round(textWidth + dp(18));
      return cachedDesiredWidth;
   }

   @Override
   protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      if (editor == null) {
         drawFallback(canvas);
         return;
      }
      Layout layout = editor.getLayout();
      if (layout == null) {
         drawFallback(canvas);
         return;
      }

      int scrollY = editor.getScrollY();
      int viewportTop = scrollY;
      int viewportBottom = scrollY + editor.getHeight();
      int firstLine = layout.getLineForVertical(viewportTop);
      int lastLine = layout.getLineForVertical(viewportBottom);
      int totalLayoutLines = layout.getLineCount();
      int paddingTop = editor.getTotalPaddingTop();
      int width = getWidth();
      float right = width - horizontalPadding;
      int maxLine = Math.min(lastLine, totalLayoutLines - 1);

      for (int i = firstLine; i <= maxLine && i < lineCount; i++) {
         float baseline = layout.getLineBaseline(i) + paddingTop - scrollY;
         canvas.drawText(String.valueOf(i + 1), right, baseline, paint);
      }
      drawDivider(canvas, width);
   }

   private void drawFallback(Canvas canvas) {
      Paint.FontMetrics fm = paint.getFontMetrics();
      float lineHeight = fm.descent - fm.ascent;
      float baseline = -fm.ascent + dp(2);
      int width = getWidth();
      float right = width - horizontalPadding;
      int max = Math.min(lineCount, 200);
      for (int i = 1; i <= max; i++) {
         canvas.drawText(String.valueOf(i), right, baseline, paint);
         baseline += lineHeight;
      }
      drawDivider(canvas, width);
   }

   private void drawDivider(Canvas canvas, int width) {
      float x = width - dividerStrokeWidth * 0.5f;
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
