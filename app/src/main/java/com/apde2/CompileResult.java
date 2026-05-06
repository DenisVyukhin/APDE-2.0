package com.apde2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class CompileResult {
   final SketchProgram program;
   final List<Diagnostic> diagnostics;

   private CompileResult(SketchProgram program, List<Diagnostic> diagnostics) {
      this.program = program;
      this.diagnostics = diagnostics;
   }

   static CompileResult success(SketchProgram program, List<Diagnostic> warnings) {
      return new CompileResult(program, new ArrayList<>(warnings));
   }

   static CompileResult failure(List<Diagnostic> diagnostics) {
      return new CompileResult(null, new ArrayList<>(diagnostics));
   }

   boolean hasErrors() {
      return program == null;
   }

   List<Diagnostic> diagnostics() {
      return Collections.unmodifiableList(diagnostics);
   }
}
