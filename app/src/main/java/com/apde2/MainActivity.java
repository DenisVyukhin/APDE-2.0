package com.apde2;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.provider.DocumentsContract;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.ComponentActivity;
import androidx.documentfile.provider.DocumentFile;

import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MainActivity extends ComponentActivity {
   private static final int REQUEST_OPEN_FOLDER = 70;
   private static final int REQUEST_ALL_FILES_ACCESS = 71;
   private static final int LOG_INFO = 1;
   private static final int LOG_SUCCESS = 2;
   private static final int LOG_ERROR = 3;

   private final Handler runHandler = new Handler(Looper.getMainLooper());
   private final List<SketchFile> files = new ArrayList<>();
   private final List<ConsoleEntry> consoleEntries = new ArrayList<>();
   private final List<Runnable> pendingRunSteps = new ArrayList<>();
   private final EditorTheme theme = EditorTheme.dark();

   private SketchStore store;
   private AppSettings settings;
   private AppStrings strings;
   private String appliedLanguage;
   private FrameLayout rootContainer;
   private LinearLayout rootView;
   private LinearLayout tabsBar;
   private LinearLayout editorPanel;
   private CodeEditorView editor;
   private TextView editorEmptyState;
   private TextView projectTitle;
   private LineNumberView lineNumbers;
   private LinearLayout tabs;
   private LinearLayout consolePanel;
   private LinearLayout consoleHeader;
   private ScrollView consoleScroll;
   private LinearLayout consoleContent;
   private TextView consoleTab;
   private TextView errorsTab;
   private ControlIconButton runButton;
   private View filePanelScrim;
   private LinearLayout filePanelContainer;
   private LinearLayout fileTreeContent;
   private TextView filePanelTitle;
   private DocumentFile fileRoot;
   private DocumentFile selectedFolder;
   private final Set<String> expandedFolders = new HashSet<>();
   private int activeIndex = -1;
   private int activeConsoleTab = LOG_INFO;
   private int consoleHeight;
   private int expandedConsoleHeight;
   private int consoleStartHeight;
   private float consoleStartY;
   private boolean consoleCollapsed = false;
   private boolean userResizingConsole = false;
   private boolean keyboardVisible = false;
   private int keyboardInsetHeight = 0;
   private int keyboardVisibleBottom = 0;
   private boolean restoreConsoleAfterKeyboard = false;
   private int consoleHeightBeforeKeyboard = 0;
   private boolean loadingTab = false;
   private boolean runInProgress = false;
   private float filePanelStartX;
   private float filePanelStartY;
   private float filePanelStartTranslationX;
   private boolean filePanelTouchActive = false;
   private boolean filePanelDragging = false;

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      store = new SketchStore(this);
      settings = new AppSettings(this);
      appliedLanguage = settings.language();
      strings = new AppStrings(appliedLanguage);
      applySystemBarColors();
      files.addAll(store.loadFiles());
      activeIndex = files.isEmpty() ? -1 : Math.min(store.loadActiveIndex(), files.size() - 1);
      buildUi();
      applySettings();
      syncEditorState();
      updateProjectTitle();
      if (!files.isEmpty()) {
         openTab(Math.max(0, activeIndex));
      }
      addConsoleEntry(LOG_INFO, s(AppStrings.Key.EDITOR_READY));
   }

   @Override
   protected void onPause() {
      super.onPause();
      persistCurrentFile();
      store.save(files, activeIndex);
   }

   @Override
   protected void onResume() {
      super.onResume();
      store.refreshStorageState();
      String language = settings.language();
      if (!language.equals(appliedLanguage)) {
         recreate();
         return;
      }
      applySettings();
      renderConsole();
   }

   @Override
   protected void onDestroy() {
      cancelPendingRunSteps();
      super.onDestroy();
   }

   @Override
   protected void onActivityResult(int requestCode, int resultCode, Intent data) {
      super.onActivityResult(requestCode, resultCode, data);
      if (requestCode == REQUEST_ALL_FILES_ACCESS) {
         store.refreshStorageState();
         resetToInternalSketchbook();
         return;
      }
      if (requestCode != REQUEST_OPEN_FOLDER || resultCode != RESULT_OK || data == null || data.getData() == null) {
         return;
      }

      Uri uri = data.getData();
      int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
      try {
         getContentResolver().takePersistableUriPermission(uri, flags);
      } catch (RuntimeException ignored) {
      }
      fileRoot = DocumentFile.fromTreeUri(this, uri);
      selectedFolder = fileRoot;
      expandedFolders.clear();
      if (fileRoot != null) {
         expandedFolders.add(fileKey(fileRoot));
         addConsoleEntry(LOG_INFO, sf(AppStrings.Key.OPENED_FOLDER, displayName(fileRoot)));
      }
      clearOpenFiles();
      renderFileTree();
   }

   @Override
   public void onBackPressed() {
      if (filePanelContainer != null && filePanelContainer.getVisibility() == View.VISIBLE) {
         hideFilePanel();
         return;
      }
      if (editor != null && editor.hasFocus()) {
         editor.clearFocus();
         hideKeyboard();
         return;
      }
      super.onBackPressed();
   }

   private void buildUi() {
      FrameLayout container = new FrameLayout(this);
      rootContainer = container;

      LinearLayout root = new LinearLayout(this);
      rootView = root;
      root.setOrientation(LinearLayout.VERTICAL);
      root.setBackgroundColor(theme.background);
      root.setPadding(0, 0, 0, 0);
      root.setOnApplyWindowInsetsListener((view, insets) -> {
         view.setPadding(0, insets.getSystemWindowInsetTop() + dp(12), 0, 0);
         return insets;
      });

      root.addView(createTopBar(), sectionParams(dp(68)));
      root.addView(createTabsBar(), sectionParams(dp(54)));
      root.addView(createEditorPanel(), editorSectionParams());

      consoleHeight = dp(220);
      consolePanel = createConsolePanel();
      LinearLayout.LayoutParams consoleParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, consoleHeight);
      consoleParams.setMargins(0, 0, 0, 0);
      root.addView(consolePanel, consoleParams);

      root.post(() -> {
         int targetHeight = Math.round(root.getHeight() * 0.32f);
         if (targetHeight > dp(160)) {
            expandedConsoleHeight = targetHeight;
            setConsoleHeight(targetHeight);
         } else {
            expandedConsoleHeight = consoleHeight;
         }
      });

      container.addView(root, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
      container.addView(createFilePanelOverlay(), new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

      setContentView(container);
      container.requestApplyInsets();
      root.requestApplyInsets();
      installKeyboardWatcher(root);
      rebuildTabs();
   }

   private LinearLayout createTopBar() {
      LinearLayout toolbar = new LinearLayout(this);
      toolbar.setGravity(Gravity.CENTER_VERTICAL);
      toolbar.setPadding(0, dp(6), 0, dp(8));

      ControlIconButton menu = new ControlIconButton(this, theme, ControlIconButton.MODE_MENU);
      menu.setOnClickListener(view -> showFilePanel());
      toolbar.addView(menu, iconParams());

      LinearLayout projectInfo = new LinearLayout(this);
      projectInfo.setOrientation(LinearLayout.VERTICAL);
      projectInfo.setGravity(Gravity.CENTER_VERTICAL);
      projectInfo.setPadding(dp(14), 0, dp(8), 0);

      LinearLayout projectTitleRow = new LinearLayout(this);
      projectTitleRow.setGravity(Gravity.CENTER_VERTICAL);
      projectTitle = new TextView(this);
      projectTitle.setTextColor(theme.text);
      projectTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
      projectTitle.setTypeface(Typeface.DEFAULT_BOLD);
      projectTitle.setSingleLine(true);
      projectTitle.setEllipsize(TextUtils.TruncateAt.END);
      projectTitleRow.addView(projectTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

      TextView mode = new TextView(this);
      mode.setText("APDE v2");
      mode.setTextColor(theme.textMuted);
      mode.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
      projectInfo.addView(projectTitleRow);
      projectInfo.addView(mode);
      toolbar.addView(projectInfo, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

      runButton = new ControlIconButton(this, theme, ControlIconButton.MODE_PLAY);
      runButton.setOnClickListener(view -> toggleRunSketch());
      LinearLayout.LayoutParams runParams = iconParams();
      runParams.setMargins(0, 0, dp(8), 0);
      toolbar.addView(runButton, runParams);

      ControlIconButton more = new ControlIconButton(this, theme, ControlIconButton.MODE_MORE);
      more.setOnClickListener(this::showProjectMenu);
      toolbar.addView(more, iconParams());

      return toolbar;
   }

   private LinearLayout createTabsBar() {
      tabsBar = new LinearLayout(this);
      tabsBar.setGravity(Gravity.CENTER_VERTICAL);
      tabsBar.setPadding(0, dp(4), 0, dp(6));

      HorizontalScrollView tabsScroll = new HorizontalScrollView(this);
      tabsScroll.setHorizontalScrollBarEnabled(false);
      tabsScroll.setFillViewport(false);
      tabs = new LinearLayout(this);
      tabs.setOrientation(LinearLayout.HORIZONTAL);
      tabsScroll.addView(tabs);
      tabsBar.addView(tabsScroll, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

      ControlIconButton undo = new ControlIconButton(this, theme, ControlIconButton.MODE_UNDO);
      undo.setOnClickListener(view -> {
         if (editor == null || files.isEmpty()) {
            return;
         }
         if (!editor.undo()) {
            addConsoleEntry(LOG_INFO, s(AppStrings.Key.NOTHING_TO_UNDO));
         }
      });
      LinearLayout.LayoutParams undoParams = iconParams();
      undoParams.setMargins(dp(8), 0, dp(6), 0);
      tabsBar.addView(undo, undoParams);

      ControlIconButton redo = new ControlIconButton(this, theme, ControlIconButton.MODE_REDO);
      redo.setOnClickListener(view -> {
         if (editor == null || files.isEmpty()) {
            return;
         }
         if (!editor.redo()) {
            addConsoleEntry(LOG_INFO, s(AppStrings.Key.NOTHING_TO_REDO));
         }
      });
      tabsBar.addView(redo, iconParams());

      return tabsBar;
   }

   private LinearLayout createEditorPanel() {
      editorPanel = new LinearLayout(this);
      editorPanel.setOrientation(LinearLayout.HORIZONTAL);
      editorPanel.setPadding(dp(2), dp(10), 0, 0);
      UiStyles.topRounded(editorPanel, theme.surfaceSoft, dp(14));

      lineNumbers = new LineNumberView(this, theme);
      editorPanel.addView(lineNumbers, new LinearLayout.LayoutParams(lineNumbers.desiredWidth(), LinearLayout.LayoutParams.MATCH_PARENT));

      editor = new CodeEditorView(this, theme);
      editor.setPadding(dp(14), dp(2), 0, 0);
      lineNumbers.attachEditor(editor);
      editor.setOnScrollChangeListener((view, scrollX, scrollY, oldScrollX, oldScrollY) -> lineNumbers.invalidate());
      editor.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
         lineNumbers.invalidate();
         lineNumbers.postInvalidateOnAnimation();
      });
      editor.addTextChangedListener(new TextWatcher() {
         @Override
         public void beforeTextChanged(CharSequence s, int start, int count, int after) {
         }

         @Override
         public void onTextChanged(CharSequence s, int start, int before, int count) {
         }

         @Override
         public void afterTextChanged(Editable editable) {
            updateLineNumbers();
            if (!loadingTab && activeIndex >= 0 && activeIndex < files.size()) {
               files.get(activeIndex).code = editable.toString();
               store.save(files, activeIndex);
            }
         }
      });
      editorPanel.addView(editor, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

      editorEmptyState = new TextView(this);
      editorEmptyState.setText(s(AppStrings.Key.OPEN_FILE_TO_START_EDITING));
      editorEmptyState.setTextColor(theme.textMuted);
      editorEmptyState.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
      editorEmptyState.setGravity(Gravity.CENTER);
      editorEmptyState.setVisibility(View.GONE);
      editorPanel.addView(editorEmptyState, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

      LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
      panelParams.setMargins(0, dp(6), 0, 0);
      editorPanel.setLayoutParams(panelParams);
      return editorPanel;
   }

   private LinearLayout createConsolePanel() {
      LinearLayout panel = new LinearLayout(this);
      panel.setOrientation(LinearLayout.VERTICAL);
      panel.setPadding(dp(12), dp(8), dp(12), dp(10));
      UiStyles.topRoundedStroke(panel, theme.surface, theme.border, dp(1), dp(14));
      panel.setOnTouchListener((view, event) -> resizeConsoleFromTopEdge(event));

      LinearLayout dragArea = new LinearLayout(this);
      dragArea.setGravity(Gravity.CENTER);
      dragArea.setPadding(0, 0, 0, dp(2));
      dragArea.setOnTouchListener((view, event) -> resizeConsole(view, event));

      View handle = new View(this);
      UiStyles.rounded(handle, theme.border, dp(3));
      LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(54), dp(5));
      handleParams.gravity = Gravity.CENTER_HORIZONTAL;
      dragArea.addView(handle, handleParams);
      panel.addView(dragArea, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(24)));

      consoleHeader = new LinearLayout(this);
      consoleHeader.setGravity(Gravity.CENTER_VERTICAL);

      consoleTab = consoleTab(s(AppStrings.Key.CONSOLE), true);
      consoleTab.setOnClickListener(view -> {
         activeConsoleTab = LOG_INFO;
         renderConsole();
      });
      errorsTab = consoleTab(s(AppStrings.Key.ERRORS), false);
      errorsTab.setOnClickListener(view -> {
         activeConsoleTab = LOG_ERROR;
         renderConsole();
      });
      consoleHeader.addView(consoleTab);
      consoleHeader.addView(errorsTab);

      View spacer = new View(this);
      consoleHeader.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

      ControlIconButton clear = new ControlIconButton(this, theme, ControlIconButton.MODE_CLEAR);
      clear.setOnClickListener(view -> {
         consoleEntries.clear();
         renderConsole();
      });
      consoleHeader.addView(clear, iconParams());
      panel.addView(consoleHeader, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

      consoleScroll = new ScrollView(this);
      consoleScroll.setFillViewport(true);
      consoleScroll.setVerticalScrollBarEnabled(true);
      consoleScroll.setClipToPadding(false);
      consoleScroll.setOnApplyWindowInsetsListener((view, insets) -> {
         view.setPadding(0, 0, 0, insets.getSystemWindowInsetBottom() + dp(14));
         return insets;
      });
      LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
      scrollParams.setMargins(0, dp(8), 0, 0);
      consoleContent = new LinearLayout(this);
      consoleContent.setOrientation(LinearLayout.VERTICAL);
      consoleScroll.addView(consoleContent, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
      panel.addView(consoleScroll, scrollParams);

      return panel;
   }

   private TextView consoleTab(String label, boolean active) {
      TextView tab = new TextView(this);
      tab.setText(label);
      tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
      tab.setGravity(Gravity.CENTER);
      tab.setPadding(dp(14), 0, dp(14), 0);
      UiStyles.roundedStroke(tab, active ? theme.surfaceSoft : theme.surface, active ? theme.accent : theme.border, dp(1), dp(10));
      tab.setTextColor(active ? theme.text : theme.textMuted);
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40));
      params.setMargins(0, 0, dp(8), 0);
      tab.setLayoutParams(params);
      return tab;
   }

   private void toggleRunSketch() {
      if (runInProgress) {
         stopSketchRun();
      } else {
         runSketch();
      }
   }

   private void runSketch() {
      persistCurrentFile();
      store.save(files, activeIndex);
      runInProgress = true;
      updateRunButton();
      addConsoleEntry(LOG_INFO, sf(AppStrings.Key.SAVED_SKETCH_TO, store.sketchDir().getAbsolutePath()));
      addConsoleEntry(LOG_INFO, s(AppStrings.Key.STARTING_SKETCH_RUN));
      scheduleRunStep(420, () -> addConsoleEntry(LOG_INFO, s(AppStrings.Key.PREPARING_PROJECT_SNAPSHOT)));
      scheduleRunStep(900, () -> addConsoleEntry(LOG_INFO, s(AppStrings.Key.LOADING_1)));
      scheduleRunStep(1250, () -> addConsoleEntry(LOG_INFO, s(AppStrings.Key.LOADING_2)));
      scheduleRunStep(1600, () -> addConsoleEntry(LOG_INFO, s(AppStrings.Key.LOADING_3)));
      scheduleRunStep(2050, () -> addConsoleEntry(LOG_INFO, s(AppStrings.Key.COMPILER_AVAILABLE_NOT_CONNECTED)));
      scheduleRunStep(2500, () -> addConsoleEntry(LOG_SUCCESS, s(AppStrings.Key.SKETCH_STARTED)));
   }

   private void stopSketchRun() {
      cancelPendingRunSteps();
      runInProgress = false;
      updateRunButton();
      addConsoleEntry(LOG_INFO, s(AppStrings.Key.SKETCH_STOPPED));
   }

   private void scheduleRunStep(long delayMillis, Runnable action) {
      final Runnable[] holder = new Runnable[1];
      holder[0] = () -> {
         pendingRunSteps.remove(holder[0]);
         if (runInProgress) {
            action.run();
         }
      };
      pendingRunSteps.add(holder[0]);
      runHandler.postDelayed(holder[0], delayMillis);
   }

   private void cancelPendingRunSteps() {
      for (Runnable step : new ArrayList<>(pendingRunSteps)) {
         runHandler.removeCallbacks(step);
      }
      pendingRunSteps.clear();
   }

   private void updateRunButton() {
      if (runButton != null) {
         runButton.setMode(runInProgress ? ControlIconButton.MODE_STOP : ControlIconButton.MODE_PLAY);
      }
   }

   private void addTab() {
      persistCurrentFile();
      showAddFileDialog();
   }

   private void createTab(String requestedName) {
      int nextNumber = files.size() + 1;
      String name = normalizeFileName(requestedName, "Tab" + nextNumber + ".pde");
      File currentProjectDir = store.currentProjectDir();
      files.add(new SketchFile(name, "", null, currentProjectDir == null ? null : currentProjectDir.getAbsolutePath()));
      addConsoleEntry(LOG_INFO, sf(AppStrings.Key.CREATED_FILE, name));
      openTab(files.size() - 1);
      store.save(files, activeIndex);
   }

   private void showAddFileDialog() {
      showTextInputDialog(s(AppStrings.Key.NEW_FILE), "NewFile.pde", "Tab" + (files.size() + 1) + ".pde", s(AppStrings.Key.CREATE), value -> {
         if (normalizeFileName(value, "").isEmpty()) {
            return false;
         }
         createTab(value);
         return true;
      });
   }

   private String normalizeFileName(String requestedName, String fallback) {
      String name = requestedName == null ? "" : requestedName.trim();
      if (name.isEmpty()) {
         name = fallback;
      }
      if (name.isEmpty()) {
         return "";
      }
      if (!name.endsWith(".pde")) {
         name += ".pde";
      }

      String baseName = name;
      String stem = baseName.substring(0, baseName.length() - 4);
      int copy = 2;
      while (fileNameExists(name)) {
         name = stem + " " + copy + ".pde";
         copy++;
      }
      return name;
   }

   private boolean fileNameExists(String name) {
      for (SketchFile file : files) {
         if (file.name.equalsIgnoreCase(name)) {
            return true;
         }
      }
      return false;
   }

   private void removeTab() {
      if (files.size() <= 1) {
         if (!files.isEmpty()) {
            String removedName = files.get(activeIndex).name;
            files.clear();
            activeIndex = -1;
            syncEditorState();
            rebuildTabs();
            updateProjectTitle();
            store.save(files, activeIndex);
            addConsoleEntry(LOG_INFO, sf(AppStrings.Key.DELETED, removedName));
         }
         return;
      }
      String removedName = files.get(activeIndex).name;
      files.remove(activeIndex);
      activeIndex = Math.max(0, activeIndex - 1);
      addConsoleEntry(LOG_INFO, sf(AppStrings.Key.DELETED, removedName));
      openTab(activeIndex);
      store.save(files, activeIndex);
   }

   private void openTab(int index) {
      if (files.isEmpty()) {
         activeIndex = -1;
         syncEditorState();
         rebuildTabs();
         updateProjectTitle();
         return;
      }
      persistCurrentFile();
      activeIndex = Math.max(0, Math.min(index, files.size() - 1));
      loadingTab = true;
      editor.setCode(files.get(activeIndex).code);
      loadingTab = false;
      syncEditorState();
      updateProjectTitle();
      updateLineNumbers();
      rebuildTabs();
   }

   private void persistCurrentFile() {
      if (editor != null && !files.isEmpty() && activeIndex >= 0 && activeIndex < files.size()) {
         SketchFile file = files.get(activeIndex);
         file.code = editor.code();
         if (file.documentUri != null) {
            writeDocumentFile(file.documentUri, file.code);
         }
      }
   }

   private void rebuildTabs() {
      if (tabs == null) {
         return;
      }
      tabs.removeAllViews();
      if (tabsBar != null) {
         tabsBar.setVisibility(files.isEmpty() ? View.GONE : View.VISIBLE);
      }
      for (int i = 0; i < files.size(); i++) {
         final int index = i;
         TextView tab = new TextView(this);
         tab.setText(files.get(i).name);
         tab.setTextColor(index == activeIndex ? theme.accent : theme.textMuted);
         tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
         tab.setTypeface(Typeface.DEFAULT_BOLD);
         tab.setGravity(Gravity.CENTER);
         tab.setPadding(dp(16), 0, dp(16), 0);
         tab.setSingleLine(true);
         tab.setEllipsize(TextUtils.TruncateAt.END);
         tab.setMaxWidth(dp(170));
         UiStyles.roundedStroke(tab, theme.surface, index == activeIndex ? theme.accent : theme.border, dp(1), dp(12));
         tab.setOnClickListener(view -> openTab(index));
         tab.setOnLongClickListener(view -> {
            if (settings != null && settings.hapticEnabled()) {
               view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
            openTab(index);
            tabs.post(() -> {
               if (activeIndex >= 0 && activeIndex < tabs.getChildCount()) {
                  showTabMenu(tabs.getChildAt(activeIndex));
               }
            });
            return true;
         });
         LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40));
         params.setMargins(0, 0, dp(8), 0);
         tabs.addView(tab, params);
      }
   }

   private void clearOpenFiles() {
      persistCurrentFile();
      files.clear();
      activeIndex = -1;
      syncEditorState();
      rebuildTabs();
      updateProjectTitle();
   }

   private void syncEditorState() {
      boolean hasFile = !files.isEmpty() && activeIndex >= 0 && activeIndex < files.size();
      if (editor != null) {
         editor.setVisibility(hasFile ? View.VISIBLE : View.GONE);
         editor.setEnabled(hasFile);
         editor.setFocusable(hasFile);
         editor.setFocusableInTouchMode(hasFile);
         if (!hasFile) {
            loadingTab = true;
            editor.setCode("");
            loadingTab = false;
            editor.clearFocus();
         }
      }
      if (lineNumbers != null) {
         lineNumbers.setVisibility(hasFile ? View.VISIBLE : View.GONE);
      }
      if (editorEmptyState != null) {
         editorEmptyState.setVisibility(hasFile ? View.GONE : View.VISIBLE);
      }
   }

   private void showFilePanel() {
      hideKeyboard();
      if (editor != null) {
         editor.clearFocus();
      }
      if (needsAllFilesAccessForSketchbook()) {
         requestAllFilesAccess();
         return;
      }
      if (fileRoot == null || !fileRoot.exists()) {
         resetToInternalSketchbook();
      }
      renderFileTree();
      if (filePanelScrim == null || filePanelContainer == null) {
         return;
      }
      View overlay = (View) filePanelContainer.getParent();
      if (overlay != null) {
         overlay.setVisibility(View.VISIBLE);
      }
      filePanelScrim.setVisibility(View.VISIBLE);
      filePanelScrim.setAlpha(0f);
      filePanelScrim.animate().alpha(1f).setDuration(150).start();
      filePanelContainer.setVisibility(View.VISIBLE);
      filePanelContainer.setTranslationX(-dp(330));
      filePanelContainer.animate().translationX(0f).setDuration(180).start();
   }

   private void resetToInternalSketchbook() {
      store.refreshStorageState();
      File sketchbookRoot = store == null ? null : store.sketchbookDir();
      File currentProjectDir = store == null ? null : store.currentProjectDir();
      fileRoot = sketchbookRoot == null ? null : DocumentFile.fromFile(sketchbookRoot);
      selectedFolder = currentProjectDir == null ? fileRoot : DocumentFile.fromFile(currentProjectDir);
      expandedFolders.clear();
      if (fileRoot != null) {
         expandedFolders.add(fileKey(fileRoot));
      }
      if (selectedFolder != null) {
         expandedFolders.add(fileKey(selectedFolder));
      }
   }

   private boolean needsAllFilesAccessForSketchbook() {
      return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
         && !Environment.isExternalStorageManager()
         && store != null
         && store.sketchbookDir() != null
         && !store.sketchbookDir().getAbsolutePath().startsWith(Environment.getExternalStorageDirectory().getAbsolutePath());
   }

   private void requestAllFilesAccess() {
      Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
      intent.setData(Uri.parse("package:" + getPackageName()));
      try {
         startActivityForResult(intent, REQUEST_ALL_FILES_ACCESS);
      } catch (RuntimeException exception) {
         Intent fallback = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
         startActivityForResult(fallback, REQUEST_ALL_FILES_ACCESS);
      }
      addConsoleEntry(LOG_INFO, "Grant file access to create Sketchbook in shared storage.");
   }

   private FrameLayout createFilePanelOverlay() {
      FrameLayout overlay = new FrameLayout(this) {
         @Override
         public boolean onInterceptTouchEvent(MotionEvent event) {
            return handleFilePanelSwipeIntercept(event) || super.onInterceptTouchEvent(event);
         }

         @Override
         public boolean onTouchEvent(MotionEvent event) {
            if (handleFilePanelSwipe(event)) {
               return true;
            }
            return super.onTouchEvent(event);
         }
      };
      overlay.setVisibility(View.GONE);
      overlay.setOnApplyWindowInsetsListener((view, insets) -> {
         if (filePanelContainer != null) {
            filePanelContainer.setPadding(dp(14), insets.getSystemWindowInsetTop() + dp(18), dp(14), dp(12));
         }
         return insets;
      });

      filePanelScrim = new View(this);
      filePanelScrim.setBackgroundColor(Color.argb(110, 0, 0, 0));
      filePanelScrim.setAlpha(0f);
      filePanelScrim.setOnClickListener(view -> hideFilePanel());
      overlay.addView(filePanelScrim, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

      LinearLayout panel = new LinearLayout(this);
      filePanelContainer = panel;
      panel.setOrientation(LinearLayout.VERTICAL);
      panel.setPadding(dp(14), dp(18), dp(14), dp(12));
      UiStyles.roundedStroke(panel, theme.surface, theme.border, dp(1), dp(14));

      LinearLayout header = new LinearLayout(this);
      header.setGravity(Gravity.CENTER_VERTICAL);
      filePanelTitle = new TextView(this);
      filePanelTitle.setTextColor(theme.text);
      filePanelTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
      filePanelTitle.setTypeface(Typeface.DEFAULT_BOLD);
      filePanelTitle.setSingleLine(true);
      filePanelTitle.setEllipsize(TextUtils.TruncateAt.END);
      filePanelTitle.setOnClickListener(view -> {
         if (fileRoot != null) {
            selectedFolder = fileRoot;
            renderFileTree();
         }
      });
      header.addView(filePanelTitle, new LinearLayout.LayoutParams(0, dp(42), 1f));

      ControlIconButton close = new ControlIconButton(this, theme, ControlIconButton.MODE_CLEAR);
      close.setOnClickListener(view -> hideFilePanel());
      header.addView(close, iconParams());
      panel.addView(header, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

      LinearLayout actions = new LinearLayout(this);
      actions.setGravity(Gravity.CENTER_VERTICAL);
      actions.addView(filePanelButton(s(AppStrings.Key.OPEN_ELLIPSIS), this::openDeviceFolder), filePanelOpenButtonParams());
      actions.addView(filePanelIconButton(ControlIconButton.MODE_ADD_FOLDER, this::createFolderInSelectedFolder), filePanelIconButtonParams(true));
      actions.addView(filePanelIconButton(ControlIconButton.MODE_ADD_FILE, this::createFileInSelectedFolder), filePanelIconButtonParams(false));
      panel.addView(actions, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

      ScrollView scroll = new ScrollView(this);
      scroll.setFillViewport(true);
      scroll.setVerticalScrollBarEnabled(true);
      scroll.setClickable(true);
      scroll.setOnClickListener(view -> selectProjectRoot());
      fileTreeContent = new LinearLayout(this);
      fileTreeContent.setOrientation(LinearLayout.VERTICAL);
      fileTreeContent.setClickable(true);
      fileTreeContent.setOnClickListener(view -> selectProjectRoot());
      scroll.addView(fileTreeContent, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
      LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
      scrollParams.setMargins(0, dp(12), 0, 0);
      panel.addView(scroll, scrollParams);

      FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(dp(330), FrameLayout.LayoutParams.MATCH_PARENT);
      panelParams.gravity = Gravity.START;
      overlay.addView(panel, panelParams);
      return overlay;
   }

   private void hideFilePanel() {
      if (filePanelScrim == null || filePanelContainer == null) {
         return;
      }
      filePanelContainer.animate().cancel();
      filePanelScrim.animate().cancel();
      filePanelScrim.animate().alpha(0f).setDuration(140).start();
      filePanelContainer.animate().translationX(-filePanelContainer.getWidth()).setDuration(160).withEndAction(() -> {
         if (filePanelScrim != null) {
            filePanelScrim.setVisibility(View.GONE);
         }
         if (filePanelContainer != null) {
            filePanelContainer.setVisibility(View.GONE);
         }
         View parent = (View) filePanelContainer.getParent();
         if (parent != null) {
            parent.setVisibility(View.GONE);
         }
      }).start();
   }

   private boolean handleFilePanelSwipe(MotionEvent event) {
      if (filePanelContainer == null || filePanelScrim == null) {
         return false;
      }
      switch (event.getActionMasked()) {
         case MotionEvent.ACTION_DOWN:
            filePanelTouchActive = isTouchInsideFilePanel(event);
            if (!filePanelTouchActive) {
               return false;
            }
            filePanelStartX = event.getRawX();
            filePanelStartY = event.getRawY();
            filePanelStartTranslationX = filePanelContainer.getTranslationX();
            filePanelDragging = false;
            return false;
         case MotionEvent.ACTION_MOVE:
            if (!filePanelTouchActive) {
               return false;
            }
            float deltaX = event.getRawX() - filePanelStartX;
            float deltaY = event.getRawY() - filePanelStartY;
            if (!filePanelDragging) {
               if (Math.abs(deltaX) < dp(8) || Math.abs(deltaX) <= Math.abs(deltaY)) {
                  return false;
               }
               filePanelDragging = true;
            }
            float translationX = Math.min(0f, filePanelStartTranslationX + deltaX);
            filePanelContainer.setTranslationX(translationX);
            float width = Math.max(1, filePanelContainer.getWidth());
            float progress = 1f - Math.min(1f, Math.abs(translationX) / width);
            filePanelScrim.setAlpha(progress);
            return true;
         case MotionEvent.ACTION_UP:
         case MotionEvent.ACTION_CANCEL:
            if (!filePanelTouchActive) {
               return false;
            }
            filePanelTouchActive = false;
            if (!filePanelDragging) {
               return false;
            }
            filePanelDragging = false;
            float panelWidth = Math.max(1, filePanelContainer.getWidth());
            if (Math.abs(filePanelContainer.getTranslationX()) > panelWidth * 0.35f) {
               hideFilePanel();
            } else {
               filePanelContainer.animate().translationX(0f).setDuration(160).start();
               filePanelScrim.animate().alpha(1f).setDuration(160).start();
            }
            return true;
         default:
            return false;
      }
   }

   private boolean handleFilePanelSwipeIntercept(MotionEvent event) {
      if (filePanelContainer == null) {
         return false;
      }
      switch (event.getActionMasked()) {
         case MotionEvent.ACTION_DOWN:
            filePanelTouchActive = isTouchInsideFilePanel(event);
            if (!filePanelTouchActive) {
               return false;
            }
            filePanelStartX = event.getRawX();
            filePanelStartY = event.getRawY();
            filePanelStartTranslationX = filePanelContainer.getTranslationX();
            filePanelDragging = false;
            return false;
         case MotionEvent.ACTION_MOVE:
            if (!filePanelTouchActive) {
               return false;
            }
            float deltaX = event.getRawX() - filePanelStartX;
            float deltaY = event.getRawY() - filePanelStartY;
            if (Math.abs(deltaX) >= dp(8) && Math.abs(deltaX) > Math.abs(deltaY)) {
               filePanelDragging = true;
               return true;
            }
            return false;
         case MotionEvent.ACTION_UP:
         case MotionEvent.ACTION_CANCEL:
            filePanelTouchActive = false;
            filePanelDragging = false;
            return false;
         default:
            return false;
      }
   }

   private boolean isTouchInsideFilePanel(MotionEvent event) {
      if (filePanelContainer == null || filePanelContainer.getVisibility() != View.VISIBLE) {
         return false;
      }
      int[] location = new int[2];
      filePanelContainer.getLocationOnScreen(location);
      float x = event.getRawX();
      float y = event.getRawY();
      return x >= location[0]
         && x <= location[0] + filePanelContainer.getWidth()
         && y >= location[1]
         && y <= location[1] + filePanelContainer.getHeight();
   }

   private TextView filePanelButton(String label, Runnable action) {
      TextView button = new TextView(this);
      button.setText(label);
      button.setTextColor(theme.text);
      button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
      button.setGravity(Gravity.CENTER);
      UiStyles.roundedStroke(button, theme.surfaceSoft, theme.border, dp(1), dp(10));
      button.setOnClickListener(view -> action.run());
      return button;
   }

   private ControlIconButton filePanelIconButton(int mode, Runnable action) {
      ControlIconButton button = new ControlIconButton(this, theme, mode);
      button.setOnClickListener(view -> action.run());
      return button;
   }

   private LinearLayout.LayoutParams filePanelOpenButtonParams() {
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1f);
      params.setMargins(0, 0, dp(8), 0);
      return params;
   }

   private LinearLayout.LayoutParams filePanelIconButtonParams(boolean withRightMargin) {
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(48));
      if (withRightMargin) {
         params.setMargins(0, 0, dp(2), 0);
      }
      return params;
   }

   private void openDeviceFolder() {
      Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
      intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
         | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
         | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
         | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
      startActivityForResult(intent, REQUEST_OPEN_FOLDER);
   }

   private void renderFileTree() {
      if (fileTreeContent == null) {
         return;
      }
      fileTreeContent.removeAllViews();
      if (fileRoot == null || !fileRoot.exists()) {
         filePanelTitle.setText(s(AppStrings.Key.FILES));
         TextView empty = createMutedText(s(AppStrings.Key.CHOOSE_FOLDER_TO_SHOW_FILES));
         fileTreeContent.addView(empty);
         return;
      }

      updateFilePanelTitle();
      DocumentFile[] children = fileRoot.listFiles();
      if (children == null || children.length == 0) {
         fileTreeContent.addView(createMutedText(s(AppStrings.Key.THIS_PROJECT_IS_EMPTY)));
         return;
      }
      sortDocuments(children);
      for (DocumentFile child : children) {
         renderDocument(child, 0);
      }
   }

   private void renderDocument(DocumentFile document, int depth) {
      fileTreeContent.addView(createFileRow(document, depth));
      if (!document.isDirectory() || !expandedFolders.contains(fileKey(document))) {
         return;
      }
      DocumentFile[] children = document.listFiles();
      sortDocuments(children);
      for (DocumentFile child : children) {
         renderDocument(child, depth + 1);
      }
   }

   private void sortDocuments(DocumentFile[] documents) {
      Arrays.sort(documents, (left, right) -> {
         if (left.isDirectory() != right.isDirectory()) {
            return left.isDirectory() ? -1 : 1;
         }
         return displayName(left).compareToIgnoreCase(displayName(right));
      });
   }

   private void updateFilePanelTitle() {
      if (fileRoot == null || filePanelTitle == null) {
         return;
      }
      filePanelTitle.setText(displayName(fileRoot));
      filePanelTitle.setTextColor(selectedFolder == fileRoot ? theme.accent : theme.text);
   }

   private void selectProjectRoot() {
      if (fileRoot == null) {
         return;
      }
      selectedFolder = fileRoot;
      updateFilePanelTitle();
      renderFileTree();
   }

   private LinearLayout createFileRow(DocumentFile document, int depth) {
      boolean folder = document.isDirectory();
      boolean expanded = folder && expandedFolders.contains(fileKey(document));
      boolean selected = selectedFolder != null && fileKey(selectedFolder).equals(fileKey(document));

      LinearLayout row = new LinearLayout(this);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(dp(8 + depth * 14), 0, dp(8), 0);
      row.setHapticFeedbackEnabled(settings == null || settings.hapticEnabled());
      UiStyles.rounded(row, selected ? theme.surfaceSoft : theme.surface, dp(8));

      FileIconView chevron = new FileIconView(this, theme, folder
         ? (expanded ? FileIconView.MODE_CHEVRON_DOWN : FileIconView.MODE_CHEVRON_RIGHT)
         : FileIconView.MODE_CHEVRON_RIGHT);
      if (!folder) {
         chevron.setVisibility(View.INVISIBLE);
      }
      row.addView(chevron, new LinearLayout.LayoutParams(dp(18), dp(38)));

      FileIconView icon = new FileIconView(this, theme, folder
         ? (expanded ? FileIconView.MODE_FOLDER_OPEN : FileIconView.MODE_FOLDER_CLOSED)
         : FileIconView.MODE_FILE);
      LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
      iconParams.setMargins(0, 0, dp(8), 0);
      row.addView(icon, iconParams);

      TextView name = new TextView(this);
      name.setText(displayName(document));
      name.setTextColor(theme.text);
      name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
      name.setSingleLine(true);
      name.setEllipsize(TextUtils.TruncateAt.END);
      name.setGravity(Gravity.CENTER_VERTICAL);
      name.setIncludeFontPadding(false);
      row.addView(name, new LinearLayout.LayoutParams(0, dp(38), 1f));

      row.setOnClickListener(view -> {
         if (folder) {
            selectedFolder = document;
            String key = fileKey(document);
            if (expandedFolders.contains(key)) {
               expandedFolders.remove(key);
            } else {
               expandedFolders.add(key);
            }
            renderFileTree();
         } else {
            openDocumentFile(document);
         }
      });
      row.setOnLongClickListener(view -> {
         if (settings != null && settings.hapticEnabled()) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
         }
         if (folder) {
            selectedFolder = document;
            updateFilePanelTitle();
         }
         showFileNodeMenu(view, document);
         return true;
      });

      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
      params.setMargins(0, 0, 0, dp(2));
      row.setLayoutParams(params);
      return row;
   }

   private void showFileNodeMenu(View anchor, DocumentFile document) {
      if (document.isDirectory()) {
         showContextMenu(anchor, dp(196),
            new ContextMenuItem(s(AppStrings.Key.NEW_FILE_MENU), this::createFileInSelectedFolder),
            new ContextMenuItem(s(AppStrings.Key.NEW_FOLDER_MENU), this::createFolderInSelectedFolder),
            new ContextMenuItem(s(AppStrings.Key.RENAME), () -> showRenameDocumentDialog(document)),
            new ContextMenuItem(s(AppStrings.Key.DELETE), () -> showDeleteDocumentConfirmation(document))
         );
      } else {
         showContextMenu(anchor, dp(172),
            new ContextMenuItem(s(AppStrings.Key.OPEN), () -> openDocumentFile(document)),
            new ContextMenuItem(s(AppStrings.Key.RENAME), () -> showRenameDocumentDialog(document)),
            new ContextMenuItem(s(AppStrings.Key.DELETE), () -> showDeleteDocumentConfirmation(document))
         );
      }
   }

   private void createFolderInSelectedFolder() {
      DocumentFile parent = writableSelectedFolder();
      if (parent == null) {
         return;
      }
      showTextInputDialog(s(AppStrings.Key.NEW_FOLDER), s(AppStrings.Key.FOLDER), s(AppStrings.Key.FOLDER), s(AppStrings.Key.CREATE), value -> {
         String name = normalizeDocumentName(value);
         if (name.isEmpty()) {
            return false;
         }
         DocumentFile created = parent.createDirectory(uniqueChildName(parent, name));
         if (created == null) {
            addConsoleEntry(LOG_ERROR, s(AppStrings.Key.COULD_NOT_CREATE_FOLDER));
            return false;
         }
         expandedFolders.add(fileKey(parent));
         selectedFolder = created;
         addConsoleEntry(LOG_INFO, sf(AppStrings.Key.CREATED_FOLDER, displayName(created)));
         renderFileTree();
         return true;
      });
   }

   private void createFileInSelectedFolder() {
      DocumentFile parent = writableSelectedFolder();
      if (parent == null) {
         return;
      }
      showTextInputDialog(s(AppStrings.Key.NEW_FILE), "NewFile.pde", "NewFile.pde", s(AppStrings.Key.CREATE), value -> {
         String name = normalizeDocumentName(value);
         if (name.isEmpty()) {
            return false;
         }
         DocumentFile created = createDocumentFile(parent, uniqueChildName(parent, name));
         if (created == null) {
            addConsoleEntry(LOG_ERROR, s(AppStrings.Key.COULD_NOT_CREATE_FILE));
            return false;
         }
         expandedFolders.add(fileKey(parent));
         addConsoleEntry(LOG_INFO, sf(AppStrings.Key.CREATED_FILE, displayName(created)));
         renderFileTree();
         openDocumentFile(created, false);
         return true;
      });
   }

   private DocumentFile writableSelectedFolder() {
      DocumentFile folder = selectedFolder != null ? selectedFolder : fileRoot;
      if (folder == null || !folder.isDirectory() || !folder.canWrite()) {
         addConsoleEntry(LOG_ERROR, s(AppStrings.Key.CHOOSE_WRITABLE_FOLDER_FIRST));
         activeConsoleTab = LOG_ERROR;
         renderConsole();
         return null;
      }
      return folder;
   }

   private void showDeleteDocumentConfirmation(DocumentFile document) {
      String title = document.isDirectory() ? s(AppStrings.Key.DELETE_FOLDER) : s(AppStrings.Key.DELETE_FILE);
      String message = sf(AppStrings.Key.DELETE_QUESTION, displayName(document));
      showConfirmationDialog(title, message, s(AppStrings.Key.DELETE), () -> {
         String name = displayName(document);
         List<String> deletedUris = collectDocumentUris(document);
         String key = fileKey(document);
         boolean deletedCurrentLocalProject = isCurrentLocalProject(document);
         String deletedLocalProjectPath = localProjectDirForDocument(document);
         boolean deleted = deleteDocumentRecursively(document);
         if (!deleted) {
            addConsoleEntry(LOG_ERROR, sf(AppStrings.Key.COULD_NOT_DELETE, name));
            activeConsoleTab = LOG_ERROR;
            renderConsole();
            return;
         }
         if (deletedCurrentLocalProject) {
            store.deleteProject(new File(document.getUri().getPath()));
            fileRoot = DocumentFile.fromFile(store.sketchbookDir());
            selectedFolder = fileRoot;
            expandedFolders.clear();
            if (fileRoot != null) {
               expandedFolders.add(fileKey(fileRoot));
            }
         }
         expandedFolders.remove(key);
         if (selectedFolder != null && key.equals(fileKey(selectedFolder))) {
            selectedFolder = fileRoot;
         }
         closeTabsForDeletedDocuments(deletedUris, deletedCurrentLocalProject, deletedLocalProjectPath);
         addConsoleEntry(LOG_INFO, sf(AppStrings.Key.DELETED, name));
         renderFileTree();
      });
   }

   private void showRenameDocumentDialog(DocumentFile document) {
      String oldUri = document.getUri().toString();
      showTextInputDialog(s(AppStrings.Key.RENAME), displayName(document), displayName(document), s(AppStrings.Key.RENAME), value -> {
         String name = normalizeDocumentName(value);
         if (name.isEmpty()) {
            return false;
         }
         boolean renamed = document.renameTo(name);
         if (!renamed) {
            addConsoleEntry(LOG_ERROR, sf(AppStrings.Key.COULD_NOT_RENAME, displayName(document)));
            return false;
         }
         updateRenamedTab(oldUri, document);
         addConsoleEntry(LOG_INFO, sf(AppStrings.Key.RENAMED_TO, name));
         renderFileTree();
         rebuildTabs();
         return true;
      });
   }

   private void updateRenamedTab(String oldUri, DocumentFile document) {
      for (SketchFile file : files) {
         if (oldUri.equals(file.documentUri)) {
            file.name = displayName(document);
            file.documentUri = document.getUri().toString();
            updateProjectTitle();
            return;
         }
      }
   }

   private void openDocumentFile(DocumentFile document) {
      openDocumentFile(document, true);
   }

   private void openDocumentFile(DocumentFile document, boolean closePanel) {
      if (!document.isFile()) {
         return;
      }
      if (!isTextDocument(document)) {
         addConsoleEntry(LOG_ERROR, sf(AppStrings.Key.IS_NOT_A_TEXT_FILE, displayName(document)));
         activeConsoleTab = LOG_ERROR;
         renderConsole();
         return;
      }

      persistCurrentFile();
      String uri = document.getUri().toString();
      if (isProjectLocalFile(document)) {
         File projectDir = localProjectDir(document);
         if (projectDir != null) {
            File currentProjectDir = store.currentProjectDir();
            if (currentProjectDir == null || !currentProjectDir.equals(projectDir)) {
               openInternalProject(projectDir);
            }
         }
         for (int i = 0; i < files.size(); i++) {
            if (files.get(i).documentUri == null
               && files.get(i).name.equalsIgnoreCase(displayName(document))
               && sameLocalProject(files.get(i).localProjectPath, projectDir)) {
               if (activeIndex != i) {
                  openTab(i);
               }
               if (closePanel) {
                  hideFilePanel();
               }
               return;
            }
         }
      }
      for (int i = 0; i < files.size(); i++) {
         if (uri.equals(files.get(i).documentUri)) {
            openTab(i);
            if (closePanel) {
               hideFilePanel();
            }
            return;
         }
      }
      File localProjectDir = localProjectDir(document);
      files.add(new SketchFile(displayName(document), readDocumentFile(document), uri,
         localProjectDir == null ? null : localProjectDir.getAbsolutePath()));
      openTab(files.size() - 1);
      if (closePanel) {
         hideFilePanel();
      }
      addConsoleEntry(LOG_INFO, sf(AppStrings.Key.OPENED, displayName(document)));
   }

   private boolean isProjectLocalFile(DocumentFile document) {
      Uri uri = document.getUri();
      return uri != null
         && "file".equalsIgnoreCase(uri.getScheme())
         && localProjectDir(document) != null;
    }

   private boolean isTextDocument(DocumentFile document) {
      String name = displayName(document).toLowerCase(Locale.US);
      return name.endsWith(".pde")
         || name.endsWith(".java")
         || name.endsWith(".kt")
         || name.endsWith(".txt")
         || name.endsWith(".xml")
         || name.endsWith(".json")
         || name.endsWith(".md")
         || name.endsWith(".gradle");
   }

   private String readDocumentFile(DocumentFile document) {
      try (InputStream input = getContentResolver().openInputStream(document.getUri())) {
         if (input == null) {
            return "";
         }
         byte[] buffer = new byte[8192];
         StringBuilder builder = new StringBuilder();
         int read;
         while ((read = input.read(buffer)) != -1) {
            builder.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
         }
         return builder.toString();
      } catch (IOException exception) {
         addConsoleEntry(LOG_ERROR, sf(AppStrings.Key.COULD_NOT_READ, displayName(document)));
         return "";
      }
   }

   private void openInternalProject(File projectDir) {
      persistCurrentFile();
      store.switchProject(projectDir.getName());
      files.clear();
      files.addAll(store.loadFiles());
      activeIndex = files.isEmpty() ? -1 : 0;
      fileRoot = DocumentFile.fromFile(store.sketchbookDir());
      selectedFolder = DocumentFile.fromFile(store.currentProjectDir());
      expandedFolders.clear();
      if (fileRoot != null) {
         expandedFolders.add(fileKey(fileRoot));
      }
      if (selectedFolder != null) {
         expandedFolders.add(fileKey(selectedFolder));
      }
      if (!files.isEmpty()) {
         loadingTab = true;
         editor.setCode(files.get(activeIndex).code);
         loadingTab = false;
         updateLineNumbers();
      } else if (editor != null) {
         loadingTab = true;
         editor.setCode("");
         loadingTab = false;
         editor.clearFocus();
      }
      syncEditorState();
      updateProjectTitle();
      rebuildTabs();
      renderFileTree();
      if (!files.isEmpty()) {
         updateLineNumbers();
      } else {
         rebuildTabs();
      }
      store.save(files, activeIndex);
   }

   private File localProjectDir(DocumentFile document) {
      Uri uri = document.getUri();
      if (uri == null || !"file".equalsIgnoreCase(uri.getScheme()) || store == null) {
         return null;
      }
      String path = uri.getPath();
      if (path == null || path.isEmpty()) {
         return null;
      }
      File file = new File(path);
      File projectDir = file.getParentFile();
      File sketchbookDir = store.sketchbookDir();
      if (projectDir == null || sketchbookDir == null) {
         return null;
      }
      String projectPath = projectDir.getAbsolutePath();
      String sketchbookPath = sketchbookDir.getAbsolutePath();
      return projectPath.startsWith(sketchbookPath + File.separator) ? projectDir : null;
   }

   private boolean isSketchbookRoot(DocumentFile root) {
      if (root == null) {
         return false;
      }
      DocumentFile internalRoot = DocumentFile.fromFile(store.sketchbookDir());
      return internalRoot != null && fileKey(internalRoot).equals(fileKey(root));
   }

   private String sanitizeProjectFolderName(String value) {
      String sanitized = value == null ? "" : value.replace("/", "").replace("\\", "").trim();
      return sanitized.isEmpty() ? store.currentProjectName() : sanitized;
   }

   private String uniqueProjectFolderName(String baseName) {
      File root = store.sketchbookDir();
      String name = baseName;
      int copy = 2;
      while (new File(root, name).exists()) {
         name = baseName + " " + copy;
         copy++;
      }
      return name;
   }

   private boolean copyDocumentTreeToDirectory(DocumentFile source, File targetDir) {
      if (source == null) {
         return false;
      }
      if (source.isDirectory()) {
         if (!targetDir.exists() && !targetDir.mkdirs()) {
            return false;
         }
         DocumentFile[] children = source.listFiles();
         for (DocumentFile child : children) {
            File targetChild = new File(targetDir, displayName(child));
            if (!copyDocumentTreeToDirectory(child, targetChild)) {
               return false;
            }
         }
         return true;
      }
      try (InputStream input = getContentResolver().openInputStream(source.getUri())) {
         if (input == null) {
            return false;
         }
         File parent = targetDir.getParentFile();
         if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
         }
         try (OutputStream output = new java.io.FileOutputStream(targetDir)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
               output.write(buffer, 0, read);
            }
         }
         return true;
      } catch (IOException exception) {
         return false;
      }
   }

   private void writeDocumentFile(String uri, String content) {
      try (OutputStream output = getContentResolver().openOutputStream(Uri.parse(uri), "wt")) {
         if (output != null) {
            output.write((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
         }
      } catch (IOException exception) {
         addConsoleEntry(LOG_ERROR, s(AppStrings.Key.COULD_NOT_SAVE_EXTERNAL_FILE));
      }
   }

   private String normalizeDocumentName(String value) {
      return value == null ? "" : value.replace("/", "").replace("\\", "").trim();
   }

   private DocumentFile createDocumentFile(DocumentFile parent, String name) {
      if ("file".equalsIgnoreCase(parent.getUri().getScheme())) {
         File parentFile = new File(parent.getUri().getPath());
         File childFile = new File(parentFile, name);
         try {
            if (!childFile.exists() && !childFile.createNewFile()) {
               return null;
            }
            return DocumentFile.fromFile(childFile);
         } catch (IOException exception) {
            return null;
         }
      }
      try {
         Uri createdUri = DocumentsContract.createDocument(getContentResolver(), parent.getUri(), fileMimeType(name), name);
         if (createdUri == null) {
            return null;
         }
         return DocumentFile.fromSingleUri(this, createdUri);
      } catch (FileNotFoundException | RuntimeException exception) {
         return null;
      }
   }

   private String fileMimeType(String name) {
      int dot = name.lastIndexOf('.');
      if (dot > 0 && dot < name.length() - 1) {
         String extension = name.substring(dot + 1).toLowerCase(Locale.US);
         String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
         if (mime != null && !mime.isEmpty()) {
            return mime;
         }
      }
      return "application/octet-stream";
   }

   private boolean deleteDocumentRecursively(DocumentFile document) {
      if (document.isDirectory()) {
         DocumentFile[] children = document.listFiles();
         for (DocumentFile child : children) {
            if (!deleteDocumentRecursively(child)) {
               return false;
            }
         }
      }
      return document.delete();
   }

   private List<String> collectDocumentUris(DocumentFile document) {
      List<String> uris = new ArrayList<>();
      uris.add(fileKey(document));
      if (document.isDirectory()) {
         DocumentFile[] children = document.listFiles();
         for (DocumentFile child : children) {
            uris.addAll(collectDocumentUris(child));
         }
      }
      return uris;
   }

   private void closeTabsForDeletedDocuments(List<String> deletedUris, boolean deletedCurrentLocalProject, String deletedLocalProjectPath) {
      boolean removedAny = false;
      for (int i = files.size() - 1; i >= 0; i--) {
         String uri = files.get(i).documentUri;
         boolean deletedExternalTab = uri != null && deletedUris.contains(uri);
         boolean deletedLocalTab = belongsToDeletedLocalProject(files.get(i).localProjectPath, deletedLocalProjectPath);
         if (deletedExternalTab || deletedLocalTab || (uri == null && deletedCurrentLocalProject)) {
            files.remove(i);
            removedAny = true;
            if (i < activeIndex) {
               activeIndex--;
            } else if (i == activeIndex) {
               activeIndex = files.isEmpty() ? -1 : Math.max(0, activeIndex - 1);
      }
    }
      }
      if (removedAny) {
         if (files.isEmpty()) {
            activeIndex = -1;
            syncEditorState();
            rebuildTabs();
            updateProjectTitle();
         } else {
            openTab(Math.max(0, Math.min(activeIndex, files.size() - 1)));
         }
         store.save(files, activeIndex);
      }
   }

   private String localProjectDirForDocument(DocumentFile document) {
      if (document != null && document.isDirectory()) {
         Uri uri = document.getUri();
         if (uri != null && "file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
            return new File(uri.getPath()).getAbsolutePath();
         }
      }
      File projectDir = localProjectDir(document);
      if (projectDir != null) {
         return projectDir.getAbsolutePath();
      }
      return null;
   }

   private boolean isCurrentLocalProject(DocumentFile document) {
      if (document == null || store == null) {
         return false;
      }
      File currentProjectDir = store.currentProjectDir();
      if (currentProjectDir == null) {
         return false;
      }
      String deletedProjectPath = localProjectDirForDocument(document);
      return deletedProjectPath != null && deletedProjectPath.equals(currentProjectDir.getAbsolutePath());
   }

   private boolean sameLocalProject(String localProjectPath, File projectDir) {
      if (localProjectPath == null || projectDir == null) {
         return false;
      }
      return localProjectPath.equals(projectDir.getAbsolutePath());
   }

   private boolean belongsToDeletedLocalProject(String localProjectPath, String deletedLocalProjectPath) {
      if (localProjectPath == null || deletedLocalProjectPath == null) {
         return false;
      }
      return localProjectPath.equals(deletedLocalProjectPath)
         || localProjectPath.startsWith(deletedLocalProjectPath + File.separator);
   }

   private String uniqueChildName(DocumentFile parent, String requestedName) {
      String name = requestedName;
      String stem = requestedName;
      String extension = "";
      int dot = requestedName.lastIndexOf('.');
      if (dot > 0) {
         stem = requestedName.substring(0, dot);
         extension = requestedName.substring(dot);
      }
      int copy = 2;
      while (parent.findFile(name) != null) {
         name = stem + " " + copy + extension;
         copy++;
      }
      return name;
   }

   private TextView createMutedText(String text) {
      TextView view = new TextView(this);
      view.setText(text);
      view.setTextColor(theme.textMuted);
      view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
      view.setPadding(dp(8), dp(12), dp(8), 0);
      return view;
   }

   private String displayName(DocumentFile document) {
      String name = document.getName();
      return name == null || name.isEmpty() ? s(AppStrings.Key.UNTITLED) : name;
   }

   private String fileKey(DocumentFile document) {
      return document.getUri().toString();
   }

   private void showProjectMenu(View anchor) {
      boolean localSketch = fileRoot != null && isSketchbookRoot(fileRoot);
      showContextMenu(anchor, dp(238),
         new ContextMenuItem(s(AppStrings.Key.NEW_SKETCH), this::createNewSketchProject),
         new ContextMenuItem(s(AppStrings.Key.LOAD_SKETCH), this::showFilePanel),
         new ContextMenuItem(localSketch ? s(AppStrings.Key.RENAME_SKETCH) : s(AppStrings.Key.MOVE_TO_SKETCHBOOK), localSketch ? this::renameCurrentSketch : this::moveCurrentProjectToSketchbook),
         new ContextMenuItem(s(AppStrings.Key.DELETE_SKETCH), this::deleteCurrentSketch),
         new ContextMenuItem(s(AppStrings.Key.TOOLS), this::showToolsDialog),
         new ContextMenuItem(s(AppStrings.Key.SKETCH_PROPERTIES), this::openSketchProperties),
         new ContextMenuItem(s(AppStrings.Key.SETTINGS), this::openSettings)
      );
   }

   private void showToolsDialog() {
      Dialog dialog = new Dialog(this);
      dialog.getWindow();

      LinearLayout content = new LinearLayout(this);
      content.setOrientation(LinearLayout.VERTICAL);
      content.setPadding(dp(10), dp(10), dp(10), dp(10));
      UiStyles.roundedStroke(content, theme.surface, theme.border, dp(1), dp(14));

      TextView titleView = new TextView(this);
      titleView.setText(s(AppStrings.Key.TOOLS));
      titleView.setTextColor(theme.text);
      titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
      titleView.setTypeface(Typeface.DEFAULT_BOLD);
      titleView.setPadding(dp(8), dp(4), dp(8), dp(10));
      content.addView(titleView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

      ContextMenuItem[] items = {
         new ContextMenuItem(s(AppStrings.Key.AUTO_FORMAT), this::autoFormatActivePde),
         new ContextMenuItem(s(AppStrings.Key.COLOR_SELECTOR), this::showColorSelectorDialog),
         new ContextMenuItem(s(AppStrings.Key.FIND_REPLACE), () -> showPlaceholderAction(s(AppStrings.Key.FIND_REPLACE))),
         new ContextMenuItem(s(AppStrings.Key.IMPORT_LIBRARY), () -> showPlaceholderAction(s(AppStrings.Key.IMPORT_LIBRARY))),
         new ContextMenuItem(s(AppStrings.Key.OPEN_REFERENCE), this::openReference),
         new ContextMenuItem(s(AppStrings.Key.AI_AGENT), () -> showPlaceholderAction(s(AppStrings.Key.AI_AGENT)))
      };
      for (ContextMenuItem item : items) {
         TextView row = new TextView(this);
         row.setText(item.title);
         row.setTextColor(theme.text);
         row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
         row.setGravity(Gravity.CENTER_VERTICAL);
         row.setPadding(dp(12), 0, dp(12), 0);
         UiStyles.rounded(row, theme.surface, dp(8));
         row.setOnClickListener(view -> {
            dialog.dismiss();
            item.action.run();
         });
         LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
         params.setMargins(0, 0, 0, dp(4));
         content.addView(row, params);
      }

      dialog.setContentView(content, new ViewGroup.LayoutParams(dp(280), ViewGroup.LayoutParams.WRAP_CONTENT));
      if (dialog.getWindow() != null) {
         dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
         dialog.getWindow().setGravity(Gravity.CENTER);
      }
      dialog.show();
   }

   private void openSettings() {
      startActivity(new Intent(this, SettingsActivity.class));
   }

   private void openSketchProperties() {
      Intent intent = new Intent(this, SketchPropertiesActivity.class);
      intent.putExtra(SketchPropertiesActivity.EXTRA_PROJECT_NAME, projectName());
      File currentProjectDir = store == null ? null : store.currentProjectDir();
      if (currentProjectDir != null) {
         intent.putExtra(SketchPropertiesActivity.EXTRA_PROJECT_PATH, currentProjectDir.getAbsolutePath());
      }
      startActivity(intent);
   }

   private void createNewSketchProject() {
      File projectDir = store.createNewSketchProject();
      openInternalProject(projectDir);
      addConsoleEntry(LOG_INFO, sf(AppStrings.Key.OPENED_FOLDER, projectDir.getName()));
   }

   private void moveCurrentProjectToSketchbook() {
      File sketchbookRoot = store.sketchbookDir();
      File currentInternalProject = store.currentProjectDir();
      if (fileRoot == null || isSketchbookRoot(fileRoot)) {
         openInternalProject(currentInternalProject);
         addConsoleEntry(LOG_INFO, sf(AppStrings.Key.OPENED_FOLDER, currentInternalProject.getName()));
         return;
      }

      String targetName = uniqueProjectFolderName(sanitizeProjectFolderName(displayName(fileRoot)));
      File targetDir = new File(sketchbookRoot, targetName);
      if (!copyDocumentTreeToDirectory(fileRoot, targetDir)) {
         addConsoleEntry(LOG_ERROR, s(AppStrings.Key.COULD_NOT_CREATE_FOLDER));
         activeConsoleTab = LOG_ERROR;
         renderConsole();
         return;
      }
      openInternalProject(targetDir);
      addConsoleEntry(LOG_INFO, sf(AppStrings.Key.OPENED_FOLDER, targetDir.getName()));
   }

   private void renameCurrentSketch() {
      File currentProjectDir = store.currentProjectDir();
      if (currentProjectDir == null) {
         return;
      }
      showTextInputDialog(s(AppStrings.Key.RENAME_SKETCH), currentProjectDir.getName(), currentProjectDir.getName(), s(AppStrings.Key.RENAME), value -> {
         String normalized = sanitizeProjectFolderName(value);
         if (normalized.isEmpty()) {
            return false;
         }
         File targetDir = new File(store.sketchbookDir(), normalized);
         if (currentProjectDir.equals(targetDir)) {
            return true;
         }
         if (targetDir.exists() || !currentProjectDir.renameTo(targetDir)) {
            addConsoleEntry(LOG_ERROR, sf(AppStrings.Key.COULD_NOT_RENAME, currentProjectDir.getName()));
            activeConsoleTab = LOG_ERROR;
            renderConsole();
            return false;
         }
         store.switchProject(targetDir.getName());
         fileRoot = DocumentFile.fromFile(store.sketchbookDir());
         selectedFolder = DocumentFile.fromFile(store.currentProjectDir());
         expandedFolders.clear();
         if (fileRoot != null) {
            expandedFolders.add(fileKey(fileRoot));
         }
         if (selectedFolder != null) {
            expandedFolders.add(fileKey(selectedFolder));
         }
         updateProjectTitle();
         renderFileTree();
         addConsoleEntry(LOG_INFO, sf(AppStrings.Key.RENAMED_TO, targetDir.getName()));
         return true;
      });
   }

   private void deleteCurrentSketch() {
      File currentProjectDir = store.currentProjectDir();
      if (currentProjectDir == null) {
         return;
      }
      String name = currentProjectDir.getName();
      showConfirmationDialog(s(AppStrings.Key.DELETE_SKETCH), sf(AppStrings.Key.DELETE_QUESTION, name), s(AppStrings.Key.DELETE), () -> {
         store.deleteProject(currentProjectDir);
         files.clear();
         activeIndex = -1;
         fileRoot = DocumentFile.fromFile(store.sketchbookDir());
         selectedFolder = fileRoot;
         expandedFolders.clear();
         if (fileRoot != null) {
            expandedFolders.add(fileKey(fileRoot));
         }
         syncEditorState();
         rebuildTabs();
         updateProjectTitle();
         renderFileTree();
         store.save(files, activeIndex);
         addConsoleEntry(LOG_INFO, sf(AppStrings.Key.DELETED, name));
      });
   }

   private void autoFormatActivePde() {
      if (editor == null) {
         addConsoleEntry(LOG_ERROR, s(AppStrings.Key.NO_EDITOR_AVAILABLE));
         activeConsoleTab = LOG_ERROR;
         renderConsole();
         return;
      }
      if (activeIndex < 0 || activeIndex >= files.size()) {
         addConsoleEntry(LOG_ERROR, s(AppStrings.Key.OPEN_A_PDE_FILE_FIRST));
         activeConsoleTab = LOG_ERROR;
         renderConsole();
         return;
      }
      SketchFile activeFile = files.get(activeIndex);
      if (!activeFile.name.toLowerCase(Locale.US).endsWith(".pde")) {
         addConsoleEntry(LOG_ERROR, s(AppStrings.Key.AUTO_FORMAT_WORKS_ONLY_FOR_PDE));
         activeConsoleTab = LOG_ERROR;
         renderConsole();
         return;
      }
      String code = editor.code();
      if (code == null || code.trim().isEmpty()) {
         addConsoleEntry(LOG_ERROR, s(AppStrings.Key.THERE_IS_NO_CODE_TO_FORMAT));
         activeConsoleTab = LOG_ERROR;
         renderConsole();
         return;
      }
      String formatted = CodeEditorView.formatCode(code, settings != null ? settings.tabSize() : 3);
      if (!editor.applyFormattedCode(formatted)) {
         addConsoleEntry(LOG_INFO, s(AppStrings.Key.CODE_IS_ALREADY_FORMATTED));
         return;
      }
      activeFile.code = editor.code();
      store.save(files, activeIndex);
      addConsoleEntry(LOG_INFO, s(AppStrings.Key.CODE_FORMATTED));
   }

   private void openReference() {
      Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://processing.org/reference"));
      startActivity(intent);
   }

   private void showColorSelectorDialog() {
      Dialog dialog = new Dialog(this);
      dialog.getWindow();

      final float[] hsv = {132f, 0.66f, 0.73f};
      final boolean[] updating = {false};

      LinearLayout content = new LinearLayout(this);
      content.setOrientation(LinearLayout.VERTICAL);
      content.setPadding(dp(18), dp(16), dp(18), dp(14));
      UiStyles.roundedStroke(content, theme.surface, theme.border, dp(1), dp(14));

      TextView titleView = new TextView(this);
      titleView.setText(s(AppStrings.Key.COLOR_SELECTOR));
      titleView.setTextColor(theme.text);
      titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
      titleView.setTypeface(Typeface.DEFAULT_BOLD);
      content.addView(titleView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(30)));

      LinearLayout pickerRow = new LinearLayout(this);
      pickerRow.setOrientation(LinearLayout.HORIZONTAL);
      LinearLayout.LayoutParams pickerRowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(220));
      pickerRowParams.setMargins(0, dp(12), 0, dp(14));

      ColorFieldView colorField = new ColorFieldView(this);
      colorField.setHue(hsv[0]);
      colorField.setSelection(hsv[1], hsv[2]);
      LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
      pickerRow.addView(colorField, fieldParams);

      HueSliderView hueSlider = new HueSliderView(this);
      hueSlider.setHue(hsv[0]);
      LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(dp(30), LinearLayout.LayoutParams.MATCH_PARENT);
      sliderParams.setMargins(dp(12), 0, 0, 0);
      pickerRow.addView(hueSlider, sliderParams);
      content.addView(pickerRow, pickerRowParams);

      LinearLayout rgbRow = new LinearLayout(this);
      rgbRow.setOrientation(LinearLayout.HORIZONTAL);
      rgbRow.setGravity(Gravity.CENTER_VERTICAL);
      LinearLayout.LayoutParams rgbRowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      rgbRowParams.setMargins(0, 0, 0, dp(10));

      EditText redInput = createColorChannelInput("R");
      EditText greenInput = createColorChannelInput("G");
      EditText blueInput = createColorChannelInput("B");
      rgbRow.addView(createColorChannelGroup("R", redInput), colorChannelParams(true));
      rgbRow.addView(createColorChannelGroup("G", greenInput), colorChannelParams(true));
      rgbRow.addView(createColorChannelGroup("B", blueInput), colorChannelParams(false));
      content.addView(rgbRow, rgbRowParams);

      LinearLayout hsbRow = new LinearLayout(this);
      hsbRow.setOrientation(LinearLayout.HORIZONTAL);
      hsbRow.setGravity(Gravity.CENTER_VERTICAL);
      LinearLayout.LayoutParams hsbRowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      hsbRowParams.setMargins(0, 0, 0, dp(10));

      EditText hueInput = createColorChannelInput("H");
      EditText saturationInput = createColorChannelInput("S");
      EditText brightnessInput = createColorChannelInput("B");
      hsbRow.addView(createColorChannelGroup("H", hueInput), colorChannelParams(true));
      hsbRow.addView(createColorChannelGroup("S", saturationInput), colorChannelParams(true));
      hsbRow.addView(createColorChannelGroup("B", brightnessInput), colorChannelParams(false));
      content.addView(hsbRow, hsbRowParams);

      LinearLayout hexRow = new LinearLayout(this);
      hexRow.setGravity(Gravity.CENTER_VERTICAL);
      LinearLayout.LayoutParams hexRowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      hexRowParams.setMargins(0, dp(10), 0, 0);
      TextView hexLabel = new TextView(this);
      hexLabel.setText("HEX");
      hexLabel.setTextColor(theme.textMuted);
      hexLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
      hexRow.addView(hexLabel, new LinearLayout.LayoutParams(dp(34), LinearLayout.LayoutParams.WRAP_CONTENT));

      TextView hexValue = createColorValueField();
      LinearLayout.LayoutParams hexValueParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
      hexValueParams.setMargins(dp(8), 0, dp(8), 0);
      hexRow.addView(hexValue, hexValueParams);

      TextView copy = new TextView(this);
      copy.setText(s(AppStrings.Key.COPY));
      copy.setTextColor(theme.background);
      copy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
      copy.setGravity(Gravity.CENTER);
      UiStyles.roundedStroke(copy, theme.accent, theme.accent, dp(1), dp(8));
      copy.setOnClickListener(view -> copyPlainText("HEX", hexValue.getText().toString()));
      hexRow.addView(copy, new LinearLayout.LayoutParams(dp(60), dp(32)));
      content.addView(hexRow, hexRowParams);

      Runnable syncFromHsv = () -> {
         updating[0] = true;
         int color = Color.HSVToColor(hsv);
         int red = Color.red(color);
         int green = Color.green(color);
         int blue = Color.blue(color);
         redInput.setText(String.valueOf(red));
         greenInput.setText(String.valueOf(green));
         blueInput.setText(String.valueOf(blue));
         hueInput.setText(String.valueOf(Math.round(hsv[0])));
         saturationInput.setText(String.valueOf(Math.round(hsv[1] * 100f)));
         brightnessInput.setText(String.valueOf(Math.round(hsv[2] * 100f)));
         hexValue.setText(String.format(Locale.US, "#%02X%02X%02X", red, green, blue));
         colorField.setHue(hsv[0]);
         colorField.setSelection(hsv[1], hsv[2]);
         hueSlider.setHue(hsv[0]);
         applyColorValueFieldStyle(hexValue, color);
         updating[0] = false;
      };

      colorField.setListener((saturation, value) -> {
         hsv[1] = saturation;
         hsv[2] = value;
         syncFromHsv.run();
      });
      hueSlider.setListener(hue -> {
         hsv[0] = hue;
         syncFromHsv.run();
      });

      TextWatcher rgbWatcher = new TextWatcher() {
         @Override
         public void beforeTextChanged(CharSequence s, int start, int count, int after) {
         }

         @Override
         public void onTextChanged(CharSequence s, int start, int before, int count) {
         }

         @Override
         public void afterTextChanged(Editable editable) {
            if (updating[0]) {
               return;
            }
            int red = parseColorChannel(redInput.getText().toString());
            int green = parseColorChannel(greenInput.getText().toString());
            int blue = parseColorChannel(blueInput.getText().toString());
            Color.RGBToHSV(red, green, blue, hsv);
            syncFromHsv.run();
         }
      };
      redInput.addTextChangedListener(rgbWatcher);
      greenInput.addTextChangedListener(rgbWatcher);
      blueInput.addTextChangedListener(rgbWatcher);

      TextWatcher hsbWatcher = new TextWatcher() {
         @Override
         public void beforeTextChanged(CharSequence s, int start, int count, int after) {
         }

         @Override
         public void onTextChanged(CharSequence s, int start, int before, int count) {
         }

         @Override
         public void afterTextChanged(Editable editable) {
            if (updating[0]) {
               return;
            }
            hsv[0] = parseHueChannel(hueInput.getText().toString());
            hsv[1] = parsePercentChannel(saturationInput.getText().toString());
            hsv[2] = parsePercentChannel(brightnessInput.getText().toString());
            syncFromHsv.run();
         }
      };
      hueInput.addTextChangedListener(hsbWatcher);
      saturationInput.addTextChangedListener(hsbWatcher);
      brightnessInput.addTextChangedListener(hsbWatcher);

      syncFromHsv.run();

      LinearLayout actions = new LinearLayout(this);
      actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
      TextView close = dialogButton(s(AppStrings.Key.CLOSE), false);
      close.setOnClickListener(view -> dialog.dismiss());
      actions.addView(close, new LinearLayout.LayoutParams(dp(104), dp(42)));
      LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
      actionsParams.setMargins(0, dp(10), 0, 0);
      content.addView(actions, actionsParams);

      dialog.setContentView(content, new ViewGroup.LayoutParams(dp(320), ViewGroup.LayoutParams.WRAP_CONTENT));
      if (dialog.getWindow() != null) {
         dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
         dialog.getWindow().setGravity(Gravity.CENTER);
      }
      dialog.show();
   }

   private LinearLayout createColorChannelGroup(String label, EditText input) {
      LinearLayout group = new LinearLayout(this);
      group.setOrientation(LinearLayout.VERTICAL);

      TextView labelView = new TextView(this);
      labelView.setText(label);
      labelView.setTextColor(theme.textMuted);
      labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
      labelView.setPadding(dp(2), 0, 0, dp(4));
      group.addView(labelView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

      group.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
      return group;
   }

   private LinearLayout.LayoutParams colorChannelParams(boolean withRightMargin) {
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
      if (withRightMargin) {
         params.setMargins(0, 0, dp(8), 0);
      }
      return params;
   }

   private EditText createColorChannelInput(String hint) {
      EditText input = new EditText(this);
      input.setSingleLine(true);
      input.setTextColor(theme.text);
      input.setHintTextColor(theme.textMuted);
      input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
      input.setTypeface(AppFonts.code(this));
      input.setInputType(InputType.TYPE_CLASS_NUMBER);
      input.setHint(hint);
      input.setGravity(Gravity.CENTER);
      UiStyles.roundedStroke(input, theme.surfaceSoft, theme.border, dp(1), dp(10));
      return input;
   }

   private TextView createColorValueField() {
      TextView value = new TextView(this);
      value.setTextColor(theme.text);
      value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
      value.setTypeface(AppFonts.code(this));
      value.setSingleLine(true);
      value.setGravity(Gravity.CENTER_VERTICAL);
      value.setPadding(dp(12), 0, dp(12), 0);
      UiStyles.roundedStroke(value, theme.surfaceSoft, theme.border, dp(1), dp(10));
      return value;
   }

   private void applyColorValueFieldStyle(TextView field, int color) {
      if (field == null) {
         return;
      }
      UiStyles.roundedStroke(field, color, theme.border, dp(1), dp(10));
      field.setTextColor(contrastingTextColor(color));
   }

   private int contrastingTextColor(int backgroundColor) {
      int red = Color.red(backgroundColor);
      int green = Color.green(backgroundColor);
      int blue = Color.blue(backgroundColor);
      if (green >= 140 && green >= red + 18 && green >= blue + 18) {
         return Color.BLACK;
      }
      double luminance = (0.299d * red) + (0.587d * green) + (0.114d * blue);
      return luminance >= 160d ? Color.BLACK : Color.WHITE;
   }

   private int parseColorChannel(String value) {
      if (value == null || value.trim().isEmpty()) {
         return 0;
      }
      try {
         return clamp(Integer.parseInt(value.trim()), 0, 255);
      } catch (NumberFormatException exception) {
         return 0;
      }
   }

   private float parseHueChannel(String value) {
      if (value == null || value.trim().isEmpty()) {
         return 0f;
      }
      try {
         return clamp(Integer.parseInt(value.trim()), 0, 360);
      } catch (NumberFormatException exception) {
         return 0f;
      }
   }

   private float parsePercentChannel(String value) {
      if (value == null || value.trim().isEmpty()) {
         return 0f;
      }
      try {
         return clamp(Integer.parseInt(value.trim()), 0, 100) / 100f;
      } catch (NumberFormatException exception) {
         return 0f;
      }
   }

   private void copyPlainText(String label, String value) {
      ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
      if (clipboard == null) {
         addConsoleEntry(LOG_ERROR, s(AppStrings.Key.CLIPBOARD_IS_UNAVAILABLE));
         return;
      }
      clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
      addConsoleEntry(LOG_INFO, sf(AppStrings.Key.COPIED, label));
   }

   private void showTabMenu(View anchor) {
      showContextMenu(anchor, dp(180),
         new ContextMenuItem(s(AppStrings.Key.NEW_TAB), this::addTab),
         new ContextMenuItem(s(AppStrings.Key.RENAME_TAB), this::showRenameTabDialog),
         new ContextMenuItem(s(AppStrings.Key.DELETE_TAB), this::removeTab)
      );
   }

   private void showPlaceholderAction(String title) {
      addConsoleEntry(LOG_INFO, sf(AppStrings.Key.IS_NOT_IMPLEMENTED_YET, title));
   }

   private void showTextInputDialog(String title, String hint, String value, String confirmLabel, TextInputAction action) {
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
      input.setSingleLine(true);
      input.setTextColor(theme.text);
      input.setHintTextColor(theme.textMuted);
      input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
      input.setHint(hint);
      input.setText(value);
      input.setSelectAllOnFocus(true);
      input.setPadding(dp(12), 0, dp(12), 0);
      UiStyles.roundedStroke(input, theme.surfaceSoft, theme.border, dp(1), dp(10));
      LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
      inputParams.setMargins(0, dp(12), 0, dp(14));
      content.addView(input, inputParams);

      LinearLayout actions = new LinearLayout(this);
      actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);

      TextView cancel = dialogButton(s(AppStrings.Key.CANCEL), false);
      cancel.setOnClickListener(view -> dialog.dismiss());
      TextView confirm = dialogButton(confirmLabel, true);
      confirm.setOnClickListener(view -> {
         boolean handled = action.apply(input.getText().toString());
         if (handled) {
            dialog.dismiss();
         } else {
            input.setError(s(AppStrings.Key.ENTER_A_VALID_NAME));
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

   private void showConfirmationDialog(String title, String message, String confirmLabel, Runnable action) {
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

      TextView messageView = new TextView(this);
      messageView.setText(message);
      messageView.setTextColor(theme.textMuted);
      messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
      messageView.setPadding(0, dp(12), 0, dp(14));
      content.addView(messageView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

      LinearLayout actions = new LinearLayout(this);
      actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);

      TextView cancel = dialogButton(s(AppStrings.Key.CANCEL), false);
      cancel.setOnClickListener(view -> dialog.dismiss());
      TextView confirm = dialogButton(confirmLabel, true);
      confirm.setOnClickListener(view -> {
         action.run();
         dialog.dismiss();
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

   private void showContextMenu(View anchor, int width, ContextMenuItem... items) {
      LinearLayout menu = new LinearLayout(this);
      menu.setOrientation(LinearLayout.VERTICAL);
      menu.setPadding(dp(6), dp(6), dp(6), dp(6));
      UiStyles.roundedStroke(menu, theme.surface, theme.border, dp(1), dp(12));

      PopupWindow popup = new PopupWindow(menu, width, LinearLayout.LayoutParams.WRAP_CONTENT, true);
      popup.setOutsideTouchable(true);
      popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
      popup.setElevation(dp(10));

      for (ContextMenuItem item : items) {
         TextView row = new TextView(this);
         row.setText(item.title);
         row.setTextColor(theme.text);
         row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
         row.setGravity(Gravity.CENTER_VERTICAL);
         row.setPadding(dp(12), 0, dp(12), 0);
         UiStyles.rounded(row, theme.surface, dp(8));
         row.setOnClickListener(view -> {
            popup.dismiss();
            item.action.run();
         });
         LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
         params.setMargins(0, 0, 0, dp(3));
         menu.addView(row, params);
      }

      popup.showAsDropDown(anchor, 0, dp(6));
   }

   private void showRenameTabDialog() {
      if (files.isEmpty() || activeIndex < 0 || activeIndex >= files.size()) {
         return;
      }

      showTextInputDialog(s(AppStrings.Key.RENAME_TAB), "Sketch.pde", files.get(activeIndex).name, s(AppStrings.Key.RENAME), value -> {
         SketchFile activeFile = files.get(activeIndex);
         String normalized = activeFile.documentUri == null
            ? normalizeRenameFileName(value, activeFile.name)
            : normalizeDocumentName(value);
         if (normalized.isEmpty()) {
            return false;
         }
         if (activeFile.documentUri != null) {
            DocumentFile document = DocumentFile.fromSingleUri(this, Uri.parse(activeFile.documentUri));
            if (document == null || !document.renameTo(normalized)) {
               addConsoleEntry(LOG_ERROR, s(AppStrings.Key.COULD_NOT_RENAME_EXTERNAL_FILE));
               activeConsoleTab = LOG_ERROR;
               renderConsole();
               return false;
            }
            activeFile.documentUri = document.getUri().toString();
            renderFileTree();
         }
         files.get(activeIndex).name = normalized;
         addConsoleEntry(LOG_INFO, sf(AppStrings.Key.RENAMED_TAB_TO, normalized));
         updateProjectTitle();
         rebuildTabs();
         store.save(files, activeIndex);
         return true;
      });
   }

   private String normalizeRenameFileName(String requestedName, String currentName) {
      String name = requestedName == null ? "" : requestedName.trim();
      if (name.isEmpty()) {
         return "";
      }
      if (!name.endsWith(".pde")) {
         name += ".pde";
      }
      if (name.equalsIgnoreCase(currentName)) {
         return name;
      }

      String baseName = name;
      String stem = baseName.substring(0, baseName.length() - 4);
      int copy = 2;
      while (fileNameExists(name)) {
         name = stem + " " + copy + ".pde";
         copy++;
      }
      return name;
   }

   private boolean resizeConsole(View handle, MotionEvent event) {
      if (consolePanel == null) {
         return false;
      }
      if (event.getAction() == MotionEvent.ACTION_DOWN) {
         consoleStartY = event.getRawY();
         consoleStartHeight = consolePanel.getHeight();
         userResizingConsole = true;
         return true;
      }
      if (event.getAction() == MotionEvent.ACTION_MOVE) {
         float delta = consoleStartY - event.getRawY();
         int topLimit = dp(90);
         if (rootView != null && rootView.getChildCount() > 0) {
            topLimit = rootView.getChildAt(0).getBottom() + dp(6);
         }
         int maxHeight = Math.max(dp(180), consolePanel.getBottom() - topLimit);
         setConsoleHeight(clamp(Math.round(consoleStartHeight + delta), collapsedConsoleHeight(), maxHeight));
         return true;
      }
      if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
         userResizingConsole = false;
         return true;
      }
      return false;
   }

   private boolean resizeConsoleFromTopEdge(MotionEvent event) {
      if (event.getAction() == MotionEvent.ACTION_DOWN && event.getY() > dp(46)) {
         return false;
      }
      if (!userResizingConsole && event.getAction() != MotionEvent.ACTION_DOWN) {
         return false;
      }
      return resizeConsole(consolePanel, event);
   }

   private void setConsoleHeight(int height) {
      boolean wasCollapsed = consoleCollapsed;
      consoleHeight = height <= dp(72) ? collapsedConsoleHeight() : height;
      consoleCollapsed = consoleHeight == collapsedConsoleHeight();
      if (userResizingConsole && wasCollapsed != consoleCollapsed && consolePanel != null) {
         performConsoleToggleFeedback();
      }
      if (!consoleCollapsed) {
         expandedConsoleHeight = consoleHeight;
      }
      LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) consolePanel.getLayoutParams();
      params.height = consoleHeight;
      consolePanel.setLayoutParams(params);
      if (consoleHeader != null) {
         consoleHeader.setVisibility(consoleCollapsed ? View.GONE : View.VISIBLE);
      }
      if (consoleScroll != null) {
         consoleScroll.setVisibility(consoleCollapsed ? View.GONE : View.VISIBLE);
      }
      if (lineNumbers != null) {
         lineNumbers.postInvalidateOnAnimation();
      }
      if (consolePanel != null) {
         consolePanel.post(this::updateConsoleForKeyboardInset);
      }
   }

   private void collapseConsole() {
      if (consolePanel != null) {
         setConsoleHeight(collapsedConsoleHeight());
      }
   }

   private void collapseConsoleForKeyboard() {
      if (consolePanel == null) {
         restoreConsoleAfterKeyboard = false;
         return;
      }
      if (consoleCollapsed) {
         restoreConsoleAfterKeyboard = false;
         setConsoleHeight(collapsedConsoleHeight());
         return;
      }
      restoreConsoleAfterKeyboard = true;
      consoleHeightBeforeKeyboard = consoleHeight;
      collapseConsole();
   }

   private void restoreConsoleAfterKeyboard() {
      if (!restoreConsoleAfterKeyboard || consolePanel == null) {
         return;
      }
      restoreConsoleAfterKeyboard = false;
      setConsoleHeight(Math.max(consoleHeightBeforeKeyboard, dp(180)));
   }

   private void updateConsoleForKeyboardInset() {
      if (consolePanel == null) {
         return;
      }
      if (!keyboardVisible || keyboardInsetHeight <= 0 || keyboardVisibleBottom <= 0) {
         consolePanel.setTranslationY(0f);
         return;
      }
      int[] location = new int[2];
      consolePanel.getLocationOnScreen(location);
      int consoleBottom = location[1] + consolePanel.getHeight();
      int overlap = Math.max(0, consoleBottom - keyboardVisibleBottom);
      consolePanel.setTranslationY(-overlap);
   }

   private void updateEditorForKeyboardInset() {
      if (editor == null) {
         return;
      }
      int bottomPadding = 0;
      if (keyboardVisible && keyboardInsetHeight > 0) {
         bottomPadding = keyboardInsetHeight + collapsedConsoleHeight() + dp(8);
      }
      editor.setPadding(dp(10), dp(2), 0, bottomPadding);
      if (lineNumbers != null) {
         lineNumbers.postInvalidateOnAnimation();
      }
   }

   private void performConsoleToggleFeedback() {
      if (consolePanel == null || settings == null || !settings.hapticEnabled()) {
         return;
      }
      boolean performed = consolePanel.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK);
      if (performed) {
         return;
      }
      Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
      if (vibrator == null || !vibrator.hasVibrator()) {
         return;
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
         vibrator.vibrate(VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE));
      } else {
         vibrator.vibrate(18L);
      }
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

   private void expandConsoleForError() {
      if (consolePanel == null) {
         return;
      }
      setConsoleHeight(Math.max(expandedConsoleHeight, dp(180)));
      scrollConsoleToBottom();
   }

   private int collapsedConsoleHeight() {
      return keyboardVisible ? dp(25) : dp(60);
   }

   private void addConsoleEntry(int type, String message) {
      consoleEntries.add(new ConsoleEntry(type, message));
      renderConsole();
      scrollConsoleToBottom();
   }

   private void renderConsole() {
      if (consoleContent == null) {
         return;
      }
      boolean showingErrors = activeConsoleTab == LOG_ERROR;
      styleConsoleTab(consoleTab, !showingErrors);
      styleConsoleTab(errorsTab, showingErrors);
      consoleContent.removeAllViews();

      int rendered = 0;
      for (ConsoleEntry entry : consoleEntries) {
         if (showingErrors && entry.type != LOG_ERROR) {
            continue;
         }
         consoleContent.addView(createConsoleRow(entry));
         rendered++;
      }

      if (rendered == 0) {
         TextView placeholder = new TextView(this);
         placeholder.setText(showingErrors ? s(AppStrings.Key.NO_ERRORS) : s(AppStrings.Key.NO_CONSOLE_OUTPUT));
         placeholder.setTextColor(theme.textMuted);
         placeholder.setTextSize(TypedValue.COMPLEX_UNIT_SP, consoleFontSizeSp());
         placeholder.setPadding(0, dp(14), 0, 0);
         consoleContent.addView(placeholder);
      }
   }

   private void scrollConsoleToBottom() {
      if (consoleScroll == null) {
         return;
      }
      consoleScroll.post(() -> consoleScroll.fullScroll(View.FOCUS_DOWN));
   }

   private LinearLayout createConsoleRow(ConsoleEntry entry) {
      LinearLayout row = new LinearLayout(this);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(0, dp(4), 0, dp(4));
      row.setOnLongClickListener(view -> {
         copyConsoleEntry(entry);
         return true;
      });

      ConsoleStatusIconView icon = new ConsoleStatusIconView(this, theme, entry.statusIconMode());
      LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
      row.addView(icon, iconParams);

      TextView message = new TextView(this);
      message.setText(entry.message);
      message.setTextColor(theme.text);
      message.setTextSize(TypedValue.COMPLEX_UNIT_SP, consoleFontSizeSp());
      message.setTypeface(AppFonts.code(this));
      message.setGravity(Gravity.CENTER_VERTICAL);
      message.setIncludeFontPadding(false);
      message.setMinHeight(dp(24));
      LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
      messageParams.setMargins(dp(10), 0, 0, 0);
      row.addView(message, messageParams);
      return row;
   }

   private void copyConsoleEntry(ConsoleEntry entry) {
      ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
      if (clipboard == null) {
         addConsoleEntry(LOG_ERROR, s(AppStrings.Key.CLIPBOARD_IS_UNAVAILABLE));
         return;
      }
      clipboard.setPrimaryClip(ClipData.newPlainText("APDE console message", entry.copyText()));
      addConsoleEntry(LOG_INFO, s(AppStrings.Key.CONSOLE_MESSAGE_COPIED));
   }

   private void styleConsoleTab(TextView tab, boolean active) {
      if (tab == null) {
         return;
      }
      tab.setTextColor(active ? theme.text : theme.textMuted);
      UiStyles.roundedStroke(tab, active ? theme.surfaceSoft : theme.surface, active ? theme.accent : theme.border, dp(1), dp(10));
   }

   private void updateProjectTitle() {
      if (projectTitle == null) {
         return;
      }
      projectTitle.setText(projectName());
   }

   private String projectName() {
      File currentProjectDir = store == null ? null : store.currentProjectDir();
      if (currentProjectDir != null) {
         String dirName = currentProjectDir.getName();
         if (dirName != null && !dirName.isEmpty()) {
            return dirName;
         }
      }
      return s(AppStrings.Key.UNTITLED);
   }

   private void updateLineNumbers() {
      if (lineNumbers == null || editor == null) {
         return;
      }
      String code = editor.code();
      int lines = 1;
      for (int i = 0; i < code.length(); i++) {
         if (code.charAt(i) == '\n') {
            lines++;
         }
      }

      lineNumbers.setLineCount(lines);
      ViewGroup.LayoutParams params = lineNumbers.getLayoutParams();
      if (params != null) {
         int desiredWidth = lineNumbers.desiredWidth();
         if (params.width != desiredWidth) {
            params.width = desiredWidth;
            lineNumbers.setLayoutParams(params);
         }
      }
   }

   private void applySettings() {
      if (settings == null) {
         return;
      }
      if (editor != null) {
         editor.setEditorFontSizeSp(settings.editorFontSizeSp());
         editor.setTabSize(settings.tabSize());
         editor.setAutoClosePairs(settings.autoClosePairs());
      }
      if (lineNumbers != null) {
         lineNumbers.setFontSizeSp(settings.editorFontSizeSp());
         ViewGroup.LayoutParams params = lineNumbers.getLayoutParams();
         if (params != null) {
            params.width = lineNumbers.desiredWidth();
            lineNumbers.setLayoutParams(params);
         }
      }
   }

   private int consoleFontSizeSp() {
      return settings == null ? 13 : settings.consoleFontSizeSp();
   }

   private String s(AppStrings.Key key) {
      return strings.text(key);
   }

   private String sf(AppStrings.Key key, Object... args) {
      return strings.format(key, args);
   }

   private int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   private int dp(int value) {
      return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
   }

   private void hideKeyboard() {
      InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
      if (inputMethodManager != null && editor != null) {
         inputMethodManager.hideSoftInputFromWindow(editor.getWindowToken(), 0);
      }
   }

   private void installKeyboardWatcher(View root) {
      root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
         @Override
         public void onGlobalLayout() {
            Rect visibleFrame = new Rect();
            root.getWindowVisibleDisplayFrame(visibleFrame);
            int rootViewHeight = root.getRootView().getHeight();
            int hiddenHeight = Math.max(0, rootViewHeight - visibleFrame.bottom);
            boolean nowVisible = hiddenHeight > dp(140);
            keyboardInsetHeight = nowVisible ? hiddenHeight : 0;
            keyboardVisibleBottom = nowVisible ? visibleFrame.bottom : 0;
            if (nowVisible && !keyboardVisible) {
               keyboardVisible = true;
               collapseConsoleForKeyboard();
            } else if (!nowVisible && keyboardVisible) {
               keyboardVisible = false;
               keyboardInsetHeight = 0;
               keyboardVisibleBottom = 0;
               if (restoreConsoleAfterKeyboard) {
                  restoreConsoleAfterKeyboard();
               } else if (consolePanel != null && consoleHeight <= dp(72)) {
                  setConsoleHeight(collapsedConsoleHeight());
               }
            } else {
               keyboardVisible = nowVisible;
               if (!nowVisible) {
                  keyboardInsetHeight = 0;
                  keyboardVisibleBottom = 0;
               }
            }
            updateEditorForKeyboardInset();
            updateConsoleForKeyboardInset();
            root.setPadding(0, root.getPaddingTop(), 0, 0);
         }
      });
   }

   private LinearLayout.LayoutParams iconParams() {
      return new LinearLayout.LayoutParams(dp(48), dp(48));
   }

   private LinearLayout.LayoutParams sectionParams(int height) {
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height);
      params.setMargins(dp(16), 0, dp(16), 0);
      return params;
   }

   private LinearLayout.LayoutParams sectionWeightParams() {
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
      params.setMargins(dp(16), 0, dp(16), 0);
      return params;
   }

   private LinearLayout.LayoutParams editorSectionParams() {
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
      params.setMargins(0, 0, 0, 0);
      return params;
   }

   private static final class ConsoleEntry {
      final int type;
      final String message;

      ConsoleEntry(int type, String message) {
         this.type = type;
         this.message = message;
      }

      int statusIconMode() {
         if (type == LOG_SUCCESS) {
            return ConsoleStatusIconView.MODE_SUCCESS;
         }
         if (type == LOG_ERROR) {
            return ConsoleStatusIconView.MODE_ERROR;
         }
         return ConsoleStatusIconView.MODE_INFO;
      }

      int color(EditorTheme theme) {
         if (type == LOG_SUCCESS) {
            return theme.accent;
         }
         if (type == LOG_ERROR) {
            return theme.error;
         }
         return theme.codeAccent;
      }

      String copyText() {
         return message;
      }
   }

   private static final class ContextMenuItem {
      final String title;
      final Runnable action;

      ContextMenuItem(String title, Runnable action) {
         this.title = title;
         this.action = action;
      }
   }

   private interface TextInputAction {
      boolean apply(String value);
   }
}
