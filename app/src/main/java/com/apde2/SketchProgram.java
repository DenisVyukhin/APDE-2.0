package com.apde2;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

final class SketchProgram {
   int width = 640;
   int height = 480;
   final List<SketchCommand> setupCommands = new ArrayList<>();
   final List<SketchCommand> drawCommands = new ArrayList<>();

   void render(Canvas canvas, long frameCount, int viewWidth, int viewHeight) {
      Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
      RuntimeState state = new RuntimeState(frameCount, viewWidth, viewHeight);

      for (SketchCommand command : setupCommands) {
         command.apply(canvas, paint, state);
      }
      for (SketchCommand command : drawCommands) {
         command.apply(canvas, paint, state);
      }
   }

   interface SketchCommand {
      void apply(Canvas canvas, Paint paint, RuntimeState state);
   }

   static final class RuntimeState {
      final long frameCount;
      final int width;
      final int height;
      int fillColor = Color.WHITE;
      int strokeColor = Color.BLACK;
      boolean fillEnabled = true;
      boolean strokeEnabled = true;

      RuntimeState(long frameCount, int width, int height) {
         this.frameCount = frameCount;
         this.width = width;
         this.height = height;
      }
   }

   static final class BackgroundCommand implements SketchCommand {
      private final Value r;
      private final Value g;
      private final Value b;

      BackgroundCommand(Value r, Value g, Value b) {
         this.r = r;
         this.g = g;
         this.b = b;
      }

      @Override
      public void apply(Canvas canvas, Paint paint, RuntimeState state) {
         canvas.drawColor(Color.rgb(r.asColor(state), g.asColor(state), b.asColor(state)));
      }
   }

   static final class FillCommand implements SketchCommand {
      private final Value r;
      private final Value g;
      private final Value b;

      FillCommand(Value r, Value g, Value b) {
         this.r = r;
         this.g = g;
         this.b = b;
      }

      @Override
      public void apply(Canvas canvas, Paint paint, RuntimeState state) {
         state.fillEnabled = true;
         state.fillColor = Color.rgb(r.asColor(state), g.asColor(state), b.asColor(state));
      }
   }

   static final class StrokeCommand implements SketchCommand {
      private final Value r;
      private final Value g;
      private final Value b;

      StrokeCommand(Value r, Value g, Value b) {
         this.r = r;
         this.g = g;
         this.b = b;
      }

      @Override
      public void apply(Canvas canvas, Paint paint, RuntimeState state) {
         state.strokeEnabled = true;
         state.strokeColor = Color.rgb(r.asColor(state), g.asColor(state), b.asColor(state));
      }
   }

   static final class NoStrokeCommand implements SketchCommand {
      @Override
      public void apply(Canvas canvas, Paint paint, RuntimeState state) {
         state.strokeEnabled = false;
      }
   }

   static final class NoFillCommand implements SketchCommand {
      @Override
      public void apply(Canvas canvas, Paint paint, RuntimeState state) {
         state.fillEnabled = false;
      }
   }

   static final class EllipseCommand implements SketchCommand {
      private final Value x;
      private final Value y;
      private final Value w;
      private final Value h;

      EllipseCommand(Value x, Value y, Value w, Value h) {
         this.x = x;
         this.y = y;
         this.w = w;
         this.h = h;
      }

      @Override
      public void apply(Canvas canvas, Paint paint, RuntimeState state) {
         float centerX = x.resolve(state);
         float centerY = y.resolve(state);
         float width = w.resolve(state);
         float height = h.resolve(state);
         RectF bounds = new RectF(centerX - width / 2f, centerY - height / 2f, centerX + width / 2f, centerY + height / 2f);
         drawShape(canvas, paint, state, () -> canvas.drawOval(bounds, paint));
      }
   }

   static final class RectCommand implements SketchCommand {
      private final Value x;
      private final Value y;
      private final Value w;
      private final Value h;

      RectCommand(Value x, Value y, Value w, Value h) {
         this.x = x;
         this.y = y;
         this.w = w;
         this.h = h;
      }

      @Override
      public void apply(Canvas canvas, Paint paint, RuntimeState state) {
         RectF bounds = new RectF(x.resolve(state), y.resolve(state), x.resolve(state) + w.resolve(state), y.resolve(state) + h.resolve(state));
         drawShape(canvas, paint, state, () -> canvas.drawRect(bounds, paint));
      }
   }

   static final class LineCommand implements SketchCommand {
      private final Value x1;
      private final Value y1;
      private final Value x2;
      private final Value y2;

      LineCommand(Value x1, Value y1, Value x2, Value y2) {
         this.x1 = x1;
         this.y1 = y1;
         this.x2 = x2;
         this.y2 = y2;
      }

      @Override
      public void apply(Canvas canvas, Paint paint, RuntimeState state) {
         if (!state.strokeEnabled) {
            return;
         }
         paint.setStyle(Paint.Style.STROKE);
         paint.setStrokeWidth(3f);
         paint.setColor(state.strokeColor);
         canvas.drawLine(x1.resolve(state), y1.resolve(state), x2.resolve(state), y2.resolve(state), paint);
      }
   }

   static final class TextCommand implements SketchCommand {
      private final String text;
      private final Value x;
      private final Value y;

      TextCommand(String text, Value x, Value y) {
         this.text = text;
         this.x = x;
         this.y = y;
      }

      @Override
      public void apply(Canvas canvas, Paint paint, RuntimeState state) {
         paint.setStyle(Paint.Style.FILL);
         paint.setTextSize(34f);
         paint.setColor(state.fillEnabled ? state.fillColor : state.strokeColor);
         canvas.drawText(text, x.resolve(state), y.resolve(state), paint);
      }
   }

   interface ShapeDraw {
      void draw();
   }

   private static void drawShape(Canvas canvas, Paint paint, RuntimeState state, ShapeDraw draw) {
      if (state.fillEnabled) {
         paint.setStyle(Paint.Style.FILL);
         paint.setColor(state.fillColor);
         draw.draw();
      }
      if (state.strokeEnabled) {
         paint.setStyle(Paint.Style.STROKE);
         paint.setStrokeWidth(3f);
         paint.setColor(state.strokeColor);
         draw.draw();
      }
   }
}
