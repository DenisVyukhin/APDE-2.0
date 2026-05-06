package com.apde2;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class EditorTheme {
   final int background;
   final int surface;
   final int surfaceSoft;
   final int border;
   final int text;
   final int textMuted;
   final int accent;
   final int codeAccent;
   final int play;
   final int stop;
   final int error;
   final int selection;
   final int currentLine;
   final int keyword;
   final int type;
   final int processing;
   final int runtimeValue;
   final int constant;
   final int number;
   final int string;
   final int comment;
   final int operator;
   final List<SyntaxRule> syntaxRules;

   private EditorTheme(Builder builder) {
      background = builder.background;
      surface = builder.surface;
      surfaceSoft = builder.surfaceSoft;
      border = builder.border;
      text = builder.text;
      textMuted = builder.textMuted;
      accent = builder.accent;
      codeAccent = builder.codeAccent;
      play = builder.play;
      stop = builder.stop;
      error = builder.error;
      selection = builder.selection;
      currentLine = builder.currentLine;
      keyword = builder.keyword;
      type = builder.type;
      processing = builder.processing;
      runtimeValue = builder.runtimeValue;
      constant = builder.constant;
      number = builder.number;
      string = builder.string;
      comment = builder.comment;
      operator = builder.operator;
      syntaxRules = Collections.unmodifiableList(buildSyntaxRules());
   }

   static EditorTheme dark() {
      return new Builder()
         .background(Color.rgb(13, 17, 23))
         .surface(Color.rgb(22, 27, 34))
         .surfaceSoft(Color.rgb(13, 17, 23))
         .border(Color.rgb(48, 54, 61))
         .text(Color.rgb(201, 209, 217))
         .textMuted(Color.rgb(139, 148, 158))
         .accent(Color.rgb(63, 185, 80))
         .codeAccent(Color.rgb(88, 166, 255))
         .play(Color.rgb(63, 185, 80))
         .stop(Color.rgb(248, 81, 73))
         .error(Color.rgb(248, 81, 73))
         .selection(Color.rgb(56, 139, 253))
         .currentLine(Color.rgb(22, 27, 34))
         .keyword(Color.rgb(210, 168, 255))
         .type(Color.rgb(210, 168, 255))
         .processing(Color.rgb(88, 166, 255))
         .runtimeValue(Color.rgb(121, 192, 255))
         .constant(Color.rgb(255, 123, 114))
         .number(Color.rgb(255, 166, 87))
         .string(Color.rgb(126, 231, 135))
         .comment(Color.rgb(139, 148, 158))
         .operator(Color.rgb(121, 192, 255))
         .build();
   }

   private List<SyntaxRule> buildSyntaxRules() {
      List<SyntaxRule> rules = new ArrayList<>();
      rules.add(new SyntaxRule(wordPattern(
         "void", "byte", "char", "int", "long", "float", "double", "boolean",
         "String", "color", "if", "else", "for", "while", "do", "switch", "case", "default",
         "break", "continue", "return", "class", "interface", "extends", "implements",
         "new", "public", "private", "protected", "static", "final", "abstract", "synchronized",
         "this", "super", "try", "catch", "finally", "throw", "throws", "import", "package",
         "true", "false", "null"
      ), keyword));
      rules.add(new SyntaxRule(wordPattern(
         "PVector", "PImage", "PFont", "PGraphics", "PShape", "PShader", "PApplet",
         "ArrayList", "HashMap", "HashSet", "List", "Map", "Set", "Iterator", "Collections",
         "Math", "Object"
      ), type));
      rules.add(new SyntaxRule(wordPattern(
         "setup", "draw", "settings", "size", "fullScreen", "smooth", "noSmooth", "pixelDensity",
         "frameRate", "loop", "noLoop", "redraw", "background", "fill", "stroke", "strokeWeight",
         "strokeCap", "strokeJoin", "noStroke", "noFill", "tint", "noTint", "ellipse", "circle",
         "rect", "square", "line", "point", "triangle", "quad", "arc", "bezier", "curve",
         "beginShape", "endShape", "vertex", "bezierVertex", "curveVertex", "beginContour", "endContour",
         "push", "pop", "pushMatrix", "popMatrix", "translate", "rotate", "rotateX", "rotateY", "rotateZ",
         "scale", "shearX", "shearY", "resetMatrix", "applyMatrix", "text", "textSize", "textFont",
         "textAlign", "textLeading", "textWidth", "textAscent", "textDescent", "image", "imageMode",
         "shape", "shapeMode", "loadImage", "loadShape", "loadFont", "createFont", "loadPixels", "updatePixels",
         "imageMode", "rectMode", "ellipseMode", "colorMode", "blendMode", "filter", "mask", "copy",
         "createGraphics", "createImage", "save", "saveFrame", "saveStrings", "saveBytes",
         "loadStrings", "loadBytes", "loadJSONArray", "loadJSONObject", "loadTable",
         "createWriter", "createReader", "print", "println", "printArray", "nf", "nfc", "nfp", "nfs",
         "binary", "hex", "unbinary", "unhex", "parseInt", "parseFloat", "parseBoolean", "parseByte",
         "str", "join", "split", "splitTokens", "trim", "match", "matchAll", "subset", "concat", "sort",
         "append", "shorten", "splice", "reverse", "random", "randomSeed", "noise", "noiseSeed", "noiseDetail",
         "map", "norm", "lerp", "dist", "mag", "sq", "sqrt", "pow", "exp", "log", "abs", "constrain",
         "min", "max", "floor", "ceil", "round", "sin", "cos", "tan", "asin", "acos", "atan", "atan2",
         "degrees", "radians", "cursor", "noCursor", "delay", "exit", "link", "selectInput",
         "selectOutput", "selectFolder", "createInput", "createOutput", "beginCamera", "endCamera",
         "camera", "perspective", "ortho", "lights", "noLights", "ambientLight", "directionalLight",
         "pointLight", "spotLight", "ambient", "specular", "emissive", "shininess", "hint",
         "beginRecord", "endRecord", "beginRaw", "endRaw", "millis", "day", "month", "year",
         "hour", "minute", "second", "keyPressed", "mousePressed", "mouseReleased", "mouseClicked",
         "mouseDragged", "mouseMoved", "keyTyped", "keyReleased", "surface", "setResizable",
         "setLocation", "setTitle", "setSize", "setVisible", "setAlwaysOnTop", "add", "remove",
         "clear", "get", "set", "mult", "div", "normalize", "limit", "heading", "copy", "sub"
      ), processing));
      rules.add(new SyntaxRule(wordPattern(
         "width", "height", "displayWidth", "displayHeight", "pixelWidth", "pixelHeight", "pixels",
         "frameCount", "frameRate", "focused", "mouseX", "mouseY", "pmouseX", "pmouseY", "mouseButton",
         "mousePressed", "key", "keyCode", "keyPressed"
      ), runtimeValue));
      rules.add(new SyntaxRule(wordPattern(
         "PI", "HALF_PI", "QUARTER_PI", "TWO_PI", "TAU",
         "LEFT", "RIGHT", "CENTER", "TOP", "BOTTOM", "BASELINE", "CORNER", "CORNERS", "RADIUS",
         "ROUND", "SQUARE", "PROJECT", "MITER", "BEVEL", "CLOSE", "OPEN", "POINTS", "LINES", "TRIANGLES",
         "TRIANGLE_STRIP", "TRIANGLE_FAN", "QUADS", "QUAD_STRIP", "ARGB", "RGB", "HSB", "P2D", "P3D",
         "JAVA2D", "PDF", "SVG", "BLEND", "ADD", "SUBTRACT", "DARKEST", "LIGHTEST", "DIFFERENCE",
         "EXCLUSION", "MULTIPLY", "SCREEN", "REPLACE", "OVERLAY", "HARD_LIGHT", "SOFT_LIGHT", "DODGE",
         "BURN", "THRESHOLD", "GRAY", "INVERT", "OPAQUE", "POSTERIZE", "BLUR", "ERODE", "DILATE"
      ), constant));
      rules.add(new SyntaxRule(functionCallPattern(
         "if", "else", "for", "while", "switch", "case", "catch", "return", "new", "throw", "do", "try", "synchronized"
      ), processing));
      rules.add(new SyntaxRule("\\b\\d+(\\.\\d+)?\\b", number));
      rules.add(new SyntaxRule("\"([^\"\\\\]|\\\\.)*\"", string));
      rules.add(new SyntaxRule("[-+*/%=!<>|&^~?:]+", operator));
      rules.add(new SyntaxRule("//.*$", comment, Pattern.MULTILINE));
      return rules;
   }

   private static String wordPattern(String... words) {
      StringBuilder builder = new StringBuilder("\\b(");
      for (int i = 0; i < words.length; i++) {
         if (i > 0) {
            builder.append('|');
         }
         builder.append(Pattern.quote(words[i]));
      }
      builder.append(")\\b");
      return builder.toString();
   }

   private static String functionCallPattern(String... excludedWords) {
      StringBuilder excluded = new StringBuilder();
      for (int i = 0; i < excludedWords.length; i++) {
         if (i > 0) {
            excluded.append('|');
         }
         excluded.append(Pattern.quote(excludedWords[i]));
      }
      return "\\b(?!(" + excluded + ")\\b)[A-Za-z_][A-Za-z0-9_]*\\b(?=\\s*\\()";
   }

   static final class SyntaxRule {
      final Pattern pattern;
      final int color;

      SyntaxRule(String regex, int color) {
         this(regex, color, 0);
      }

      SyntaxRule(String regex, int color, int flags) {
         pattern = Pattern.compile(regex, flags);
         this.color = color;
      }
   }

   private static final class Builder {
      private int background;
      private int surface;
      private int surfaceSoft;
      private int border;
      private int text;
      private int textMuted;
      private int accent;
      private int codeAccent;
      private int play;
      private int stop;
      private int error;
      private int selection;
      private int currentLine;
      private int keyword;
      private int type;
      private int processing;
      private int runtimeValue;
      private int constant;
      private int number;
      private int string;
      private int comment;
      private int operator;

      Builder background(int value) {
         background = value;
         return this;
      }

      Builder surface(int value) {
         surface = value;
         return this;
      }

      Builder surfaceSoft(int value) {
         surfaceSoft = value;
         return this;
      }

      Builder border(int value) {
         border = value;
         return this;
      }

      Builder text(int value) {
         text = value;
         return this;
      }

      Builder textMuted(int value) {
         textMuted = value;
         return this;
      }

      Builder accent(int value) {
         accent = value;
         return this;
      }

      Builder codeAccent(int value) {
         codeAccent = value;
         return this;
      }

      Builder play(int value) {
         play = value;
         return this;
      }

      Builder stop(int value) {
         stop = value;
         return this;
      }

      Builder error(int value) {
         error = value;
         return this;
      }

      Builder selection(int value) {
         selection = value;
         return this;
      }

      Builder currentLine(int value) {
         currentLine = value;
         return this;
      }

      Builder keyword(int value) {
         keyword = value;
         return this;
      }

      Builder type(int value) {
         type = value;
         return this;
      }

      Builder processing(int value) {
         processing = value;
         return this;
      }

      Builder runtimeValue(int value) {
         runtimeValue = value;
         return this;
      }

      Builder constant(int value) {
         constant = value;
         return this;
      }

      Builder number(int value) {
         number = value;
         return this;
      }

      Builder string(int value) {
         string = value;
         return this;
      }

      Builder comment(int value) {
         comment = value;
         return this;
      }

      Builder operator(int value) {
         operator = value;
         return this;
      }

      EditorTheme build() {
         return new EditorTheme(this);
      }
   }
}
