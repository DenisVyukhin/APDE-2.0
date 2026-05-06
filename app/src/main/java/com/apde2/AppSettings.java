package com.apde2;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

final class AppSettings {
   static final String LANGUAGE_EN = "en";
   static final String LANGUAGE_RU = "ru";
   static final String THEME_DARK = "dark";
   static final String THEME_LIGHT = "light";

   private static final String PREFS = "apde2_settings";
   private static final String KEY_LANGUAGE = "language";
   private static final String KEY_HAPTIC = "haptic";
   private static final String KEY_EDITOR_FONT = "editor_font";
   private static final String KEY_TAB_SIZE = "tab_size";
   private static final String KEY_AUTO_CLOSE = "auto_close_pairs";
   private static final String KEY_CONSOLE_FONT = "console_font";
   private static final String KEY_THEME = "theme";

   private final SharedPreferences prefs;
   private final String defaultLanguage;

   AppSettings(Context context) {
      prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
      String deviceLanguage = Locale.getDefault().getLanguage();
      defaultLanguage = LANGUAGE_RU.equalsIgnoreCase(deviceLanguage) ? LANGUAGE_RU : LANGUAGE_EN;
   }

   String language() {
      return prefs.getString(KEY_LANGUAGE, defaultLanguage);
   }

   void setLanguage(String language) {
      prefs.edit().putString(KEY_LANGUAGE, language).apply();
   }

   boolean hapticEnabled() {
      return prefs.getBoolean(KEY_HAPTIC, true);
   }

   void setHapticEnabled(boolean enabled) {
      prefs.edit().putBoolean(KEY_HAPTIC, enabled).apply();
   }

   int editorFontSizeSp() {
      return prefs.getInt(KEY_EDITOR_FONT, 15);
   }

   void setEditorFontSizeSp(int value) {
      prefs.edit().putInt(KEY_EDITOR_FONT, clamp(value, 10, 28)).apply();
   }

   int tabSize() {
      return prefs.getInt(KEY_TAB_SIZE, 3);
   }

   void setTabSize(int value) {
      prefs.edit().putInt(KEY_TAB_SIZE, clamp(value, 2, 8)).apply();
   }

   boolean autoClosePairs() {
      return prefs.getBoolean(KEY_AUTO_CLOSE, true);
   }

   void setAutoClosePairs(boolean enabled) {
      prefs.edit().putBoolean(KEY_AUTO_CLOSE, enabled).apply();
   }

   int consoleFontSizeSp() {
      return prefs.getInt(KEY_CONSOLE_FONT, 13);
   }

   void setConsoleFontSizeSp(int value) {
      prefs.edit().putInt(KEY_CONSOLE_FONT, clamp(value, 10, 24)).apply();
   }

   String themeMode() {
      return prefs.getString(KEY_THEME, THEME_DARK);
   }

   void setThemeMode(String mode) {
      prefs.edit().putString(KEY_THEME, mode).apply();
   }

   private int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }
}
