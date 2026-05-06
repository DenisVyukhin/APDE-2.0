package com.apde2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.View;

final class SketchRunnerView extends View {
   private SketchProgram program;
   private long frameCount = 0;
   private boolean running = false;

   SketchRunnerView(Context context) {
      super(context);
      setBackgroundColor(Color.BLACK);
   }

   void run(SketchProgram program) {
      this.program = program;
      frameCount = 0;
      running = true;
      invalidate();
   }

   void stop() {
      running = false;
      invalidate();
   }

   @Override
   protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      if (program == null) {
         canvas.drawColor(Color.rgb(18, 20, 25));
         return;
      }
      float scale = Math.min(getWidth() / (float) program.width, getHeight() / (float) program.height);
      float offsetX = (getWidth() - program.width * scale) / 2f;
      float offsetY = (getHeight() - program.height * scale) / 2f;

      canvas.save();
      canvas.translate(offsetX, offsetY);
      canvas.scale(scale, scale);
      program.render(canvas, frameCount, program.width, program.height);
      canvas.restore();

      if (running) {
         frameCount++;
         postInvalidateDelayed(16);
      }
   }
}
