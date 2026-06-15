package com.apde2.preview

import java.io.File

class ProcessingSourceGenerator(private val cacheRoot: File) {
   private val generatorVersion = "processing-source-generator-v17"
   private val defaultImports = listOf(
      "java.util.HashMap",
      "java.util.ArrayList",
      "java.io.File",
      "java.io.BufferedReader",
      "java.io.PrintWriter",
      "java.io.InputStream",
      "java.io.OutputStream",
      "java.io.IOException"
   )
   private val pAppletOverrides = setOf(
      "settings",
      "setup",
      "draw",
      "mousePressed",
      "mouseReleased",
      "mouseClicked",
      "mouseDragged",
      "mouseMoved",
      "mouseWheel",
      "keyPressed",
      "keyReleased",
      "keyTyped",
      "touchStarted",
      "touchMoved",
      "touchEnded",
      "pause",
      "resume",
      "dispose"
   )

   fun generate(snapshot: SketchSnapshot): GeneratedSketchProject {
      val allFiles = snapshot.root.walkTopDown().filter { it.isFile }.toList()
      val cacheKey = digestFiles(
         allFiles,
         "$generatorVersion|${snapshot.settings.packageName}|${snapshot.settings.className}|${snapshot.settings.orientation}|${snapshot.settings.minSdk}|${snapshot.settings.targetSdk}|${snapshot.settings.permissions.joinToString(",")}"
      )
      val projectRoot = File(cacheRoot, "generated/$cacheKey").also {
         if (it.exists()) return existingProject(it, snapshot, cacheKey)
         it.mkdirs()
      }
      val packageDir = File(projectRoot, "src/${snapshot.settings.packageName.replace('.', '/')}")
      packageDir.mkdirs()
      val generatedSource = File(packageDir, "${snapshot.settings.className}.java")
      generatedSource.writeText(wrapSketch(snapshot), Charsets.UTF_8)
      snapshot.dataDir?.copyRecursively(File(projectRoot, "assets"), overwrite = true)
      snapshot.assetsDir?.copyRecursively(File(projectRoot, "assets"), overwrite = true)
      snapshot.codeDir?.copyRecursively(File(projectRoot, "code"), overwrite = true)
      snapshot.androidDir?.copyRecursively(File(projectRoot, "android"), overwrite = true)
      return GeneratedSketchProject(
         root = projectRoot,
         sourceDir = File(projectRoot, "src"),
         generatedSource = generatedSource,
         dataDir = File(projectRoot, "assets").takeIf { it.isDirectory },
         packageName = snapshot.settings.packageName,
         className = snapshot.settings.className,
         cacheKey = cacheKey
      )
   }

   private fun existingProject(root: File, snapshot: SketchSnapshot, cacheKey: String): GeneratedSketchProject {
      val source = File(root, "src/${snapshot.settings.packageName.replace('.', '/')}/${snapshot.settings.className}.java")
      return GeneratedSketchProject(root, File(root, "src"), source, File(root, "assets").takeIf { it.isDirectory }, snapshot.settings.packageName, snapshot.settings.className, cacheKey)
   }

   private fun wrapSketch(snapshot: SketchSnapshot): String {
      val imports = linkedSetOf<String>()
      val settingsCalls = linkedSetOf<String>()
      val hasSettingsMethod = snapshot.pdeFiles.any { file ->
         val processor = PdeLineProcessor()
         file.readText(Charsets.UTF_8).lineSequence().any { line ->
            Regex("""^\s*(public\s+)?void\s+settings\s*\(""").containsMatchIn(processor.process(line).codeOnly)
         }
      }
      val hasSetupMethod = hasVoidMethod(snapshot, "setup")
      val hasDrawMethod = hasVoidMethod(snapshot, "draw")
      val source = snapshot.pdeFiles.joinToString("\n\n") { file ->
         val processor = PdeLineProcessor()
         val body = file.readText(Charsets.UTF_8).lines()
            .asSequence()
            .map(processor::process)
            .filterNot { processed ->
               val trimmed = processed.codeOnly.trim()
               if (trimmed.startsWith("import ") && trimmed.endsWith(";")) {
                  imports += trimmed
                  true
               } else {
                  false
               }
            }
            .filterNot { processed ->
               val trimmed = processed.codeOnly.trim()
               if (!hasSettingsMethod && isRendererSettingsCall(trimmed)) {
                  settingsCalls += trimmed
                  true
               } else {
                  false
               }
            }
            .map { it.rewritten }
            .joinToString("\n")
         "// ${file.name}\n$body"
      }
      val sketchBody = buildSketchBody(source, hasSetupMethod, hasDrawMethod)
      val generatedImports = defaultImports.joinToString("\n") { "import $it;" }
      val userImports = imports.joinToString("\n")
      val generatedSettings = if (!hasSettingsMethod) {
         val calls = if (settingsCalls.isNotEmpty()) settingsCalls else linkedSetOf("size(100, 100);")
         calls.joinToString(
            prefix = "\npublic void settings() {\n",
            separator = "\n",
            postfix = "\n}\n"
         ) { "  $it" }
      } else {
         ""
      }
      return """
         package ${snapshot.settings.packageName};

         import processing.core.*;
         import processing.data.*;
         import processing.event.*;
         import processing.opengl.*;
         $generatedImports
         $userImports

         public class ${snapshot.settings.className} extends PApplet {
         $generatedSettings
         $sketchBody
         ${consolePrintBridge(snapshot.settings.className)}
         ${runtimeErrorBridge()}
         }
      """.trimIndent()
   }

