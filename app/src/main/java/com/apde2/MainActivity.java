package com.apde2;

import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
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
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.documentfile.provider.DocumentFile;

import com.apde2.preview.PreviewBuildStatus;
import com.apde2.preview.PreviewDiagnostic;
import com.apde2.preview.SketchPreviewController;

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
   private static final int LOG_CONSOLE = 4;
   private static final String PREVIEW_LOG_ACTION = "com.apde2.preview.LOG";
   private static final String PREVIEW_CRASH_ACTION = "com.apde2.preview.CRASH";
   private static final String PREVIEW_EXTRA_MESSAGE = "com.apde2.preview.extra.MESSAGE";
   private static final String PREVIEW_EXTRA_STACKTRACE = "com.apde2.preview.extra.STACKTRACE";
   private static final String PREVIEW_EXTRA_CHANNEL = "com.apde2.preview.extra.CHANNEL";
   private static final String SECTION_SKETCHES = "apde:sketches";
   private static final String SECTION_EXAMPLES = "apde:examples";
   private static final String SECTION_LIBRARY_EXAMPLES = "apde:library_examples";
   private static final String SECTION_RECENT = "apde:recent";
   private static final String SKETCHBOOK_DIR_NAME = "Sketchbook";
   private static final String SKETCHES_DIR_NAME = "Sketches";
   private static final String EXAMPLES_DIR_NAME = "Examples";
   private static final String LIBRARY_EXAMPLES_DIR_NAME = "Library Examples";
   private static final String RECENT_DIR_NAME = "Recent";
   private static final long PREVIEW_LOG_FLUSH_DELAY_MS = 120L;
   private static final int MAX_CONSOLE_ENTRIES = 1200;

   private final Handler runHandler = new Handler(Looper.getMainLooper());
   private final List<SketchFile> files = new ArrayList<>();
   private final List<ConsoleEntry> consoleEntries = new ArrayList<>();
   private final List<String> pendingPreviewConsoleOutput = new ArrayList<>();
   private final List<Runnable> pendingRunSteps = new ArrayList<>();
   private final Runnable flushPreviewConsoleRunnable = this::flushPreviewConsoleEntries;
   private EditorTheme theme;
   private ValueAnimator themeAnimator;
   private boolean previewConsoleFlushScheduled = false;
   private ConsoleEntry openPreviewConsoleEntry;
   private final BroadcastReceiver previewReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
         if (intent == null || intent.getAction() == null) {
            return;
         }
         String message = intent.getStringExtra(PREVIEW_EXTRA_MESSAGE);
         if (message == null || message.trim().isEmpty()) {
            message = intent.getAction();
         }
         if (PREVIEW_CRASH_ACTION.equals(intent.getAction())) {
            flushPreviewConsoleEntries();
            runInProgress = false;
            previewActivityLaunched = false;
            updateRunButton();
            addConsoleEntry(LOG_ERROR, message);
            String stacktrace = intent.getStringExtra(PREVIEW_EXTRA_STACKTRACE);
            if (stacktrace != null && !stacktrace.trim().isEmpty()) {
               addConsoleEntry(LOG_ERROR, stacktrace.trim());
            }
            expandConsoleForError();
         } else if (PREVIEW_LOG_ACTION.equals(intent.getAction())) {
            String channel = intent.getStringExtra(PREVIEW_EXTRA_CHANNEL);
            if ("stdout".equals(channel) || "stderr".equals(channel)) {
               queuePreviewConsoleOutput(message);
            } else if (message.startsWith("[stdout] ")) {
               queuePreviewConsoleOutput(message.substring(9) + "\n");
            } else if (message.startsWith("[stderr] ")) {
               queuePreviewConsoleOutput(message.substring(9) + "\n");
            }
         }
      }
   };

   private SketchStore store;
   private AppSettings settings;
   private AppStrings strings;
   private SketchPreviewController previewController;
   private String appliedLanguage;
   private String appliedThemeMode;
   private FrameLayout rootContainer;
   private LinearLayout rootView;
   private View navigationBarBackground;
   private LinearLayout tabsBar;
   private FrameLayout editorWorkspace;
   private LinearLayout editorPanel;
   private CodeEditorView editor;
   private TextView editorEmptyState;
   private TextView projectTitle;
   private LineNumberView lineNumbers;
   private LinearLayout tabs;
   private LinearLayout consolePanel;
   private LinearLayout consoleHeader;
   private View consoleHandle;
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
   private boolean fileTreeDirty = true;
   private int activeIndex = -1;
   private int activeConsoleTab = LOG_INFO;
   private int consoleHeight;
   private int expandedConsoleHeight;
   private int consoleStartHeight;
   private int pendingConsoleHeight;
   private float consoleStartY;
   private boolean consoleCollapsed = false;
   private boolean userResizingConsole = false;
   private boolean consoleHeightApplyScheduled = false;
   private final Runnable applyPendingConsoleHeightRunnable = this::applyPendingConsoleHeight;
   private boolean previewActivityLaunched = false;
   private int imeInset = 0;
   private int systemTopInset = 0;
   private int systemBottomInset = 0;
   private boolean imeAnimating = false;
   private boolean imeShown = false;
   private boolean imeTransitionAppearing = false;
   private int consoleHeightBeforeIme = 0;
   private boolean consoleCollapsedByIme = false;
   private int consoleImeRestoreStartInset = 0;
   private int consoleImeRestoreHeightDelta = 0;
   private boolean loadingTab = false;
   private boolean editorLoadedFile = false;
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
      appliedThemeMode = settings.themeMode();
      theme = EditorTheme.load(this, appliedThemeMode);
      previewController = new SketchPreviewController(this);
      appliedLanguage = settings.language();
      strings = new AppStrings(appliedLanguage);
      applySystemBarColors();
      registerPreviewReceiver();
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
      openExternalPdeIntent(getIntent());
   }

   @Override
   protected void onPause() {
      super.onPause();
      flushAutosave();
   }

   @Override
   protected void onStop() {
      flushAutosave();
      super.onStop();
   }

   @Override
   protected void onResume() {
      super.onResume();
      flushAutosave();
      if (previewActivityLaunched && runInProgress) {
         previewActivityLaunched = false;
         runInProgress = false;
         updateRunButton();
      }
      store.refreshStorageState();
      String language = settings.language();
      if (!language.equals(appliedLanguage)) {
         flushAutosave();
         recreate();
         return;
      }
      String themeMode = settings.themeMode();
      if (!themeMode.equals(appliedThemeMode)) {
         flushAutosave();
         animateThemeChange(themeMode);
         return;
      }
      applySettings();
      renderConsole();
   }

   @Override
   protected void onDestroy() {
      flushAutosave();
      resetConsoleImeOffset();
      cancelThemeAnimation();
      cancelPendingRunSteps();
      runHandler.removeCallbacks(flushPreviewConsoleRunnable);
      previewConsoleFlushScheduled = false;
      unregisterPreviewReceiver();
      if (previewController != null) {
         previewController.close();
      }
      super.onDestroy();
   }

   @Override
   protected void onSaveInstanceState(Bundle outState) {
      flushAutosave();
      super.onSaveInstanceState(outState);
   }

   @Override
   protected void onNewIntent(Intent intent) {
      super.onNewIntent(intent);
      setIntent(intent);
      openExternalPdeIntent(intent);
   }

   private void registerPreviewReceiver() {
      IntentFilter filter = new IntentFilter();
      filter.addAction(PREVIEW_LOG_ACTION);
      filter.addAction(PREVIEW_CRASH_ACTION);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
         registerReceiver(previewReceiver, filter, Context.RECEIVER_EXPORTED);
      } else {
         registerReceiver(previewReceiver, filter);
      }
   }

   private void unregisterPreviewReceiver() {
      try {
         unregisterReceiver(previewReceiver);
      } catch (IllegalArgumentException ignored) {
      }
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
      WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

      FrameLayout container = new FrameLayout(this);
      rootContainer = container;

      LinearLayout root = new LinearLayout(this);
      rootView = root;
      root.setOrientation(LinearLayout.VERTICAL);
      root.setBackgroundColor(theme.background);
      root.setPadding(0, 0, 0, 0);

      root.addView(createTopBar(), sectionParams(dp(68)));
      root.addView(createTabsBar(), sectionParams(dp(54)));
      root.addView(createEditorWorkspace(), editorSectionParams());

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
      navigationBarBackground = new View(this);
      navigationBarBackground.setBackgroundColor(theme.surface);
      FrameLayout.LayoutParams navBackgroundParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM);
      container.addView(navigationBarBackground, navBackgroundParams);
      container.addView(createFilePanelOverlay(), new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

      setContentView(container);
      installInsetsHandler(root);
      rebuildTabs();
   }

   private void installInsetsHandler(View root) {
      ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
         Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
         Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
         systemTopInset = sys.top;
         systemBottomInset = sys.bottom;
         if (!imeAnimating) {
            int newImeInset = Math.max(0, ime.bottom - sys.bottom);
            boolean willBeShown = newImeInset > 0;
            if (willBeShown != imeShown) {
               if (editor != null) {
                  editor.setExternalResize(true);
               }
               handleImeTransition(willBeShown, newImeInset);
               if (editor != null) {
                  editor.setExternalResize(false);
                  if (willBeShown) {
                     editor.revealSelectionAfterOverlaySettles();
                  }
               }
            }
            imeInset = newImeInset;
            applyContentPadding();
         }
         return insets;
      });

      ViewCompat.setWindowInsetsAnimationCallback(root, new WindowInsetsAnimationCompat.Callback(WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP) {
         @Override
         public void onPrepare(WindowInsetsAnimationCompat animation) {
            if ((animation.getTypeMask() & WindowInsetsCompat.Type.ime()) != 0) {
               imeAnimating = true;
               if (editor != null) {
                  editor.setExternalResize(true);
               }
            }
         }

         @Override
         public WindowInsetsAnimationCompat.BoundsCompat onStart(WindowInsetsAnimationCompat animation, WindowInsetsAnimationCompat.BoundsCompat bounds) {
            if ((animation.getTypeMask() & WindowInsetsCompat.Type.ime()) != 0) {
               WindowInsetsCompat current = ViewCompat.getRootWindowInsets(root);
               boolean appearing = current != null && current.isVisible(WindowInsetsCompat.Type.ime());
               beginImeTransition(appearing);
            }
            return bounds;
         }

         @Override
         public WindowInsetsCompat onProgress(WindowInsetsCompat insets, java.util.List<WindowInsetsAnimationCompat> running) {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            systemTopInset = sys.top;
            systemBottomInset = sys.bottom;
            imeInset = Math.max(0, ime.bottom - sys.bottom);
            applyContentPadding();
            updateNavigationBarBackground();
            return insets;
         }

         @Override
         public void onEnd(WindowInsetsAnimationCompat animation) {
            if ((animation.getTypeMask() & WindowInsetsCompat.Type.ime()) != 0) {
               imeAnimating = false;
               finishImeTransition();
               if (editor != null) {
                  editor.setExternalResize(false);
                  if (imeTransitionAppearing) {
                     editor.revealSelectionAfterOverlaySettles();
                  }
               }
            }
         }
      });

      root.requestApplyInsets();
   }

   private void applyContentPadding() {
      if (rootView == null) return;
      int top = systemTopInset + dp(12);
      int bottom = systemBottomInset;
      if (rootView.getPaddingTop() != top || rootView.getPaddingBottom() != bottom) {
         rootView.setPadding(0, top, 0, bottom);
      }
      applyImeOverlayOffset();
      updateNavigationBarBackground();
   }

   private void applyImeOverlayOffset() {
      if (consolePanel != null) {
         consolePanel.setTranslationY(-imeInset + consoleImeRestoreOffset());
      }
      updateEditorViewportForConsole();
   }

   private float consoleImeRestoreOffset() {
      if (imeTransitionAppearing || consoleImeRestoreStartInset <= 0 || consoleImeRestoreHeightDelta == 0) {
         return 0f;
      }
      float insetFraction = Math.min(1f, Math.max(0f, (float) imeInset / consoleImeRestoreStartInset));
      return consoleImeRestoreHeightDelta * insetFraction;
   }

   private void updateNavigationBarBackground() {
      if (navigationBarBackground == null) return;
      FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) navigationBarBackground.getLayoutParams();
      if (params.height != systemBottomInset) {
         params.height = systemBottomInset;
         navigationBarBackground.setLayoutParams(params);
      }
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
         if (!ensureCurrentProjectWritable()) {
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
         if (!ensureCurrentProjectWritable()) {
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
      UiStyles.topRounded(editorPanel, theme.background, dp(14));

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
            if (activeProjectReadOnly()) {
               return;
            }
            if (!loadingTab && activeIndex >= 0 && activeIndex < files.size()) {
               files.get(activeIndex).code = editable.toString();
               persistCurrentFile();
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

   private FrameLayout createEditorWorkspace() {
      editorWorkspace = new FrameLayout(this);
      editorWorkspace.addView(createEditorPanel(), new FrameLayout.LayoutParams(
         FrameLayout.LayoutParams.MATCH_PARENT,
         FrameLayout.LayoutParams.MATCH_PARENT
      ));

      consoleHeight = dp(220);
      consolePanel = createConsolePanel();
      FrameLayout.LayoutParams consoleParams = new FrameLayout.LayoutParams(
         FrameLayout.LayoutParams.MATCH_PARENT,
         consoleHeight,
         Gravity.BOTTOM
      );
      editorWorkspace.addView(consolePanel, consoleParams);
      updateEditorViewportForConsole();
      return editorWorkspace;
   }

   private LinearLayout createConsolePanel() {
      LinearLayout panel = new LinearLayout(this);
      panel.setOrientation(LinearLayout.VERTICAL);
      panel.setPadding(dp(12), dp(8), dp(12), dp(10));
      UiStyles.topRoundedSideStroke(panel, theme.surface, theme.border, dp(1), dp(14));
      panel.setOnTouchListener((view, event) -> resizeConsoleFromTopEdge(event));

      LinearLayout dragArea = new LinearLayout(this);
      dragArea.setGravity(Gravity.CENTER);
      dragArea.setPadding(0, 0, 0, dp(2));
      dragArea.setOnTouchListener((view, event) -> resizeConsole(view, event));

      View handle = new View(this);
      consoleHandle = handle;
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
      clear.setOnClickListener(view -> clearConsole());
      consoleHeader.addView(clear, iconParams());
      panel.addView(consoleHeader, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

      consoleScroll = new ScrollView(this);
      consoleScroll.setFillViewport(true);
      consoleScroll.setVerticalScrollBarEnabled(true);
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
      if (editor != null) {
         editor.clearFocus();
      }
      hideKeyboard();
      flushAutosave();
      runHandler.removeCallbacks(flushPreviewConsoleRunnable);
      previewConsoleFlushScheduled = false;
      clearConsole();
      openConsoleForRun();
      File projectDir = store.currentProjectDir();
      if (projectDir == null) {
         addConsoleEntry(LOG_ERROR, "Could not read sketch project.");
         expandConsoleForError();
         return;
      }
      runInProgress = true;
      updateRunButton();
      addConsoleEntry(LOG_INFO, "Initializing Sketch Preview...");
      if (previewController == null) {
         previewController = new SketchPreviewController(this);
      }
      final boolean[] compilingReported = { false };
      final boolean[] launchingReported = { false };
      final List<PreviewDiagnostic> buildDiagnostics = new ArrayList<>();
      previewController.run(projectDir, projectName(), new SketchPreviewController.Callback() {
         @Override
         public void onProgress(PreviewBuildStatus status) {
            switch (status.getPhase()) {
               case COMPILING:
                  if (!compilingReported[0]) {
                     compilingReported[0] = true;
                     addConsoleEntry(LOG_INFO, "Compiling sketch...");
                  }
                  break;
               case LAUNCHING:
                  if (!launchingReported[0]) {
                     launchingReported[0] = true;
                     addConsoleEntry(LOG_INFO, "Launching sketch...");
                  }
                  break;
               default:
                  break;
            }
         }

         @Override
         public void onDiagnostic(PreviewDiagnostic diagnostic) {
            buildDiagnostics.add(diagnostic);
         }

         @Override
         public void onSuccess() {
            runInProgress = true;
            previewActivityLaunched = true;
            updateRunButton();
            addConsoleEntry(LOG_SUCCESS, "Sketch started");
         }

         @Override
         public void onFailure(String message) {
            runInProgress = false;
            previewActivityLaunched = false;
            updateRunButton();
            if (buildDiagnostics.isEmpty()) {
               addConsoleEntry(LOG_ERROR, message);
            } else {
               for (PreviewDiagnostic diagnostic : buildDiagnostics) {
                  consoleEntries.add(new ConsoleEntry(LOG_ERROR, diagnostic.toString()));
               }
               trimConsoleEntries();
            }
            expandConsoleForError();
         }

         @Override
         public void onStopped() {
            runInProgress = false;
            previewActivityLaunched = false;
            updateRunButton();
         }
      });
   }

   private void stopSketchRun() {
      cancelPendingRunSteps();
      if (previewController != null) {
         previewController.stop();
      }
      runInProgress = false;
      previewActivityLaunched = false;
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
      if (!ensureCurrentProjectWritable()) {
         return;
      }
      flushAutosave();
      showAddFileDialog();
   }

   private void createTab(String requestedName) {
      int nextNumber = files.size() + 1;
      String name = normalizeFileName(requestedName, "Tab" + nextNumber + ".pde");
      File currentProjectDir = store.currentProjectDir();
      files.add(new SketchFile(name, "", null,
         currentProjectDir == null ? null : currentProjectDir.getAbsolutePath(),
         currentProjectDir == null ? null : new File(currentProjectDir, name).getAbsolutePath()));
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
      String name = requestedName == null ? "" : requestedName.replace("/", "").replace("\\", "").trim();
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
      if (!ensureCurrentProjectWritable()) {
         return;
      }
      if (files.size() <= 1) {
         if (!files.isEmpty()) {
            SketchFile removedFile = files.get(activeIndex);
            String removedName = removedFile.name;
            if (removedFile.documentUri == null && !store.deleteSketchFile(removedFile)) {
               addConsoleEntry(LOG_ERROR, sf(AppStrings.Key.COULD_NOT_DELETE, removedName));
               activeConsoleTab = LOG_ERROR;
               renderConsole();
               return;
            }
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
      SketchFile removedFile = files.get(activeIndex);
      String removedName = removedFile.name;
      if (removedFile.documentUri == null && !store.deleteSketchFile(removedFile)) {
         addConsoleEntry(LOG_ERROR, sf(AppStrings.Key.COULD_NOT_DELETE, removedName));
         activeConsoleTab = LOG_ERROR;
         renderConsole();
         return;
      }
      files.remove(activeIndex);
      activeIndex = Math.max(0, activeIndex - 1);
      addConsoleEntry(LOG_INFO, sf(AppStrings.Key.DELETED, removedName));
      loadTab(activeIndex);
      store.save(files, activeIndex);
   }

   private void openTab(int index) {
      if (files.isEmpty()) {
         activeIndex = -1;
         editorLoadedFile = false;
         syncEditorState();
         rebuildTabs();
         updateProjectTitle();
         return;
      }
      persistCurrentFile();
      loadTab(index);
   }

   private void loadTab(int index) {
      if (files.isEmpty()) {
         activeIndex = -1;
         editorLoadedFile = false;
         syncEditorState();
         rebuildTabs();
         updateProjectTitle();
         return;
      }
      activeIndex = Math.max(0, Math.min(index, files.size() - 1));
      loadingTab = true;
      editor.setCode(files.get(activeIndex).code);
      loadingTab = false;
      editorLoadedFile = true;
      syncEditorState();
      updateProjectTitle();
      updateLineNumbers();
      rebuildTabs();
   }

   private void persistCurrentFile() {
      if (activeProjectReadOnly()) {
         return;
      }
      if (editor != null && editorLoadedFile && !files.isEmpty() && activeIndex >= 0 && activeIndex < files.size()) {
         SketchFile file = files.get(activeIndex);
         file.code = editor.code();
         if (file.documentUri != null) {
            writeDocumentFile(file.documentUri, file.code);
         } else if (store != null) {
            store.saveFile(file, activeIndex);
         }
      }
   }

   private void flushAutosave() {
      persistCurrentFile();
      if (store != null) {
         store.save(files, activeIndex);
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
      flushAutosave();
      files.clear();
      activeIndex = -1;
      syncEditorState();
      rebuildTabs();
      updateProjectTitle();
   }

   private void syncEditorState() {
      boolean hasFile = !files.isEmpty() && activeIndex >= 0 && activeIndex < files.size();
      boolean readOnly = activeProjectReadOnly();
      if (editor != null) {
         editor.setVisibility(hasFile ? View.VISIBLE : View.GONE);
         editor.setEnabled(hasFile);
         editor.setFocusable(hasFile && !readOnly);
         editor.setFocusableInTouchMode(hasFile && !readOnly);
         if (readOnly) {
            editor.clearFocus();
            editor.setCursorVisible(false);
         }
         if (!hasFile) {
            loadingTab = true;
            editor.setCode("");
            loadingTab = false;
            editorLoadedFile = false;
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
      if (fileTreeDirty) {
         filePanelContainer.postDelayed(this::renderFileTreeIfDirty, 80);
      }
   }

   private void resetToInternalSketchbook() {
      store.refreshStorageState();
      syncFilePanelToCurrentProject();
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
      filePanelScrim.setBackgroundColor(theme.scrim);
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
      fileTreeDirty = false;
      fileTreeContent.removeAllViews();
      if (fileRoot == null || !fileRoot.exists()) {
         filePanelTitle.setText(s(AppStrings.Key.FILES));
         TextView empty = createMutedText(s(AppStrings.Key.CHOOSE_FOLDER_TO_SHOW_FILES));
         fileTreeContent.addView(empty);
         return;
      }

      if (isSketchbookRoot(fileRoot)) {
         renderApdeSketchbookTree();
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

   private void renderFileTreeIfDirty() {
      if (fileTreeDirty) {
         renderFileTree();
      }
   }

   private void markFileTreeDirty() {
      fileTreeDirty = true;
   }

   private void renderApdeSketchbookTree() {
      updateFilePanelTitle();
      DocumentFile sketches = sketchbookSection(SKETCHES_DIR_NAME, store == null ? null : store.sketchesDir());
      DocumentFile examples = sketchbookSection(EXAMPLES_DIR_NAME, store == null ? null : store.examplesDir());
      DocumentFile libraryExamples = sketchbookSection(LIBRARY_EXAMPLES_DIR_NAME, store == null ? null : store.libraryExamplesDir());
      DocumentFile recent = sketchbookSection(RECENT_DIR_NAME, store == null ? null : store.recentDir());

      renderSketchbookSection(SKETCHES_DIR_NAME, SECTION_SKETCHES, sketches);
      if (expandedFolders.contains(SECTION_SKETCHES)) {
         renderSectionDirectoryContents(sketches, 1, true);
      }

      renderSketchbookSection(EXAMPLES_DIR_NAME, SECTION_EXAMPLES, examples);
      if (expandedFolders.contains(SECTION_EXAMPLES)) {
         renderSectionDirectoryContents(examples, 1, true);
      }

      renderSketchbookSection(LIBRARY_EXAMPLES_DIR_NAME, SECTION_LIBRARY_EXAMPLES, libraryExamples);
      if (expandedFolders.contains(SECTION_LIBRARY_EXAMPLES)) {
         renderSectionDirectoryContents(libraryExamples, 1, false);
      }

      renderSketchbookSection("Recent Sketches", SECTION_RECENT, recent);
      if (expandedFolders.contains(SECTION_RECENT)) {
         List<File> recentProjects = store == null ? new ArrayList<>() : store.recentProjects();
         if (recentProjects.isEmpty()) {
            fileTreeContent.addView(createIndentedMutedText(s(AppStrings.Key.NO_RECENT_SKETCHES), 1));
         } else {
            for (File project : recentProjects) {
               fileTreeContent.addView(createProjectRow(project, 1));
            }
         }
      }

      renderSketchbookRootExtras();
   }

   private DocumentFile sketchbookSection(String name, File fallbackDir) {
      DocumentFile section = fileRoot == null ? null : fileRoot.findFile(name);
      if (section != null && section.isDirectory()) {
         return section;
      }
      return fallbackDir == null ? null : DocumentFile.fromFile(fallbackDir);
   }

   private void renderSketchbookSection(String title, String key, DocumentFile sectionDir) {
      fileTreeContent.addView(createSectionRow(title, key, sectionDir));
   }

   private void renderSectionDirectoryContents(DocumentFile sectionDir, int depth, boolean markSketchProjects) {
      if (sectionDir == null || !sectionDir.isDirectory()) {
         fileTreeContent.addView(createIndentedMutedText(s(AppStrings.Key.THIS_PROJECT_IS_EMPTY), depth));
         return;
      }
      DocumentFile[] children = sectionDir.listFiles();
      if (children == null || children.length == 0) {
         fileTreeContent.addView(createIndentedMutedText(s(AppStrings.Key.THIS_PROJECT_IS_EMPTY), depth));
         return;
      }
      sortDocuments(children);
      for (DocumentFile child : children) {
         File localSketchProject = markSketchProjects ? localSketchProjectForDocument(child) : null;
         if (localSketchProject != null) {
            fileTreeContent.addView(createProjectRow(localSketchProject, depth));
            continue;
         }
         renderDocument(child, depth);
      }
   }

   private File localSketchProjectForDocument(DocumentFile document) {
      if (document == null || !document.isDirectory()) {
         return null;
      }
      Uri uri = document.getUri();
      if (uri != null && "file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
         File project = new File(uri.getPath());
         return store != null && store.isSketchProject(project) ? project : null;
      }
      if (!hasDirectPdeFile(document)) {
         return null;
      }
      File sketchesDir = store == null ? null : store.sketchesDir();
      File project = sketchesDir == null ? null : new File(sketchesDir, displayName(document));
      return project != null && store.isSketchProject(project) ? project : null;
   }

   private boolean hasDirectPdeFile(DocumentFile directory) {
      DocumentFile[] children = directory.listFiles();
      for (DocumentFile child : children) {
         if (child.isFile() && displayName(child).toLowerCase(Locale.US).endsWith(".pde")) {
            return true;
         }
      }
      return false;
   }

   private void sortLocalFiles(File[] files) {
      Arrays.sort(files, (left, right) -> {
         if (left.isDirectory() != right.isDirectory()) {
            return left.isDirectory() ? -1 : 1;
         }
         return left.getName().compareToIgnoreCase(right.getName());
      });
   }

   private void renderSketchbookRootExtras() {
      if (fileRoot == null || !fileRoot.isDirectory()) {
         return;
      }
      DocumentFile[] children = fileRoot.listFiles();
      if (children == null || children.length == 0) {
         return;
      }
      sortDocuments(children);
      for (DocumentFile child : children) {
         if (!child.isDirectory()) {
            continue;
         }
         if (isManagedSketchbookSection(child)) {
            continue;
         }
         renderDocument(child, 0);
      }
   }

   private boolean isManagedSketchbookSection(DocumentFile file) {
      if (file == null) {
         return false;
      }
      String name = displayName(file);
      return SKETCHES_DIR_NAME.equals(name)
         || EXAMPLES_DIR_NAME.equals(name)
         || LIBRARY_EXAMPLES_DIR_NAME.equals(name)
         || RECENT_DIR_NAME.equals(name);
   }

   private LinearLayout createSectionRow(String title, String key, DocumentFile sectionDir) {
      boolean expanded = expandedFolders.contains(key);
      boolean selected = selectedFolder != null && sectionDir != null && fileKey(selectedFolder).equals(fileKey(sectionDir));

      LinearLayout row = new LinearLayout(this);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(dp(8), 0, dp(8), 0);
      UiStyles.rounded(row, selected ? theme.surfaceSoft : theme.surface, dp(8));

      FileIconView chevron = new FileIconView(this, theme, expanded
         ? FileIconView.MODE_CHEVRON_DOWN
         : FileIconView.MODE_CHEVRON_RIGHT);
      row.addView(chevron, new LinearLayout.LayoutParams(dp(18), dp(38)));

      FileIconView icon = new FileIconView(this, theme, FileIconView.MODE_FOLDER_CODE);
      LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
      iconParams.setMargins(0, 0, dp(8), 0);
      row.addView(icon, iconParams);

      TextView name = new TextView(this);
      name.setText(title);
      name.setTextColor(theme.text);
      name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
      name.setSingleLine(true);
      name.setEllipsize(TextUtils.TruncateAt.END);
      name.setGravity(Gravity.CENTER_VERTICAL);
      name.setIncludeFontPadding(false);
      row.addView(name, new LinearLayout.LayoutParams(0, dp(38), 1f));

      row.setOnClickListener(view -> {
         if (sectionDir != null) {
            selectedFolder = sectionDir;
         }
         if (expandedFolders.contains(key)) {
            expandedFolders.remove(key);
         } else {
            expandedFolders.add(key);
         }
         renderFileTree();
      });

      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
      params.setMargins(0, 0, 0, dp(2));
      row.setLayoutParams(params);
      return row;
   }

   private LinearLayout createProjectRow(File projectDir, int depth) {
      boolean selected = store != null && samePath(projectDir, store.currentProjectDir());

      LinearLayout row = new LinearLayout(this);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(dp(8 + depth * 14), 0, dp(8), 0);
      row.setHapticFeedbackEnabled(settings == null || settings.hapticEnabled());
      UiStyles.rounded(row, selected ? theme.surfaceSoft : theme.surface, dp(8));

      FileIconView chevron = new FileIconView(this, theme, FileIconView.MODE_CHEVRON_RIGHT);
      chevron.setVisibility(View.INVISIBLE);
      row.addView(chevron, new LinearLayout.LayoutParams(dp(18), dp(38)));

      FileIconView icon = new FileIconView(this, theme, FileIconView.MODE_SKETCH_PROJECT);
      LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
      iconParams.setMargins(0, 0, dp(8), 0);
      row.addView(icon, iconParams);

      TextView name = new TextView(this);
      name.setText(projectDir.getName());
      name.setTextColor(theme.text);
      name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
      name.setSingleLine(true);
      name.setEllipsize(TextUtils.TruncateAt.END);
      name.setGravity(Gravity.CENTER_VERTICAL);
      name.setIncludeFontPadding(false);
      row.addView(name, new LinearLayout.LayoutParams(0, dp(38), 1f));

      row.setOnClickListener(view -> {
         openInternalProject(projectDir);
         hideFilePanel();
         addConsoleEntry(LOG_INFO, sf(AppStrings.Key.OPENED_FOLDER, projectDir.getName()));
      });
      row.setOnLongClickListener(view -> {
         if (store != null && store.isExamplesProject(projectDir)) {
            return true;
         }
         if (settings != null && settings.hapticEnabled()) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
         }
         showProjectNodeMenu(view, projectDir);
         return true;
      });

      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
      params.setMargins(0, 0, 0, dp(2));
      row.setLayoutParams(params);
      return row;
   }

   private TextView createIndentedMutedText(String text, int depth) {
      TextView view = createMutedText(text);
      view.setPadding(dp(8 + depth * 14 + 42), dp(8), dp(8), dp(8));
      return view;
   }

   private boolean sameDocumentPath(DocumentFile document, File file) {
      if (document == null || file == null || document.getUri() == null) {
         return false;
      }
      Uri uri = document.getUri();
      return "file".equalsIgnoreCase(uri.getScheme())
         && uri.getPath() != null
         && samePath(new File(uri.getPath()), file);
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
      if (isReadOnlyDocument(document)) {
         if (document.isFile()) {
            showContextMenu(anchor, dp(208),
               new ContextMenuItem(s(AppStrings.Key.OPEN), R.drawable.context_menu_open_in_new_24, () -> openDocumentFile(document))
            );
         }
         return;
      }
      if (document.isDirectory()) {
         showContextMenu(anchor, dp(224),
            new ContextMenuItem(s(AppStrings.Key.NEW_FILE_MENU), R.drawable.context_menu_note_add_24, this::createFileInSelectedFolder),
            new ContextMenuItem(s(AppStrings.Key.NEW_FOLDER_MENU), R.drawable.context_menu_create_new_folder_24, this::createFolderInSelectedFolder),
            new ContextMenuItem(s(AppStrings.Key.RENAME), R.drawable.context_menu_edit_24, () -> showRenameDocumentDialog(document)),
            ContextMenuItem.destructive(s(AppStrings.Key.DELETE), R.drawable.context_menu_delete_24, () -> showDeleteDocumentConfirmation(document))
         );
      } else {
         showContextMenu(anchor, dp(208),
            new ContextMenuItem(s(AppStrings.Key.OPEN), R.drawable.context_menu_open_in_new_24, () -> openDocumentFile(document)),
            new ContextMenuItem(s(AppStrings.Key.RENAME), R.drawable.context_menu_edit_24, () -> showRenameDocumentDialog(document)),
            ContextMenuItem.destructive(s(AppStrings.Key.DELETE), R.drawable.context_menu_delete_24, () -> showDeleteDocumentConfirmation(document))
         );
      }
   }

   private void showProjectNodeMenu(View anchor, File projectDir) {
      showContextMenu(anchor, dp(224),
         new ContextMenuItem(s(AppStrings.Key.OPEN), R.drawable.context_menu_open_in_new_24, () -> {
            openInternalProject(projectDir);
            hideFilePanel();
            addConsoleEntry(LOG_INFO, sf(AppStrings.Key.OPENED_FOLDER, projectDir.getName()));
         }),
         new ContextMenuItem(s(AppStrings.Key.RENAME), R.drawable.context_menu_edit_24, () -> showRenameProjectDialog(projectDir)),
         ContextMenuItem.destructive(s(AppStrings.Key.DELETE_SKETCH), R.drawable.context_menu_delete_24, () -> showDeleteProjectConfirmation(projectDir))
      );
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
      if (isSystemSketchbookSection(folder) || isSketchProjectFolder(folder) || isReadOnlyDocument(folder)) {
         showToast(s(AppStrings.Key.CANNOT_CREATE_IN_SYSTEM_FOLDER));
         return null;
      }
      if (folder == null || !folder.isDirectory() || !folder.canWrite()) {
         addConsoleEntry(LOG_ERROR, s(AppStrings.Key.CHOOSE_WRITABLE_FOLDER_FIRST));
         activeConsoleTab = LOG_ERROR;
         renderConsole();
         return null;
      }
      return folder;
   }

   private boolean isSystemSketchbookSection(DocumentFile folder) {
      if (folder == null || !isSketchbookRoot(fileRoot)) {
         return false;
      }
      return sameDocument(folder, sketchbookSection(SKETCHES_DIR_NAME, store == null ? null : store.sketchesDir()))
         || sameDocument(folder, sketchbookSection(EXAMPLES_DIR_NAME, store == null ? null : store.examplesDir()))
         || sameDocument(folder, sketchbookSection(LIBRARY_EXAMPLES_DIR_NAME, store == null ? null : store.libraryExamplesDir()))
         || sameDocument(folder, sketchbookSection(RECENT_DIR_NAME, store == null ? null : store.recentDir()));
   }

   private boolean isSketchProjectFolder(DocumentFile folder) {
      if (folder == null || store == null || !isSketchbookRoot(fileRoot)) {
         return false;
      }
      Uri uri = folder.getUri();
      if (uri != null && "file".equalsIgnoreCase(uri.getScheme()) && uri.getPath() != null) {
         return store.isSketchProject(new File(uri.getPath()));
      }
      DocumentFile sketches = sketchbookSection(SKETCHES_DIR_NAME, store.sketchesDir());
      if (sketches == null) {
         return false;
      }
      File candidate = new File(store.sketchesDir(), displayName(folder));
      return isDirectChildOf(folder, sketches) && store.isSketchProject(candidate);
   }

   private boolean isDirectChildOf(DocumentFile child, DocumentFile parent) {
      if (child == null || parent == null) {
         return false;
      }
      DocumentFile found = parent.findFile(displayName(child));
      return found != null && fileKey(found).equals(fileKey(child));
   }

   private boolean sameDocument(DocumentFile left, DocumentFile right) {
      return left != null && right != null && fileKey(left).equals(fileKey(right));
   }

   private boolean activeProjectReadOnly() {
      return store != null && store.isCurrentProjectReadOnly();
   }

   private boolean ensureCurrentProjectWritable() {
      if (!activeProjectReadOnly()) {
         return true;
      }
      showReadOnlyProjectMessage();
      return false;
   }

   private void showReadOnlyProjectMessage() {
      showToast(s(AppStrings.Key.EXAMPLE_PROJECT_READ_ONLY));
   }

   private boolean isReadOnlyDocument(DocumentFile document) {
      if (document == null || store == null) {
         return false;
      }
      String path = localFilePath(document);
      File examplesDir = store.examplesDir();
      return path != null && examplesDir != null && isInsideDirectory(new File(path), examplesDir);
   }

   private boolean isInsideDirectory(File file, File directory) {
      if (file == null || directory == null) {
         return false;
      }
      try {
         String child = file.getCanonicalPath();
         String root = directory.getCanonicalPath();
         return child.equals(root) || child.startsWith(root + File.separator);
      } catch (IOException exception) {
         String child = file.getAbsolutePath();
         String root = directory.getAbsolutePath();
         return child.equals(root) || child.startsWith(root + File.separator);
      }
   }

   private void showToast(String message) {
      Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
   }

   private void showDeleteDocumentConfirmation(DocumentFile document) {
      if (isReadOnlyDocument(document)) {
         showReadOnlyProjectMessage();
         return;
      }
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
      if (isReadOnlyDocument(document)) {
         showReadOnlyProjectMessage();
         return;
      }
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

   private void openExternalPdeIntent(Intent intent) {
      if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) {
         return;
      }
      DocumentFile document = externalDocument(intent.getData());
      if (document == null || !document.isFile() || !isPdeDocument(document)) {
         return;
      }

      File localFile = localFileFromUri(intent.getData());
      if (localFile == null) {
         localFile = queryLocalFileFromContentUri(intent.getData());
      }
      File projectDir = projectDirForPdeFile(localFile);
      if (projectDir != null) {
         openInternalProject(projectDir);
         openLocalProjectFile(localFile);
         addConsoleEntry(LOG_INFO, sf(AppStrings.Key.OPENED_FOLDER, projectDir.getName()));
         return;
      }
      openExternalPdeAsNewSketch(document);
   }

   private DocumentFile externalDocument(Uri uri) {
      if (uri == null) {
         return null;
      }
      if ("file".equalsIgnoreCase(uri.getScheme())) {
         String path = uri.getPath();
         return path == null || path.isEmpty() ? null : DocumentFile.fromFile(new File(path));
      }
      return DocumentFile.fromSingleUri(this, uri);
   }

   private File localFileFromUri(Uri uri) {
      if (uri == null) {
         return null;
      }
      if ("file".equalsIgnoreCase(uri.getScheme())) {
         String path = uri.getPath();
         return path == null || path.isEmpty() ? null : new File(path);
      }
      if (!"content".equalsIgnoreCase(uri.getScheme()) || !DocumentsContract.isDocumentUri(this, uri)) {
         return null;
      }
      try {
         String documentId = DocumentsContract.getDocumentId(uri);
         return fileFromExternalStorageDocumentId(documentId);
      } catch (RuntimeException ignored) {
         return null;
      }
   }

   private File queryLocalFileFromContentUri(Uri uri) {
      if (uri == null || !"content".equalsIgnoreCase(uri.getScheme())) {
         return null;
      }
      try (Cursor cursor = getContentResolver().query(uri, new String[] {"_data"}, null, null, null)) {
         if (cursor == null || !cursor.moveToFirst()) {
            return null;
         }
         int index = cursor.getColumnIndex("_data");
         if (index < 0) {
            return null;
         }
         String path = cursor.getString(index);
         return path == null || path.trim().isEmpty() ? null : new File(path);
      } catch (RuntimeException ignored) {
         return null;
      }
   }

   private File fileFromExternalStorageDocumentId(String documentId) {
      if (documentId == null || documentId.trim().isEmpty()) {
         return null;
      }
      if (documentId.startsWith("raw:")) {
         return new File(documentId.substring(4));
      }
      int separator = documentId.indexOf(':');
      if (separator < 0) {
         return null;
      }
      String volume = documentId.substring(0, separator);
      String relativePath = documentId.substring(separator + 1);
      if ("primary".equalsIgnoreCase(volume)) {
         return new File(Environment.getExternalStorageDirectory(), relativePath);
      }
      return new File(new File("/storage", volume), relativePath);
   }

   private File projectDirForPdeFile(File file) {
      if (file == null || !file.getName().toLowerCase(Locale.US).endsWith(".pde")) {
         return null;
      }
      File parent = file.getParentFile();
      return parent == null || !parent.isDirectory() ? null : parent;
   }

   private void openExternalPdeAsNewSketch(DocumentFile document) {
      flushAutosave();
      clearConsole();
      File sketchesRoot = store == null ? null : store.sketchesDir();
      if (sketchesRoot == null) {
         addConsoleEntry(LOG_ERROR, s(AppStrings.Key.COULD_NOT_CREATE_FOLDER));
         activeConsoleTab = LOG_ERROR;
         renderConsole();
         return;
      }
      String sourceName = safePdeFileName(displayName(document));
      String projectBaseName = sourceName.substring(0, sourceName.length() - 4);
      File projectDir = new File(sketchesRoot, uniqueProjectFolderName(sanitizeProjectFolderName(projectBaseName)));
      if (!projectDir.mkdirs() && !projectDir.isDirectory()) {
         addConsoleEntry(LOG_ERROR, s(AppStrings.Key.COULD_NOT_CREATE_FOLDER));
         activeConsoleTab = LOG_ERROR;
         renderConsole();
         return;
      }
      if (!copyDocumentTreeToDirectory(document, new File(projectDir, sourceName))) {
         addConsoleEntry(LOG_ERROR, sf(AppStrings.Key.COULD_NOT_READ, displayName(document)));
         activeConsoleTab = LOG_ERROR;
         renderConsole();
         return;
      }
      openInternalProject(projectDir, false);
      openLocalProjectFile(new File(projectDir, sourceName));
      addConsoleEntry(LOG_INFO, sf(AppStrings.Key.OPENED_FOLDER, projectDir.getName()));
   }

   private String safePdeFileName(String name) {
      String safeName = safeLocalChildName(name);
      if (!safeName.toLowerCase(Locale.US).endsWith(".pde")) {
         safeName += ".pde";
      }
      return safeName;
   }

   private boolean isPdeDocument(DocumentFile document) {
      String name = document.getName();
      if (name != null && name.toLowerCase(Locale.US).endsWith(".pde")) {
         return true;
      }
      Uri uri = document.getUri();
      String lastSegment = uri == null ? null : uri.getLastPathSegment();
      return lastSegment != null && lastSegment.toLowerCase(Locale.US).endsWith(".pde");
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

      flushAutosave();
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
         if (projectDir != null && displayName(document).toLowerCase(Locale.US).endsWith(".pde")) {
            File localFile = new File(document.getUri().getPath());
            files.add(new SketchFile(displayName(document), readDocumentFile(document), null,
               projectDir.getAbsolutePath(), localFile.getAbsolutePath()));
            openTab(files.size() - 1);
            if (closePanel) {
               hideFilePanel();
            }
            addConsoleEntry(LOG_INFO, sf(AppStrings.Key.OPENED, displayName(document)));
            return;
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
         localProjectDir == null ? null : localProjectDir.getAbsolutePath(),
         localFilePath(document)));
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
      openInternalProject(projectDir, true);
   }

   private void openInternalProject(File projectDir, boolean saveCurrentProjectFirst) {
      if (saveCurrentProjectFirst) {
         flushAutosave();
      }
      clearConsole();
      store.switchProject(projectDir);
      files.clear();
      files.addAll(store.loadFiles());
      activeIndex = files.isEmpty() ? -1 : 0;
      syncFilePanelToCurrentProject();
      if (!files.isEmpty()) {
         loadingTab = true;
         editor.setCode(files.get(activeIndex).code);
         loadingTab = false;
         editorLoadedFile = true;
         updateLineNumbers();
      } else if (editor != null) {
         loadingTab = true;
         editor.setCode("");
         loadingTab = false;
         editorLoadedFile = false;
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

   private void openLocalProjectFile(File localFile) {
      if (localFile == null || files.isEmpty()) {
         return;
      }
      for (int i = 0; i < files.size(); i++) {
         SketchFile file = files.get(i);
         if ((file.localFilePath != null && samePath(new File(file.localFilePath), localFile))
            || file.name.equalsIgnoreCase(localFile.getName())) {
            openTab(i);
            store.save(files, activeIndex);
            return;
         }
      }
   }

   private void syncFilePanelToCurrentProject() {
      if (store == null) {
         fileRoot = null;
         selectedFolder = null;
         expandedFolders.clear();
         markFileTreeDirty();
         return;
      }
      File currentProjectDir = store.currentProjectDir();
      File root = store.sketchbookDir();
      fileRoot = root == null ? null : DocumentFile.fromFile(root);
      File selectedDir = currentProjectDir == null ? store.sketchesDir() : currentProjectDir;
      selectedFolder = selectedDir == null ? fileRoot : DocumentFile.fromFile(selectedDir);
      expandedFolders.clear();
      if (fileRoot != null) {
         expandedFolders.add(fileKey(fileRoot));
      }
      if (currentProjectDir != null && store.isExamplesProject(currentProjectDir)) {
         expandedFolders.add(SECTION_EXAMPLES);
      } else {
         expandedFolders.add(SECTION_SKETCHES);
      }
      markFileTreeDirty();
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
      File sketchesDir = store.sketchesDir();
      File examplesDir = store.examplesDir();
      if (sketchesDir == null && examplesDir == null) {
         return null;
      }
      File candidate = document.isDirectory() ? file : file.getParentFile();
      while (candidate != null && candidate.getParentFile() != null) {
         File parent = candidate.getParentFile();
         if ((sketchesDir != null && samePath(parent, sketchesDir))
            || (examplesDir != null && samePath(parent, examplesDir))) {
            return candidate;
         }
         candidate = candidate.getParentFile();
      }
      return null;
   }

   private String localFilePath(DocumentFile document) {
      if (document == null) {
         return null;
      }
      Uri uri = document.getUri();
      if (uri == null || !"file".equalsIgnoreCase(uri.getScheme()) || uri.getPath() == null) {
         return null;
      }
      return new File(uri.getPath()).getAbsolutePath();
   }

   private boolean samePath(File left, File right) {
      if (left == null || right == null) {
         return false;
      }
      try {
         return left.getCanonicalFile().equals(right.getCanonicalFile());
      } catch (IOException exception) {
         return left.getAbsoluteFile().equals(right.getAbsoluteFile());
      }
   }

   private boolean isSketchbookRoot(DocumentFile root) {
      if (root == null || store == null || store.sketchbookDir() == null) {
         return false;
      }
      Uri uri = root.getUri();
      if (uri != null && "file".equalsIgnoreCase(uri.getScheme())) {
         DocumentFile internalRoot = DocumentFile.fromFile(store.sketchbookDir());
         return internalRoot != null && fileKey(internalRoot).equals(fileKey(root));
      }
      String expectedName = store.sketchbookDir().getName();
      String rootName = displayName(root);
      return expectedName.equals(rootName) || SKETCHBOOK_DIR_NAME.equals(rootName);
   }

   private String sanitizeProjectFolderName(String value) {
      String sanitized = value == null ? "" : value.replace("/", "").replace("\\", "").trim().replaceAll("\\s+", "_");
      return sanitized.isEmpty() ? store.currentProjectName() : sanitized;
   }

   private String uniqueProjectFolderName(String baseName) {
      File root = store.sketchesDir();
      String name = baseName;
      int copy = 2;
      while (new File(root, name).exists()) {
         name = baseName + "_" + copy;
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
            File targetChild = new File(targetDir, safeLocalChildName(displayName(child)));
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
      } catch (IOException | RuntimeException exception) {
         addConsoleEntry(LOG_ERROR, s(AppStrings.Key.COULD_NOT_SAVE_EXTERNAL_FILE));
      }
   }

   private String normalizeDocumentName(String value) {
      return value == null ? "" : value.replace("/", "").replace("\\", "").trim();
   }

   private String safeLocalChildName(String value) {
      String name = normalizeDocumentName(value);
      return name.isEmpty() ? s(AppStrings.Key.UNTITLED) : name;
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
         boolean deletedLocalTab = belongsToDeletedLocalPath(files.get(i), deletedLocalProjectPath);
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
      return localFilePath(document);
   }

   private boolean isCurrentLocalProject(DocumentFile document) {
      if (document == null || store == null) {
         return false;
      }
      Uri uri = document.getUri();
      if (!document.isDirectory() || uri == null || !"file".equalsIgnoreCase(uri.getScheme()) || uri.getPath() == null) {
         return false;
      }
      File currentProjectDir = store.currentProjectDir();
      if (currentProjectDir == null) {
         return false;
      }
      return samePath(new File(uri.getPath()), currentProjectDir);
   }

   private boolean sameLocalProject(String localProjectPath, File projectDir) {
      if (localProjectPath == null || projectDir == null) {
         return false;
      }
      return localProjectPath.equals(projectDir.getAbsolutePath());
   }

   private boolean belongsToDeletedLocalPath(SketchFile file, String deletedLocalPath) {
      if (file == null || deletedLocalPath == null) {
         return false;
      }
      String localFilePath = file.localFilePath;
      if ((localFilePath == null || localFilePath.trim().isEmpty()) && file.documentUri == null && file.localProjectPath != null) {
         localFilePath = new File(file.localProjectPath, file.name).getAbsolutePath();
      }
      if (localFilePath == null || localFilePath.trim().isEmpty()) {
         return false;
      }
      return localFilePath.equals(deletedLocalPath)
         || localFilePath.startsWith(deletedLocalPath + File.separator);
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
      if (activeProjectReadOnly()) {
         showContextMenu(anchor, dp(274),
            new ContextMenuItem(s(AppStrings.Key.NEW_SKETCH), R.drawable.context_menu_note_add_24, this::createNewSketchProject),
            new ContextMenuItem(s(AppStrings.Key.RECENT_SKETCHES), R.drawable.context_menu_history_24, this::showRecentProjectsDialog),
            new ContextMenuItem(s(AppStrings.Key.TOOLS), R.drawable.context_menu_build_24, this::showToolsDialog),
            new ContextMenuItem(s(AppStrings.Key.SKETCH_PROPERTIES), R.drawable.context_menu_tune_24, this::openSketchProperties),
            new ContextMenuItem(s(AppStrings.Key.SETTINGS), R.drawable.context_menu_settings_24, this::openSettings)
         );
         return;
      }
      showContextMenu(anchor, dp(274),
         new ContextMenuItem(s(AppStrings.Key.NEW_SKETCH), R.drawable.context_menu_note_add_24, this::createNewSketchProject),
         new ContextMenuItem(s(AppStrings.Key.RENAME_SKETCH), R.drawable.context_menu_edit_24, this::renameCurrentSketch),
         ContextMenuItem.destructive(s(AppStrings.Key.DELETE_SKETCH), R.drawable.context_menu_delete_24, this::deleteCurrentSketch),
         new ContextMenuItem(s(AppStrings.Key.RECENT_SKETCHES), R.drawable.context_menu_history_24, this::showRecentProjectsDialog),
         new ContextMenuItem(s(AppStrings.Key.TOOLS), R.drawable.context_menu_build_24, this::showToolsDialog),
         new ContextMenuItem(s(AppStrings.Key.SKETCH_PROPERTIES), R.drawable.context_menu_tune_24, this::openSketchProperties),
         new ContextMenuItem(s(AppStrings.Key.SETTINGS), R.drawable.context_menu_settings_24, this::openSettings)
      );
   }

   private void saveCurrentSketch() {
      flushAutosave();
      File projectDir = store == null ? null : store.currentProjectDir();
      if (projectDir != null) {
         addConsoleEntry(LOG_INFO, sf(AppStrings.Key.SAVED_SKETCH_TO, projectDir.getAbsolutePath()));
      }
   }

   private boolean isCurrentProjectRoot(DocumentFile root) {
      if (root == null || store == null) {
         return false;
      }
      File currentProjectDir = store.currentProjectDir();
      Uri uri = root.getUri();
      return currentProjectDir != null
         && uri != null
         && "file".equalsIgnoreCase(uri.getScheme())
         && uri.getPath() != null
         && samePath(new File(uri.getPath()), currentProjectDir);
   }

   private void showRecentProjectsDialog() {
      Dialog dialog = new Dialog(this);
      dialog.getWindow();

      LinearLayout content = new LinearLayout(this);
      content.setOrientation(LinearLayout.VERTICAL);
      content.setPadding(dp(10), dp(10), dp(10), dp(10));
      UiStyles.roundedStroke(content, theme.surface, theme.border, dp(1), dp(14));

      TextView titleView = new TextView(this);
      titleView.setText(s(AppStrings.Key.RECENT_SKETCHES));
      titleView.setTextColor(theme.text);
      titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
      titleView.setTypeface(Typeface.DEFAULT_BOLD);
      titleView.setPadding(dp(8), dp(4), dp(8), dp(10));
      content.addView(titleView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

      List<File> recentProjects = store == null ? new ArrayList<>() : store.recentProjects();
      if (recentProjects.isEmpty()) {
         TextView empty = createMutedText(s(AppStrings.Key.NO_RECENT_SKETCHES));
         content.addView(empty, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));
      } else {
         for (File project : recentProjects) {
            TextView row = new TextView(this);
            row.setText(project.getName());
            row.setTextColor(theme.text);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setSingleLine(true);
            row.setEllipsize(TextUtils.TruncateAt.END);
            row.setPadding(dp(12), 0, dp(12), 0);
            UiStyles.rounded(row, theme.surface, dp(8));
            row.setOnClickListener(view -> {
               dialog.dismiss();
               openInternalProject(project);
               addConsoleEntry(LOG_INFO, sf(AppStrings.Key.OPENED_FOLDER, project.getName()));
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
            params.setMargins(0, 0, 0, dp(4));
            content.addView(row, params);
         }
      }

      dialog.setContentView(content, new ViewGroup.LayoutParams(dp(300), ViewGroup.LayoutParams.WRAP_CONTENT));
      if (dialog.getWindow() != null) {
         dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
         dialog.getWindow().setGravity(Gravity.CENTER);
      }
      dialog.show();
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
         new ContextMenuItem(s(AppStrings.Key.AUTO_FORMAT), R.drawable.tools_format_paint_24, this::autoFormatActivePde),
         new ContextMenuItem(s(AppStrings.Key.COLOR_SELECTOR), R.drawable.tools_color_lens_24, this::showColorSelectorDialog),
         new ContextMenuItem(s(AppStrings.Key.FIND_REPLACE), R.drawable.tools_find_replace_24, this::showFindReplaceDialog),
         new ContextMenuItem(s(AppStrings.Key.IMPORT_LIBRARY), R.drawable.tools_library_add_24, () -> showPlaceholderAction(s(AppStrings.Key.IMPORT_LIBRARY))),
         new ContextMenuItem(s(AppStrings.Key.OPEN_REFERENCE), R.drawable.tools_menu_book_24, this::openReference),
         new ContextMenuItem(s(AppStrings.Key.AI_AGENT), R.drawable.tools_smart_toy_24, () -> showPlaceholderAction(s(AppStrings.Key.AI_AGENT)))
      };
      for (ContextMenuItem item : items) {
         LinearLayout row = new LinearLayout(this);
         row.setGravity(Gravity.CENTER_VERTICAL);
         row.setPadding(dp(12), 0, dp(12), 0);
         UiStyles.rounded(row, theme.surface, dp(8));

         ImageView icon = new ImageView(this);
         icon.setImageResource(item.iconResource);
         icon.setColorFilter(theme.textMuted);
         icon.setScaleType(ImageView.ScaleType.CENTER);
         LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(44));
         iconParams.setMargins(0, 0, dp(8), 0);
         row.addView(icon, iconParams);

         TextView title = new TextView(this);
         title.setText(item.title);
         title.setTextColor(theme.text);
         title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
         title.setSingleLine(true);
         title.setEllipsize(TextUtils.TruncateAt.END);
         title.setGravity(Gravity.CENTER_VERTICAL);
         title.setIncludeFontPadding(false);
         row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

         row.setOnClickListener(view -> {
            dialog.dismiss();
            item.action.run();
         });
         LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
         params.setMargins(0, 0, 0, dp(4));
         content.addView(row, params);
      }

      dialog.setContentView(content, new ViewGroup.LayoutParams(dp(320), ViewGroup.LayoutParams.WRAP_CONTENT));
      if (dialog.getWindow() != null) {
         dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
         dialog.getWindow().setGravity(Gravity.CENTER);
      }
      dialog.show();
   }

   private void openSettings() {
      flushAutosave();
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
      flushAutosave();
      File projectDir = store.createNewSketchProject();
      if (projectDir == null) {
         addConsoleEntry(LOG_ERROR, s(AppStrings.Key.COULD_NOT_CREATE_FOLDER));
         activeConsoleTab = LOG_ERROR;
         renderConsole();
         return;
      }
      openInternalProject(projectDir, false);
      applyFileTemplateToNewSketch();
      addConsoleEntry(LOG_INFO, sf(AppStrings.Key.OPENED_FOLDER, projectDir.getName()));
   }

   private void applyFileTemplateToNewSketch() {
      if (settings == null || !settings.fileTemplateEnabled() || files.isEmpty()) {
         return;
      }
      activeIndex = 0;
      files.get(activeIndex).code = defaultSketchTemplate(settings.tabSize());
      loadingTab = true;
      editor.setCode(files.get(activeIndex).code);
      loadingTab = false;
      editorLoadedFile = true;
      updateLineNumbers();
      store.save(files, activeIndex);
   }

   private String defaultSketchTemplate(int tabSize) {
      String indent = repeatString(" ", Math.max(2, Math.min(8, tabSize)));
      return "\n\nvoid setup() {\n"
         + indent + "fullScreen();\n"
         + "}\n\n"
         + "void draw() {\n"
         + indent + "background(255);\n"
         + "}";
   }

   private String repeatString(String value, int count) {
      StringBuilder builder = new StringBuilder(value.length() * Math.max(0, count));
      for (int i = 0; i < count; i++) {
         builder.append(value);
      }
      return builder.toString();
   }

   private void saveCurrentProjectToSketchbook() {
      if (!ensureCurrentProjectWritable()) {
         return;
      }
      flushAutosave();
      File savedProject = store.saveCurrentProjectToSketchbook();
      if (savedProject == null) {
         addConsoleEntry(LOG_ERROR, s(AppStrings.Key.COULD_NOT_CREATE_FOLDER));
         activeConsoleTab = LOG_ERROR;
         renderConsole();
         return;
      }
      openInternalProject(savedProject);
      addConsoleEntry(LOG_INFO, sf(AppStrings.Key.OPENED_FOLDER, savedProject.getName()));
   }

   private void moveCurrentProjectToSketchbook() {
      if (!ensureCurrentProjectWritable()) {
         return;
      }
      File sketchbookRoot = store.sketchesDir();
      File currentInternalProject = store.currentProjectDir();
      if (fileRoot == null || isSketchbookRoot(fileRoot)) {
         openInternalProject(currentInternalProject);
         addConsoleEntry(LOG_INFO, sf(AppStrings.Key.OPENED_FOLDER, currentInternalProject.getName()));
         return;
      }

      String targetName = uniqueProjectFolderName(sanitizeProjectFolderName(displayName(fileRoot)));
      File targetDir = new File(sketchbookRoot, targetName);
      if (!copyDocumentTreeToDirectory(fileRoot, targetDir)) {
         deleteLocalRecursively(targetDir);
         addConsoleEntry(LOG_ERROR, s(AppStrings.Key.COULD_NOT_CREATE_FOLDER));
         activeConsoleTab = LOG_ERROR;
         renderConsole();
         return;
      }
      openInternalProject(targetDir);
      addConsoleEntry(LOG_INFO, sf(AppStrings.Key.OPENED_FOLDER, targetDir.getName()));
   }

   private void deleteLocalRecursively(File file) {
      if (file == null || !file.exists()) {
         return;
      }
      if (file.isDirectory()) {
         File[] children = file.listFiles();
         if (children != null) {
            for (File child : children) {
               deleteLocalRecursively(child);
            }
         }
      }
      file.delete();
   }

   private void renameCurrentSketch() {
      if (!ensureCurrentProjectWritable()) {
         return;
      }
      File currentProjectDir = store.currentProjectDir();
      if (currentProjectDir == null) {
         return;
      }
      showRenameProjectDialog(currentProjectDir);
   }

   private void showRenameProjectDialog(File projectDir) {
      if (store == null || projectDir == null) {
         return;
      }
      if (store.isExamplesProject(projectDir)) {
         showReadOnlyProjectMessage();
         return;
      }
      File currentProjectDir = store.currentProjectDir();
      boolean renamingCurrentProject = currentProjectDir != null && samePath(currentProjectDir, projectDir);
      String oldProjectPath = projectDir.getAbsolutePath();
      showTextInputDialog(s(AppStrings.Key.RENAME_SKETCH), projectDir.getName(), projectDir.getName(), s(AppStrings.Key.RENAME), true, value -> {
         String normalized = sanitizeProjectFolderName(value);
         if (normalized.isEmpty()) {
            return false;
         }
         File targetDir = new File(store.sketchesDir(), normalized);
         if (samePath(projectDir, targetDir)) {
            return true;
         }
         if (targetDir.exists() || !projectDir.renameTo(targetDir)) {
            addConsoleEntry(LOG_ERROR, sf(AppStrings.Key.COULD_NOT_RENAME, projectDir.getName()));
            activeConsoleTab = LOG_ERROR;
            renderConsole();
            return false;
         }
         if (renamingCurrentProject) {
            store.switchProject(targetDir);
            for (SketchFile file : files) {
               if (file.documentUri == null && oldProjectPath.equals(file.localProjectPath)) {
                  file.localProjectPath = targetDir.getAbsolutePath();
                  file.localFilePath = new File(targetDir, file.name).getAbsolutePath();
               }
            }
            updateProjectTitle();
         }
         fileRoot = DocumentFile.fromFile(store.sketchbookDir());
         selectedFolder = DocumentFile.fromFile(renamingCurrentProject ? store.currentProjectDir() : targetDir);
         expandedFolders.clear();
         if (fileRoot != null) {
            expandedFolders.add(fileKey(fileRoot));
         }
         expandedFolders.add(SECTION_SKETCHES);
         updateProjectTitle();
         renderFileTree();
         addConsoleEntry(LOG_INFO, sf(AppStrings.Key.RENAMED_TO, targetDir.getName()));
         return true;
      });
   }

   private void deleteCurrentSketch() {
      if (!ensureCurrentProjectWritable()) {
         return;
      }
      File currentProjectDir = store.currentProjectDir();
      if (currentProjectDir == null) {
         return;
      }
      showDeleteProjectConfirmation(currentProjectDir);
   }

   private void showDeleteProjectConfirmation(File projectDir) {
      if (store == null || projectDir == null) {
         return;
      }
      if (store.isExamplesProject(projectDir)) {
         showReadOnlyProjectMessage();
         return;
      }
      String name = projectDir.getName();
      showConfirmationDialog(s(AppStrings.Key.DELETE_SKETCH), sf(AppStrings.Key.DELETE_QUESTION, name), s(AppStrings.Key.DELETE), () -> {
         File currentProjectDir = store.currentProjectDir();
         boolean deletingCurrentProject = currentProjectDir != null && samePath(currentProjectDir, projectDir);
         if (deletingCurrentProject) {
            flushAutosave();
         }
         store.deleteProject(projectDir);
         if (deletingCurrentProject) {
            File nextProjectDir = store.currentProjectDir();
            if (nextProjectDir != null) {
               openInternalProject(nextProjectDir, false);
               addConsoleEntry(LOG_INFO, sf(AppStrings.Key.DELETED, name));
               return;
            }
            files.clear();
            activeIndex = -1;
         }
         fileRoot = DocumentFile.fromFile(store.sketchbookDir());
         selectedFolder = deletingCurrentProject && store.currentProjectDir() != null
            ? DocumentFile.fromFile(store.currentProjectDir())
            : (store.sketchesDir() == null ? fileRoot : DocumentFile.fromFile(store.sketchesDir()));
         expandedFolders.clear();
         if (fileRoot != null) {
            expandedFolders.add(fileKey(fileRoot));
         }
         expandedFolders.add(SECTION_SKETCHES);
         if (deletingCurrentProject) {
            syncEditorState();
            rebuildTabs();
            updateProjectTitle();
            store.save(files, activeIndex);
         }
         renderFileTree();
         addConsoleEntry(LOG_INFO, sf(AppStrings.Key.DELETED, name));
      });
   }

   private void autoFormatActivePde() {
      if (!ensureCurrentProjectWritable()) {
         return;
      }
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

   private void showFindReplaceDialog() {
      if (editor == null || activeIndex < 0 || activeIndex >= files.size()) {
         addConsoleEntry(LOG_ERROR, s(AppStrings.Key.NO_EDITOR_AVAILABLE));
         activeConsoleTab = LOG_ERROR;
         renderConsole();
         return;
      }

      Dialog dialog = new Dialog(this);
      dialog.setCanceledOnTouchOutside(true);
      FindSession session = new FindSession();

      LinearLayout content = new LinearLayout(this);
      content.setOrientation(LinearLayout.VERTICAL);
      content.setPadding(dp(12), dp(10), dp(12), dp(10));
      UiStyles.roundedStroke(content, theme.surface, theme.border, dp(1), dp(12));

      LinearLayout header = new LinearLayout(this);
      header.setGravity(Gravity.CENTER_VERTICAL);
      TextView titleView = new TextView(this);
      titleView.setText(s(AppStrings.Key.FIND_REPLACE));
      titleView.setTextColor(theme.text);
      titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
      titleView.setTypeface(Typeface.DEFAULT_BOLD);
      header.addView(titleView, new LinearLayout.LayoutParams(0, dp(32), 1f));
      TextView more = dialogButton(s(AppStrings.Key.MORE), false);
      header.addView(more, new LinearLayout.LayoutParams(dp(84), dp(34)));
      content.addView(header, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(34)));

      EditText findInput = createFindReplaceInput(s(AppStrings.Key.FIND), selectedEditorTextForSearch());
      LinearLayout.LayoutParams findParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
      findParams.setMargins(0, dp(8), 0, dp(6));
      content.addView(findInput, findParams);

      EditText replaceInput = createFindReplaceInput(s(AppStrings.Key.REPLACE), "");
      LinearLayout.LayoutParams replaceParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40));
      replaceParams.setMargins(0, 0, 0, dp(8));
      content.addView(replaceInput, replaceParams);

      LinearLayout actions = new LinearLayout(this);
      actions.setGravity(Gravity.CENTER_VERTICAL);
      TextView find = dialogButton(s(AppStrings.Key.FIND), true);
      TextView replace = dialogButton(s(AppStrings.Key.REPLACE), false);
      TextView replaceAll = dialogButton(s(AppStrings.Key.REPLACE_ALL), false);
      actions.addView(find, weightedActionParams(0));
      actions.addView(replace, weightedActionParams(dp(6)));
      actions.addView(replaceAll, weightedActionParams(dp(6)));
      content.addView(actions, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));

      TextView status = new TextView(this);
      status.setTextColor(theme.textMuted);
      status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
      status.setGravity(Gravity.CENTER_VERTICAL);
      content.addView(status, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(24)));

      LinearLayout advanced = new LinearLayout(this);
      advanced.setGravity(Gravity.CENTER_VERTICAL);
      advanced.setVisibility(View.GONE);
      TextView previous = dialogButton(s(AppStrings.Key.PREVIOUS), false);
      advanced.addView(previous, new LinearLayout.LayoutParams(dp(92), dp(38)));
      CheckBox matchCase = new CheckBox(this);
      matchCase.setText(s(AppStrings.Key.MATCH_CASE));
      matchCase.setTextColor(theme.text);
      matchCase.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
      matchCase.setPadding(dp(8), 0, 0, 0);
      matchCase.setButtonTintList(new ColorStateList(
         new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
         new int[] {theme.accent, theme.border}
      ));
      advanced.addView(matchCase, new LinearLayout.LayoutParams(0, dp(38), 1f));
      content.addView(advanced, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));

      TextWatcher watcher = new TextWatcher() {
         @Override
         public void beforeTextChanged(CharSequence s, int start, int count, int after) {
         }

         @Override
         public void onTextChanged(CharSequence s, int start, int before, int count) {
         }

         @Override
         public void afterTextChanged(Editable editable) {
            refreshFindSession(session, findInput.getText().toString(), matchCase.isChecked(), status, true);
         }
      };
      findInput.addTextChangedListener(watcher);
      matchCase.setOnCheckedChangeListener((buttonView, isChecked) ->
         refreshFindSession(session, findInput.getText().toString(), isChecked, status, true));

      more.setOnClickListener(view -> {
         boolean showAdvanced = advanced.getVisibility() != View.VISIBLE;
         advanced.setVisibility(showAdvanced ? View.VISIBLE : View.GONE);
         more.setText(s(showAdvanced ? AppStrings.Key.LESS : AppStrings.Key.MORE));
      });
      previous.setOnClickListener(view -> moveFindSelection(session, findInput.getText().toString(), matchCase.isChecked(), status, false));
      find.setOnClickListener(view -> moveFindSelection(session, findInput.getText().toString(), matchCase.isChecked(), status, true));
      replace.setOnClickListener(view -> replaceCurrentFindMatch(session, findInput.getText().toString(), replaceInput.getText().toString(), matchCase.isChecked(), status));
      replaceAll.setOnClickListener(view -> replaceAllFindMatches(session, findInput.getText().toString(), replaceInput.getText().toString(), matchCase.isChecked(), status));

      int width = Math.min(dp(330), getResources().getDisplayMetrics().widthPixels - dp(28));
      dialog.setContentView(content, new ViewGroup.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT));
      dialog.show();
      if (dialog.getWindow() != null) {
         dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
         dialog.getWindow().setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
         android.view.WindowManager.LayoutParams attributes = dialog.getWindow().getAttributes();
         attributes.y = systemTopInset + dp(18);
         dialog.getWindow().setAttributes(attributes);
      }
      findInput.requestFocus();
      findInput.selectAll();
      refreshFindSession(session, findInput.getText().toString(), matchCase.isChecked(), status, true);
   }

   private EditText createFindReplaceInput(String hint, String value) {
      EditText input = new EditText(this);
      input.setSingleLine(true);
      input.setTextColor(theme.text);
      input.setHintTextColor(theme.textMuted);
      input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
      input.setTypeface(AppFonts.code(this));
      input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
      input.setHint(hint);
      input.setText(value);
      input.setSelectAllOnFocus(true);
      input.setPadding(dp(10), 0, dp(10), 0);
      UiStyles.roundedStroke(input, theme.surfaceSoft, theme.border, dp(1), dp(8));
      return input;
   }

   private LinearLayout.LayoutParams weightedActionParams(int leftMargin) {
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1f);
      params.setMargins(leftMargin, 0, 0, 0);
      return params;
   }

   private String selectedEditorTextForSearch() {
      if (editor == null || !editor.hasSelection()) {
         return "";
      }
      int start = Math.max(0, Math.min(editor.getSelectionStart(), editor.getSelectionEnd()));
      int end = Math.max(0, Math.max(editor.getSelectionStart(), editor.getSelectionEnd()));
      String selected = editor.code().substring(start, Math.min(end, editor.code().length()));
      return selected.indexOf('\n') >= 0 || selected.indexOf('\r') >= 0 ? "" : selected;
   }

   private void refreshFindSession(FindSession session, String query, boolean matchCase, TextView status, boolean selectWhenAvailable) {
      session.matches = findMatches(editor.code(), query, matchCase);
      if (query == null || query.isEmpty()) {
         session.index = -1;
         status.setText(s(AppStrings.Key.SEARCH_FIELD_EMPTY));
         return;
      }
      if (session.matches.isEmpty()) {
         session.index = -1;
         status.setText(s(AppStrings.Key.NO_MATCHES_FOUND));
         return;
      }

      int selectedIndex = selectedFindIndex(session.matches);
      session.index = selectedIndex >= 0 ? selectedIndex : Math.max(0, Math.min(session.index, session.matches.size() - 1));
      if (selectedIndex < 0 && selectWhenAvailable) {
         session.index = 0;
         selectFindMatch(session.matches.get(session.index));
      }
      status.setText(sf(AppStrings.Key.FIND_MATCH_COUNT, session.index + 1, session.matches.size()));
   }

   private void moveFindSelection(FindSession session, String query, boolean matchCase, TextView status, boolean forward) {
      session.matches = findMatches(editor.code(), query, matchCase);
      if (query == null || query.isEmpty()) {
         session.index = -1;
         status.setText(s(AppStrings.Key.SEARCH_FIELD_EMPTY));
         return;
      }
      if (session.matches.isEmpty()) {
         session.index = -1;
         status.setText(s(AppStrings.Key.NO_MATCHES_FOUND));
         return;
      }

      int selectedIndex = selectedFindIndex(session.matches);
      int anchor = forward
         ? Math.max(editor.getSelectionStart(), editor.getSelectionEnd())
         : Math.min(editor.getSelectionStart(), editor.getSelectionEnd());
      if (selectedIndex >= 0) {
         FindMatch selected = session.matches.get(selectedIndex);
         anchor = forward ? selected.end : selected.start;
      }

      session.index = forward ? firstMatchAtOrAfter(session.matches, anchor) : lastMatchBefore(session.matches, anchor);
      selectFindMatch(session.matches.get(session.index));
      status.setText(sf(AppStrings.Key.FIND_MATCH_COUNT, session.index + 1, session.matches.size()));
   }

   private void replaceCurrentFindMatch(FindSession session, String query, String replacement, boolean matchCase, TextView status) {
      if (!ensureCurrentProjectWritable()) {
         return;
      }
      session.matches = findMatches(editor.code(), query, matchCase);
      if (query == null || query.isEmpty()) {
         session.index = -1;
         status.setText(s(AppStrings.Key.SEARCH_FIELD_EMPTY));
         return;
      }
      if (session.matches.isEmpty()) {
         session.index = -1;
         status.setText(s(AppStrings.Key.NO_MATCHES_FOUND));
         return;
      }

      int selectedIndex = selectedFindIndex(session.matches);
      if (selectedIndex < 0) {
         moveFindSelection(session, query, matchCase, status, true);
         return;
      }

      FindMatch match = session.matches.get(selectedIndex);
      String code = editor.code();
      String edited = code.substring(0, match.start) + replacement + code.substring(match.end);
      int nextAnchor = match.start + replacement.length();
      if (editor.applyEditedCode(edited, nextAnchor)) {
         files.get(activeIndex).code = editor.code();
         store.save(files, activeIndex);
      }

      session.matches = findMatches(editor.code(), query, matchCase);
      if (session.matches.isEmpty()) {
         session.index = -1;
         status.setText(s(AppStrings.Key.NO_MATCHES_FOUND));
         return;
      }
      session.index = firstMatchAtOrAfter(session.matches, nextAnchor);
      selectFindMatch(session.matches.get(session.index));
      status.setText(sf(AppStrings.Key.FIND_MATCH_COUNT, session.index + 1, session.matches.size()));
   }

   private void replaceAllFindMatches(FindSession session, String query, String replacement, boolean matchCase, TextView status) {
      if (!ensureCurrentProjectWritable()) {
         return;
      }
      List<FindMatch> matches = findMatches(editor.code(), query, matchCase);
      if (query == null || query.isEmpty()) {
         session.index = -1;
         status.setText(s(AppStrings.Key.SEARCH_FIELD_EMPTY));
         return;
      }
      if (matches.isEmpty()) {
         session.index = -1;
         status.setText(s(AppStrings.Key.NO_MATCHES_FOUND));
         return;
      }

      String code = editor.code();
      StringBuilder edited = new StringBuilder(code.length() + Math.max(0, replacement.length() - query.length()) * matches.size());
      int cursor = 0;
      for (FindMatch match : matches) {
         edited.append(code, cursor, match.start);
         edited.append(replacement);
         cursor = match.end;
      }
      edited.append(code, cursor, code.length());

      int selection = Math.min(edited.length(), matches.get(0).start + replacement.length());
      if (editor.applyEditedCode(edited.toString(), selection)) {
         files.get(activeIndex).code = editor.code();
         store.save(files, activeIndex);
      }
      addConsoleEntry(LOG_INFO, sf(AppStrings.Key.REPLACED_OCCURRENCES, matches.size()));
      refreshFindSession(session, query, matchCase, status, true);
   }

   private List<FindMatch> findMatches(String code, String query, boolean matchCase) {
      List<FindMatch> matches = new ArrayList<>();
      if (code == null || query == null || query.isEmpty() || query.length() > code.length()) {
         return matches;
      }
      int index = 0;
      while (index <= code.length() - query.length()) {
         if (code.regionMatches(!matchCase, index, query, 0, query.length())) {
            matches.add(new FindMatch(index, index + query.length()));
            index += query.length();
         } else {
            index++;
         }
      }
      return matches;
   }

   private int selectedFindIndex(List<FindMatch> matches) {
      int start = Math.min(editor.getSelectionStart(), editor.getSelectionEnd());
      int end = Math.max(editor.getSelectionStart(), editor.getSelectionEnd());
      for (int i = 0; i < matches.size(); i++) {
         FindMatch match = matches.get(i);
         if (match.start == start && match.end == end) {
            return i;
         }
      }
      return -1;
   }

   private int firstMatchAtOrAfter(List<FindMatch> matches, int anchor) {
      for (int i = 0; i < matches.size(); i++) {
         if (matches.get(i).start >= anchor) {
            return i;
         }
      }
      return 0;
   }

   private int lastMatchBefore(List<FindMatch> matches, int anchor) {
      for (int i = matches.size() - 1; i >= 0; i--) {
         if (matches.get(i).start < anchor) {
            return i;
         }
      }
      return matches.size() - 1;
   }

   private void selectFindMatch(FindMatch match) {
      editor.setSelection(match.start, match.end);
      editor.setCursorVisible(true);
      editor.post(() -> {
         editor.bringPointIntoView(match.start);
         editor.bringPointIntoView(match.end);
      });
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

      ColorFieldView colorField = new ColorFieldView(this, theme);
      colorField.setHue(hsv[0]);
      colorField.setSelection(hsv[1], hsv[2]);
      LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
      pickerRow.addView(colorField, fieldParams);

      HueSliderView hueSlider = new HueSliderView(this, theme);
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

      ImageButton copy = new ImageButton(this);
      copy.setImageResource(R.drawable.copy_24);
      copy.setColorFilter(theme.background);
      copy.setContentDescription(s(AppStrings.Key.COPY));
      copy.setPadding(dp(7), dp(7), dp(7), dp(7));
      copy.setScaleType(ImageButton.ScaleType.CENTER);
      UiStyles.roundedStroke(copy, theme.accent, theme.accent, dp(1), dp(8));
      copy.setOnClickListener(view -> copyPlainText("HEX", hexValue.getText().toString()));
      hexRow.addView(copy, new LinearLayout.LayoutParams(dp(38), dp(38)));
      content.addView(hexRow, hexRowParams);

      class ColorSync {
         void sync(EditText skippedInput) {
            updating[0] = true;
            int color = Color.HSVToColor(hsv);
            int red = Color.red(color);
            int green = Color.green(color);
            int blue = Color.blue(color);
            setChannelText(redInput, String.valueOf(red), skippedInput);
            setChannelText(greenInput, String.valueOf(green), skippedInput);
            setChannelText(blueInput, String.valueOf(blue), skippedInput);
            setChannelText(hueInput, String.valueOf(Math.round(hsv[0])), skippedInput);
            setChannelText(saturationInput, String.valueOf(Math.round(hsv[1] * 100f)), skippedInput);
            setChannelText(brightnessInput, String.valueOf(Math.round(hsv[2] * 100f)), skippedInput);
            hexValue.setText(String.format(Locale.US, "#%02X%02X%02X", red, green, blue));
            colorField.setHue(hsv[0]);
            colorField.setSelection(hsv[1], hsv[2]);
            hueSlider.setHue(hsv[0]);
            applyColorValueFieldStyle(hexValue, color);
            updating[0] = false;
         }

         private void setChannelText(EditText input, String value, EditText skippedInput) {
            if (input == skippedInput && input.hasFocus()) {
               return;
            }
            if (TextUtils.equals(input.getText(), value)) {
               return;
            }
            input.setText(value);
            if (input.hasFocus()) {
               input.setSelection(input.getText().length());
            }
         }
      }
      ColorSync colorSync = new ColorSync();

      colorField.setListener((saturation, value) -> {
         hsv[1] = saturation;
         hsv[2] = value;
         colorSync.sync(null);
      });
      hueSlider.setListener(hue -> {
         hsv[0] = hue;
         colorSync.sync(null);
      });

      class ColorChannelWatcher implements TextWatcher {
         private final EditText source;
         private final boolean rgb;

         ColorChannelWatcher(EditText source, boolean rgb) {
            this.source = source;
            this.rgb = rgb;
         }

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
            if (rgb) {
               int red = parseColorChannel(redInput.getText().toString());
               int green = parseColorChannel(greenInput.getText().toString());
               int blue = parseColorChannel(blueInput.getText().toString());
               Color.RGBToHSV(red, green, blue, hsv);
            } else {
               hsv[0] = parseHueChannel(hueInput.getText().toString());
               hsv[1] = parsePercentChannel(saturationInput.getText().toString());
               hsv[2] = parsePercentChannel(brightnessInput.getText().toString());
            }
            colorSync.sync(source);
         }
      }
      redInput.addTextChangedListener(new ColorChannelWatcher(redInput, true));
      greenInput.addTextChangedListener(new ColorChannelWatcher(greenInput, true));
      blueInput.addTextChangedListener(new ColorChannelWatcher(blueInput, true));
      hueInput.addTextChangedListener(new ColorChannelWatcher(hueInput, false));
      saturationInput.addTextChangedListener(new ColorChannelWatcher(saturationInput, false));
      brightnessInput.addTextChangedListener(new ColorChannelWatcher(brightnessInput, false));

      View.OnFocusChangeListener normalizeOnBlur = (view, hasFocus) -> {
         if (!hasFocus && !updating[0]) {
            colorSync.sync(null);
         }
      };
      redInput.setOnFocusChangeListener(normalizeOnBlur);
      greenInput.setOnFocusChangeListener(normalizeOnBlur);
      blueInput.setOnFocusChangeListener(normalizeOnBlur);
      hueInput.setOnFocusChangeListener(normalizeOnBlur);
      saturationInput.setOnFocusChangeListener(normalizeOnBlur);
      brightnessInput.setOnFocusChangeListener(normalizeOnBlur);

      colorSync.sync(null);

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
      input.setGravity(Gravity.CENTER_VERTICAL);
      input.setPadding(dp(10), 0, dp(10), 0);
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
      if (activeProjectReadOnly()) {
         return;
      }
      showContextMenu(anchor, dp(268),
         new ContextMenuItem(s(AppStrings.Key.NEW_TAB), R.drawable.context_menu_note_add_24, this::addTab),
         new ContextMenuItem(s(AppStrings.Key.RENAME), R.drawable.context_menu_edit_24, this::showRenameTabDialog),
         ContextMenuItem.destructive(s(AppStrings.Key.DELETE_TAB), R.drawable.context_menu_delete_24, this::removeTab)
      );
   }

   private void showPlaceholderAction(String title) {
      addConsoleEntry(LOG_INFO, sf(AppStrings.Key.IS_NOT_IMPLEMENTED_YET, title));
   }

   private void showTextInputDialog(String title, String hint, String value, String confirmLabel, TextInputAction action) {
      showTextInputDialog(title, hint, value, confirmLabel, false, action);
   }

   private void showTextInputDialog(String title, String hint, String value, String confirmLabel, boolean replaceSpacesWithUnderscores, TextInputAction action) {
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
      if (replaceSpacesWithUnderscores) {
         installSpaceToUnderscoreFilter(input);
      }
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

   private void installSpaceToUnderscoreFilter(EditText input) {
      final boolean[] updating = {false};
      TextWatcher watcher = new TextWatcher() {
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
            String text = editable.toString();
            String normalized = text.replace(' ', '_');
            if (text.equals(normalized)) {
               return;
            }
            int cursor = input.getSelectionStart();
            updating[0] = true;
            input.setText(normalized);
            input.setSelection(Math.max(0, Math.min(cursor, normalized.length())));
            updating[0] = false;
         }
      };
      input.addTextChangedListener(watcher);
      watcher.afterTextChanged(input.getText());
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
         LinearLayout row = new LinearLayout(this);
         row.setGravity(Gravity.CENTER_VERTICAL);
         row.setPadding(dp(12), 0, dp(12), 0);
         UiStyles.rounded(row, theme.surface, dp(8));

         if (item.iconResource != 0) {
            ImageView icon = new ImageView(this);
            icon.setImageResource(item.iconResource);
            icon.setColorFilter(item.destructive ? theme.error : theme.textMuted);
            icon.setScaleType(ImageView.ScaleType.CENTER);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(44));
            iconParams.setMargins(0, 0, dp(8), 0);
            row.addView(icon, iconParams);
         }

         TextView title = new TextView(this);
         title.setText(item.title);
         title.setTextColor(item.destructive ? theme.error : theme.text);
         title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
         title.setSingleLine(true);
         title.setEllipsize(TextUtils.TruncateAt.END);
         title.setGravity(Gravity.CENTER_VERTICAL);
         title.setIncludeFontPadding(false);
         row.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

         row.setOnClickListener(view -> {
            popup.dismiss();
            item.action.run();
         });
         LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
         params.setMargins(0, 0, 0, dp(3));
         menu.addView(row, params);
      }

      showContextPopup(anchor, popup, menu, width);
   }

   private void showContextPopup(View anchor, PopupWindow popup, View menu, int width) {
      int gap = dp(6);
      Rect visibleFrame = new Rect();
      anchor.getWindowVisibleDisplayFrame(visibleFrame);
      int[] anchorLocation = new int[2];
      anchor.getLocationOnScreen(anchorLocation);

      int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
      int heightSpec = View.MeasureSpec.makeMeasureSpec(visibleFrame.height(), View.MeasureSpec.AT_MOST);
      menu.measure(widthSpec, heightSpec);

      int menuHeight = menu.getMeasuredHeight();
      int anchorBottom = anchorLocation[1] + anchor.getHeight();
      int spaceBelow = visibleFrame.bottom - (anchorLocation[1] + anchor.getHeight()) - gap;
      int spaceAbove = anchorLocation[1] - visibleFrame.top - gap;
      int menuTop = spaceBelow >= menuHeight || spaceBelow >= spaceAbove
         ? anchorBottom + gap
         : anchorLocation[1] - menuHeight - gap;

      int minimumTop = visibleFrame.top + gap;
      int maximumTop = Math.max(minimumTop, visibleFrame.bottom - menuHeight - gap);
      menuTop = Math.max(minimumTop, Math.min(menuTop, maximumTop));
      popup.showAsDropDown(anchor, 0, menuTop - anchorBottom);
   }

   private void showRenameTabDialog() {
      if (!ensureCurrentProjectWritable()) {
         return;
      }
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
         } else if (!normalized.equalsIgnoreCase(activeFile.name)) {
            persistCurrentFile();
            if (!store.renameSketchFile(activeFile, normalized)) {
               addConsoleEntry(LOG_ERROR, sf(AppStrings.Key.COULD_NOT_RENAME, activeFile.name));
               activeConsoleTab = LOG_ERROR;
               renderConsole();
               return false;
            }
         } else {
            activeFile.name = normalized;
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
      String name = requestedName == null ? "" : requestedName.replace("/", "").replace("\\", "").trim();
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
         if (editor != null) {
            editor.setExternalResize(true);
            editor.setExternalResizeTracksOverlayBounds(true);
         }
         return true;
      }
      if (event.getAction() == MotionEvent.ACTION_MOVE) {
         float delta = consoleStartY - event.getRawY();
         scheduleConsoleHeight(clamp(Math.round(consoleStartHeight + delta), collapsedConsoleHeight(), maxConsoleHeight()));
         return true;
      }
      if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
         flushPendingConsoleHeight();
         userResizingConsole = false;
         updateEditorViewportForConsole();
         if (editor != null) editor.setExternalResize(false);
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
      if (consolePanel == null) return;
      boolean wasCollapsed = consoleCollapsed;
      int collapsed = collapsedConsoleHeight();
      consoleHeight = height <= dp(72) ? collapsed : height;
      consoleCollapsed = consoleHeight == collapsed;
      if (userResizingConsole && wasCollapsed != consoleCollapsed) {
         performConsoleToggleFeedback();
      }
      if (!consoleCollapsed) {
         expandedConsoleHeight = consoleHeight;
      }
      applyConsoleHeightLayout();
   }

   private void scheduleConsoleHeight(int height) {
      pendingConsoleHeight = height;
      if (consoleHeightApplyScheduled || consolePanel == null) {
         return;
      }
      consoleHeightApplyScheduled = true;
      consolePanel.postOnAnimation(applyPendingConsoleHeightRunnable);
   }

   private void applyPendingConsoleHeight() {
      consoleHeightApplyScheduled = false;
      setConsoleHeight(pendingConsoleHeight);
   }

   private void flushPendingConsoleHeight() {
      if (!consoleHeightApplyScheduled || consolePanel == null) {
         return;
      }
      consolePanel.removeCallbacks(applyPendingConsoleHeightRunnable);
      consoleHeightApplyScheduled = false;
      setConsoleHeight(pendingConsoleHeight);
   }

   private void applyConsoleHeightLayout() {
      if (consolePanel == null) return;
      ViewGroup.LayoutParams params = consolePanel.getLayoutParams();
      if (params.height != consoleHeight) {
         params.height = consoleHeight;
         consolePanel.setLayoutParams(params);
      }
      updateEditorViewportForConsole();
      if (consoleHeader != null) {
         consoleHeader.setVisibility(consoleCollapsed ? View.GONE : View.VISIBLE);
      }
      if (consoleScroll != null) {
         consoleScroll.setVisibility(consoleCollapsed ? View.GONE : View.VISIBLE);
      }
   }

   private void updateEditorViewportForConsole() {
      if (editor == null) {
         return;
      }
      int translatedConsoleOffset = Math.round(consoleImeRestoreOffset());
      editor.setBottomOverlayInset(Math.max(0, consoleHeight + imeInset - translatedConsoleOffset));
   }

   private void collapseConsole() {
      if (consolePanel != null) {
         setConsoleHeight(collapsedConsoleHeight());
      }
   }

   private void performConsoleToggleFeedback() {
      if (consolePanel == null || settings == null || !settings.hapticEnabled()) {
         return;
      }
      Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
      if (vibrator == null || !vibrator.hasVibrator()) {
         return;
      }
      try {
         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
         } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE));
         } else {
            vibrator.vibrate(18L);
         }
      } catch (SecurityException ignored) {
      }
   }

   private void applySystemBarColors() {
      if (getWindow() == null) {
         return;
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
         getWindow().setStatusBarColor(theme.background);
         getWindow().setNavigationBarColor(theme.surface);
      }
   }

   private void animateThemeChange(String themeMode) {
      EditorTheme from = theme;
      EditorTheme to = EditorTheme.load(this, themeMode);
      cancelThemeAnimation();
      appliedThemeMode = themeMode;
      themeAnimator = ValueAnimator.ofFloat(0f, 1f);
      themeAnimator.setDuration(220L);
      themeAnimator.addUpdateListener(animator -> {
         float fraction = (Float) animator.getAnimatedValue();
         applyAnimatedTheme(EditorTheme.interpolate(from, to, fraction), false);
      });
      final boolean[] canceled = {false};
      themeAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
         @Override
         public void onAnimationEnd(android.animation.Animator animation) {
            if (canceled[0]) {
               return;
            }
            themeAnimator = null;
            applyAnimatedTheme(to, true);
         }

         @Override
         public void onAnimationCancel(android.animation.Animator animation) {
            canceled[0] = true;
            themeAnimator = null;
         }
      });
      themeAnimator.start();
   }

   private void cancelThemeAnimation() {
      if (themeAnimator != null) {
         ValueAnimator animator = themeAnimator;
         themeAnimator = null;
         animator.cancel();
      }
   }

   private void applyAnimatedTheme(EditorTheme nextTheme, boolean finalPass) {
      EditorTheme previousTheme = theme;
      theme = nextTheme;
      applySystemBarColors();
      if (rootView != null) {
         rootView.setBackgroundColor(theme.background);
      }
      if (navigationBarBackground != null) {
         navigationBarBackground.setBackgroundColor(theme.surface);
      }
      if (editorPanel != null) {
         UiStyles.topRounded(editorPanel, theme.background, dp(14));
      }
      if (consolePanel != null) {
         UiStyles.topRoundedSideStroke(consolePanel, theme.surface, theme.border, dp(1), dp(14));
      }
      if (consoleHandle != null) {
         UiStyles.rounded(consoleHandle, theme.border, dp(3));
      }
      if (filePanelScrim != null) {
         filePanelScrim.setBackgroundColor(theme.scrim);
      }
      if (filePanelContainer != null) {
         UiStyles.roundedStroke(filePanelContainer, theme.surface, theme.border, dp(1), dp(14));
      }
      styleConsoleTab(consoleTab, activeConsoleTab == LOG_INFO);
      styleConsoleTab(errorsTab, activeConsoleTab == LOG_ERROR);
      updateFilePanelTitle();
      applyThemeToTree(rootContainer, previousTheme, theme);
      if (finalPass) {
         rebuildTabs();
         renderConsole();
         renderFileTree();
      }
   }

   private void applyThemeToTree(View view, EditorTheme previousTheme, EditorTheme nextTheme) {
      if (view == null) {
         return;
      }
      if (view instanceof ThemeAware) {
         ((ThemeAware) view).applyTheme(nextTheme);
      } else if (view instanceof TextView) {
         remapTextColor((TextView) view, previousTheme, nextTheme);
      }
      remapBackgroundColor(view, previousTheme, nextTheme);
      if (view instanceof ViewGroup) {
         ViewGroup group = (ViewGroup) view;
         for (int i = 0; i < group.getChildCount(); i++) {
            applyThemeToTree(group.getChildAt(i), previousTheme, nextTheme);
         }
      }
   }

   private void remapTextColor(TextView textView, EditorTheme previousTheme, EditorTheme nextTheme) {
      int current = textView.getCurrentTextColor();
      int updated = remapThemeColor(current, previousTheme, nextTheme);
      if (updated != current) {
         textView.setTextColor(updated);
      }
      if (textView instanceof EditText) {
         ((EditText) textView).setHintTextColor(remapThemeColor(textView.getHintTextColors().getDefaultColor(), previousTheme, nextTheme));
      }
   }

   private void remapBackgroundColor(View view, EditorTheme previousTheme, EditorTheme nextTheme) {
      if (UiStyles.remapThemeBackground(view, previousTheme, nextTheme)) {
         return;
      }
      if (view.getBackground() instanceof ColorDrawable) {
         ColorDrawable drawable = (ColorDrawable) view.getBackground();
         int updated = remapThemeColor(drawable.getColor(), previousTheme, nextTheme);
         if (updated != drawable.getColor()) {
            drawable.setColor(updated);
         }
      } else if (view.getBackground() instanceof GradientDrawable) {
         GradientDrawable drawable = (GradientDrawable) view.getBackground();
         ColorStateList color = drawable.getColor();
         if (color == null) {
            return;
         }
         int current = color.getDefaultColor();
         int updated = remapThemeColor(current, previousTheme, nextTheme);
         if (updated != current) {
            drawable.setColor(updated);
         }
      }
   }

   private int remapThemeColor(int color, EditorTheme previousTheme, EditorTheme nextTheme) {
      if (color == previousTheme.text) return nextTheme.text;
      if (color == previousTheme.textMuted) return nextTheme.textMuted;
      if (color == previousTheme.accent) return nextTheme.accent;
      if (color == previousTheme.background) return nextTheme.background;
      if (color == previousTheme.surface) return nextTheme.surface;
      if (color == previousTheme.surfaceSoft) return nextTheme.surfaceSoft;
      if (color == previousTheme.border) return nextTheme.border;
      if (sameRgb(color, previousTheme.border)) return withAlpha(nextTheme.border, Color.alpha(color));
      if (color == previousTheme.codeAccent) return nextTheme.codeAccent;
      if (color == previousTheme.error) return nextTheme.error;
      if (color == previousTheme.play) return nextTheme.play;
      if (color == previousTheme.stop) return nextTheme.stop;
      return color;
   }

   private boolean sameRgb(int left, int right) {
      return Color.red(left) == Color.red(right)
         && Color.green(left) == Color.green(right)
         && Color.blue(left) == Color.blue(right);
   }

   private int withAlpha(int color, int alpha) {
      return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
   }

   private void expandConsoleForError() {
      activeConsoleTab = LOG_ERROR;
      if (consolePanel != null && consoleCollapsed) {
         setConsoleHeight(runConsoleHeight());
      }
      renderConsole();
      scrollConsoleToBottom();
   }

   private void openConsoleForRun() {
      activeConsoleTab = LOG_INFO;
      if (consolePanel != null && consoleCollapsed) {
         setConsoleHeight(runConsoleHeight());
      }
      renderConsole();
      scrollConsoleToBottom();
   }

   private int collapsedConsoleHeight() {
      return imeShown ? dp(28) : dp(40);
   }

   private int maxConsoleHeight() {
      if (editorWorkspace == null || editorWorkspace.getHeight() <= 0) {
         return dp(180);
      }
      int visibleWorkspaceHeight = Math.max(0, editorWorkspace.getHeight() - imeInset);
      return Math.max(collapsedConsoleHeight(), visibleWorkspaceHeight - dp(90));
   }

   private int runConsoleHeight() {
      int mediumRunHeight = rootView == null ? dp(250) : Math.round(rootView.getHeight() * 0.42f);
      int targetHeight = Math.max(expandedConsoleHeight, Math.max(dp(240), mediumRunHeight));
      return Math.min(targetHeight, maxConsoleHeight());
   }

   private void handleImeTransition(boolean appearing, int targetImeInset) {
      beginImeTransition(appearing);
      imeInset = targetImeInset;
      applyContentPadding();
      finishImeTransition();
   }

   private void beginImeTransition(boolean appearing) {
      resetConsoleImeOffset();
      imeTransitionAppearing = appearing;
      imeShown = appearing;
      if (editor != null) {
         editor.setExternalResizeTracksOverlayBounds(!appearing);
      }
      if (consolePanel == null) {
         return;
      }
      if (appearing) {
         if (!consoleCollapsed) {
            consoleHeightBeforeIme = consoleHeight;
            consoleCollapsedByIme = true;
         }
         setConsoleHeight(collapsedConsoleHeight());
      } else {
         int startHeight = consoleHeight;
         int targetHeight = consoleCollapsedByIme
            ? Math.max(consoleHeightBeforeIme, dp(180))
            : collapsedConsoleHeight();
         // Restore the layout height now, but keep its top edge on the IME path until the inset reaches zero.
         consoleImeRestoreStartInset = Math.max(0, imeInset);
         consoleImeRestoreHeightDelta = targetHeight - startHeight;
         setConsoleHeight(targetHeight);
         applyImeOverlayOffset();
      }
   }

   private void finishImeTransition() {
      if (imeTransitionAppearing) {
         return;
      }
      int targetHeight = consoleCollapsedByIme
         ? Math.max(consoleHeightBeforeIme, dp(180))
         : collapsedConsoleHeight();
      consoleCollapsedByIme = false;
      imeInset = 0;
      resetConsoleImeOffset();
      setConsoleHeight(targetHeight);
      applyContentPadding();
   }

   private void resetConsoleImeOffset() {
      consoleImeRestoreStartInset = 0;
      consoleImeRestoreHeightDelta = 0;
      if (consolePanel != null) {
         consolePanel.setTranslationY(0f);
      }
   }

   private void clearConsole() {
      runHandler.removeCallbacks(flushPreviewConsoleRunnable);
      previewConsoleFlushScheduled = false;
      pendingPreviewConsoleOutput.clear();
      openPreviewConsoleEntry = null;
      consoleEntries.clear();
      activeConsoleTab = LOG_INFO;
      renderConsole();
   }

   private void addConsoleEntry(int type, String message) {
      consoleEntries.add(new ConsoleEntry(type, message));
      trimConsoleEntries();
      renderConsole();
      scrollConsoleToBottom();
   }

   private void queuePreviewConsoleOutput(String output) {
      if (output == null || output.isEmpty()) {
         return;
      }
      pendingPreviewConsoleOutput.add(output);
      if (pendingPreviewConsoleOutput.size() > MAX_CONSOLE_ENTRIES) {
         pendingPreviewConsoleOutput.remove(0);
      }
      if (!previewConsoleFlushScheduled) {
         previewConsoleFlushScheduled = true;
         runHandler.postDelayed(flushPreviewConsoleRunnable, PREVIEW_LOG_FLUSH_DELAY_MS);
      }
   }

   private void flushPreviewConsoleEntries() {
      runHandler.removeCallbacks(flushPreviewConsoleRunnable);
      previewConsoleFlushScheduled = false;
      if (pendingPreviewConsoleOutput.isEmpty()) {
         return;
      }
      for (String output : pendingPreviewConsoleOutput) {
         appendPreviewConsoleOutput(output);
      }
      pendingPreviewConsoleOutput.clear();
      trimConsoleEntries();
      renderConsole();
      scrollConsoleToBottom();
   }

   private void appendPreviewConsoleOutput(String output) {
      int start = 0;
      for (int i = 0; i < output.length(); i++) {
         char ch = output.charAt(i);
         if (ch == '\n') {
            appendPreviewConsoleSegment(output.substring(start, i).replace("\r", ""));
            openPreviewConsoleEntry = null;
            start = i + 1;
         }
      }
      if (start < output.length()) {
         appendPreviewConsoleSegment(output.substring(start).replace("\r", ""));
      }
   }

   private void appendPreviewConsoleSegment(String segment) {
      if (openPreviewConsoleEntry == null) {
         openPreviewConsoleEntry = new ConsoleEntry(LOG_CONSOLE, "");
         consoleEntries.add(openPreviewConsoleEntry);
      }
      openPreviewConsoleEntry.message += segment;
   }

   private void trimConsoleEntries() {
      int overflow = consoleEntries.size() - MAX_CONSOLE_ENTRIES;
      if (overflow > 0) {
         consoleEntries.subList(0, overflow).clear();
         if (openPreviewConsoleEntry != null && !consoleEntries.contains(openPreviewConsoleEntry)) {
            openPreviewConsoleEntry = null;
         }
      }
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
      String message;

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
         if (type == LOG_CONSOLE) {
            return ConsoleStatusIconView.MODE_TERMINAL;
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
         if (type == LOG_CONSOLE) {
            return theme.play;
         }
         return theme.codeAccent;
      }

      String copyText() {
         return message;
      }
   }

   private static final class ContextMenuItem {
      final String title;
      final int iconResource;
      final boolean destructive;
      final Runnable action;

      ContextMenuItem(String title, Runnable action) {
         this(title, 0, false, action);
      }

      ContextMenuItem(String title, int iconResource, Runnable action) {
         this(title, iconResource, false, action);
      }

      private ContextMenuItem(String title, int iconResource, boolean destructive, Runnable action) {
         this.title = title;
         this.iconResource = iconResource;
         this.destructive = destructive;
         this.action = action;
      }

      static ContextMenuItem destructive(String title, int iconResource, Runnable action) {
         return new ContextMenuItem(title, iconResource, true, action);
      }
   }

   private static final class FindSession {
      List<FindMatch> matches = new ArrayList<>();
      int index = -1;
   }

   private static final class FindMatch {
      final int start;
      final int end;

      FindMatch(int start, int end) {
         this.start = start;
         this.end = end;
      }
   }

   private interface TextInputAction {
      boolean apply(String value);
   }
}
