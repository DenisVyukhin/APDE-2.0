package com.apde2;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
   private static final String CURRENT_PROJECT_PATH = "current_project_path";
   private static final String RECENT_PROJECTS = "recent_projects";
   private static final String SKETCHBOOK_DIR = "Sketchbook";
   private static final String SKETCHES_DIR = "Sketches";
   private static final String EXAMPLES_DIR = "Examples";
   private static final String EXAMPLES_ASSET_DIR = "Examples";
   private static final String LIBRARY_EXAMPLES_DIR = "Library Examples";
   private static final String RECENT_DIR = "Recent";
   private static final String DATA_DIR = "data";
   private static final String LEGACY_PARENT_DIR = "sketchbook";
   private static final String LEGACY_SKETCH_DIR = "Sketch";
   private static final String DEFAULT_SKETCH_FILE = "sketch.pde";
   private static final String SKETCH_PROPERTIES_FILE = "sketch.properties";
   private static final int MAX_RECENT_PROJECTS = 12;

   private final Context context;
   private final SharedPreferences prefs;
   private final File preferredSketchbookDir;
   private final File fallbackSketchbookDir;
   private final File legacySketchDir;
   private File sketchbookDir;
   private File sketchesDir;
   private File examplesDir;
   private File libraryExamplesDir;
   private File recentDir;
   private File sketchDir;
   private String bundledExamplesRootPath;

   SketchStore(Context context) {
      this.context = context.getApplicationContext();
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
      addRecentProject(sketchDir);
      return loadFromDisk();
   }

   int loadActiveIndex() {
      return prefs.getInt(ACTIVE_INDEX, 0);
   }

   void save(List<SketchFile> files, int activeIndex) {
      ensureSketchDir();
      if (sketchDir == null || isCurrentProjectReadOnly()) {
         rememberActiveIndex(activeIndex);
         return;
      }
      for (SketchFile file : files) {
         if (file == null || file.documentUri != null) {
            continue;
         }
         File target = targetFileFor(file);
         if (target != null) {
            if (writeFile(target, file.code)) {
               file.name = target.getName();
               file.localProjectPath = sketchDir.getAbsolutePath();
               file.localFilePath = target.getAbsolutePath();
            }
         }
      }
      prefs.edit()
         .putInt(ACTIVE_INDEX, Math.max(-1, activeIndex))
         .putBoolean(MIGRATED_TO_FILES, true)
         .apply();
   }

   boolean saveFile(SketchFile file, int activeIndex) {
      ensureSketchDir();
      if (sketchDir == null || isCurrentProjectReadOnly()) {
         rememberActiveIndex(activeIndex);
         return false;
      }
      boolean saved = false;
      if (file != null && file.documentUri == null) {
         File target = targetFileFor(file);
         if (target != null && writeFile(target, file.code)) {
            file.name = target.getName();
            file.localProjectPath = sketchDir.getAbsolutePath();
            file.localFilePath = target.getAbsolutePath();
            saved = true;
         }
      }
      rememberActiveIndex(activeIndex);
      return saved;
   }

   File sketchDir() {
      ensureSketchDir();
      return sketchDir;
   }

   File sketchbookDir() {
      ensureSketchDir();
      return sketchbookDir;
   }

   File sketchesDir() {
      ensureSketchDir();
      return sketchesDir;
   }

   File examplesDir() {
      ensureSketchDir();
      return examplesDir;
   }

   File libraryExamplesDir() {
      ensureSketchDir();
      return libraryExamplesDir;
   }

   File recentDir() {
      ensureSketchDir();
      return recentDir;
   }

   File currentProjectDir() {
      ensureSketchDir();
      return sketchDir;
   }

   boolean isDraftProject() {
      return false;
   }

   boolean isSketchbookProject() {
      ensureSketchDir();
      return isDirectSketchesChild(sketchDir);
   }

   boolean isSketchProject(File projectDir) {
      ensureSketchDir();
      return (isDirectSketchesChild(projectDir) || isDirectExamplesChild(projectDir)) && isSketchProjectDir(projectDir);
   }

   boolean isExamplesProject(File projectDir) {
      ensureSketchDir();
      return isDirectExamplesChild(projectDir) && isSketchProjectDir(projectDir);
   }

   boolean isCurrentProjectReadOnly() {
      ensureSketchDir();
      return isExamplesProject(sketchDir);
   }

   List<File> sketchProjects() {
      ensureSketchDir();
      List<File> projects = new ArrayList<>();
      if (sketchesDir == null) {
         return projects;
      }
      File[] children = sketchesDir.listFiles(File::isDirectory);
      if (children == null) {
         return projects;
      }
      java.util.Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
      for (File child : children) {
         if (isSketchProjectDir(child)) {
            projects.add(child);
         }
      }
      return projects;
   }

   String currentProjectName() {
      ensureSketchDir();
      return sketchDir == null ? "" : sketchDir.getName();
   }

   void refreshStorageState() {
      sketchbookDir = null;
      sketchesDir = null;
      examplesDir = null;
      libraryExamplesDir = null;
      recentDir = null;
      sketchDir = null;
      bundledExamplesRootPath = null;
   }

   void switchProject(String projectName) {
      ensureSketchDir();
      String sanitized = sanitizeProjectName(projectName);
      switchProject(new File(sketchesDir, sanitized));
   }

   void switchProject(File projectDir) {
      ensureSketchbookRoot();
      if (projectDir == null) {
         return;
      }
      File nextDir = projectDir;
      if (!nextDir.exists()) {
         nextDir.mkdirs();
      }
      sketchDir = nextDir;
      rememberCurrentProject(nextDir);
      if (!isExamplesProject(nextDir)) {
         ensureProjectDefaults(nextDir);
      }
   }

   File createNewSketchProject() {
      ensureSketchbookRoot();
      ensureStandardDirs();
      String projectName = nextDefaultProjectName();
      File projectDir = uniqueProjectDir(sketchesDir, projectName);
      if (!projectDir.mkdirs() && !projectDir.isDirectory()) {
         return null;
      }
      if (!ensureProjectDefaults(projectDir)) {
         deleteRecursively(projectDir);
         return null;
      }
      sketchDir = projectDir;
      rememberCurrentProject(projectDir);
      return projectDir;
   }

   File saveCurrentProjectToSketchbook() {
      ensureSketchbookRoot();
      ensureSketchDir();
      if (sketchDir == null) {
         return null;
      }
      if (isDirectSketchesChild(sketchDir)) {
         rememberCurrentProject(sketchDir);
         return sketchDir;
      }
      File targetDir = uniqueProjectDir(sketchesDir, sanitizeProjectName(sketchDir.getName()));
      if (!copyDirectory(sketchDir, targetDir)) {
         deleteRecursively(targetDir);
         return null;
      }
      sketchDir = targetDir;
      rememberCurrentProject(targetDir);
      return targetDir;
   }

   List<File> recentProjects() {
      ensureSketchbookRoot();
      List<File> projects = new ArrayList<>();
      String raw = prefs.getString(RECENT_PROJECTS, "[]");
      try {
         JSONArray array = new JSONArray(raw);
         for (int i = 0; i < array.length(); i++) {
            File project = new File(array.getString(i));
            if (project.isDirectory() && isSketchProject(project) && !containsFile(projects, project)) {
               projects.add(project);
            }
         }
      } catch (JSONException ignored) {
         projects.clear();
      }
      return projects;
   }

   boolean deleteSketchFile(SketchFile sketchFile) {
      ensureSketchDir();
      if (isCurrentProjectReadOnly()) {
         return false;
      }
      File target = existingFileFor(sketchFile);
      if (target == null || !target.exists()) {
         return true;
      }
      return target.isFile() && target.delete();
   }

   boolean renameSketchFile(SketchFile sketchFile, String newName) {
      ensureSketchDir();
      if (sketchDir == null || sketchFile == null || isCurrentProjectReadOnly()) {
         return false;
      }
      File source = existingFileFor(sketchFile);
      if (source == null) {
         return false;
      }
      File target = new File(sketchDir, sanitizeFileName(newName));
      if (!isInsideSketchDir(target) || target.exists()) {
         return false;
      }
      if (source.exists() && !source.renameTo(target)) {
         return false;
      }
      sketchFile.name = target.getName();
      sketchFile.localProjectPath = sketchDir.getAbsolutePath();
      sketchFile.localFilePath = target.getAbsolutePath();
      return true;
   }

   void deleteProject(File projectDir) {
      ensureSketchDir();
      if (projectDir == null) {
         return;
      }
      if (!isManagedProject(projectDir)) {
         return;
      }
      deleteRecursively(projectDir);
      removeRecentProject(projectDir);
      if (sketchDir != null && sameFile(projectDir, sketchDir)) {
         File[] existingProjects = sketchesDir == null ? null : sketchesDir.listFiles(File::isDirectory);
         if (existingProjects != null && existingProjects.length > 0) {
            java.util.Arrays.sort(existingProjects, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            sketchDir = null;
            for (File existingProject : existingProjects) {
               if (isSketchProjectDir(existingProject)) {
                  sketchDir = existingProject;
                  rememberCurrentProject(sketchDir);
                  break;
               }
            }
            if (sketchDir == null) {
               prefs.edit().remove(CURRENT_PROJECT_DIR).remove(CURRENT_PROJECT_PATH).apply();
            }
         } else {
            sketchDir = null;
            prefs.edit().remove(CURRENT_PROJECT_DIR).remove(CURRENT_PROJECT_PATH).apply();
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
         ensureProjectDefaults(sketchDir);
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
      java.util.Arrays.sort(pdeFiles, (left, right) -> {
         boolean leftMain = DEFAULT_SKETCH_FILE.equalsIgnoreCase(left.getName());
         boolean rightMain = DEFAULT_SKETCH_FILE.equalsIgnoreCase(right.getName());
         if (leftMain != rightMain) {
            return leftMain ? -1 : 1;
         }
         return left.getName().compareToIgnoreCase(right.getName());
      });
      for (File file : pdeFiles) {
         files.add(new SketchFile(file.getName(), readFile(file), null, sketchDir.getAbsolutePath(), file.getAbsolutePath()));
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

   private void ensureSketchDir() {
      ensureSketchbookRoot();
      if (sketchbookDir == null) {
         sketchDir = null;
         return;
      }
      ensureStandardDirs();
      migrateRootProjectDirs();
      if (sketchDir == null || !sketchDir.exists()) {
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

   private void ensureSketchbookRoot() {
      sketchbookDir = resolveSketchbookRoot();
      if (sketchbookDir != null && !sketchbookDir.exists()) {
         if (!sketchbookDir.mkdirs() && !sketchbookDir.exists()) {
            sketchbookDir = null;
         }
      }
      ensureStandardDirs();
   }

   private void ensureStandardDirs() {
      if (sketchbookDir == null) {
         sketchesDir = null;
         examplesDir = null;
         libraryExamplesDir = null;
         recentDir = null;
         return;
      }
      sketchesDir = new File(sketchbookDir, SKETCHES_DIR);
      examplesDir = new File(sketchbookDir, EXAMPLES_DIR);
      libraryExamplesDir = new File(sketchbookDir, LIBRARY_EXAMPLES_DIR);
      recentDir = new File(sketchbookDir, RECENT_DIR);
      ensureDirectory(sketchesDir);
      ensureDirectory(examplesDir);
      ensureDirectory(libraryExamplesDir);
      ensureDirectory(recentDir);
      ensureBundledExamples();
   }

   private void ensureDirectory(File dir) {
      if (dir != null && !dir.exists()) {
         dir.mkdirs();
      }
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

   private void migrateRootProjectDirs() {
      if (sketchbookDir == null || sketchesDir == null) {
         return;
      }
      File[] children = sketchbookDir.listFiles(File::isDirectory);
      if (children == null) {
         return;
      }
      for (File child : children) {
         if (isStandardRootDir(child) || !isSketchProjectDir(child)) {
            continue;
         }
         File target = uniqueProjectDir(sketchesDir, child.getName());
         if (!child.renameTo(target)) {
            if (copyDirectory(child, target)) {
               deleteRecursively(child);
            } else {
               deleteRecursively(target);
            }
         }
      }
   }

   private void ensureDefaultSketch() {
      if (hasPdeFiles()) {
         return;
      }
      ensureProjectDefaults(sketchDir);
   }

   private boolean ensureProjectDefaults(File projectDir) {
      if (projectDir == null) {
         return false;
      }
      if (!projectDir.exists() && !projectDir.mkdirs()) {
         return false;
      }
      File sketchFile = new File(projectDir, DEFAULT_SKETCH_FILE);
      if (!sketchFile.exists() && !writeFile(sketchFile, "")) {
         return false;
      }
      File properties = new File(projectDir, SKETCH_PROPERTIES_FILE);
      if (!properties.exists() && !writeFile(properties, "")) {
         return false;
      }
      File dataDir = new File(projectDir, DATA_DIR);
      return dataDir.exists() || dataDir.mkdirs();
   }

   private File resolveCurrentSketchDir() {
      if (sketchbookDir == null) {
         return null;
      }
      String storedPath = prefs.getString(CURRENT_PROJECT_PATH, "");
      if (storedPath != null && !storedPath.trim().isEmpty()) {
         File storedDir = new File(storedPath);
         if (!storedDir.exists() && isLegacyRootProjectPath(storedDir)) {
            storedDir = new File(sketchesDir, storedDir.getName());
         }
         if (storedDir.exists()) {
            return storedDir;
         }
      }

      String stored = prefs.getString(CURRENT_PROJECT_DIR, "");
      if (stored != null && !stored.trim().isEmpty()) {
         File storedDir = new File(sketchesDir, stored);
         if (storedDir.exists()) {
            return storedDir;
         }
      }

      File[] existingProjects = sketchesDir.listFiles(File::isDirectory);
      if (existingProjects != null && existingProjects.length > 0) {
         java.util.Arrays.sort(existingProjects, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
         for (File existingProject : existingProjects) {
            if (isSketchProjectDir(existingProject)) {
               rememberCurrentProject(existingProject);
               return existingProject;
            }
         }
      }

      if (prefs.getBoolean(MIGRATED_TO_FILES, false)) {
         return null;
      }

      String projectName = nextDefaultProjectName();
      File projectDir = uniqueProjectDir(sketchesDir, projectName);
      projectDir.mkdirs();
      rememberCurrentProject(projectDir);
      return projectDir;
   }

   private void rememberCurrentProject(String name) {
      rememberCurrentProject(new File(sketchesDir, name));
   }

   private void rememberCurrentProject(File projectDir) {
      if (projectDir == null) {
         return;
      }
      SharedPreferences.Editor editor = prefs.edit()
         .putString(CURRENT_PROJECT_PATH, projectDir.getAbsolutePath());
      if (isDirectSketchesChild(projectDir)) {
         editor.putString(CURRENT_PROJECT_DIR, projectDir.getName());
      }
      editor.apply();
      addRecentProject(projectDir);
   }

   private void rememberActiveIndex(int activeIndex) {
      prefs.edit()
         .putInt(ACTIVE_INDEX, Math.max(-1, activeIndex))
         .putBoolean(MIGRATED_TO_FILES, true)
         .apply();
   }

   private String sanitizeProjectName(String value) {
      String sanitized = value == null ? "" : value.replace("/", "").replace("\\", "").trim().replaceAll("\\s+", "_");
      return sanitized.isEmpty() ? nextDefaultProjectName() : sanitized;
   }

   private String nextDefaultProjectName() {
      String datePart = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
      int index = 0;
      while (true) {
         String name = "sketch_" + datePart + alphaSuffix(index);
         if (!new File(sketchesDir, name).exists()) {
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

   private File targetFileFor(SketchFile file) {
      if (sketchDir == null || file == null) {
         return null;
      }
      File target = file.localFilePath == null || file.localFilePath.trim().isEmpty()
         ? new File(sketchDir, sanitizeFileName(file.name))
         : new File(file.localFilePath);
      if (!isInsideSketchDir(target)) {
         target = new File(sketchDir, sanitizeFileName(file.name));
      }
      return isInsideSketchDir(target) ? target : null;
   }

   private File existingFileFor(SketchFile file) {
      if (sketchDir == null || file == null) {
         return null;
      }
      File target = file.localFilePath == null || file.localFilePath.trim().isEmpty()
         ? new File(sketchDir, sanitizeFileName(file.name))
         : new File(file.localFilePath);
      if (!isInsideSketchDir(target)) {
         target = new File(sketchDir, sanitizeFileName(file.name));
      }
      return isInsideSketchDir(target) ? target : null;
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

   private boolean writeFile(File file, String content) {
      if (file == null) {
         return false;
      }
      File parent = file.getParentFile();
      if (parent != null && !parent.exists() && !parent.mkdirs()) {
         return false;
      }
      try (FileOutputStream output = new FileOutputStream(file, false)) {
         output.write((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
         output.getFD().sync();
         return true;
      } catch (IOException ignored) {
         return false;
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

   private boolean isInsideSketchDir(File file) {
      if (file == null || sketchDir == null) {
         return false;
      }
      try {
         String root = sketchDir.getCanonicalPath();
         String child = file.getCanonicalPath();
         return child.equals(root) || child.startsWith(root + File.separator);
      } catch (IOException exception) {
         String root = sketchDir.getAbsolutePath();
         String child = file.getAbsolutePath();
         return child.equals(root) || child.startsWith(root + File.separator);
      }
   }

   private boolean isDirectSketchesChild(File file) {
      if (file == null || sketchesDir == null) {
         return false;
      }
      File parent = file.getParentFile();
      return parent != null && sameFile(parent, sketchesDir);
   }

   private boolean isDirectExamplesChild(File file) {
      if (file == null || examplesDir == null) {
         return false;
      }
      File parent = file.getParentFile();
      return parent != null && sameFile(parent, examplesDir);
   }

   private boolean isManagedProject(File file) {
      return isDirectSketchesChild(file);
   }

   private void ensureBundledExamples() {
      if (context == null || examplesDir == null) {
         return;
      }
      String rootPath = examplesDir.getAbsolutePath();
      if (rootPath.equals(bundledExamplesRootPath)) {
         return;
      }
      if (copyAssetTreeIfMissing(context.getAssets(), EXAMPLES_ASSET_DIR, examplesDir)) {
         bundledExamplesRootPath = rootPath;
      }
   }

   private boolean copyAssetTreeIfMissing(AssetManager assets, String assetPath, File target) {
      String[] children;
      try {
         children = assets.list(assetPath);
      } catch (IOException exception) {
         return false;
      }
      if (children != null && children.length > 0) {
         if (!target.exists() && !target.mkdirs()) {
            return false;
         }
         for (String child : children) {
            if (!copyAssetTreeIfMissing(assets, assetPath + "/" + child, new File(target, child))) {
               return false;
            }
         }
         return true;
      }
      if (!looksLikeAssetFile(assetPath)) {
         return target.exists() || target.mkdirs();
      }
      if (target.exists()) {
         return true;
      }
      File parent = target.getParentFile();
      if (parent != null && !parent.exists() && !parent.mkdirs()) {
         return false;
      }
      try (InputStream input = assets.open(assetPath);
           FileOutputStream output = new FileOutputStream(target)) {
         byte[] buffer = new byte[8192];
         int read;
         while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
         }
         output.getFD().sync();
         return true;
      } catch (IOException exception) {
         return false;
      }
   }

   private boolean looksLikeAssetFile(String assetPath) {
      int slash = assetPath.lastIndexOf('/');
      String name = slash >= 0 ? assetPath.substring(slash + 1) : assetPath;
      return name.indexOf('.') > 0;
   }

   private boolean isSketchProjectDir(File dir) {
      return dir != null && dir.isDirectory() && hasPdeFiles(dir);
   }

   private boolean hasPdeFiles(File dir) {
      if (dir == null) {
         return false;
      }
      File[] files = dir.listFiles((parent, name) -> name.toLowerCase().endsWith(".pde"));
      return files != null && files.length > 0;
   }

   private boolean isStandardRootDir(File dir) {
      if (dir == null) {
         return false;
      }
      return sameFile(dir, sketchesDir)
         || sameFile(dir, examplesDir)
         || sameFile(dir, libraryExamplesDir)
         || sameFile(dir, recentDir);
   }

   private boolean isLegacyRootProjectPath(File dir) {
      if (dir == null || sketchbookDir == null) {
         return false;
      }
      File parent = dir.getParentFile();
      return parent != null && sameFile(parent, sketchbookDir) && !isStandardRootDir(dir);
   }

   private File uniqueProjectDir(File root, String requestedName) {
      String baseName = sanitizeProjectName(requestedName);
      File candidate = new File(root, baseName);
      int copy = 2;
      while (candidate.exists()) {
         candidate = new File(root, baseName + "_" + copy);
         copy++;
      }
      return candidate;
   }

   private boolean copyDirectory(File source, File target) {
      if (source == null || target == null || !source.exists()) {
         return false;
      }
      if (source.isDirectory()) {
         if (!target.exists() && !target.mkdirs()) {
            return false;
         }
         File[] children = source.listFiles();
         if (children == null) {
            return true;
         }
         for (File child : children) {
            if (!copyDirectory(child, new File(target, child.getName()))) {
               return false;
            }
         }
         return true;
      }
      try (FileInputStream input = new FileInputStream(source);
           FileOutputStream output = new FileOutputStream(target)) {
         byte[] buffer = new byte[8192];
         int read;
         while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
         }
         output.getFD().sync();
         return true;
      } catch (IOException exception) {
         return false;
      }
   }

   private void addRecentProject(File projectDir) {
      if (projectDir == null) {
         return;
      }
      List<File> projects = recentProjectsWithoutEnsuring();
      projects.removeIf(existing -> sameFile(existing, projectDir));
      projects.add(0, projectDir);
      while (projects.size() > MAX_RECENT_PROJECTS) {
         projects.remove(projects.size() - 1);
      }
      writeRecentProjects(projects);
   }

   private void removeRecentProject(File projectDir) {
      List<File> projects = recentProjectsWithoutEnsuring();
      projects.removeIf(existing -> sameFile(existing, projectDir));
      writeRecentProjects(projects);
   }

   private List<File> recentProjectsWithoutEnsuring() {
      List<File> projects = new ArrayList<>();
      String raw = prefs.getString(RECENT_PROJECTS, "[]");
      try {
         JSONArray array = new JSONArray(raw);
         for (int i = 0; i < array.length(); i++) {
            File project = new File(array.getString(i));
            if (!containsFile(projects, project)) {
               projects.add(project);
            }
         }
      } catch (JSONException ignored) {
         projects.clear();
      }
      return projects;
   }

   private void writeRecentProjects(List<File> projects) {
      JSONArray array = new JSONArray();
      for (File project : projects) {
         array.put(project.getAbsolutePath());
      }
      prefs.edit().putString(RECENT_PROJECTS, array.toString()).apply();
   }

   private boolean containsFile(List<File> files, File candidate) {
      for (File file : files) {
         if (sameFile(file, candidate)) {
            return true;
         }
      }
      return false;
   }

   private boolean sameFile(File left, File right) {
      if (left == null || right == null) {
         return false;
      }
      try {
         return left.getCanonicalFile().equals(right.getCanonicalFile());
      } catch (IOException exception) {
         return left.getAbsoluteFile().equals(right.getAbsoluteFile());
      }
   }

}
