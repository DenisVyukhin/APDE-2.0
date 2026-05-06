package com.apde2;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.EditText;
import android.widget.OverScroller;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.regex.Matcher;

final class CodeEditorView extends EditText {
   private static final long HISTORY_SNAPSHOT_DELAY_MS = 500L;

   private final EditorTheme theme;
   private final OverScroller flingScroller;
   private final Deque<EditState> undoStack = new ArrayDeque<>();
   private final Deque<EditState> redoStack = new ArrayDeque<>();
   private final Handler historyHandler = new Handler(Looper.getMainLooper());
   private final Runnable commitHistoryRunnable = this::commitPendingHistory;
   private final int maximumFlingVelocity;
   private final int minimumFlingVelocity;
   private boolean highlighting;
   private boolean editingPair;
   private boolean editingHistory;
   private EditState pendingState;
   private VelocityTracker velocityTracker;
   private int skipPairEditStart = -1;
   private float touchStartX;
   private float touchStartY;
   private int touchStartSelection;
   private boolean touchStartedFocused;
   private boolean touchMoved;
   private boolean selectionMode;
   private boolean selectionScrollLocked;
   private int selectionLockScrollX;
   private int selectionLockScrollY;
   private int tabSize = 3;
   private boolean autoClosePairs = true;

   CodeEditorView(Context context, EditorTheme theme) {
      super(context);
      this.theme = theme;
      flingScroller = new OverScroller(context);
      ViewConfiguration viewConfiguration = ViewConfiguration.get(context);
      maximumFlingVelocity = viewConfiguration.getScaledMaximumFlingVelocity();
      minimumFlingVelocity = viewConfiguration.getScaledMinimumFlingVelocity();
      setTextColor(theme.text);
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
      setTypeface(AppFonts.code(context));
      setSingleLine(false);
      setHorizontallyScrolling(true);
      setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
      setPadding(dp(12), dp(12), dp(12), dp(12));
      setImeOptions(EditorInfo.IME_ACTION_NONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
      setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
      setBackgroundColor(theme.background);
      setHighlightColor(theme.selection);
      setCursorVisible(false);
      setShowSoftInputOnFocus(false);
      setVerticalScrollBarEnabled(true);
      setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
      setScrollContainer(true);
      setOnFocusChangeListener((view, hasFocus) -> setCursorVisible(hasFocus));

      addTextChangedListener(new TextWatcher() {
         @Override
         public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            if (!editingHistory && pendingState == null) {
               pendingState = new EditState(s.toString(), Math.max(0, getSelectionStart()));
            }
         }

         @Override
         public void onTextChanged(CharSequence s, int start, int before, int count) {
         }

         @Override
         public void afterTextChanged(Editable editable) {
            scheduleHistorySnapshot();
            highlight(editable);
         }
      });
   }

   @Override
   public boolean onTouchEvent(MotionEvent event) {
      if (event.getAction() == MotionEvent.ACTION_DOWN) {
         flingScroller.abortAnimation();
         recycleVelocityTracker();
         velocityTracker = VelocityTracker.obtain();
         touchStartX = event.getX();
         touchStartY = event.getY();
         touchStartSelection = Math.max(0, getSelectionStart());
         touchStartedFocused = hasFocus();
         touchMoved = false;
         selectionMode = hasSelection();
      } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
         ensureVelocityTracker();
         float dx = Math.abs(event.getX() - touchStartX);
         float dy = Math.abs(event.getY() - touchStartY);
         if (dx >= dp(8) || dy >= dp(8)) {
            touchMoved = true;
            setCursorVisible(false);
         }
      }

      if (velocityTracker != null) {
         velocityTracker.addMovement(event);
      }

      boolean handled = super.onTouchEvent(event);

