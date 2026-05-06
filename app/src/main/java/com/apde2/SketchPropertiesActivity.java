package com.apde2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.ComponentActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public final class SketchPropertiesActivity extends ComponentActivity {
   static final String EXTRA_PROJECT_NAME = "project_name";
   static final String EXTRA_PROJECT_PATH = "project_path";

   private static final String PREFS = "apde2_sketch_properties";
   private static final String KEY_ORIENTATION_PREFIX = "orientation_";
   private static final int REQUEST_ADD_FILE = 81;

   private final EditorTheme theme = EditorTheme.dark();
   private AppSettings settings;
   private AppStrings strings;
   private SharedPreferences prefs;
   private String projectName;
   private String projectPath;

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      settings = new AppSettings(this);
      strings = new AppStrings(settings.language());
      applySystemBarColors();
      prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
      String extraName = getIntent().getStringExtra(EXTRA_PROJECT_NAME);
      projectName = extraName == null || extraName.trim().isEmpty() ? strings.text(AppStrings.Key.UNTITLED) : extraName.trim();
      projectPath = getIntent().getStringExtra(EXTRA_PROJECT_PATH);
      buildUi();
   }

   private void applySystemBarColors() {
      if (getWindow() == null) {
         return;
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
         getWindow().setStatusBarColor(theme.background);
         getWindow().setNavigationBarColor(theme.background);
      }
   }

   @Override
   protected void onActivityResult(int requestCode, int resultCode, Intent data) {
      super.onActivityResult(requestCode, resultCode, data);
      if (requestCode != REQUEST_ADD_FILE || resultCode != RESULT_OK || data == null || data.getData() == null) {
         return;
      }
      importFileIntoDataFolder(data.getData());
   }

   private void buildUi() {
      ScrollView scroll = new ScrollView(this);
      scroll.setFillViewport(true);
      scroll.setBackgroundColor(theme.background);

      LinearLayout root = new LinearLayout(this);
      root.setOrientation(LinearLayout.VERTICAL);
      root.setPadding(dp(16), dp(18), dp(16), dp(20));
      root.setOnApplyWindowInsetsListener((view, insets) -> {
         view.setPadding(dp(16), insets.getSystemWindowInsetTop() + dp(18), dp(16), insets.getSystemWindowInsetBottom() + dp(20));
         return insets;
      });
      scroll.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

      LinearLayout header = new LinearLayout(this);
      header.setGravity(Gravity.CENTER_VERTICAL);
      header.setPadding(0, 0, 0, dp(8));

      ControlIconButton back = new ControlIconButton(this, theme, ControlIconButton.MODE_BACK);
      back.setOnClickListener(view -> finish());
      header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

      TextView title = new TextView(this);
      title.setText(projectName);
      title.setTextColor(theme.text);
      title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
      title.setGravity(Gravity.CENTER_VERTICAL);
      title.setSingleLine(true);
      title.setEllipsize(TextUtils.TruncateAt.END);
      LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
      titleParams.setMargins(dp(8), 0, 0, 0);
      header.addView(title, titleParams);
      root.addView(header, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

      root.addView(readOnlyRow(strings.text(AppStrings.Key.SKETCH_DISPLAY_NAME), projectName));
      root.addView(readOnlyRow(strings.text(AppStrings.Key.PACKAGE_NAME), defaultPackageName()));
      root.addView(readOnlyRow(strings.text(AppStrings.Key.VERSION_CODE), "1"));
      root.addView(readOnlyRow(strings.text(AppStrings.Key.VERSION_NAME), "1.0"));
      root.addView(readOnlyRow(strings.text(AppStrings.Key.SKETCH_PERMISSIONS), ""));
      root.addView(readOnlyRow(strings.text(AppStrings.Key.TARGET_SDK), "26"));
      root.addView(readOnlyRow(strings.text(AppStrings.Key.MINIMUM_SDK), "17"));
      root.addView(orientationRow());
      root.addView(actionRow(strings.text(AppStrings.Key.ADD_FILE), this::openAddFilePicker));
      root.addView(readOnlyRow(strings.text(AppStrings.Key.CHANGE_SKETCH_ICON), ""));

      setContentView(scroll);
      scroll.requestApplyInsets();
   }

   private LinearLayout readOnlyRow(String label, String value) {
      LinearLayout row = baseRow();
      TextView text = rowLabel(label);
      row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

      if (value != null && !value.isEmpty()) {
         TextView valueView = new TextView(this);
         valueView.setText(value);
         valueView.setTextColor(theme.textMuted);
         valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
         valueView.setEllipsize(TextUtils.TruncateAt.END);
         valueView.setSingleLine(true);
         valueView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
         row.addView(valueView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
      }
      return row;
   }

   private LinearLayout actionRow(String label, Runnable action) {
      LinearLayout row = baseRow();
      TextView text = rowLabel(label);
      row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
      row.setOnClickListener(view -> action.run());
      return row;
   }

   private LinearLayout orientationRow() {
      LinearLayout wrapper = new LinearLayout(this);
      wrapper.setOrientation(LinearLayout.VERTICAL);
      wrapper.setPadding(dp(14), dp(12), dp(14), dp(12));
      UiStyles.roundedStroke(wrapper, theme.surface, theme.border, dp(1), dp(12));
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      params.setMargins(0, 0, 0, dp(8));
      wrapper.setLayoutParams(params);

      TextView label = rowLabel(strings.text(AppStrings.Key.LOCKED_ORIENTATION));
      wrapper.addView(label);

      LinearLayout topChoices = orientationChoicesRow();
      topChoices.setPadding(0, dp(10), 0, 0);
      wrapper.addView(topChoices, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
      addOrientationButton(topChoices, strings.text(AppStrings.Key.NONE), "none", true);
      addOrientationButton(topChoices, strings.text(AppStrings.Key.PORTRAIT), "portrait", false);

      LinearLayout bottomChoices = orientationChoicesRow();
      LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      bottomParams.setMargins(0, dp(8), 0, 0);
      wrapper.addView(bottomChoices, bottomParams);
      addOrientationButton(bottomChoices, strings.text(AppStrings.Key.LANDSCAPE), "landscape", true);
      addOrientationButton(bottomChoices, strings.text(AppStrings.Key.REVERSE_LANDSCAPE), "reverse_landscape", false);
      return wrapper;
   }

   private LinearLayout orientationChoicesRow() {
      LinearLayout row = new LinearLayout(this);
      row.setOrientation(LinearLayout.HORIZONTAL);
      return row;
   }

   private void addOrientationButton(LinearLayout container, String label, String value, boolean withRightMargin) {
      boolean active = value.equals(lockedOrientation());
      TextView button = choiceButton(label, active);
      button.setOnClickListener(view -> {
         prefs.edit().putString(orientationKey(), value).apply();
         recreate();
      });
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1f);
      if (withRightMargin) {
         params.setMargins(0, 0, dp(8), 0);
      }
      container.addView(button, params);
   }

   private String lockedOrientation() {
      return prefs.getString(orientationKey(), "none");
   }

   private String orientationKey() {
      return KEY_ORIENTATION_PREFIX + packageSafeName(projectName);
   }

   private String defaultPackageName() {
      return "processing.test." + packageSafeName(projectName).toLowerCase(Locale.US);
   }

   private String packageSafeName(String value) {
      String normalized = value == null ? "" : value.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", "");
      return normalized.isEmpty() ? "sketch" : normalized;
   }

   private void openAddFilePicker() {
      Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
      intent.addCategory(Intent.CATEGORY_OPENABLE);
      intent.setType("*/*");
      startActivityForResult(intent, REQUEST_ADD_FILE);
   }

   private void importFileIntoDataFolder(Uri uri) {
      File projectDir = projectPath == null || projectPath.trim().isEmpty() ? null : new File(projectPath);
      if (projectDir == null || (!projectDir.exists() && !projectDir.mkdirs())) {
         return;
      }
      File dataDir = new File(projectDir, "data");
      if (!dataDir.exists() && !dataDir.mkdirs()) {
         return;
      }
      String displayName = displayName(uri);
      if (displayName.isEmpty()) {
         displayName = "file";
      }
      File target = uniqueFile(dataDir, displayName);
      try (InputStream input = getContentResolver().openInputStream(uri);
           FileOutputStream output = new FileOutputStream(target)) {
         if (input == null) {
            return;
         }
         byte[] buffer = new byte[8192];
         int read;
         while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
         }
      } catch (IOException ignored) {
      }
   }

   private String displayName(Uri uri) {
      Cursor cursor = null;
      try {
         cursor = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null);
         if (cursor != null && cursor.moveToFirst()) {
            int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (index >= 0) {
               String name = cursor.getString(index);
               return name == null ? "" : name;
            }
         }
      } catch (RuntimeException ignored) {
      } finally {
         if (cursor != null) {
            cursor.close();
         }
      }
      String lastSegment = uri.getLastPathSegment();
      return lastSegment == null ? "" : lastSegment;
   }

   private File uniqueFile(File directory, String requestedName) {
      File candidate = new File(directory, requestedName);
      if (!candidate.exists()) {
         return candidate;
      }
      String name = requestedName;
      String stem = requestedName;
      String extension = "";
      int dot = requestedName.lastIndexOf('.');
      if (dot > 0) {
         stem = requestedName.substring(0, dot);
         extension = requestedName.substring(dot);
      }
      int copy = 2;
      while (candidate.exists()) {
         name = stem + " " + copy + extension;
         candidate = new File(directory, name);
         copy++;
      }
      return candidate;
   }

   private LinearLayout baseRow() {
      LinearLayout row = new LinearLayout(this);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(dp(14), dp(12), dp(14), dp(12));
      UiStyles.roundedStroke(row, theme.surface, theme.border, dp(1), dp(12));
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      params.setMargins(0, 0, 0, dp(8));
      row.setLayoutParams(params);
      return row;
   }

   private TextView rowLabel(String label) {
      TextView text = new TextView(this);
      text.setText(label);
      text.setTextColor(theme.text);
      text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
      text.setEllipsize(TextUtils.TruncateAt.END);
      text.setSingleLine(false);
      return text;
   }

   private TextView choiceButton(String label, boolean active) {
      TextView button = new TextView(this);
      button.setText(label);
      button.setTextColor(active ? theme.background : theme.text);
      button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
      button.setGravity(Gravity.CENTER);
      button.setPadding(dp(6), 0, dp(6), 0);
      UiStyles.roundedStroke(button, active ? theme.accent : theme.surfaceSoft, active ? theme.accent : theme.border, dp(1), dp(10));
      return button;
   }

   private int dp(int value) {
      return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
   }
}
