package com.apde2;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ProcessingCompiler {
   CompileResult compile(String code) {
      List<Diagnostic> diagnostics = new ArrayList<>();
      SketchProgram program = new SketchProgram();
      String[] lines = code.split("\\r?\\n");
      Section section = Section.GLOBAL;

      for (int i = 0; i < lines.length; i++) {
         String rawLine = lines[i];
         String line = stripComment(rawLine).trim();
         int lineNumber = i + 1;

         if (line.isEmpty()) {
            continue;
         }
         if (line.startsWith("void setup()")) {
            section = Section.SETUP;
            continue;
         }
         if (line.startsWith("void draw()")) {
            section = Section.DRAW;
            continue;
         }
         if ("}".equals(line)) {
            section = Section.GLOBAL;
            continue;
         }
         if ("{".equals(line)) {
            continue;
         }

         parseStatement(line, lineNumber, section, program, diagnostics);
      }

      if (!diagnostics.isEmpty()) {
         return CompileResult.failure(diagnostics);
      }
      return CompileResult.success(program, diagnostics);
   }

   private void parseStatement(String line, int lineNumber, Section section, SketchProgram program, List<Diagnostic> diagnostics) {
      String normalized = line.toLowerCase(Locale.US);
      List<SketchProgram.SketchCommand> target = section == Section.DRAW ? program.drawCommands : program.setupCommands;

      if (normalized.startsWith("size(")) {
         List<String> args = args(line);
         if (args.size() != 2) {
            diagnostics.add(new Diagnostic(lineNumber, "size() expects width and height."));
            return;
         }
         program.width = Math.round(Value.parse(args.get(0)).resolve(emptyState()));
         program.height = Math.round(Value.parse(args.get(1)).resolve(emptyState()));
         return;
      }

      if (normalized.startsWith("background(")) {
         List<Value> values = colorArgs(line, lineNumber, diagnostics);
         if (values != null) {
            target.add(new SketchProgram.BackgroundCommand(values.get(0), values.get(1), values.get(2)));
         }
         return;
      }

      if (normalized.startsWith("fill(")) {
         List<Value> values = colorArgs(line, lineNumber, diagnostics);
         if (values != null) {
            target.add(new SketchProgram.FillCommand(values.get(0), values.get(1), values.get(2)));
         }
         return;
      }

      if (normalized.startsWith("stroke(")) {
         List<Value> values = colorArgs(line, lineNumber, diagnostics);
         if (values != null) {
            target.add(new SketchProgram.StrokeCommand(values.get(0), values.get(1), values.get(2)));
         }
         return;
      }

      if (normalized.startsWith("nostroke(")) {
         target.add(new SketchProgram.NoStrokeCommand());
         return;
      }

      if (normalized.startsWith("nofill(")) {
         target.add(new SketchProgram.NoFillCommand());
         return;
      }

      if (normalized.startsWith("ellipse(")) {
         List<String> args = args(line);
         if (args.size() == 4) {
            target.add(new SketchProgram.EllipseCommand(Value.parse(args.get(0)), Value.parse(args.get(1)), Value.parse(args.get(2)), Value.parse(args.get(3))));
         } else {
            diagnostics.add(new Diagnostic(lineNumber, "ellipse() expects x, y, width, height."));
         }
         return;
      }

      if (normalized.startsWith("rect(")) {
         List<String> args = args(line);
         if (args.size() == 4) {
            target.add(new SketchProgram.RectCommand(Value.parse(args.get(0)), Value.parse(args.get(1)), Value.parse(args.get(2)), Value.parse(args.get(3))));
         } else {
            diagnostics.add(new Diagnostic(lineNumber, "rect() expects x, y, width, height."));
         }
         return;
      }

      if (normalized.startsWith("line(")) {
         List<String> args = args(line);
         if (args.size() == 4) {
            target.add(new SketchProgram.LineCommand(Value.parse(args.get(0)), Value.parse(args.get(1)), Value.parse(args.get(2)), Value.parse(args.get(3))));
         } else {
            diagnostics.add(new Diagnostic(lineNumber, "line() expects x1, y1, x2, y2."));
         }
         return;
      }

      if (normalized.startsWith("text(")) {
         List<String> args = args(line);
         if (args.size() == 3) {
            target.add(new SketchProgram.TextCommand(unquote(args.get(0)), Value.parse(args.get(1)), Value.parse(args.get(2))));
         } else {
            diagnostics.add(new Diagnostic(lineNumber, "text() expects text, x, y."));
         }
         return;
      }

      diagnostics.add(new Diagnostic(lineNumber, "Unsupported MVP statement: " + line));
   }

   private List<Value> colorArgs(String line, int lineNumber, List<Diagnostic> diagnostics) {
      List<String> args = args(line);
      if (args.size() == 1) {
         Value gray = Value.parse(args.get(0));
         List<Value> values = new ArrayList<>();
         values.add(gray);
         values.add(gray);
         values.add(gray);
         return values;
      }
      if (args.size() == 3) {
         List<Value> values = new ArrayList<>();
         values.add(Value.parse(args.get(0)));
         values.add(Value.parse(args.get(1)));
         values.add(Value.parse(args.get(2)));
         return values;
      }
      diagnostics.add(new Diagnostic(lineNumber, "Color functions expect gray or r, g, b."));
      return null;
   }

   private List<String> args(String line) {
      int open = line.indexOf('(');
      int close = line.lastIndexOf(')');
      List<String> args = new ArrayList<>();
      if (open < 0 || close <= open) {
         return args;
      }

      String body = line.substring(open + 1, close);
      StringBuilder current = new StringBuilder();
      boolean inString = false;
      int nested = 0;
      for (int i = 0; i < body.length(); i++) {
         char c = body.charAt(i);
         if (c == '"') {
            inString = !inString;
         }
         if (!inString && c == '(') {
            nested++;
         }
         if (!inString && c == ')') {
            nested--;
         }
         if (!inString && nested == 0 && c == ',') {
            args.add(current.toString().trim());
            current.setLength(0);
         } else {
            current.append(c);
         }
      }
      if (current.length() > 0) {
         args.add(current.toString().trim());
      }
      return args;
   }

   private String stripComment(String line) {
      int commentIndex = line.indexOf("//");
      if (commentIndex < 0) {
         return line;
      }
      return line.substring(0, commentIndex);
   }

   private String unquote(String value) {
      String trimmed = value.trim();
      if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
         return trimmed.substring(1, trimmed.length() - 1);
      }
      return trimmed;
   }

   private SketchProgram.RuntimeState emptyState() {
      return new SketchProgram.RuntimeState(0, 640, 480);
   }

   enum Section {
      GLOBAL,
      SETUP,
      DRAW
   }
}
