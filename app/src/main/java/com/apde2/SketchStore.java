package com.apde2;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class SketchStore {
   private static final String PREFS = "apde2_sketches";
   private static final String FILES = "files";
   private static final String ACTIVE_INDEX = "active_index";
   private static final String MIGRATED_TO_FILES = "migrated_to_files";
   private static final String CURRENT_PROJECT_DIR = "current_project_dir";
   private static final String SKETCHBOOK_DIR = "Sketchbook";
   private static final String LEGACY_PARENT_DIR = "sketchbook";
   private static final String LEGACY_SKETCH_DIR = "Sketch";
   private static final String DEFAULT_SKETCH_FILE = "sketch.pde";

   private final SharedPreferences prefs;
   private final File preferredSketchbookDir;
   private final File fallbackSketchbookDir;
   private final File legacySketchDir;
   private File sketchbookDir;
   private File sketchDir;

   SketchStore(Context context) {
      prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
      preferredSketchbookDir = new File(Environment.getExternalStorageDirectory(), SKETCHBOOK_DIR);
      fallbackSketchbookDir = new File(context.getFilesDir(), SKETCHBOOK_DIR);
      legacySketchDir = new File(new File(context.getFilesDir(), LEGACY_PARENT_DIR), LEGACY_SKETCH_DIR);
   }

   List<SketchFile> loadFiles() {
      ensureMigrated();
      if (sketchDir == null) {
         return new ArrayList<>();
      }
      return loadFromDisk();
   }

   int loadActiveIndex() {
      return prefs.getInt(ACTIVE_INDEX, 0);
   }

   void save(List<SketchFile> files, int activeIndex) {
      ensureSketchDir();
      if (sketchDir == null) {
         prefs.edit()
            .putInt(ACTIVE_INDEX, Math.max(-1, activeIndex))
            .putBoolean(MIGRATED_TO_FILES, true)
            .apply();
         return;
      }
      clearPdeFiles();
      for (SketchFile file : files) {
         if (file.documentUri != null) {
            continue;
         }
         writeFile(new File(sketchDir, sanitizeFileName(file.name)), file.code);
      }
      prefs.edit()
         .putInt(ACTIVE_INDEX, Math.max(-1, activeIndex))
         .putBoolean(MIGRATED_TO_FILES, true)
         .apply();
   }

   File sketchDir() {
      ensureSketchDir();
      return sketchDir;
   }

   File sketchbookDir() {
      ensureSketchDir();
      return sketchbookDir;
   }

   File currentProjectDir() {
      ensureSketchDir();
      return sketchDir;
   }

   String currentProjectName() {
      ensureSketchDir();
      return sketchDir == null ? "" : sketchDir.getName();
   }

   void refreshStorageState() {
      sketchbookDir = null;
      sketchDir = null;
   }

   void switchProject(String projectName) {
      ensureSketchDir();
      String sanitized = sanitizeProjectName(projectName);
      File nextDir = new File(sketchbookDir, sanitized);
      if (!nextDir.exists()) {
         nextDir.mkdirs();
      }
      sketchDir = nextDir;
      rememberCurrentProject(sanitized);
      ensureDefaultSketch();
   }

   File createNewSketchProject() {
      ensureSketchDir();
      String projectName = nextDefaultProjectName();
      File projectDir = new File(sketchbookDir, projectName);
      projectDir.mkdirs();
      writeFile(new File(projectDir, DEFAULT_SKETCH_FILE), "");
      return projectDir;
   }

   void deleteProject(File projectDir) {
      if (projectDir == null) {
         return;
      }
      String deletedName = projectDir.getName();
      deleteRecursively(projectDir);
      if (sketchDir != null && deletedName.equals(sketchDir.getName())) {
         File[] existingProjects = sketchbookDir.listFiles(File::isDirectory);
         if (existingProjects != null && existingProjects.length > 0) {
            java.util.Arrays.sort(existingProjects, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            sketchDir = existingProjects[0];
            rememberCurrentProject(sketchDir.getName());
         } else {
            sketchDir = null;
            prefs.edit().remove(CURRENT_PROJECT_DIR).apply();
         }
      }
   }

   private void ensureMigrated() {
      ensureSketchDir();
      if (prefs.getBoolean(MIGRATED_TO_FILES, false)) {
         return;
      }

      List<SketchFile> legacy = loadLegacyPrefs();
      if (!legacy.isEmpty()) {
         save(legacy, Math.min(loadActiveIndex(), legacy.size() - 1));
         return;
      }
      if (!hasPdeFiles()) {
         ensureDefaultSketch();
      }
      prefs.edit().putBoolean(MIGRATED_TO_FILES, true).apply();
   }

   private List<SketchFile> loadFromDisk() {
      List<SketchFile> files = new ArrayList<>();
      ensureSketchDir();
      if (sketchDir == null) {
         return files;
      }
      File[] pdeFiles = sketchDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".pde"));
      if (pdeFiles == null) {
         return files;
      }
      java.util.Arrays.sort(pdeFiles, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
      for (File file : pdeFiles) {
         files.add(new SketchFile(file.getName(), readFile(file), null, sketchDir.getAbsolutePath()));
      }
      return files;
   }

   private List<SketchFile> loadLegacyPrefs() {
      List<SketchFile> files = new ArrayList<>();
      String raw = prefs.getString(FILES, "");
      if (!raw.isEmpty()) {
         try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
               JSONObject file = array.getJSONObject(i);
               files.add(new SketchFile(file.getString("name"), file.getString("code")));
            }
         } catch (JSONException ignored) {
            files.clear();
         }
      }
      return files;
   }

   private boolean hasPdeFiles() {
      if (sketchDir == null) {
         return false;
      }
      File[] files = sketchDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".pde"));
      return files != null && files.length > 0;
   }

   private void clearPdeFiles() {
      if (sketchDir == null) {
         return;
      }
      File[] files = sketchDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".pde"));
      if (files == null) {
         return;
      }
      for (File file : files) {
         file.delete();
      }
   }

   private void ensureSketchDir() {
      sketchbookDir = resolveSketchbookRoot();
      if (sketchbookDir == null) {
         sketchDir = null;
         return;
      }
      if (!sketchbookDir.exists()) {
         if (!sketchbookDir.mkdirs() && !sketchbookDir.exists()) {
            sketchDir = null;
            return;
         }
      }
      if (sketchDir == null || !sketchDir.exists() || !sketchbookDir.equals(sketchDir.getParentFile())) {
         sketchDir = resolveCurrentSketchDir();
      }
      if (sketchDir != null && !sketchDir.exists()) {
         sketchDir.mkdirs();
      }
      if (sketchDir == null) {
         return;
      }
      migrateLegacySketchDir();
      migrateRootPdeFiles();
   }

   private File resolveSketchbookRoot() {
      if (canUsePreferredExternalRoot()
         && preferredSketchbookDir != null
         && preferredSketchbookDir.exists()
         && preferredSketchbookDir.isDirectory()) {
         return preferredSketchbookDir;
      }
      if (canUsePreferredExternalRoot()
         && preferredSketchbookDir != null
         && (preferredSketchbookDir.exists() || preferredSketchbookDir.mkdirs())) {
         return preferredSketchbookDir;
      }
      if (fallbackSketchbookDir.exists() || fallbackSketchbookDir.mkdirs()) {
         return fallbackSketchbookDir;
      }
      return null;
   }

   private boolean canUsePreferredExternalRoot() {
      return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
   }

   private void migrateLegacySketchDir() {
      if (!legacySketchDir.exists() || !legacySketchDir.isDirectory()) {
         return;
      }
      File[] legacyFiles = legacySketchDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".pde"));
      if (legacyFiles == null || legacyFiles.length == 0) {
         return;
      }
      if (hasPdeFiles()) {
         return;
      }
      for (File legacyFile : legacyFiles) {
         writeFile(new File(sketchDir, legacyFile.getName()), readFile(legacyFile));
      }
   }

   private void migrateRootPdeFiles() {
      File[] rootPdeFiles = sketchbookDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".pde"));
      if (rootPdeFiles == null || rootPdeFiles.length == 0) {
         return;
      }
      for (File rootFile : rootPdeFiles) {
         String targetName = hasPdeFiles() ? rootFile.getName() : DEFAULT_SKETCH_FILE;
         writeFile(new File(sketchDir, targetName), readFile(rootFile));
         rootFile.delete();
      }
   }

   private void ensureDefaultSketch() {
      if (hasPdeFiles()) {
         return;
      }
      writeFile(new File(sketchDir, DEFAULT_SKETCH_FILE), "");
   }

   private File resolveCurrentSketchDir() {
      if (sketchbookDir == null) {
         return null;
      }
      String stored = prefs.getString(CURRENT_PROJECT_DIR, "");
      if (stored != null && !stored.trim().isEmpty()) {
         File storedDir = new File(sketchbookDir, stored);
         if (storedDir.exists() || storedDir.mkdirs()) {
            return storedDir;
         }
      }

      File[] existingProjects = sketchbookDir.listFiles(File::isDirectory);
      if (existingProjects != null && existingProjects.length > 0) {
         java.util.Arrays.sort(existingProjects, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
         rememberCurrentProject(existingProjects[0].getName());
         return existingProjects[0];
      }

      if (prefs.getBoolean(MIGRATED_TO_FILES, false)) {
         return null;
      }

      String projectName = nextDefaultProjectName();
      File projectDir = new File(sketchbookDir, projectName);
      projectDir.mkdirs();
      rememberCurrentProject(projectName);
      return projectDir;
   }

   private void rememberCurrentProject(String name) {
      prefs.edit().putString(CURRENT_PROJECT_DIR, name).apply();
   }

   private String sanitizeProjectName(String value) {
      String sanitized = value == null ? "" : value.replace("/", "").replace("\\", "").trim();
      return sanitized.isEmpty() ? nextDefaultProjectName() : sanitized;
   }

   private String nextDefaultProjectName() {
      String datePart = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
      int index = 0;
      while (true) {
         String name = "sketch_" + datePart + alphaSuffix(index);
         if (!new File(sketchbookDir, name).exists()) {
            return name;
         }
         index++;
      }
   }

   private String alphaSuffix(int index) {
      StringBuilder builder = new StringBuilder();
      int value = Math.max(0, index);
      do {
         builder.insert(0, (char) ('a' + (value % 26)));
         value = value / 26 - 1;
      } while (value >= 0);
      return builder.toString();
   }

   private String sanitizeFileName(String name) {
      String sanitized = name == null ? "" : name.replace("/", "").replace("\\", "").trim();
      if (sanitized.isEmpty()) {
         sanitized = "Sketch.pde";
      }
      if (!sanitized.toLowerCase().endsWith(".pde")) {
         sanitized += ".pde";
      }
      return sanitized;
   }

   private String readFile(File file) {
      try (FileInputStream input = new FileInputStream(file)) {
         byte[] bytes = new byte[(int) file.length()];
         int read = input.read(bytes);
         return new String(bytes, 0, Math.max(0, read), StandardCharsets.UTF_8);
      } catch (IOException exception) {
         return "";
      }
   }

   private void writeFile(File file, String content) {
      try (FileOutputStream output = new FileOutputStream(file)) {
         output.write((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
      } catch (IOException ignored) {
      }
   }

   private void deleteRecursively(File file) {
      if (file == null || !file.exists()) {
         return;
      }
      if (file.isDirectory()) {
         File[] children = file.listFiles();
         if (children != null) {
            for (File child : children) {
               deleteRecursively(child);
            }
         }
      }
      file.delete();
   }

}