   private fun consolePrintBridge(className: String): String {
      return """

      private static volatile $className __apdeActiveSketch;
      private static final Object __apdeConsoleLock = new Object();
      private static final StringBuilder __apdeConsoleBuffer = new StringBuilder();
      private static long __apdeLastConsoleFlush = 0L;

      public static void print(byte value) { __apdeConsoleAppend(String.valueOf(value)); }
      public static void print(boolean value) { __apdeConsoleAppend(String.valueOf(value)); }
      public static void print(char value) { __apdeConsoleAppend(String.valueOf(value)); }
      public static void print(int value) { __apdeConsoleAppend(String.valueOf(value)); }
      public static void print(float value) { __apdeConsoleAppend(String.valueOf(value)); }
      public static void print(String value) { __apdeConsoleAppend(String.valueOf(value)); }
      public static void print(Object value) { __apdeConsoleAppend(String.valueOf(value)); }
      public static void print(Object... values) { __apdeConsoleAppend(__apdeJoinValues(values)); }

      public static void println() { __apdeConsoleAppend("\n"); }
      public static void println(byte value) { __apdeConsoleAppend(String.valueOf(value) + "\n"); }
      public static void println(boolean value) { __apdeConsoleAppend(String.valueOf(value) + "\n"); }
      public static void println(char value) { __apdeConsoleAppend(String.valueOf(value) + "\n"); }
      public static void println(int value) { __apdeConsoleAppend(String.valueOf(value) + "\n"); }
      public static void println(float value) { __apdeConsoleAppend(String.valueOf(value) + "\n"); }
      public static void println(String value) { __apdeConsoleAppend(String.valueOf(value) + "\n"); }
      public static void println(Object value) { __apdeConsoleAppend(String.valueOf(value) + "\n"); }
      public static void println(Object... values) { __apdeConsoleAppend(__apdeJoinValues(values) + "\n"); }

      private static String __apdeJoinValues(Object... values) {
        if (values == null) {
          return "null";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
          if (i > 0) {
            builder.append(' ');
          }
          builder.append(String.valueOf(values[i]));
        }
        return builder.toString();
      }

      private static void __apdeConsoleAppend(String message) {
        synchronized (__apdeConsoleLock) {
          __apdeConsoleBuffer.append(message);
          long now = android.os.SystemClock.uptimeMillis();
          if (__apdeLastConsoleFlush == 0L || now - __apdeLastConsoleFlush >= 120L || __apdeConsoleBuffer.length() >= 2048) {
            __apdeFlushConsoleLocked(now);
          }
        }
      }

      private static void __apdeFlushConsoleLocked(long now) {
        if (__apdeConsoleBuffer.length() == 0) {
          return;
        }
        $className sketch = __apdeActiveSketch;
        if (sketch == null) {
          return;
        }
        sketch.__apdeBroadcastConsole(__apdeConsoleBuffer.toString());
        __apdeConsoleBuffer.setLength(0);
        __apdeLastConsoleFlush = now;
      }

      private static void __apdeFlushConsole() {
        synchronized (__apdeConsoleLock) {
          __apdeFlushConsoleLocked(android.os.SystemClock.uptimeMillis());
        }
      }
      """.trimIndent()
   }