      if (event.getAction() == MotionEvent.ACTION_UP) {
         float dx = Math.abs(event.getX() - touchStartX);
         float dy = Math.abs(event.getY() - touchStartY);
         boolean tap = !touchMoved && dx < dp(8) && dy < dp(8);
         if (tap) {
            requestFocus();
            setCursorVisible(true);
            showKeyboard();
         } else if (!selectionMode && !hasSelection()) {
            int scrollX = getScrollX();
            int scrollY = getScrollY();
            scrollTo(scrollX, scrollY);
            post(() -> scrollTo(scrollX, scrollY));
            startFlingIfNeeded();
            if (!touchStartedFocused) {
               clearFocus();
            }
            setCursorVisible(false);
         } else {
            setCursorVisible(true);
         }
         recycleVelocityTracker();
      } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
         recycleVelocityTracker();
      }

      return handled;
   }

   @Override
   public void computeScroll() {
      super.computeScroll();
      if (flingScroller.computeScrollOffset()) {
         scrollTo(flingScroller.getCurrX(), flingScroller.getCurrY());
         postInvalidateOnAnimation();
      }
   }

   @Override
   public void scrollTo(int x, int y) {
      super.scrollTo(clamp(x, 0, maxScrollX()), clamp(y, 0, maxScrollY()));
   }

   @Override
   public boolean onKeyDown(int keyCode, KeyEvent event) {
      if (keyCode == KeyEvent.KEYCODE_TAB) {
         int start = Math.max(getSelectionStart(), 0);
         getText().insert(start, indentationUnit());
         return true;
      }
      if (keyCode == KeyEvent.KEYCODE_ENTER) {
         insertNewLineWithIndent();
         return true;
      }
      return super.onKeyDown(keyCode, event);
   }

   @Override
   protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
      super.onTextChanged(text, start, lengthBefore, lengthAfter);
      if (editingPair || lengthAfter != 1 || start < 0 || start >= text.length()) {
         return;
      }

      char typed = text.charAt(start);
      if (shouldSkipClosingPair(typed, start)) {
         editingPair = true;
         getText().delete(start, start + 1);
         setSelection(start + 1);
         editingPair = false;
         skipPairEditStart = -1;
         return;
      }

      if (typed == '\n') {
         formatInsertedNewLine(start);
         return;
      }

      if (!autoClosePairs) {
         return;
      }

      String pair = pairFor(typed);
      if (pair.isEmpty()) {
         return;
      }

      editingPair = true;
      getText().insert(start + 1, pair);
      setSelection(start + 1);
      editingPair = false;
   }

   @Override
   protected void onSelectionChanged(int selectionStart, int selectionEnd) {
      super.onSelectionChanged(selectionStart, selectionEnd);
      if (selectionStart != selectionEnd) {
         lockSelectionScroll();
      } else {
         selectionScrollLocked = false;
      }
   }

   void setCode(String code) {
      historyHandler.removeCallbacks(commitHistoryRunnable);
      editingHistory = true;
      setText(code);
      setSelection(getText().length());
      undoStack.clear();
      redoStack.clear();
      pendingState = null;
      editingHistory = false;
      highlight(getText());
   }

   String code() {
      return getText().toString();
   }

   void setEditorFontSizeSp(int sizeSp) {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
   }

   void setTabSize(int tabSize) {
      this.tabSize = Math.max(2, Math.min(8, tabSize));
   }

   void setAutoClosePairs(boolean enabled) {
      autoClosePairs = enabled;
   }

   void formatCodeInPlace() {
      String formatted = formatCode(code(), tabSize);
      if (!formatted.equals(code())) {
         applyFormattedCode(formatted);
      }
   }

   boolean applyFormattedCode(String formatted) {
      if (formatted == null) {
         return false;
      }
      String current = code();
      if (formatted.equals(current)) {
         return false;
      }
      commitPendingHistory();
      undoStack.push(new EditState(current, Math.max(0, getSelectionStart())));
      redoStack.clear();
      historyHandler.removeCallbacks(commitHistoryRunnable);
      editingHistory = true;
      Editable editable = getText();
      editable.replace(0, editable.length(), formatted);
      int selection = Math.max(0, Math.min(getSelectionStart(), editable.length()));
      setSelection(selection);
      pendingState = null;
      editingHistory = false;
      highlight(editable);
      return true;
   }

   static String formatCode(String code, int tabSize) {
      if (code == null || code.trim().isEmpty()) {
         return "";
      }
      String formatted = new Formatter(code, Math.max(2, Math.min(8, tabSize))).format();
      return normalizeFormattedCode(formatted);
   }

   boolean undo() {
      commitPendingHistory();
      if (undoStack.isEmpty()) {
         return false;
      }
      EditState current = new EditState(getText().toString(), Math.max(0, getSelectionStart()));
      EditState target = undoStack.pop();
      redoStack.push(current);
      applyState(target);
      return true;
   }

   boolean redo() {
      commitPendingHistory();
      if (redoStack.isEmpty()) {
         return false;
      }
      EditState current = new EditState(getText().toString(), Math.max(0, getSelectionStart()));
      EditState target = redoStack.pop();
      undoStack.push(current);
      applyState(target);
      return true;
   }

   private void insertNewLineWithIndent() {
      Editable editable = getText();
      int cursor = Math.max(0, getSelectionStart());
      int lineStart = cursor;
      while (lineStart > 0 && editable.charAt(lineStart - 1) != '\n') {
         lineStart--;
      }

      StringBuilder indent = new StringBuilder();
      for (int i = lineStart; i < cursor && i < editable.length(); i++) {
         char c = editable.charAt(i);
         if (c == ' ' || c == '\t') {
            indent.append(c);
         } else {
            break;
         }
      }

      if (cursor > 0 && cursor < editable.length() && editable.charAt(cursor - 1) == '{' && editable.charAt(cursor) == '}') {
         String innerIndent = indent + indentationUnit();
         String insertion = "\n" + innerIndent + "\n" + indent;
         editable.insert(cursor, insertion);
         setSelection(cursor + 1 + innerIndent.length());
         return;
      }

      editable.insert(cursor, "\n" + indent);
   }

   private void formatInsertedNewLine(int newlineIndex) {
      Editable editable = getText();
      if (newlineIndex < 0 || newlineIndex >= editable.length()) {
         return;
      }

      int lineStart = newlineIndex;
      while (lineStart > 0 && editable.charAt(lineStart - 1) != '\n') {
         lineStart--;
      }

      StringBuilder indent = new StringBuilder();
      for (int i = lineStart; i < newlineIndex && i < editable.length(); i++) {
         char c = editable.charAt(i);
         if (c == ' ' || c == '\t') {
            indent.append(c);
         } else {
            break;
         }
      }

      boolean betweenBraces = newlineIndex > 0
         && newlineIndex + 1 < editable.length()
         && editable.charAt(newlineIndex - 1) == '{'
         && editable.charAt(newlineIndex + 1) == '}';

      editingPair = true;
      if (betweenBraces) {
         String innerIndent = indent + indentationUnit();
         String replacement = "\n" + innerIndent + "\n" + indent;
         editable.replace(newlineIndex, newlineIndex + 1, replacement);
         setSelection(newlineIndex + 1 + innerIndent.length());
      } else if (indent.length() > 0) {
         editable.replace(newlineIndex, newlineIndex + 1, "\n" + indent);
         setSelection(newlineIndex + 1 + indent.length());
      }
      editingPair = false;
   }

   @Override
   protected void onDetachedFromWindow() {
      historyHandler.removeCallbacks(commitHistoryRunnable);
      super.onDetachedFromWindow();
   }

   private void scheduleHistorySnapshot() {
      if (editingHistory || pendingState == null) {
         return;
      }
      historyHandler.removeCallbacks(commitHistoryRunnable);
      historyHandler.postDelayed(commitHistoryRunnable, HISTORY_SNAPSHOT_DELAY_MS);
   }

   private void commitPendingHistory() {
      historyHandler.removeCallbacks(commitHistoryRunnable);
      if (editingHistory || pendingState == null) {
         return;
      }
      String current = getText().toString();
      if (!pendingState.text.equals(current)) {
         undoStack.push(pendingState);
         redoStack.clear();
      }
      pendingState = null;
   }

   private void applyState(EditState state) {
      editingHistory = true;
      setText(state.text);
      setSelection(Math.max(0, Math.min(state.selection, getText().length())));
      editingHistory = false;
      highlight(getText());
   }

   private void lockSelectionScroll() {
      if (!selectionScrollLocked) {
         selectionLockScrollX = getScrollX();
         selectionLockScrollY = getScrollY();
         selectionScrollLocked = true;
      }
      hideKeyboard();
      post(() -> scrollTo(selectionLockScrollX, selectionLockScrollY));
   }

   private String pairFor(char typed) {
      if (typed == '(') {
         return ")";
      }
      if (typed == '[') {
         return "]";
      }
      if (typed == '{') {
         return "}";
      }
      if (typed == '"') {
         return "\"";
      }
      if (typed == '\'') {
         return "'";
      }
      return "";
   }

   private boolean shouldSkipClosingPair(char typed, int start) {
      if (!isClosingPair(typed) || start <= 0 || start + 1 >= getText().length()) {
         return false;
      }
      if (skipPairEditStart == start) {
         return false;
      }
      return getText().charAt(start + 1) == typed;
   }

   private boolean isClosingPair(char typed) {
      return typed == ')' || typed == ']' || typed == '}' || typed == '"' || typed == '\'';
   }

   private String indentationUnit() {
      return repeatSpace(tabSize);
   }

   private static String repeat(String value, int count) {
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < count; i++) {
         builder.append(value);
      }
      return builder.toString();
   }

   private static void appendIndent(StringBuilder out, int indentLevel, String unit) {
      out.append(repeat(unit, indentLevel));
   }

   private static void appendNewline(StringBuilder out, int count) {
      trimTrailingSpaces(out);
      for (int i = 0; i < count; i++) {
         if (out.length() == 0 || out.charAt(out.length() - 1) != '\n') {
            out.append('\n');
         } else if (i > 0) {
            out.append('\n');
         }
      }
   }

   private static void trimTrailingSpaces(StringBuilder out) {
      while (out.length() > 0 && (out.charAt(out.length() - 1) == ' ' || out.charAt(out.length() - 1) == '\t')) {
         out.deleteCharAt(out.length() - 1);
      }
   }

   private static void trimTrailingNewlines(StringBuilder out) {
      while (out.length() > 0 && out.charAt(out.length() - 1) == '\n') {
         out.deleteCharAt(out.length() - 1);
      }
   }

   private static String repeatSpace(int count) {
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < count; i++) {
         builder.append(' ');
      }
      return builder.toString();
   }

   private static String normalizeFormattedCode(String code) {
      String[] lines = code.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < lines.length; i++) {
         if (i > 0) {
            builder.append('\n');
         }
         builder.append(normalizeFormattedLine(lines[i]));
      }
      trimTrailingSpaces(builder);
      return builder.toString();
   }

   private static String normalizeFormattedLine(String line) {
      if (line.isEmpty()) {
         return line;
      }
      int indentEnd = 0;
      while (indentEnd < line.length() && line.charAt(indentEnd) == ' ') {
         indentEnd++;
      }

      String indent = line.substring(0, indentEnd);
      String body = line.substring(indentEnd);
      if (!body.startsWith("//")) {
         body = body.replaceAll(",\\s*", ", ");
      }
      body = body.replaceAll("([\\w\\)\\]])\\s*\\{", "$1 {");
      body = body.replaceAll("\\}\\s*else\\s*\\{", "} else {");
      body = body.replaceAll("\\}\\s*catch\\s*\\(", "} catch (");
      body = body.replaceAll("\\}\\s*finally\\s*\\{", "} finally {");
      if (body.startsWith("//")) {
         String commentBody = body.length() <= 2 ? "" : body.substring(2);
         body = commentBody.isEmpty() ? "//" : "// " + trimLeadingWhitespace(commentBody);
      }
      return indent + body.trim();
   }

   private static String trimLeadingWhitespace(String value) {
      int start = 0;
      while (start < value.length() && Character.isWhitespace(value.charAt(start))) {
         start++;
      }
      return value.substring(start);
   }

   private void highlight(Editable editable) {
      if (highlighting) {
         return;
      }
      highlighting = true;
      ForegroundColorSpan[] spans = editable.getSpans(0, editable.length(), ForegroundColorSpan.class);
      for (ForegroundColorSpan span : spans) {
         editable.removeSpan(span);
      }

      for (EditorTheme.SyntaxRule rule : theme.syntaxRules) {
         apply(editable, rule);
      }
      highlighting = false;
   }

   private void apply(Editable editable, EditorTheme.SyntaxRule rule) {
      Matcher matcher = rule.pattern.matcher(editable);
      while (matcher.find()) {
         editable.setSpan(new ForegroundColorSpan(rule.color), matcher.start(), matcher.end(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      }
   }

   private int dp(int value) {
      return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
   }

   private void startFlingIfNeeded() {
      if (velocityTracker == null || !touchMoved) {
         return;
      }
      velocityTracker.computeCurrentVelocity(1000, maximumFlingVelocity);
      int velocityX = Math.round(-velocityTracker.getXVelocity());
      int velocityY = Math.round(-velocityTracker.getYVelocity());
      if (Math.abs(velocityX) < minimumFlingVelocity && Math.abs(velocityY) < minimumFlingVelocity) {
         return;
      }

      flingScroller.fling(
         getScrollX(),
         getScrollY(),
         velocityX,
         velocityY,
         0,
         maxScrollX(),
         0,
         maxScrollY()
      );
      postInvalidateOnAnimation();
   }

   private int maxScrollX() {
      Layout layout = getLayout();
      if (layout == null) {
         return 0;
      }

      float maxLineWidth = 0f;
      for (int i = 0; i < layout.getLineCount(); i++) {
         maxLineWidth = Math.max(maxLineWidth, layout.getLineWidth(i));
      }
      int effectiveRightPadding = Math.min(getTotalPaddingRight(), dp(12));
      int contentWidth = Math.round(maxLineWidth) + getTotalPaddingLeft() + effectiveRightPadding;
      return Math.max(0, contentWidth - getWidth());
   }

   private int maxScrollY() {
      Layout layout = getLayout();
      if (layout == null) {
         return 0;
      }
      int lastLine = Math.max(0, layout.getLineCount() - 1);
      int effectiveBottomPadding = Math.min(getTotalPaddingBottom(), getLineHeight() + dp(4));
      int contentHeight = layout.getLineBottom(lastLine) + getTotalPaddingTop() + effectiveBottomPadding;
      return Math.max(0, contentHeight - getHeight());
   }

   private void ensureVelocityTracker() {
      if (velocityTracker == null) {
         velocityTracker = VelocityTracker.obtain();
      }
   }

   private void recycleVelocityTracker() {
      if (velocityTracker != null) {
         velocityTracker.recycle();
         velocityTracker = null;
      }
   }

   private void showKeyboard() {
      InputMethodManager inputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
      if (inputMethodManager != null) {
         post(() -> inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT));
      }
   }

   private void hideKeyboard() {
      InputMethodManager inputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
      if (inputMethodManager != null) {
         inputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);
      }
   }

   private int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   private static final class EditState {
      final String text;
      final int selection;

      EditState(String text, int selection) {
         this.text = text;
         this.selection = selection;
      }
   }

   private static final class Formatter {
      private static final int WRAP_COLUMN = 100;

      private final ArrayDeque<Token> tokens;
      private final StringBuilder out = new StringBuilder();
      private final String unit;
      private int indentLevel;
      private int parenDepth;
      private int bracketDepth;
      private boolean startOfLine = true;
      private boolean pendingSpace;
      private boolean previousClosedTopLevelBlock;
      private Token previousToken;

      Formatter(String code, int tabSize) {
         tokens = tokenize(code.replace("\r\n", "\n").replace('\r', '\n'));
         unit = repeatSpace(tabSize);
      }

      String format() {
         while (!tokens.isEmpty()) {
            Token token = tokens.removeFirst();
            switch (token.type) {
               case WORD:
               case NUMBER:
                  writeWordLike(token.text);
                  break;
               case STRING:
               case CHAR:
                  writeLiteral(token.text);
                  break;
               case LINE_COMMENT:
                  writeLineComment(token.text);
                  break;
               case BLOCK_COMMENT:
                  writeBlockComment(token.text);
                  break;
               case SYMBOL:
                  writeSymbol(token.text);
                  break;
               case BLANK_LINE:
                  writeBlankLine(token.text);
                  break;
               default:
                  break;
            }
         }
         trimTrailingSpaces(out);
         trimTrailingNewlines(out);
         return out.toString();
      }

      private void writeWordLike(String text) {
         if (previousClosedTopLevelBlock && looksLikeTopLevelDeclaration()) {
            newline(2);
         }
         ensureLineStarted();
         if (needsSpaceBeforeWord(text)) {
            writeSpace();
         }
         out.append(text);
         startOfLine = false;
         pendingSpace = false;
         previousClosedTopLevelBlock = false;
         previousToken = new Token(TokenType.WORD, text);
      }

      private void writeLiteral(String text) {
         ensureLineStarted();
         if (needsSpaceBeforeWord(text)) {
            writeSpace();
         }
         out.append(text);
         startOfLine = false;
         pendingSpace = false;
         previousClosedTopLevelBlock = false;
         previousToken = new Token(TokenType.STRING, text);
      }

      private void writeLineComment(String text) {
         ensureLineStarted();
         out.append("//");
         String body = text.length() <= 2 ? "" : trimLeadingWhitespace(text.substring(2));
         if (!body.isEmpty()) {
            out.append(' ').append(body);
         }
         startOfLine = false;
         pendingSpace = false;
         previousClosedTopLevelBlock = false;
         previousToken = new Token(TokenType.LINE_COMMENT, "//");
         newline(1);
      }

      private void writeBlockComment(String text) {
         ensureLineStarted();
         if (!startOfLine) {
            writeSpace();
         }
         String normalized = normalizeBlockComment(text);
         String indent = currentIndent();
         normalized = normalized.replace("\n", "\n" + indent);
         out.append(normalized);
         startOfLine = false;
         pendingSpace = false;
         previousClosedTopLevelBlock = false;
         previousToken = new Token(TokenType.BLOCK_COMMENT, "/*");
         if (normalized.contains("\n")) {
            newline(1);
         }
      }

      private void writeBlankLine(String text) {
         trimTrailingSpaces(out);
         int newlineCount = 0;
         for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
               newlineCount++;
            }
         }
         trimTrailingNewlines(out);
         appendNewline(out, Math.max(2, newlineCount));
         startOfLine = true;
         pendingSpace = false;
         previousClosedTopLevelBlock = false;
         previousToken = null;
      }

      private void writeSymbol(String symbol) {
         if ("}".equals(symbol)) {
            writeClosingBrace();
            return;
         }
         if ("{".equals(symbol)) {
            writeOpeningBrace();
            return;
         }
         if (";".equals(symbol)) {
            writeSemicolon();
            return;
         }
         if (",".equals(symbol)) {
            writeComma();
            return;
         }
         if ("(".equals(symbol)) {
            ensureLineStarted();
            trimTrailingSpaces(out);
            out.append('(');
            parenDepth++;
            startOfLine = false;
            pendingSpace = false;
            previousClosedTopLevelBlock = false;
            previousToken = new Token(TokenType.SYMBOL, "(");
            return;
         }
         if (")".equals(symbol)) {
            trimTrailingSpaces(out);
            out.append(')');
            parenDepth = Math.max(0, parenDepth - 1);
            startOfLine = false;
            pendingSpace = false;
            previousClosedTopLevelBlock = false;
            previousToken = new Token(TokenType.SYMBOL, ")");
            return;
         }
         if ("[".equals(symbol)) {
            ensureLineStarted();
            trimTrailingSpaces(out);
            out.append('[');
            bracketDepth++;
            startOfLine = false;
            pendingSpace = false;
            previousClosedTopLevelBlock = false;
            previousToken = new Token(TokenType.SYMBOL, "[");
            return;
         }
         if ("]".equals(symbol)) {
            trimTrailingSpaces(out);
            out.append(']');
            bracketDepth = Math.max(0, bracketDepth - 1);
            startOfLine = false;
            pendingSpace = false;
            previousClosedTopLevelBlock = false;
            previousToken = new Token(TokenType.SYMBOL, "]");
            return;
         }
         if (".".equals(symbol)) {
            trimTrailingSpaces(out);
            out.append('.');
            startOfLine = false;
            pendingSpace = false;
            previousClosedTopLevelBlock = false;
            previousToken = new Token(TokenType.SYMBOL, ".");
            return;
         }
         if (":".equals(symbol)) {
            trimTrailingSpaces(out);
            out.append(": ");
            startOfLine = false;
            pendingSpace = false;
            previousClosedTopLevelBlock = false;
            previousToken = new Token(TokenType.SYMBOL, ":");
            return;
         }
         writeOperator(symbol);
      }

      private void writeOpeningBrace() {
         ensureLineStarted();
         trimTrailingSpaces(out);
         if (out.length() > 0 && out.charAt(out.length() - 1) != ' ' && out.charAt(out.length() - 1) != '\n') {
            out.append(' ');
         }
         out.append('{');
         indentLevel++;
         previousToken = new Token(TokenType.SYMBOL, "{");
         previousClosedTopLevelBlock = false;
         newline(1);
      }

      private void writeClosingBrace() {
         indentLevel = Math.max(0, indentLevel - 1);
         if (!startOfLine) {
            newline(1);
         } else {
            trimTrailingSpaces(out);
         }
         ensureLineStarted();
         out.append('}');
         startOfLine = false;
         pendingSpace = false;
         previousToken = new Token(TokenType.SYMBOL, "}");

         String nextWord = peekNextWord();
         if ("else".equals(nextWord) || "catch".equals(nextWord) || "finally".equals(nextWord)
            || ("while".equals(nextWord) && isDoWhileContinuation())) {
            out.append(' ');
            startOfLine = false;
            pendingSpace = false;
         } else if (peekNextTokenType() == TokenType.LINE_COMMENT) {
            out.append(' ');
            startOfLine = false;
            pendingSpace = false;
         } else {
            previousClosedTopLevelBlock = indentLevel == 0;
            newline(1);
         }
      }

      private void writeSemicolon() {
         trimTrailingSpaces(out);
         out.append(';');
         startOfLine = false;
         pendingSpace = false;
         previousClosedTopLevelBlock = false;
         previousToken = new Token(TokenType.SYMBOL, ";");
         if (peekNextTokenType() == TokenType.LINE_COMMENT) {
            out.append(' ');
         } else if (parenDepth == 0) {
            newline(1);
         } else {
            out.append(' ');
         }
      }

      private void writeComma() {
         trimTrailingSpaces(out);
         out.append(',');
         if (shouldWrapAfterComma()) {
            newline(1);
         } else {
            out.append(' ');
            startOfLine = false;
         }
         pendingSpace = false;
         previousClosedTopLevelBlock = false;
         previousToken = new Token(TokenType.SYMBOL, ",");
      }

      private void writeOperator(String symbol) {
         ensureLineStarted();
         boolean unary = isUnaryOperator(symbol);
         boolean postfix = unary && isPostfixUnary(symbol);
         boolean ternaryPunctuation = "?".equals(symbol);

         if (!unary || postfix || ternaryPunctuation) {
            trimTrailingSpaces(out);
            if (!startOfLine && out.length() > 0 && out.charAt(out.length() - 1) != ' ' && out.charAt(out.length() - 1) != '(') {
               out.append(' ');
            }
         }

         out.append(symbol);

         if (!postfix && (!unary || ternaryPunctuation)) {
            out.append(' ');
         }

         startOfLine = false;
         pendingSpace = false;
         previousClosedTopLevelBlock = false;
         previousToken = new Token(TokenType.SYMBOL, symbol);
      }

      private boolean shouldWrapAfterComma() {
         if (parenDepth == 0 && bracketDepth == 0) {
            return false;
         }
         return currentLineLength() >= WRAP_COLUMN;
      }

      private TokenType peekNextTokenType() {
         return tokens.isEmpty() ? null : tokens.peekFirst().type;
      }

      private boolean needsSpaceBeforeWord(String text) {
         if (startOfLine) {
            return false;
         }
         if (pendingSpace) {
            return true;
         }
         if (previousToken == null) {
            return false;
         }
         if (previousToken.type == TokenType.WORD || previousToken.type == TokenType.NUMBER
            || previousToken.type == TokenType.STRING || previousToken.type == TokenType.CHAR) {
            return true;
         }
         return ")".equals(previousToken.text) || "]".equals(previousToken.text);
      }

      private boolean looksLikeTopLevelDeclaration() {
         Token next = peekNextSignificant();
         return next != null && next.type == TokenType.WORD;
      }

      private String peekNextWord() {
         Token next = peekNextSignificant();
         if (next != null && next.type == TokenType.WORD) {
            return next.text;
         }
         return "";
      }

      private boolean isDoWhileContinuation() {
         return previousToken != null && "}".equals(previousToken.text);
      }

      private Token peekNextSignificant() {
         for (Token token : tokens) {
            if (token.type != TokenType.WHITESPACE) {
               return token;
            }
         }
         return null;
      }

      private void ensureLineStarted() {
         if (!startOfLine) {
            if (pendingSpace) {
               writeSpace();
            }
            return;
         }
         appendIndent(out, indentLevel, unit);
         startOfLine = false;
         pendingSpace = false;
      }

      private void writeSpace() {
         trimTrailingSpaces(out);
         if (out.length() > 0 && out.charAt(out.length() - 1) != ' ' && out.charAt(out.length() - 1) != '\n') {
            out.append(' ');
         }
         pendingSpace = false;
      }

      private void newline(int count) {
         startOfLine = true;
         pendingSpace = false;
         appendNewline(out, count);
      }

      private int currentLineLength() {
         int length = 0;
         for (int i = out.length() - 1; i >= 0 && out.charAt(i) != '\n'; i--) {
            length++;
         }
         return length;
      }

      private String currentIndent() {
         return repeat(unit, Math.max(0, indentLevel));
      }

      private static boolean isUnaryOperator(String symbol) {
         return "!".equals(symbol) || "~".equals(symbol) || "++".equals(symbol) || "--".equals(symbol);
      }

      private boolean isPostfixUnary(String symbol) {
         if (!"++".equals(symbol) && !"--".equals(symbol)) {
            return false;
         }
         return previousToken != null
            && (previousToken.type == TokenType.WORD
            || previousToken.type == TokenType.NUMBER
            || ")".equals(previousToken.text)
            || "]".equals(previousToken.text));
      }

      private static String normalizeBlockComment(String text) {
         String normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim();
         String[] lines = normalized.split("\n", -1);
         StringBuilder builder = new StringBuilder();
         for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("*") && line.length() > 1 && line.charAt(1) != ' ') {
               line = "* " + line.substring(1).trim();
            }
            if (i > 0) {
               builder.append('\n');
            }
            builder.append(line);
         }
         return builder.toString();
      }

      private static ArrayDeque<Token> tokenize(String source) {
         ArrayDeque<Token> result = new ArrayDeque<>();
         int i = 0;
         while (i < source.length()) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

            if (Character.isWhitespace(c)) {
               int start = i;
               int newlineCount = 0;
               while (i < source.length() && Character.isWhitespace(source.charAt(i))) {
                  if (source.charAt(i) == '\n') {
                     newlineCount++;
                  }
                  i++;
               }
               if (newlineCount >= 2) {
                  result.add(new Token(TokenType.BLANK_LINE, source.substring(start, i)));
               }
               continue;
            }

            if (c == '/' && next == '/') {
               int start = i;
               i += 2;
               while (i < source.length() && source.charAt(i) != '\n') {
                  i++;
               }
               result.add(new Token(TokenType.LINE_COMMENT, source.substring(start, i)));
               continue;
            }

            if (c == '/' && next == '*') {
               int start = i;
               i += 2;
               while (i + 1 < source.length() && !(source.charAt(i) == '*' && source.charAt(i + 1) == '/')) {
                  i++;
               }
               i = Math.min(source.length(), i + 2);
               result.add(new Token(TokenType.BLOCK_COMMENT, source.substring(start, i)));
               continue;
            }

            if (c == '"' || c == '\'') {
               char quote = c;
               int start = i++;
               boolean escaping = false;
               while (i < source.length()) {
                  char current = source.charAt(i++);
                  if (escaping) {
                     escaping = false;
                  } else if (current == '\\') {
                     escaping = true;
                  } else if (current == quote) {
                     break;
                  }
               }
               result.add(new Token(quote == '"' ? TokenType.STRING : TokenType.CHAR, source.substring(start, i)));
               continue;
            }

            if (Character.isLetter(c) || c == '_' || c == '$') {
               int start = i++;
               while (i < source.length()) {
                  char current = source.charAt(i);
                  if (Character.isLetterOrDigit(current) || current == '_' || current == '$') {
                     i++;
                  } else {
                     break;
                  }
               }
               result.add(new Token(TokenType.WORD, source.substring(start, i)));
               continue;
            }

            if (Character.isDigit(c)) {
               int start = i++;
               while (i < source.length()) {
                  char current = source.charAt(i);
                  if (Character.isLetterOrDigit(current) || current == '.' || current == '_') {
                     i++;
                  } else {
                     break;
                  }
               }
               result.add(new Token(TokenType.NUMBER, source.substring(start, i)));
               continue;
            }

            String operator = readOperator(source, i);
            result.add(new Token(TokenType.SYMBOL, operator));
            i += operator.length();
         }
         return result;
      }

      private static String readOperator(String source, int index) {
         String[] operators = {
            ">>>=", "<<=", ">>=", "==", "!=", "<=", ">=", "&&", "||", "++", "--",
            "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "->", "::", "<<", ">>",
            ">>>"
         };
         for (String operator : operators) {
            if (source.startsWith(operator, index)) {
               return operator;
            }
         }
         return String.valueOf(source.charAt(index));
      }
   }

   private enum TokenType {
      WORD,
      NUMBER,
      STRING,
      CHAR,
      LINE_COMMENT,
      BLOCK_COMMENT,
      BLANK_LINE,
      SYMBOL,
      WHITESPACE
   }

   private static final class Token {
      final TokenType type;
      final String text;

      Token(TokenType type, String text) {
         this.type = type;
         this.text = text;
      }
   }
}
