package com.apde2;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public final class CodeEditorViewFormatterTest {
   @Test
   public void keepsHexColorLiteralsTogether() {
      String formatted = CodeEditorView.formatCode(
         "void setup(){fill(#006699);color accent=#FFCC00;}",
         3
      );

      assertTrue(formatted.contains("fill(#006699);"));
      assertTrue(formatted.contains("color accent = #FFCC00;"));
   }
}