   private fun hasVoidMethod(snapshot: SketchSnapshot, name: String): Boolean {
      val pattern = Regex("""^\s*(public\s+)?void\s+${Regex.escape(name)}\s*\(""")
      return snapshot.pdeFiles.any { file ->
         val processor = PdeLineProcessor()
         file.readText(Charsets.UTF_8).lineSequence().any { line ->
            pattern.containsMatchIn(processor.process(line).codeOnly)
         }
      }
   }

   private fun buildSketchBody(source: String, hasSetupMethod: Boolean, hasDrawMethod: Boolean): String {
      val classLines = mutableListOf<String>()
      val setupLines = mutableListOf<String>()
      val classifier = PdeLineProcessor()
      var braceDepth = 0
      var activeTarget: MutableList<String>? = null
      var pendingClassDeclaration = false

      source.lines().forEach { line ->
         val codeOnly = classifier.process(line).codeOnly
         val trimmed = codeOnly.trim()
         val topLevelDeclaration = isTopLevelDeclaration(trimmed)
         val target = when {
            braceDepth > 0 -> activeTarget ?: classLines
            pendingClassDeclaration -> classLines
            trimmed.isEmpty() -> classLines
            topLevelDeclaration -> classLines
            else -> setupLines
         }
         target += line

         val previousDepth = braceDepth
         braceDepth = (braceDepth + braceDelta(codeOnly)).coerceAtLeast(0)
         if (previousDepth == 0 && braceDepth > 0) {
            activeTarget = target
         }
         if (braceDepth == 0) {
            activeTarget = null
         }
         pendingClassDeclaration = previousDepth == 0 &&
            braceDepth == 0 &&
            target === classLines &&
            trimmed.isNotEmpty() &&
            (topLevelDeclaration || pendingClassDeclaration) &&
            !trimmed.endsWith(";") &&
            !trimmed.endsWith("}")
      }

      if (!hasSetupMethod && (setupLines.any { it.trim().isNotEmpty() } || !hasDrawMethod)) {
         classLines += ""
         classLines += "public void setup() {"
         setupLines.forEach { line ->
            classLines += if (line.isBlank()) "" else "  $line"
         }
         classLines += "}"
      } else {
         classLines += setupLines
      }

      return classLines.joinToString("\n")
   }

   private fun isTopLevelDeclaration(trimmedLine: String): Boolean {
      if (trimmedLine.startsWith("@")) {
         return true
      }
      if (Regex("""^(?:public\s+|protected\s+|private\s+|static\s+|final\s+|abstract\s+)*(?:class|interface|enum)\b""").containsMatchIn(trimmedLine)) {
         return true
      }
      if (Regex("""^(?:public\s+|protected\s+|private\s+|static\s+|final\s+|abstract\s+|synchronized\s+)*(?:void|boolean|byte|char|short|int|long|float|double|String|[A-Za-z_$][\w$]*(?:\s*<[^;=()]+>)?(?:\s*\[\s*\])*)\s+[A-Za-z_$][\w$]*\s*\(""").containsMatchIn(trimmedLine)) {
         return true
      }
      if (Regex("""^(?:public\s+|protected\s+|private\s+|static\s+|final\s+|transient\s+|volatile\s+)*(?:boolean|byte|char|short|int|long|float|double|String|[A-Za-z_$][\w$]*(?:\s*<[^;=()]+>)?(?:\s*\[\s*\])*)\s+[A-Za-z_$][\w$]*(?:\s*(?:=|,|;|\[)).*""").matches(trimmedLine)) {
         return true
      }
      return false
   }

   private fun braceDelta(code: String): Int {
      var delta = 0
      var inString = false
      var inChar = false
      var escaped = false
      code.forEach { char ->
         if (escaped) {
            escaped = false
            return@forEach
         }
         if (char == '\\' && (inString || inChar)) {
            escaped = true
            return@forEach
         }
         if (char == '"' && !inChar) {
            inString = !inString
            return@forEach
         }
         if (char == '\'' && !inString) {
            inChar = !inChar
            return@forEach
         }
         if (!inString && !inChar) {
            if (char == '{') {
               delta++
            } else if (char == '}') {
               delta--
            }
         }
      }
      return delta
   }

