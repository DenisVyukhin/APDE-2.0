package com.apde2;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.ComponentActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class SketchPropertiesActivity extends ComponentActivity {
   static final String EXTRA_PROJECT_NAME = "project_name";
   static final String EXTRA_PROJECT_PATH = "project_path";

   private static final String PREFS = "apde2_sketch_properties";
   private static final String KEY_DISPLAY_NAME_PREFIX = "display_name_";
   private static final String KEY_PACKAGE_NAME_PREFIX = "package_name_";
   private static final String KEY_VERSION_CODE_PREFIX = "version_code_";
   private static final String KEY_VERSION_NAME_PREFIX = "version_name_";
   private static final String KEY_PERMISSIONS_PREFIX = "permissions_";
   private static final String KEY_TARGET_SDK_PREFIX = "target_sdk_";
   private static final String KEY_MIN_SDK_PREFIX = "min_sdk_";
   private static final String KEY_ORIENTATION_PREFIX = "orientation_";
   private static final int REQUEST_ADD_FILE = 81;
   private static final String[] ANDROID_PERMISSIONS = {
      "android.permission.ACCEPT_HANDOVER",
      "android.permission.ACCESS_BACKGROUND_LOCATION",
      "android.permission.ACCESS_CHECKIN_PROPERTIES",
      "android.permission.ACCESS_COARSE_LOCATION",
      "android.permission.ACCESS_FINE_LOCATION",
      "android.permission.ACCESS_LOCATION_EXTRA_COMMANDS",
      "android.permission.ACCESS_MEDIA_LOCATION",
      "android.permission.ACCESS_NETWORK_STATE",
      "android.permission.ACCESS_NOTIFICATION_POLICY",
      "android.permission.ACCESS_WIFI_STATE",
      "android.permission.ACCOUNT_MANAGER",
      "android.permission.ACTIVITY_RECOGNITION",
      "android.permission.ADD_VOICEMAIL",
      "android.permission.ANSWER_PHONE_CALLS",
      "android.permission.BATTERY_STATS",
      "android.permission.BIND_ACCESSIBILITY_SERVICE",
      "android.permission.BIND_APPWIDGET",
      "android.permission.BIND_AUTOFILL_SERVICE",
      "android.permission.BIND_CALL_REDIRECTION_SERVICE",
      "android.permission.BIND_CARRIER_MESSAGING_SERVICE",
      "android.permission.BIND_CARRIER_SERVICES",
      "android.permission.BIND_CHOOSER_TARGET_SERVICE",
      "android.permission.BIND_CONDITION_PROVIDER_SERVICE",
      "android.permission.BIND_DEVICE_ADMIN",
      "android.permission.BIND_DREAM_SERVICE",
      "android.permission.BIND_INCALL_SERVICE",
      "android.permission.BIND_INPUT_METHOD",
      "android.permission.BIND_MIDI_DEVICE_SERVICE",
      "android.permission.BIND_NFC_SERVICE",
      "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
      "android.permission.BIND_PRINT_SERVICE",
      "android.permission.BIND_QUICK_SETTINGS_TILE",
      "android.permission.BIND_REMOTEVIEWS",
      "android.permission.BIND_SCREENING_SERVICE",
      "android.permission.BIND_TELECOM_CONNECTION_SERVICE",
      "android.permission.BIND_TEXT_SERVICE",
      "android.permission.BIND_TV_INPUT",
      "android.permission.BIND_VISUAL_VOICEMAIL_SERVICE",
      "android.permission.BIND_VOICE_INTERACTION",
      "android.permission.BIND_VPN_SERVICE",
      "android.permission.BIND_VR_LISTENER_SERVICE",
      "android.permission.BIND_WALLPAPER",
      "android.permission.BLUETOOTH",
      "android.permission.BLUETOOTH_ADMIN",
      "android.permission.BLUETOOTH_ADVERTISE",
      "android.permission.BLUETOOTH_CONNECT",
      "android.permission.BLUETOOTH_SCAN",
      "android.permission.BODY_SENSORS",
      "android.permission.BROADCAST_PACKAGE_REMOVED",
      "android.permission.BROADCAST_SMS",
      "android.permission.BROADCAST_STICKY",
      "android.permission.BROADCAST_WAP_PUSH",
      "android.permission.CALL_COMPANION_APP",
      "android.permission.CALL_PHONE",
      "android.permission.CALL_PRIVILEGED",
      "android.permission.CAMERA",
      "android.permission.CAPTURE_AUDIO_OUTPUT",
      "android.permission.CHANGE_COMPONENT_ENABLED_STATE",
      "android.permission.CHANGE_CONFIGURATION",
      "android.permission.CHANGE_NETWORK_STATE",
      "android.permission.CHANGE_WIFI_MULTICAST_STATE",
      "android.permission.CHANGE_WIFI_STATE",
      "android.permission.CLEAR_APP_CACHE",
      "android.permission.CONTROL_LOCATION_UPDATES",
      "android.permission.DELETE_CACHE_FILES",
      "android.permission.DELETE_PACKAGES",
      "android.permission.DIAGNOSTIC",
      "android.permission.DISABLE_KEYGUARD",
      "android.permission.DUMP",
      "android.permission.EXPAND_STATUS_BAR",
      "android.permission.FACTORY_TEST",
      "android.permission.FOREGROUND_SERVICE",
      "android.permission.GET_ACCOUNTS",
      "android.permission.GET_PACKAGE_SIZE",
      "android.permission.GET_TASKS",
      "android.permission.GLOBAL_SEARCH",
      "android.permission.INSTALL_LOCATION_PROVIDER",
      "android.permission.INSTALL_PACKAGES",
      "android.permission.INSTALL_SHORTCUT",
      "android.permission.INTERNET",
      "android.permission.KILL_BACKGROUND_PROCESSES",
      "android.permission.LOCATION_HARDWARE",
      "android.permission.MANAGE_DOCUMENTS",
      "android.permission.MANAGE_EXTERNAL_STORAGE",
      "android.permission.MASTER_CLEAR",
      "android.permission.MEDIA_CONTENT_CONTROL",
      "android.permission.MODIFY_AUDIO_SETTINGS",
      "android.permission.MODIFY_PHONE_STATE",
      "android.permission.MOUNT_FORMAT_FILESYSTEMS",
      "android.permission.MOUNT_UNMOUNT_FILESYSTEMS",
      "android.permission.NFC",
      "android.permission.NFC_PREFERRED_PAYMENT_INFO",
      "android.permission.NFC_TRANSACTION_EVENT",
      "android.permission.PACKAGE_USAGE_STATS",
      "android.permission.PERSISTENT_ACTIVITY",
      "android.permission.POST_NOTIFICATIONS",
      "android.permission.PROCESS_OUTGOING_CALLS",
      "android.permission.QUERY_ALL_PACKAGES",
      "android.permission.READ_CALENDAR",
      "android.permission.READ_CALL_LOG",
      "android.permission.READ_CONTACTS",
      "android.permission.READ_EXTERNAL_STORAGE",
      "android.permission.READ_INPUT_STATE",
      "android.permission.READ_LOGS",
      "android.permission.READ_MEDIA_AUDIO",
      "android.permission.READ_MEDIA_IMAGES",
      "android.permission.READ_MEDIA_VIDEO",
      "android.permission.READ_PHONE_NUMBERS",
      "android.permission.READ_PHONE_STATE",
      "android.permission.READ_PRECISE_PHONE_STATE",
      "android.permission.READ_SMS",
      "android.permission.READ_SYNC_SETTINGS",
      "android.permission.READ_SYNC_STATS",
      "android.permission.READ_VOICEMAIL",
      "android.permission.REBOOT",
      "android.permission.RECEIVE_BOOT_COMPLETED",
      "android.permission.RECEIVE_MMS",
      "android.permission.RECEIVE_SMS",
      "android.permission.RECEIVE_WAP_PUSH",
      "android.permission.RECORD_AUDIO",
      "android.permission.REORDER_TASKS",
      "android.permission.REQUEST_COMPANION_RUN_IN_BACKGROUND",
      "android.permission.REQUEST_COMPANION_USE_DATA_IN_BACKGROUND",
      "android.permission.REQUEST_DELETE_PACKAGES",
      "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
      "android.permission.REQUEST_INSTALL_PACKAGES",
      "android.permission.RESTART_PACKAGES",
      "android.permission.SEND_RESPOND_VIA_MESSAGE",
      "android.permission.SEND_SMS",
      "android.permission.SET_ALARM",
      "android.permission.SET_ALWAYS_FINISH",
      "android.permission.SET_ANIMATION_SCALE",
      "android.permission.SET_DEBUG_APP",
      "android.permission.SET_PREFERRED_APPLICATIONS",
      "android.permission.SET_PROCESS_LIMIT",
      "android.permission.SET_TIME",
      "android.permission.SET_TIME_ZONE",
      "android.permission.SET_WALLPAPER",
      "android.permission.SET_WALLPAPER_HINTS",
      "android.permission.SIGNAL_PERSISTENT_PROCESSES",
      "android.permission.SMS_FINANCIAL_TRANSACTIONS",
      "android.permission.START_VIEW_PERMISSION_USAGE",
      "android.permission.STATUS_BAR",
      "android.permission.SYSTEM_ALERT_WINDOW",
      "android.permission.TRANSMIT_IR",
      "android.permission.UNINSTALL_SHORTCUT",
      "android.permission.UPDATE_DEVICE_STATS",
      "android.permission.USE_BIOMETRIC",
      "android.permission.USE_FINGERPRINT",
      "android.permission.USE_FULL_SCREEN_INTENT",
      "android.permission.USE_SIP",
      "android.permission.VIBRATE",
      "android.permission.WAKE_LOCK",
      "android.permission.WRITE_APN_SETTINGS",
      "android.permission.WRITE_CALENDAR",
      "android.permission.WRITE_CALL_LOG",
      "android.permission.WRITE_CONTACTS",
      "android.permission.WRITE_EXTERNAL_STORAGE",
      "android.permission.WRITE_GSERVICES",
      "android.permission.WRITE_SECURE_SETTINGS",
      "android.permission.WRITE_SETTINGS",
      "android.permission.WRITE_SYNC_SETTINGS",
      "android.permission.WRITE_VOICEMAIL"
   };

   private EditorTheme theme;
   private AppSettings settings;
   private AppStrings strings;
   private SharedPreferences prefs;
   private String projectName;
   private String projectPath;

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      settings = new AppSettings(this);
      theme = EditorTheme.load(this, settings.themeMode());
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
      title.setText(sketchDisplayName());
      title.setTextColor(theme.text);
      title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
      title.setGravity(Gravity.CENTER_VERTICAL);
      title.setSingleLine(true);
      title.setEllipsize(TextUtils.TruncateAt.END);
      LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
      titleParams.setMargins(dp(8), 0, 0, 0);
      header.addView(title, titleParams);
      root.addView(header, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

      addSection(root, strings.text(AppStrings.Key.SKETCH_PROPERTIES),
         editableTextRow(strings.text(AppStrings.Key.SKETCH_DISPLAY_NAME), sketchDisplayName(), projectName, this::saveSketchDisplayName),
         editableTextRow(strings.text(AppStrings.Key.PACKAGE_NAME), packageName(), defaultPackageName(), this::savePackageName),
         editableNumberRow(strings.text(AppStrings.Key.VERSION_CODE), versionCode(), "1", this::saveVersionCode),
         editableTextRow(strings.text(AppStrings.Key.VERSION_NAME), versionName(), "1.0", this::saveVersionName),
         permissionsRow(),
         editableNumberRow(strings.text(AppStrings.Key.TARGET_SDK), String.valueOf(targetSdk()), "35", this::saveTargetSdk),
         editableNumberRow(strings.text(AppStrings.Key.MINIMUM_SDK), String.valueOf(minSdk()), "26", this::saveMinSdk),
         orientationRow(),
         actionRow(strings.text(AppStrings.Key.ADD_FILE), this::openAddFilePicker),
         readOnlyRow(strings.text(AppStrings.Key.CHANGE_SKETCH_ICON), ""));

      setContentView(scroll);
      scroll.requestApplyInsets();
   }

   private void addSection(LinearLayout root, String title, View... rows) {
      root.addView(sectionTitle(title));
      LinearLayout card = sectionCard();
      for (int i = 0; i < rows.length; i++) {
         card.addView(rows[i]);
         if (i < rows.length - 1) {
            card.addView(sectionDivider());
         }
      }
      root.addView(card);
   }

   private LinearLayout sectionCard() {
      LinearLayout card = new LinearLayout(this);
      card.setOrientation(LinearLayout.VERTICAL);
      UiStyles.roundedStroke(card, theme.surface, theme.border, dp(1), dp(12));
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      params.setMargins(0, 0, 0, dp(8));
      card.setLayoutParams(params);
      return card;
   }

   private View sectionDivider() {
      View divider = new View(this);
      divider.setBackgroundColor(withAlpha(theme.border, 150));
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
      params.setMargins(dp(14), 0, dp(14), 0);
      divider.setLayoutParams(params);
      return divider;
   }

   private TextView sectionTitle(String text) {
      TextView view = new TextView(this);
      view.setText(text);
      view.setTextColor(theme.text);
      view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
      view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      params.setMargins(0, dp(22), 0, dp(10));
      view.setLayoutParams(params);
      return view;
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

   private LinearLayout editableTextRow(String label, String value, String hint, TextInputAction action) {
      return editableTextRow(label, value, hint, action, false);
   }

   private LinearLayout editableTextRow(String label, String value, String hint, TextInputAction action, boolean multiLine) {
      LinearLayout row = readOnlyRow(label, value);
      row.setClickable(true);
      row.setFocusable(true);
      row.setOnClickListener(view -> showTextInputDialog(
         label,
         hint,
         value,
         multiLine ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE : InputType.TYPE_CLASS_TEXT,
         multiLine,
         action));
      return row;
   }

   private LinearLayout editableNumberRow(String label, String value, String hint, TextInputAction action) {
      LinearLayout row = readOnlyRow(label, value);
      row.setClickable(true);
      row.setFocusable(true);
      row.setOnClickListener(view -> showTextInputDialog(label, hint, value, InputType.TYPE_CLASS_NUMBER, false, action));
      return row;
   }

   private LinearLayout permissionsRow() {
      LinearLayout row = readOnlyRow(strings.text(AppStrings.Key.SKETCH_PERMISSIONS), permissionsDisplayText());
      row.setClickable(true);
      row.setFocusable(true);
      row.setOnClickListener(view -> showPermissionsDialog());
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
      wrapper.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

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

   private String prefKey(String prefix) {
      return prefix + packageSafeName(projectName);
   }

   private String sketchDisplayName() {
      return nonEmptyPref(KEY_DISPLAY_NAME_PREFIX, projectName);
   }

   private String packageName() {
      return nonEmptyPref(KEY_PACKAGE_NAME_PREFIX, defaultPackageName());
   }

   private String versionCode() {
      return nonEmptyPref(KEY_VERSION_CODE_PREFIX, "1");
   }

   private String versionName() {
      return nonEmptyPref(KEY_VERSION_NAME_PREFIX, "1.0");
   }

   private int targetSdk() {
      return intPref(KEY_TARGET_SDK_PREFIX, 35);
   }

   private int minSdk() {
      return intPref(KEY_MIN_SDK_PREFIX, 26);
   }

   private String permissionsText() {
      return prefs.getString(prefKey(KEY_PERMISSIONS_PREFIX), "");
   }

   private String permissionsDisplayText() {
      return TextUtils.join(", ", splitPermissions(permissionsText()));
   }

   private String nonEmptyPref(String prefix, String fallback) {
      String value = prefs.getString(prefKey(prefix), fallback);
      return value == null || value.trim().isEmpty() ? fallback : value.trim();
   }

   private int intPref(String prefix, int fallback) {
      try {
         return Integer.parseInt(nonEmptyPref(prefix, String.valueOf(fallback)));
      } catch (RuntimeException exception) {
         return fallback;
      }
   }

   private boolean saveSketchDisplayName(String value) {
      String normalized = value.trim();
      if (normalized.isEmpty()) {
         return false;
      }
      prefs.edit().putString(prefKey(KEY_DISPLAY_NAME_PREFIX), normalized).apply();
      recreate();
      return true;
   }

   private boolean savePackageName(String value) {
      String normalized = value.trim();
      if (!isValidJavaPackage(normalized)) {
         return false;
      }
      prefs.edit().putString(prefKey(KEY_PACKAGE_NAME_PREFIX), normalized).apply();
      recreate();
      return true;
   }

   private boolean saveVersionCode(String value) {
      String normalized = value.trim();
      int parsed = parsePositiveInt(normalized, 1, Integer.MAX_VALUE);
      if (parsed < 0) {
         return false;
      }
      prefs.edit().putString(prefKey(KEY_VERSION_CODE_PREFIX), String.valueOf(parsed)).apply();
      recreate();
      return true;
   }

   private boolean saveVersionName(String value) {
      String normalized = value.trim();
      if (normalized.isEmpty()) {
         return false;
      }
      prefs.edit().putString(prefKey(KEY_VERSION_NAME_PREFIX), normalized).apply();
      recreate();
      return true;
   }

   private boolean savePermissions(Set<String> selected) {
      java.util.ArrayList<String> ordered = new java.util.ArrayList<>();
      for (String permission : ANDROID_PERMISSIONS) {
         if (selected.contains(permission)) {
            ordered.add(permission);
         }
      }
      String normalized = TextUtils.join("\n", ordered);
      prefs.edit().putString(prefKey(KEY_PERMISSIONS_PREFIX), normalized).apply();
      recreate();
      return true;
   }

   private boolean saveTargetSdk(String value) {
      int parsed = parsePositiveInt(value.trim(), minSdk(), 100);
      if (parsed < 0) {
         return false;
      }
      prefs.edit().putString(prefKey(KEY_TARGET_SDK_PREFIX), String.valueOf(parsed)).apply();
      recreate();
      return true;
   }

   private boolean saveMinSdk(String value) {
      int parsed = parsePositiveInt(value.trim(), 1, targetSdk());
      if (parsed < 0) {
         return false;
      }
      prefs.edit().putString(prefKey(KEY_MIN_SDK_PREFIX), String.valueOf(parsed)).apply();
      recreate();
      return true;
   }

   private int parsePositiveInt(String value, int min, int max) {
      try {
         int parsed = Integer.parseInt(value);
         return parsed >= min && parsed <= max ? parsed : -1;
      } catch (RuntimeException exception) {
         return -1;
      }
   }

   private boolean isValidJavaPackage(String value) {
      if (value.isEmpty() || !value.contains(".")) {
         return false;
      }
      String[] parts = value.split("\\.");
      for (String part : parts) {
         if (!isValidJavaIdentifier(part)) {
            return false;
         }
      }
      return true;
   }

   private boolean isValidJavaIdentifier(String value) {
      if (value.isEmpty() || !Character.isJavaIdentifierStart(value.charAt(0))) {
         return false;
      }
      for (int i = 1; i < value.length(); i++) {
         if (!Character.isJavaIdentifierPart(value.charAt(i))) {
            return false;
         }
      }
      return true;
   }

   private java.util.List<String> splitPermissions(String value) {
      java.util.ArrayList<String> permissions = new java.util.ArrayList<>();
      if (value == null || value.trim().isEmpty()) {
         return permissions;
      }
      String[] parts = value.split("[,\\n]");
      for (String part : parts) {
         String permission = part.trim();
         if (!permission.isEmpty()) {
            permissions.add(permission);
         }
      }
      return permissions;
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
      displayName = sanitizeFileName(displayName);
      File target = uniqueFile(dataDir, displayName);
      try (InputStream input = getContentResolver().openInputStream(uri)) {
         if (input == null) {
            return;
         }
         try (FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
               output.write(buffer, 0, read);
            }
         }
      } catch (IOException ignored) {
         target.delete();
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

   private String sanitizeFileName(String value) {
      String sanitized = value == null ? "" : value.replace("/", "").replace("\\", "").trim();
      return sanitized.isEmpty() ? "file" : sanitized;
   }

   private LinearLayout baseRow() {
      LinearLayout row = new LinearLayout(this);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(dp(14), dp(12), dp(14), dp(12));
      row.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
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

   private void showTextInputDialog(String title, String hint, String value, int inputType, boolean multiLine, TextInputAction action) {
      Dialog dialog = new Dialog(this);
      dialog.getWindow();

      LinearLayout content = new LinearLayout(this);
      content.setOrientation(LinearLayout.VERTICAL);
      content.setPadding(dp(18), dp(16), dp(18), dp(14));
      UiStyles.roundedStroke(content, theme.surface, theme.border, dp(1), dp(14));

      TextView titleView = new TextView(this);
      titleView.setText(title);
      titleView.setTextColor(theme.text);
      titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
      titleView.setTypeface(Typeface.DEFAULT_BOLD);
      content.addView(titleView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30)));

      EditText input = new EditText(this);
      input.setTextColor(theme.text);
      input.setHintTextColor(theme.textMuted);
      input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
      input.setHint(hint);
      input.setText(value);
      input.setSelectAllOnFocus(true);
      input.setGravity(multiLine ? Gravity.TOP | Gravity.START : Gravity.CENTER_VERTICAL | Gravity.START);
      input.setInputType(inputType);
      input.setSingleLine(!multiLine);
      input.setPadding(dp(12), 0, dp(12), 0);
      UiStyles.roundedStroke(input, theme.surfaceSoft, theme.border, dp(1), dp(10));
      LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, multiLine ? dp(110) : dp(46));
      inputParams.setMargins(0, dp(12), 0, dp(14));
      content.addView(input, inputParams);

      LinearLayout actions = new LinearLayout(this);
      actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);

      TextView cancel = dialogButton(strings.text(AppStrings.Key.CANCEL), false);
      cancel.setOnClickListener(view -> dialog.dismiss());
      TextView confirm = dialogButton(strings.text(AppStrings.Key.SAVE), true);
      confirm.setOnClickListener(view -> {
         boolean handled = action.apply(input.getText().toString());
         if (handled) {
            dialog.dismiss();
         } else {
            input.setError(strings.text(AppStrings.Key.ENTER_A_VALID_VALUE));
         }
      });
      actions.addView(cancel, new LinearLayout.LayoutParams(dp(94), dp(42)));
      LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(dp(104), dp(42));
      confirmParams.setMargins(dp(8), 0, 0, 0);
      actions.addView(confirm, confirmParams);
      content.addView(actions, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));

      dialog.setContentView(content, new ViewGroup.LayoutParams(dp(320), ViewGroup.LayoutParams.WRAP_CONTENT));
      if (dialog.getWindow() != null) {
         dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
         dialog.getWindow().setGravity(Gravity.CENTER);
      }
      dialog.show();
      input.requestFocus();
   }

   private void showPermissionsDialog() {
      Dialog dialog = new Dialog(this);
      dialog.getWindow();

      Set<String> selected = new HashSet<>(splitPermissions(permissionsText()));
      LinearLayout content = new LinearLayout(this);
      content.setOrientation(LinearLayout.VERTICAL);
      content.setPadding(dp(18), dp(16), dp(18), dp(14));
      UiStyles.roundedStroke(content, theme.surface, theme.border, dp(1), dp(14));

      TextView titleView = new TextView(this);
      titleView.setText(strings.text(AppStrings.Key.SKETCH_PERMISSIONS));
      titleView.setTextColor(theme.text);
      titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
      titleView.setTypeface(Typeface.DEFAULT_BOLD);
      content.addView(titleView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30)));

      ScrollView scroll = new ScrollView(this);
      scroll.setFillViewport(false);
      LinearLayout list = new LinearLayout(this);
      list.setOrientation(LinearLayout.VERTICAL);
      list.setPadding(0, dp(8), 0, dp(8));
      scroll.addView(list, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
      for (String permission : ANDROID_PERMISSIONS) {
         TextView row = permissionChoiceRow(permission, selected.contains(permission));
         row.setOnClickListener(view -> {
            if (selected.contains(permission)) {
               selected.remove(permission);
            } else {
               selected.add(permission);
            }
            stylePermissionChoice(row, selected.contains(permission));
         });
         LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
         rowParams.setMargins(0, 0, 0, dp(6));
         list.addView(row, rowParams);
      }
      LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
      scrollParams.setMargins(0, dp(10), 0, dp(12));
      content.addView(scroll, scrollParams);

      LinearLayout actions = new LinearLayout(this);
      actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);

      TextView cancel = dialogButton(strings.text(AppStrings.Key.CANCEL), false);
      cancel.setOnClickListener(view -> dialog.dismiss());
      TextView confirm = dialogButton(strings.text(AppStrings.Key.SAVE), true);
      confirm.setOnClickListener(view -> {
         savePermissions(selected);
         dialog.dismiss();
      });
      actions.addView(cancel, new LinearLayout.LayoutParams(dp(94), dp(42)));
      LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(dp(104), dp(42));
      confirmParams.setMargins(dp(8), 0, 0, 0);
      actions.addView(confirm, confirmParams);
      content.addView(actions, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));

      dialog.setContentView(content, new ViewGroup.LayoutParams(permissionsDialogWidth(), permissionsDialogHeight()));
      if (dialog.getWindow() != null) {
         dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
         dialog.getWindow().setGravity(Gravity.CENTER);
      }
      dialog.show();
   }

   private TextView permissionChoiceRow(String permission, boolean active) {
      TextView row = new TextView(this);
      row.setText(permission);
      row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setSingleLine(true);
      row.setEllipsize(TextUtils.TruncateAt.END);
      row.setPadding(dp(10), 0, dp(10), 0);
      stylePermissionChoice(row, active);
      return row;
   }

   private void stylePermissionChoice(TextView row, boolean active) {
      row.setTextColor(active ? theme.background : theme.text);
      UiStyles.roundedStroke(row, active ? theme.accent : theme.surfaceSoft, active ? theme.accent : theme.border, dp(1), dp(10));
   }

   private int permissionsDialogHeight() {
      int available = getResources().getDisplayMetrics().heightPixels - dp(80);
      return Math.max(dp(360), Math.min(dp(600), available));
   }

   private int permissionsDialogWidth() {
      int available = getResources().getDisplayMetrics().widthPixels - dp(32);
      return Math.max(dp(280), Math.min(dp(340), available));
   }

   private TextView dialogButton(String label, boolean primary) {
      TextView button = new TextView(this);
      button.setText(label);
      button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
      button.setGravity(Gravity.CENTER);
      button.setTextColor(primary ? theme.background : theme.text);
      UiStyles.roundedStroke(button, primary ? theme.accent : theme.surfaceSoft, primary ? theme.accent : theme.border, dp(1), dp(10));
      return button;
   }

   private int dp(int value) {
      return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
   }

   private static int withAlpha(int color, int alpha) {
      return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
   }

   private interface TextInputAction {
      boolean apply(String value);
   }
}
