package com.apde2.preview

class PreviewDiagnosticParser {
   private val javacPattern = Regex("""^(.+\.java):(\d+):(?:\s*error:)?\s*(.+)$""")
   private val ecjPattern = Regex("""^\s*\d+\.\s+ERROR in (.+) \(at line (\d+)\)\s*$""")

   fun parse(output: String): List<PreviewDiagnostic> {
      val diagnostics = mutableListOf<PreviewDiagnostic>()
      val lines = output.lines()
      for ((index, line) in lines.withIndex()) {
         val javac = javacPattern.matchEntire(line)
         if (javac != null) {
            diagnostics += PreviewDiagnostic(
               file = javac.groupValues[1],
               line = javac.groupValues[2].toIntOrNull() ?: 0,
               column = 0,
               message = javac.groupValues[3].trim()
            )
            continue
         }
         ecjPattern.matchEntire(line)?.let {
            val detail = ecjDetail(lines.drop(index + 1))
            diagnostics += PreviewDiagnostic(
               file = it.groupValues[1],
               line = it.groupValues[2].toIntOrNull() ?: 0,
               column = detail.column,
               message = detail.message.ifBlank { "Java compilation error" }
            )
         }
      }
      return diagnostics
   }

   private fun ecjDetail(linesAfterHeader: List<String>): EcjDetail {
      val sourceIndex = linesAfterHeader.indexOfFirst { it.isNotBlank() }
      if (sourceIndex < 0) {
         return EcjDetail(0, "")
      }
      val caretIndex = linesAfterHeader.drop(sourceIndex + 1).indexOfFirst { it.trimStart().startsWith("^") }
      if (caretIndex < 0) {
         val message = linesAfterHeader.drop(sourceIndex + 1).firstOrNull { it.isNotBlank() }.orEmpty()
         return EcjDetail(0, message.trim())
      }
      val absoluteCaretIndex = sourceIndex + 1 + caretIndex
      val caretLine = linesAfterHeader[absoluteCaretIndex]
      val column = caretLine.indexOf('^').takeIf { it >= 0 }?.plus(1) ?: 0
      val message = linesAfterHeader
         .drop(absoluteCaretIndex + 1)
         .firstOrNull { it.isNotBlank() && !it.trimStart().startsWith("^") }
         .orEmpty()
      return EcjDetail(column, message.trim())
   }

   private data class EcjDetail(
      val column: Int,
      val message: String
   )
}
