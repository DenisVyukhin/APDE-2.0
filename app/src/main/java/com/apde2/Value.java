package com.apde2;

import java.util.Locale;

final class Value {
   private final String expression;

   private Value(String expression) {
      this.expression = expression.trim();
   }

   static Value parse(String expression) {
      return new Value(expression);
   }

   float resolve(SketchProgram.RuntimeState state) {
      String normalized = expression.toLowerCase(Locale.US);
      if ("width".equals(normalized)) {
         return state.width;
      }
      if ("height".equals(normalized)) {
         return state.height;
      }
      if ("framecount".equals(normalized)) {
         return state.frameCount;
      }
      if (normalized.contains("sin(")) {
         return resolveSinExpression(normalized, state);
      }
      if (normalized.contains("cos(")) {
         return resolveCosExpression(normalized, state);
      }
      try {
         return Float.parseFloat(normalized);
      } catch (NumberFormatException ignored) {
         return 0f;
      }
   }

   int asColor(SketchProgram.RuntimeState state) {
      int value = Math.round(resolve(state));
      return Math.max(0, Math.min(255, value));
   }

   private float resolveSinExpression(String normalized, SketchProgram.RuntimeState state) {
      String inner = between(normalized, "sin(", ")");
      float base = inner.contains("framecount") ? state.frameCount : parseFallback(inner);
      return 128f + (float) Math.sin(base * 0.05f) * 127f;
   }

   private float resolveCosExpression(String normalized, SketchProgram.RuntimeState state) {
      String inner = between(normalized, "cos(", ")");
      float base = inner.contains("framecount") ? state.frameCount : parseFallback(inner);
      return 128f + (float) Math.cos(base * 0.05f) * 127f;
   }

   private String between(String source, String start, String end) {
      int startIndex = source.indexOf(start);
      if (startIndex < 0) {
         return source;
      }
      int contentStart = startIndex + start.length();
      int endIndex = source.indexOf(end, contentStart);
      if (endIndex < 0) {
         return source.substring(contentStart);
      }
      return source.substring(contentStart, endIndex);
   }

   private float parseFallback(String value) {
      try {
         return Float.parseFloat(value.trim());
      } catch (NumberFormatException ignored) {
         return 0f;
      }
   }
}