   private fun runtimeErrorBridge(): String {
      return """

      private boolean __apdeRuntimeErrorReported = false;
      private boolean __apdeFirstHandleDrawReported = false;

      @Override
      public void handleDraw() {
        try {
          __apdeActiveSketch = this;
          if (!__apdeFirstHandleDrawReported) {
            __apdeFirstHandleDrawReported = true;
            __apdeBroadcastLog("Sketch runtime: handleDraw entered.");
          }
          super.handleDraw();
          __apdeFlushConsole();
        } catch (Throwable t) {
          __apdeReportRuntimeError(t);
          throw __apdeWrapRuntimeError(t);
        }
      }

      @Override
      public boolean surfaceTouchEvent(android.view.MotionEvent event) {
        try {
          __apdeActiveSketch = this;
          return super.surfaceTouchEvent(event);
        } catch (Throwable t) {
          __apdeReportRuntimeError(t);
          throw __apdeWrapRuntimeError(t);
        }
      }

      @Override
      public void surfaceKeyDown(int code, android.view.KeyEvent event) {
        try {
          __apdeActiveSketch = this;
          super.surfaceKeyDown(code, event);
        } catch (Throwable t) {
          __apdeReportRuntimeError(t);
          throw __apdeWrapRuntimeError(t);
        }
      }

      @Override
      public void surfaceKeyUp(int code, android.view.KeyEvent event) {
        try {
          __apdeActiveSketch = this;
          super.surfaceKeyUp(code, event);
        } catch (Throwable t) {
          __apdeReportRuntimeError(t);
          throw __apdeWrapRuntimeError(t);
        }
      }

      private RuntimeException __apdeWrapRuntimeError(Throwable t) {
        if (t instanceof RuntimeException) {
          return (RuntimeException) t;
        }
        if (t instanceof Error) {
          throw (Error) t;
        }
        return new RuntimeException(t);
      }

      private void __apdeReportRuntimeError(Throwable t) {
        if (__apdeRuntimeErrorReported) {
          return;
        }
        __apdeRuntimeErrorReported = true;
        t.printStackTrace();
        try {
          java.io.StringWriter writer = new java.io.StringWriter();
          t.printStackTrace(new java.io.PrintWriter(writer));
          android.content.Intent intent = new android.content.Intent("com.apde2.preview.CRASH");
          intent.setPackage("com.apde2");
          intent.putExtra("com.apde2.preview.extra.MESSAGE", t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage()));
          intent.putExtra("com.apde2.preview.extra.STACKTRACE", writer.toString());
          android.app.Activity activity = getActivity();
          if (activity != null) {
            activity.sendBroadcast(intent);
          }
        } catch (Throwable ignored) {
        }
      }

      private void __apdeBroadcastLog(String message) {
        try {
          android.content.Intent intent = new android.content.Intent("com.apde2.preview.LOG");
          intent.setPackage("com.apde2");
          intent.putExtra("com.apde2.preview.extra.MESSAGE", message);
          android.app.Activity activity = getActivity();
          if (activity != null) {
            activity.sendBroadcast(intent);
          }
        } catch (Throwable ignored) {
        }
      }

      private void __apdeBroadcastConsole(String message) {
        try {
          android.content.Intent intent = new android.content.Intent("com.apde2.preview.LOG");
          intent.setPackage("com.apde2");
          intent.putExtra("com.apde2.preview.extra.CHANNEL", "stdout");
          intent.putExtra("com.apde2.preview.extra.MESSAGE", message);
          android.app.Activity activity = getActivity();
          if (activity != null) {
            activity.sendBroadcast(intent);
          }
        } catch (Throwable ignored) {
        }
      }
      """.trimIndent()
   }

   private fun isRendererSettingsCall(trimmedLine: String): Boolean {
      return Regex("""^(size|fullScreen|smooth|noSmooth|pixelDensity)\s*\(.*\)\s*;\s*$""").matches(trimmedLine)
   }

   private fun normalizeProcessingLine(line: String): String {
      val whitespaceNormalized = normalizeSourceWhitespace(line)
      val colorNormalized = normalizeColorType(whitespaceNormalized)
      val hexNormalized = normalizeHexColorLiterals(colorNormalized)
      val castNormalized = normalizeCastFunctions(hexNormalized)
      val literalNormalized = normalizeFloatLiterals(castNormalized)
      val method = Regex("""^(\s*)(void|boolean)\s+([A-Za-z_$][\w$]*)\s*\(""").find(literalNormalized) ?: return literalNormalized
      val name = method.groupValues[3]
      if (name !in pAppletOverrides) {
         return literalNormalized
      }
      val beforeMethod = literalNormalized.substring(0, method.range.first)
      val declarationPrefix = literalNormalized.substring(method.range.first, method.range.last + 1)
      if (declarationPrefix.contains(Regex("""\b(public|protected|private)\b"""))) {
         return literalNormalized
      }
      val indent = method.groupValues[1]
      return beforeMethod + indent + "public " + literalNormalized.substring(indent.length)
   }

