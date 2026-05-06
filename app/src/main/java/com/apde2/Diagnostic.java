package com.apde2;

final class Diagnostic {
   final int line;
   final String message;

   Diagnostic(int line, String message) {
      this.line = line;
      this.message = message;
   }

   @Override
   public String toString() {
      if (line <= 0) {
         return message;
      }
      return "Line " + line + ": " + message;
   }
}
