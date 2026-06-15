package com.apde2.preview

import android.content.Context
import java.util.Locale

class SketchSettingsReader(private val context: Context) {
   fun read(projectName: String): SketchSettings {
      val safeName = packageSafeName(projectName)
      val prefs = context.getSharedPreferences("apde2_sketch_properties", Context.MODE_PRIVATE)
      val displayName = prefs.stringValue("display_name_$safeName", projectName)
      val packageName = prefs.stringValue("package_name_$safeName", "processing.test.${safeName.lowercase(Locale.US)}")
      return SketchSettings(
         displayName = displayName,
         packageName = packageName,
         className = classSafeName(projectName),
         versionCode = prefs.intValue("version_code_$safeName", 1),
         versionName = prefs.stringValue("version_name_$safeName", "1.0"),
         orientation = prefs.getString("orientation_$safeName", "none") ?: "none",
         minSdk = prefs.intValue("min_sdk_$safeName", 26),
         targetSdk = prefs.intValue("target_sdk_$safeName", 35),
         permissions = splitPermissions(prefs.getString("permissions_$safeName", "") ?: ""),
         fullscreen = false
      )
   }

   private fun android.content.SharedPreferences.stringValue(key: String, fallback: String): String {
      return getString(key, fallback)?.trim()?.takeIf { it.isNotEmpty() } ?: fallback
   }

   private fun android.content.SharedPreferences.intValue(key: String, fallback: Int): Int {
      return getString(key, fallback.toString())?.trim()?.toIntOrNull()?.takeIf { it > 0 } ?: fallback
   }

   private fun splitPermissions(value: String): List<String> {
      return value.split(',', '\n')
         .map { it.trim() }
         .filter { it.isNotEmpty() }
   }

   private fun packageSafeName(value: String): String {
      val normalized = value.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "")
      return normalized.ifEmpty { "sketch" }
   }

   private fun classSafeName(value: String): String {
      val words = value.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotBlank() }
      val name = words.joinToString("") { it.replaceFirstChar { char -> char.uppercase(Locale.US) } }
      val candidate = name.ifBlank { "Sketch" }
      return if (candidate.first().isDigit()) "Sketch$candidate" else candidate
   }
}