   private fun normalizeSourceWhitespace(code: String): String {
      return code.map { char ->
         if (char.code > 127 && (Character.isWhitespace(char) || Character.isSpaceChar(char))) {
            ' '
         } else {
            char
         }
      }.joinToString("")
   }

   private fun normalizeColorType(code: String): String {
      return code
         .replace(Regex("""(?<![\w$])new\s+color(?=\s*\[)"""), "new int")
         .replace(Regex("""(?<=\()\s*color(?=\s*\))""")) { match -> match.value.replace("color", "int") }
         .replace(Regex("""(?<![\w$])color(?=\s*(?:\[\s*\]\s*)+[A-Za-z_$])"""), "int")
         .replace(Regex("""(?<![\w$])color(?=\s+[A-Za-z_$][\w$]*\s*(?:[=;,\)\[]|\())"""), "int")
   }

   private fun normalizeHexColorLiterals(code: String): String {
      return Regex("""(?<![\w$])#([0-9A-Fa-f]{6})(?![0-9A-Fa-f\w$])""")
         .replace(code) { match -> "0xFF${match.groupValues[1]}" }
   }

   private fun normalizeCastFunctions(code: String): String {
      return code
         .replace(Regex("""(?<![\w$])boolean(?=\s*\()"""), "parseBoolean")
         .replace(Regex("""(?<![\w$])byte(?=\s*\()"""), "parseByte")
         .replace(Regex("""(?<![\w$])char(?=\s*\()"""), "parseChar")
         .replace(Regex("""(?<![\w$])float(?=\s*\()"""), "parseFloat")
         .replace(Regex("""(?<![\w$])int(?=\s*\()"""), "parseInt")
   }

   private fun normalizeFloatLiterals(code: String): String {
      return Regex("""(?<![\w$])((?:\d+\.\d*|\.\d+)(?:[eE][+-]?\d+)?|\d+[eE][+-]?\d+)(?![fFdD\w$])""")
         .replace(code) { match -> "${match.value}f" }
   }

   private data class ProcessedLine(
      val codeOnly: String,
      val rewritten: String
   )

   private inner class PdeLineProcessor {
      private var inBlockComment = false

      fun process(line: String): ProcessedLine {
         val codeOnly = StringBuilder(line.length)
         val rewritten = StringBuilder(line.length)
         val codeSegment = StringBuilder()
         var index = 0

         fun flushCodeSegment() {
            if (codeSegment.isNotEmpty()) {
               rewritten.append(normalizeProcessingLine(codeSegment.toString()))
               codeSegment.setLength(0)
            }
         }

         fun appendMasked(raw: Char) {
            rewritten.append(raw)
            codeOnly.append(' ')
         }

         while (index < line.length) {
            if (inBlockComment) {
               appendMasked(line[index])
               if (line[index] == '*' && index + 1 < line.length && line[index + 1] == '/') {
                  index++
                  appendMasked(line[index])
                  inBlockComment = false
               }
               index++
               continue
            }

            val char = line[index]
            val next = if (index + 1 < line.length) line[index + 1] else '\u0000'
            if (char == '/' && next == '/') {
               flushCodeSegment()
               while (index < line.length) {
                  appendMasked(line[index])
                  index++
               }
               break
            }
            if (char == '/' && next == '*') {
               flushCodeSegment()
               appendMasked(char)
               index++
               appendMasked(line[index])
               inBlockComment = true
               index++
               continue
            }
            if (char == '"' || char == '\'') {
               flushCodeSegment()
               index = copyQuotedLiteral(line, index, codeOnly, rewritten)
               continue
            }

            codeSegment.append(char)
            codeOnly.append(char)
            index++
         }
         flushCodeSegment()
         return ProcessedLine(codeOnly.toString(), rewritten.toString())
      }

      private fun copyQuotedLiteral(line: String, start: Int, codeOnly: StringBuilder, rewritten: StringBuilder): Int {
         val quote = line[start]
         var index = start + 1
         var escaped = false
         rewritten.append(quote)
         codeOnly.append(quote)
         while (index < line.length) {
            val char = line[index]
            rewritten.append(char)
            codeOnly.append(char)
            index++
            if (escaped) {
               escaped = false
            } else if (char == '\\') {
               escaped = true
            } else if (char == quote) {
               break
            }
         }
         return index
      }
   }
}
