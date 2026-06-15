package com.apde2.preview

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProcessingSourceGeneratorTest {
   @get:Rule
   val temporaryFolder = TemporaryFolder()

   @Test
   fun rewritesProcessingCastFunctionsOutsideCommentsAndStrings() {
      val snapshotRoot = temporaryFolder.newFolder("snapshot")
      val sketch = File(snapshotRoot, "CastSketch.pde").apply {
         writeText(
            """
            String label = "int(value) and float(value)";
            // int(commentValue);
            void setup() {
              float ratio = float("1.5");
              int count = int(ratio);
              boolean enabled = boolean("true");
              byte raw = byte(count);
              char marker = char(65);
            }
            """.trimIndent(),
            Charsets.UTF_8
         )
      }

      val source = ProcessingSourceGenerator(temporaryFolder.newFolder("cache"))
         .generate(snapshot(snapshotRoot, sketch))
         .generatedSource
         .readText(Charsets.UTF_8)

      assertTrue(source.contains("""float ratio = parseFloat("1.5");"""))
      assertTrue(source.contains("int count = parseInt(ratio);"))
      assertTrue(source.contains("""boolean enabled = parseBoolean("true");"""))
      assertTrue(source.contains("byte raw = parseByte(count);"))
      assertTrue(source.contains("char marker = parseChar(65);"))
      assertTrue(source.contains("""String label = "int(value) and float(value)";"""))
      assertTrue(source.contains("// int(commentValue);"))
   }

   @Test
   fun rewritesProcessingHexColorsOutsideCommentsAndStrings() {
      val snapshotRoot = temporaryFolder.newFolder("hex-color-snapshot")
      val sketch = File(snapshotRoot, "HexColorSketch.pde").apply {
         writeText(
            """
            String label = "#FFCC00";
            // fill(#112233);
            void setup() {
              color accent = #FFCC00;
              fill(#006699);
            }
            """.trimIndent(),
            Charsets.UTF_8
         )
      }

      val source = ProcessingSourceGenerator(temporaryFolder.newFolder("hex-color-cache"))
         .generate(snapshot(snapshotRoot, sketch))
         .generatedSource
         .readText(Charsets.UTF_8)

      assertTrue(source.contains("int accent = 0xFFFFCC00;"))
      assertTrue(source.contains("fill(0xFF006699);"))
      assertTrue(source.contains("""String label = "#FFCC00";"""))
      assertTrue(source.contains("// fill(#112233);"))
   }

   @Test
   fun addsProcessingDefaultImportsForArrayList() {
      val snapshotRoot = temporaryFolder.newFolder("array-list-snapshot")
      val sketch = File(snapshotRoot, "ParticleSketch.pde").apply {
         writeText(
            """
            ArrayList<Particle> particles = new ArrayList<Particle>();

            class Particle {
            }
            """.trimIndent(),
            Charsets.UTF_8
         )
      }

      val source = ProcessingSourceGenerator(temporaryFolder.newFolder("array-list-cache"))
         .generate(snapshot(snapshotRoot, sketch))
         .generatedSource
         .readText(Charsets.UTF_8)

      assertTrue(source.contains("import java.util.ArrayList;"))
      assertTrue(source.contains("ArrayList<Particle> particles = new ArrayList<Particle>();"))
   }

   @Test
   fun normalizesNonBreakingSpacesInCodeWithoutChangingStrings() {
      val snapshotRoot = temporaryFolder.newFolder("unicode-space-snapshot")
      val nonBreakingSpace = '\u00A0'
      val sketch = File(snapshotRoot, "UnicodeSpaceSketch.pde").apply {
         writeText(
            "String label = \"left${nonBreakingSpace}right\";\n" +
               "void setup() {\n" +
               "${nonBreakingSpace}${nonBreakingSpace}background(250);\n" +
               "}\n",
            Charsets.UTF_8
         )
      }

      val source = ProcessingSourceGenerator(temporaryFolder.newFolder("unicode-space-cache"))
         .generate(snapshot(snapshotRoot, sketch))
         .generatedSource
         .readText(Charsets.UTF_8)

      assertTrue(source.contains("  background(250);"))
      assertTrue(source.contains("\"left${nonBreakingSpace}right\""))
      assertFalse(source.contains("${nonBreakingSpace}background"))
   }

   private fun snapshot(root: File, sketch: File): SketchSnapshot {
      return SketchSnapshot(
         root = root,
         pdeFiles = listOf(sketch),
         dataDir = null,
         assetsDir = null,
         codeDir = null,
         androidDir = null,
         settings = SketchSettings(
            displayName = "Cast Sketch",
            packageName = "processing.test.castsketch",
            className = "CastSketch",
            versionCode = 1,
            versionName = "1.0",
            orientation = "none",
            minSdk = 26,
            targetSdk = 35,
            permissions = emptyList(),
            fullscreen = false
         )
      )
   }
}
