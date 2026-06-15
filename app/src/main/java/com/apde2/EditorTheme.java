package com.apde2;

import android.content.Context;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class EditorTheme {
   private static final String THEMES_ASSET = "themes/editor_themes.json";

   final int background;
   final int surface;
   final int surfaceSoft;
   final int border;
   final int scrim;
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
   final int colorPickerBorder;
   final int colorPickerThumb;
   final int colorPickerThumbStroke;
   final int hueSliderMarker;
   final int hueSliderMarkerStroke;
   final int runnerBackground;
   final int runnerEmptyBackground;
   final List<SyntaxRule> syntaxRules;

   private EditorTheme(Builder builder) {
      background = builder.background;
      surface = builder.surface;
      surfaceSoft = builder.surfaceSoft;
      border = builder.border;
      scrim = builder.scrim;
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
      colorPickerBorder = builder.colorPickerBorder;
      colorPickerThumb = builder.colorPickerThumb;
      colorPickerThumbStroke = builder.colorPickerThumbStroke;
      hueSliderMarker = builder.hueSliderMarker;
      hueSliderMarkerStroke = builder.hueSliderMarkerStroke;
      runnerBackground = builder.runnerBackground;
      runnerEmptyBackground = builder.runnerEmptyBackground;
      syntaxRules = Collections.unmodifiableList(buildSyntaxRules());
   }

   static EditorTheme load(Context context, String themeId) {
      try {
         JSONObject root = new JSONObject(readAsset(context));
         return fromJson(findTheme(root, themeId));
      } catch (IOException | JSONException | IllegalArgumentException exception) {
         throw new IllegalStateException("Unable to load editor theme colors", exception);
      }
   }

   static List<ThemeInfo> listThemes(Context context) {
      try {
         JSONObject root = new JSONObject(readAsset(context));
         JSONArray themes = root.getJSONArray("themes");
         List<ThemeInfo> result = new ArrayList<>();
         for (int i = 0; i < themes.length(); i++) {
            JSONObject theme = themes.getJSONObject(i);
            String id = theme.getString("id");
            result.add(new ThemeInfo(id, theme.optString("name", id)));
         }
         return Collections.unmodifiableList(result);
      } catch (IOException | JSONException exception) {
         throw new IllegalStateException("Unable to load editor theme list", exception);
      }
   }

   static String resolveThemeId(Context context, String themeId) {
      try {
         JSONObject root = new JSONObject(readAsset(context));
         return findTheme(root, themeId).getString("id");
      } catch (IOException | JSONException exception) {
         throw new IllegalStateException("Unable to resolve editor theme", exception);
      }
   }

   static EditorTheme interpolate(EditorTheme from, EditorTheme to, float fraction) {
      float safeFraction = Math.max(0f, Math.min(1f, fraction));
      return new Builder()
         .background(interpolateColor(from.background, to.background, safeFraction))
         .surface(interpolateColor(from.surface, to.surface, safeFraction))
         .surfaceSoft(interpolateColor(from.surfaceSoft, to.surfaceSoft, safeFraction))
         .border(interpolateColor(from.border, to.border, safeFraction))
         .scrim(interpolateColor(from.scrim, to.scrim, safeFraction))
         .text(interpolateColor(from.text, to.text, safeFraction))
         .textMuted(interpolateColor(from.textMuted, to.textMuted, safeFraction))
         .accent(interpolateColor(from.accent, to.accent, safeFraction))
         .codeAccent(interpolateColor(from.codeAccent, to.codeAccent, safeFraction))
         .error(interpolateColor(from.error, to.error, safeFraction))
         .play(interpolateColor(from.play, to.play, safeFraction))
         .stop(interpolateColor(from.stop, to.stop, safeFraction))
         .selection(interpolateColor(from.selection, to.selection, safeFraction))
         .currentLine(interpolateColor(from.currentLine, to.currentLine, safeFraction))
         .colorPickerBorder(interpolateColor(from.colorPickerBorder, to.colorPickerBorder, safeFraction))
         .colorPickerThumb(interpolateColor(from.colorPickerThumb, to.colorPickerThumb, safeFraction))
         .colorPickerThumbStroke(interpolateColor(from.colorPickerThumbStroke, to.colorPickerThumbStroke, safeFraction))
         .hueSliderMarker(interpolateColor(from.hueSliderMarker, to.hueSliderMarker, safeFraction))
         .hueSliderMarkerStroke(interpolateColor(from.hueSliderMarkerStroke, to.hueSliderMarkerStroke, safeFraction))
         .runnerBackground(interpolateColor(from.runnerBackground, to.runnerBackground, safeFraction))
         .runnerEmptyBackground(interpolateColor(from.runnerEmptyBackground, to.runnerEmptyBackground, safeFraction))
         .keyword(interpolateColor(from.keyword, to.keyword, safeFraction))
         .type(interpolateColor(from.type, to.type, safeFraction))
         .processing(interpolateColor(from.processing, to.processing, safeFraction))
         .runtimeValue(interpolateColor(from.runtimeValue, to.runtimeValue, safeFraction))
         .constant(interpolateColor(from.constant, to.constant, safeFraction))
         .number(interpolateColor(from.number, to.number, safeFraction))
         .string(interpolateColor(from.string, to.string, safeFraction))
         .comment(interpolateColor(from.comment, to.comment, safeFraction))
         .operator(interpolateColor(from.operator, to.operator, safeFraction))
         .build();
   }

   private static int interpolateColor(int from, int to, float fraction) {
      int a = Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * fraction);
      int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * fraction);
      int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * fraction);
      int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * fraction);
      return Color.argb(a, r, g, b);
   }

   private static String readAsset(Context context) throws IOException {
      try (InputStream input = context.getAssets().open(THEMES_ASSET);
           ByteArrayOutputStream output = new ByteArrayOutputStream()) {
         byte[] buffer = new byte[4096];
         int count;
         while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
         }
         return new String(output.toByteArray(), StandardCharsets.UTF_8);
      }
   }

   private static JSONObject findTheme(JSONObject root, String themeId) throws JSONException {
      String requestedId = themeId == null ? "" : themeId.trim();
      String defaultId = root.optString("defaultTheme", "");
      JSONArray themes = root.getJSONArray("themes");
      JSONObject firstTheme = null;
      JSONObject defaultTheme = null;

      for (int i = 0; i < themes.length(); i++) {
         JSONObject theme = themes.getJSONObject(i);
         String id = theme.getString("id");
         if (firstTheme == null) {
            firstTheme = theme;
         }
         if (id.equals(requestedId)) {
            return theme;
         }
         if (id.equals(defaultId)) {
            defaultTheme = theme;
         }
      }

      if (defaultTheme != null) {
         return defaultTheme;
      }
      if (firstTheme != null) {
         return firstTheme;
      }
      throw new JSONException("Theme list is empty");
   }

   private static EditorTheme fromJson(JSONObject theme) throws JSONException {
      JSONObject colors = theme.getJSONObject("colors");
      JSONObject ui = colors.getJSONObject("ui");
      JSONObject actions = colors.getJSONObject("actions");
      JSONObject editor = colors.getJSONObject("editor");
      JSONObject widgets = colors.getJSONObject("widgets");
      JSONObject syntax = colors.getJSONObject("syntax");

      return new Builder()
         .background(color(ui, "background"))
         .surface(color(ui, "surface"))
         .surfaceSoft(color(ui, "surfaceSoft"))
         .border(color(ui, "border"))
         .scrim(color(ui, "scrim"))
         .text(color(ui, "text"))
         .textMuted(color(ui, "textMuted"))
         .accent(color(ui, "accent"))
         .codeAccent(color(ui, "codeAccent"))
         .error(color(ui, "error"))
         .play(color(actions, "play"))
         .stop(color(actions, "stop"))
         .selection(color(editor, "selection"))
         .currentLine(color(editor, "currentLine"))
         .colorPickerBorder(color(widgets, "colorPickerBorder"))
         .colorPickerThumb(color(widgets, "colorPickerThumb"))
         .colorPickerThumbStroke(color(widgets, "colorPickerThumbStroke"))
         .hueSliderMarker(color(widgets, "hueSliderMarker"))
         .hueSliderMarkerStroke(color(widgets, "hueSliderMarkerStroke"))
         .runnerBackground(color(widgets, "runnerBackground"))
         .runnerEmptyBackground(color(widgets, "runnerEmptyBackground"))
         .keyword(color(syntax, "keyword"))
         .type(color(syntax, "type"))
         .processing(color(syntax, "processing"))
         .runtimeValue(color(syntax, "runtimeValue"))
         .constant(color(syntax, "constant"))
         .number(color(syntax, "number"))
         .string(color(syntax, "string"))
         .comment(color(syntax, "comment"))
         .operator(color(syntax, "operator"))
         .build();
   }

   private static int color(JSONObject section, String key) throws JSONException {
      return Color.parseColor(section.getString(key));
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
      rules.add(new SyntaxRule("(?<![\\w$])#[0-9A-Fa-f]{6}(?![0-9A-Fa-f\\w$])", number));
      rules.add(new SyntaxRule("\\b\\d+(\\.\\d+)?\\b", number));
      rules.add(new SyntaxRule("\"([^\"\\\\]|\\\\.)*\"", string));
      rules.add(new SyntaxRule("[-+*/%=!<>|&^~?:]+", operator));
      rules.add(new SyntaxRule("/\\*.*?(?:\\*/|\\z)", comment, Pattern.DOTALL));
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

   static final class ThemeInfo {
      final String id;
      final String name;

      ThemeInfo(String id, String name) {
         this.id = id;
         this.name = name;
      }
   }

   private static final class Builder {
      private int background;
      private int surface;
      private int surfaceSoft;
      private int border;
      private int scrim;
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
      private int colorPickerBorder;
      private int colorPickerThumb;
      private int colorPickerThumbStroke;
      private int hueSliderMarker;
      private int hueSliderMarkerStroke;
      private int runnerBackground;
      private int runnerEmptyBackground;

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

      Builder scrim(int value) {
         scrim = value;
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

      Builder colorPickerBorder(int value) {
         colorPickerBorder = value;
         return this;
      }

      Builder colorPickerThumb(int value) {
         colorPickerThumb = value;
         return this;
      }

      Builder colorPickerThumbStroke(int value) {
         colorPickerThumbStroke = value;
         return this;
      }

      Builder hueSliderMarker(int value) {
         hueSliderMarker = value;
         return this;
      }

      Builder hueSliderMarkerStroke(int value) {
         hueSliderMarkerStroke = value;
         return this;
      }

      Builder runnerBackground(int value) {
         runnerBackground = value;
         return this;
      }

      Builder runnerEmptyBackground(int value) {
         runnerEmptyBackground = value;
         return this;
      }

      EditorTheme build() {
         return new EditorTheme(this);
      }
   }
}
