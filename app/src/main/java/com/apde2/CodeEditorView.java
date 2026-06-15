package com.apde2;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.SparseArray;
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

final class CodeEditorView extends EditText implements ThemeAware {
   private static final long HISTORY_SNAPSHOT_DELAY_MS = 500L;
   private static final long HIGHLIGHT_DEBOUNCE_MS = 40L;
   private static final int FULL_HIGHLIGHT_THRESHOLD = 4096;
   private static final int SELECTION_REVEAL_SCROLL_DURATION_MS = 180;

   private EditorTheme theme;
   private final OverScroller flingScroller;
   private final Deque<EditState> undoStack = new ArrayDeque<>();
   private final Deque<EditState> redoStack = new ArrayDeque<>();
   private final Handler mainHandler = new Handler(Looper.getMainLooper());
   private final Runnable commitHistoryRunnable = this::commitPendingHistory;
   private final IncrementalHighlighter highlighter;
   private final int maximumFlingVelocity;
   private final int minimumFlingVelocity;
   private final int touchSlop;

   private boolean editingPair;
   private boolean editingHistory;
   private EditState pendingState;
   private VelocityTracker velocityTracker;
   private int skipPairEditStart = -1;
   private float touchStartX;
   private float touchStartY;
   private int touchStartScrollX;
   private int touchStartScrollY;
   private boolean touchStartedFocused;
   private boolean touchMoved;
   private boolean touchGestureActive;
   private boolean scrollingGesture;
   private boolean selectionMode;
   private boolean selectionScrollLocked;
   private int selectionLockScrollX;
   private int selectionLockScrollY;
   private int tabSize = 3;
   private boolean autoClosePairs = true;

   private int cachedMaxScrollX = -1;
   private int cachedMaxScrollY = -1;
   private int bottomOverlayInset = 0;

   private boolean externalResize = false;
   private boolean externalResizeTracksOverlayBounds = false;
   private int externalResizeScrollX;
   private int externalResizeScrollY;
   private int externalResizeGeneration;

   CodeEditorView(Context context, EditorTheme theme) {
      super(context);
      this.theme = theme;
      flingScroller = new OverScroller(context);
      ViewConfiguration vc = ViewConfiguration.get(context);
      maximumFlingVelocity = vc.getScaledMaximumFlingVelocity();
      minimumFlingVelocity = vc.getScaledMinimumFlingVelocity();
      touchSlop = vc.getScaledTouchSlop();
      highlighter = new IncrementalHighlighter(theme);

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
      setScrollContainer(false);
      setOnFocusChangeListener((view, hasFocus) -> {
         setCursorVisible(hasFocus);
         if (!hasFocus) {
            resetSelectionGestureState();
         }
      });

      addTextChangedListener(new TextWatcher() {
         @Override
         public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            if (!editingHistory && pendingState == null) {
               pendingState = new EditState(s.toString(), Math.max(0, getSelectionStart()));
            }
         }

         @Override
         public void onTextChanged(CharSequence s, int start, int before, int count) {
            highlighter.markDirty(start, start + count);
         }

         @Override
         public void afterTextChanged(Editable editable) {
            scheduleHistorySnapshot();
            invalidateScrollBounds();
            highlighter.scheduleHighlight(editable);
         }
      });
   }

   @Override
   public void applyTheme(EditorTheme theme) {
      this.theme = theme;
      highlighter.applyTheme(theme);
      setTextColor(theme.text);
      setBackgroundColor(theme.background);
      setHighlightColor(theme.selection);
      highlighter.applyFullHighlight(getText());
      invalidate();
   }

   @Override
   public boolean onTouchEvent(MotionEvent event) {
      int action = event.getActionMasked();
      if (action == MotionEvent.ACTION_DOWN) {
         flingScroller.abortAnimation();
         ensureVelocityTracker();
         velocityTracker.clear();
         touchStartX = event.getX();
         touchStartY = event.getY();
         touchStartScrollX = getScrollX();
         touchStartScrollY = getScrollY();
         touchStartedFocused = hasFocus();
         touchMoved = false;
         touchGestureActive = true;
         scrollingGesture = false;
         selectionMode = hasSelection();
         if (!selectionMode) {
            selectionScrollLocked = false;
         }
      } else if (action == MotionEvent.ACTION_MOVE) {
         boolean selectionCandidate = isSelectionCandidate(event);
         if (hasSelection()) {
            selectionMode = true;
         }
         if (!touchMoved) {
            float dxFromStart = Math.abs(event.getX() - touchStartX);
            float dyFromStart = Math.abs(event.getY() - touchStartY);
            if (dxFromStart >= touchSlop || dyFromStart >= touchSlop) {
               touchMoved = true;
               if (!selectionMode && !selectionCandidate) {
                  scrollingGesture = !hasSelection();
                  setCursorVisible(false);
                  cancelNativeTouch(event);
                  if (getParent() != null) {
                     getParent().requestDisallowInterceptTouchEvent(true);
                  }
               }
            }
         }
      }

      if (velocityTracker != null) {
         velocityTracker.addMovement(event);
      }

      if (scrollingGesture) {
         if (action == MotionEvent.ACTION_MOVE) {
            int targetX = touchStartScrollX + Math.round(touchStartX - event.getX());
            int targetY = touchStartScrollY + Math.round(touchStartY - event.getY());
            scrollTo(targetX, targetY);
            return true;
         }
         if (action == MotionEvent.ACTION_UP) {
            startFlingIfNeeded();
            if (!touchStartedFocused) {
               clearFocus();
            }
            setCursorVisible(false);
            recycleVelocityTracker();
            if (getParent() != null) {
               getParent().requestDisallowInterceptTouchEvent(false);
            }
            touchGestureActive = false;
            scrollingGesture = false;
            return true;
         }
         if (action == MotionEvent.ACTION_CANCEL) {
            flingScroller.abortAnimation();
            recycleVelocityTracker();
            if (getParent() != null) {
               getParent().requestDisallowInterceptTouchEvent(false);
            }
            touchGestureActive = false;
            scrollingGesture = false;
            return true;
         }
      }

      boolean handled = super.onTouchEvent(event);
      if (!isNativeSelectionActive()) {
         clampCurrentScroll();
      }
      if (hasSelection()) {
         selectionMode = true;
         setCursorVisible(true);
      }

      if (action == MotionEvent.ACTION_UP) {
         if (!touchMoved) {
            requestFocus();
            setCursorVisible(true);
            restoreTouchScrollAfterPointerPlacement();
            if (!hasSelection()) {
               showKeyboard();
            }
         } else if (!selectionMode && !hasSelection()) {
            int scrollX = getScrollX();
            int scrollY = getScrollY();
            scrollTo(scrollX, scrollY);
            post(() -> scrollTo(scrollX, scrollY));
            if (!touchStartedFocused) {
               clearFocus();
            }
            setCursorVisible(false);
         } else {
            setCursorVisible(true);
         }
         touchGestureActive = false;
         recycleVelocityTracker();
      } else if (action == MotionEvent.ACTION_CANCEL) {
         touchGestureActive = false;
         flingScroller.abortAnimation();
         recycleVelocityTracker();
      }

      return handled;
   }

   @Override
   protected void onSelectionChanged(int selStart, int selEnd) {
      super.onSelectionChanged(selStart, selEnd);
      if (flingScroller == null) {
         return;
      }
      if (selStart != selEnd) {
         selectionMode = true;
         scrollingGesture = false;
         flingScroller.abortAnimation();
         lockTouchSelectionScroll();
         if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
         }
      } else if (!scrollingGesture) {
         selectionMode = false;
         selectionScrollLocked = false;
      }
   }

   private boolean isSelectionCandidate(MotionEvent event) {
      return hasSelection() || event.getEventTime() - event.getDownTime() >= ViewConfiguration.getLongPressTimeout();
   }

   private boolean isNativeSelectionActive() {
      return selectionMode || hasSelection();
   }

   private void resetSelectionGestureState() {
      selectionMode = false;
      selectionScrollLocked = false;
      touchGestureActive = false;
      scrollingGesture = false;
      if (flingScroller != null) {
         flingScroller.abortAnimation();
      }
      recycleVelocityTracker();
      if (getParent() != null) {
         getParent().requestDisallowInterceptTouchEvent(false);
      }
   }

   private void cancelNativeTouch(MotionEvent event) {
      MotionEvent cancel = MotionEvent.obtain(event);
      cancel.setAction(MotionEvent.ACTION_CANCEL);
      super.onTouchEvent(cancel);
      cancel.recycle();
      clampCurrentScroll();
   }

   @Override
   protected boolean overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY, int scrollRangeX, int scrollRangeY, int maxOverScrollX, int maxOverScrollY, boolean isTouchEvent) {
      if (isNativeSelectionActive()) {
         return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent);
      }
      return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, 0, 0, isTouchEvent);
   }

   @Override
   public void computeScroll() {
      super.computeScroll();
      if (!isNativeSelectionActive() && clampCurrentScroll()) {
         flingScroller.abortAnimation();
      }
      if (flingScroller.computeScrollOffset()) {
         int targetX = flingScroller.getCurrX();
         int targetY = flingScroller.getCurrY();
         scrollTo(targetX, targetY);
         if (getScrollX() != targetX || getScrollY() != targetY) {
            flingScroller.abortAnimation();
         } else {
            postInvalidateOnAnimation();
         }
      }
   }

   @Override
   protected void onScrollChanged(int left, int top, int oldLeft, int oldTop) {
      super.onScrollChanged(left, top, oldLeft, oldTop);
      if (!externalResize && !isNativeSelectionActive()) {
         clampCurrentScroll();
      }
   }

   @Override
   public void scrollTo(int x, int y) {
      if (externalResize) {
         restoreExternalResizeScroll();
         return;
      }
      if (isNativeSelectionActive()) {
         super.scrollTo(x, y);
         return;
      }
      int maxX = maxScrollX();
      int maxY = maxScrollY();
      int clampedX = x < 0 ? 0 : (x > maxX ? maxX : x);
      int clampedY = y < 0 ? 0 : (y > maxY ? maxY : y);
      super.scrollTo(clampedX, clampedY);
   }

   @Override
   public boolean bringPointIntoView(int offset) {
      return bringOffsetAboveBottomOverlay(offset, 0);
   }

   void revealSelectionAboveBottomOverlay() {
      if (bottomOverlayInset <= 0 || getSelectionEnd() < 0) {
         return;
      }
      int revealMargin = Math.max(getLineHeight() * 2, dp(24));
      bringOffsetAboveBottomOverlay(getSelectionEnd(), revealMargin, true);
   }

   void revealSelectionAfterOverlaySettles() {
      postOnAnimation(() -> postOnAnimation(this::revealSelectionAboveBottomOverlay));
   }

   private boolean bringOffsetAboveBottomOverlay(int offset, int bottomMargin) {
      return bringOffsetAboveBottomOverlay(offset, bottomMargin, false);
   }

   private boolean bringOffsetAboveBottomOverlay(int offset, int bottomMargin, boolean smooth) {
      if (bottomOverlayInset <= 0) {
         return super.bringPointIntoView(offset);
      }
      android.text.Layout layout = getLayout();
      Editable text = getText();
      if (layout == null || text == null) {
         return super.bringPointIntoView(offset);
      }

      int safeOffset = Math.max(0, Math.min(offset, text.length()));
      int line = layout.getLineForOffset(safeOffset);
      int visibleBottomOffset = Math.max(1, getHeight() - getCompoundPaddingBottom() - bottomOverlayInset - Math.max(0, bottomMargin));
      int currentX = getScrollX();
      int currentY = getScrollY();
      int targetX = pointScrollX(layout.getPrimaryHorizontal(safeOffset), currentX);
      int targetY = currentY;
      int lineTop = layout.getLineTop(line) + getCompoundPaddingTop();
      int lineBottom = layout.getLineBottom(line) + getCompoundPaddingTop();
      if (lineTop < currentY) {
         targetY = lineTop;
      } else if (lineBottom > currentY + visibleBottomOffset) {
         targetY = lineBottom - visibleBottomOffset;
      }

      return smooth ? smoothScrollToEditorBounds(targetX, targetY) : scrollToEditorBounds(targetX, targetY);
   }

   @Override
   protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
      super.onLayout(changed, left, top, right, bottom);
      if (changed) {
         int maxY = maxScrollY();
         if (externalResize) {
            restoreExternalResizeScroll(maxY);
         } else {
            int maxX = maxScrollX();
            int currentX = getScrollX();
            int currentY = getScrollY();
            if (currentX > maxX || currentY > maxY) {
               scrollTo(Math.min(currentX, maxX), Math.min(currentY, maxY));
            }
         }
      }
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
   protected void onSizeChanged(int w, int h, int oldw, int oldh) {
      super.onSizeChanged(w, h, oldw, oldh);
      if (w != oldw) {
         invalidateScrollXBounds();
      }
      if (h != oldh) {
         invalidateScrollYBounds();
      }
   }

   @Override
   public void setPadding(int left, int top, int right, int bottom) {
      super.setPadding(left, top, right, bottom);
      invalidateScrollBounds();
   }

   @Override
   public void setPaddingRelative(int start, int top, int end, int bottom) {
      super.setPaddingRelative(start, top, end, bottom);
      invalidateScrollBounds();
   }

   void setCode(String code) {
      mainHandler.removeCallbacks(commitHistoryRunnable);
      editingHistory = true;
      setText(code);
      setSelection(getText().length());
      undoStack.clear();
      redoStack.clear();
      pendingState = null;
      editingHistory = false;
      highlighter.applyFullHighlight(getText());
      invalidateScrollBounds();
   }

   String code() {
      return getText().toString();
   }

   void setEditorFontSizeSp(int sizeSp) {
      setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
      invalidateScrollBounds();
   }

   void setTabSize(int tabSize) {
      this.tabSize = Math.max(2, Math.min(8, tabSize));
   }

   void setAutoClosePairs(boolean enabled) {
      autoClosePairs = enabled;
   }

   void setBottomOverlayInset(int inset) {
      int safeInset = Math.max(0, inset);
      if (bottomOverlayInset == safeInset) {
         return;
      }
      bottomOverlayInset = safeInset;
      invalidateScrollYBounds();
      if (externalResize) {
         if (externalResizeTracksOverlayBounds) {
            restoreExternalResizeScroll(maxScrollX(), maxScrollY());
         }
      } else if (!isNativeSelectionActive()) {
         clampCurrentScroll();
      }
      awakenScrollBars();
   }

   void setExternalResize(boolean externalResize) {
      if (externalResize) {
         externalResizeGeneration++;
         if (!this.externalResize) {
            externalResizeScrollX = getScrollX();
            externalResizeScrollY = getScrollY();
            flingScroller.abortAnimation();
         }
         this.externalResize = true;
         return;
      }

      if (!this.externalResize) {
         return;
      }
      int generation = ++externalResizeGeneration;
      postOnAnimation(() -> postOnAnimation(() -> {
         if (externalResizeGeneration == generation) {
            restoreExternalResizeScroll(maxScrollX(), maxScrollY());
            this.externalResize = false;
            externalResizeTracksOverlayBounds = false;
         }
      }));
   }

   void setExternalResizeTracksOverlayBounds(boolean tracksOverlayBounds) {
      externalResizeTracksOverlayBounds = tracksOverlayBounds;
      if (externalResize && tracksOverlayBounds) {
         restoreExternalResizeScroll(maxScrollX(), maxScrollY());
      }
   }

   void formatCodeInPlace() {
      String formatted = formatCode(code(), tabSize);
      if (!formatted.equals(code())) {
         applyFormattedCode(formatted);
      }
   }

   boolean applyFormattedCode(String formatted) {
      return applyEditedCode(formatted, Math.max(0, getSelectionStart()));
   }

   boolean applyEditedCode(String edited, int selection) {
      if (edited == null) {
         return false;
      }
      String current = code();
      if (edited.equals(current)) {
         return false;
      }
      commitPendingHistory();
      undoStack.push(new EditState(current, Math.max(0, getSelectionStart())));
      redoStack.clear();
      mainHandler.removeCallbacks(commitHistoryRunnable);
      editingHistory = true;
      Editable editable = getText();
      editable.replace(0, editable.length(), edited);
      setSelection(Math.max(0, Math.min(selection, editable.length())));
      pendingState = null;
      editingHistory = false;
      highlighter.applyFullHighlight(editable);
      invalidateScrollBounds();
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

   private void invalidateScrollBounds() {
      invalidateScrollXBounds();
      invalidateScrollYBounds();
   }

   private void invalidateScrollXBounds() {
      cachedMaxScrollX = -1;
   }

   private void invalidateScrollYBounds() {
      cachedMaxScrollY = -1;
   }

   private boolean clampCurrentScroll() {
      return scrollToEditorBounds(getScrollX(), getScrollY());
   }

   private int pointScrollX(float pointX, int currentX) {
      int visibleWidth = Math.max(1, getWidth() - getTotalPaddingLeft() - getTotalPaddingRight());
      if (pointX < currentX) {
         return (int) Math.floor(pointX);
      }
      if (pointX > currentX + visibleWidth) {
         return (int) Math.ceil(pointX) - visibleWidth;
      }
      return currentX;
   }

   private boolean scrollToEditorBounds(int x, int y) {
      int currentX = getScrollX();
      int currentY = getScrollY();
      int maxX = maxScrollX();
      int maxY = maxScrollY();
      int clampedX = x < 0 ? 0 : (x > maxX ? maxX : x);
      int clampedY = y < 0 ? 0 : (y > maxY ? maxY : y);
      if (currentX != clampedX || currentY != clampedY) {
         super.scrollTo(clampedX, clampedY);
         return true;
      }
      return false;
   }

   private boolean smoothScrollToEditorBounds(int x, int y) {
      int currentX = getScrollX();
      int currentY = getScrollY();
      int maxX = maxScrollX();
      int maxY = maxScrollY();
      int clampedX = x < 0 ? 0 : (x > maxX ? maxX : x);
      int clampedY = y < 0 ? 0 : (y > maxY ? maxY : y);
      if (currentX == clampedX && currentY == clampedY) {
         return false;
      }
      flingScroller.abortAnimation();
      flingScroller.startScroll(
         currentX,
         currentY,
         clampedX - currentX,
         clampedY - currentY,
         SELECTION_REVEAL_SCROLL_DURATION_MS
      );
      postInvalidateOnAnimation();
      return true;
   }

   private void restoreTouchScrollAfterPointerPlacement() {
      if (bottomOverlayInset <= 0) {
         return;
      }
      scrollToEditorBounds(touchStartScrollX, touchStartScrollY);
      postOnAnimation(() -> scrollToEditorBounds(touchStartScrollX, touchStartScrollY));
   }

   private void lockTouchSelectionScroll() {
      if (bottomOverlayInset <= 0 || !touchGestureActive) {
         return;
      }
      if (!selectionScrollLocked) {
         selectionLockScrollX = touchStartScrollX;
         selectionLockScrollY = touchStartScrollY;
         selectionScrollLocked = true;
      }
      postOnAnimation(() -> scrollToEditorBounds(selectionLockScrollX, selectionLockScrollY));
   }

   private void restoreExternalResizeScroll(int maxX, int maxY) {
      super.scrollTo(
         Math.max(0, Math.min(externalResizeScrollX, maxX)),
         Math.max(0, Math.min(externalResizeScrollY, maxY))
      );
   }

   private void restoreExternalResizeScroll() {
      restoreExternalResizeScroll(maxScrollY());
   }

   private void restoreExternalResizeScroll(int maxY) {
      int maxX = cachedMaxScrollX >= 0 ? cachedMaxScrollX : Math.max(externalResizeScrollX, getScrollX());
      restoreExternalResizeScroll(maxX, maxY);
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
      mainHandler.removeCallbacks(commitHistoryRunnable);
      highlighter.cancel();
      flingScroller.abortAnimation();
      recycleVelocityTracker();
      super.onDetachedFromWindow();
   }

   private void scheduleHistorySnapshot() {
      if (editingHistory || pendingState == null) {
         return;
      }
      mainHandler.removeCallbacks(commitHistoryRunnable);
      mainHandler.postDelayed(commitHistoryRunnable, HISTORY_SNAPSHOT_DELAY_MS);
   }

   private void commitPendingHistory() {
      mainHandler.removeCallbacks(commitHistoryRunnable);
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
      highlighter.applyFullHighlight(getText());
      invalidateScrollBounds();
   }

   private String pairFor(char typed) {
      if (typed == '(') return ")";
      if (typed == '[') return "]";
      if (typed == '{') return "}";
      if (typed == '"') return "\"";
      if (typed == '\'') return "'";
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
      if (cachedMaxScrollX >= 0) {
         return cachedMaxScrollX;
      }
      float maxLineWidth = maxMonospaceLineWidth();
      int viewportWidth = getWidth() - getTotalPaddingLeft() - getTotalPaddingRight();
      cachedMaxScrollX = Math.max(0, (int) Math.ceil(maxLineWidth) - Math.max(0, viewportWidth));
      return cachedMaxScrollX;
   }

   private float maxMonospaceLineWidth() {
      CharSequence text = getText();
      if (text == null || text.length() == 0) {
         return 0f;
      }

      float columnWidth = getPaint().measureText("m");
      if (columnWidth <= 0f) {
         return 0f;
      }

      int currentColumns = 0;
      int maxColumns = 0;
      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c == '\n') {
            if (currentColumns > maxColumns) {
               maxColumns = currentColumns;
            }
            currentColumns = 0;
         } else if (c == '\t') {
            int spaces = tabSize - (currentColumns % tabSize);
            currentColumns += spaces <= 0 ? tabSize : spaces;
         } else {
            currentColumns++;
         }
      }
      if (currentColumns > maxColumns) {
         maxColumns = currentColumns;
      }
      return maxColumns * columnWidth;
   }

   private int maxScrollY() {
      if (cachedMaxScrollY >= 0) {
         return cachedMaxScrollY;
      }
      android.text.Layout layout = getLayout();
      if (layout == null) {
         return 0;
      }
      int lastLine = Math.max(0, layout.getLineCount() - 1);
      // Total padding contains EditText's expanded empty area; only content padding belongs in scroll bounds.
      int contentHeight = layout.getLineBottom(lastLine) + getCompoundPaddingTop() + getCompoundPaddingBottom() + bottomOverlayInset;
      cachedMaxScrollY = Math.max(0, contentHeight - getHeight());
      return cachedMaxScrollY;
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
      InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
      if (imm != null) {
         post(() -> imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT));
      }
   }

   /**
    * Incremental, debounced syntax highlighter.
    *
    * Re-highlighting whole lines around a dirty region avoids re-scanning the entire document
    * on every keystroke. Block comments can span lines, so documents that contain one of their
    * delimiters need a full pass after edits.
    */
   private final class IncrementalHighlighter {
      private EditorTheme theme;
      private final SparseArray<ArrayDeque<ForegroundColorSpan>> spanPool = new SparseArray<>();
      private final Runnable highlightRunnable = this::runScheduledHighlight;

      private int dirtyStart = -1;
      private int dirtyEnd = -1;
      private boolean fullPending;
      private boolean applying;
      private Editable scheduledTarget;

      IncrementalHighlighter(EditorTheme theme) {
         this.theme = theme;
      }

      void applyTheme(EditorTheme theme) {
         this.theme = theme;
      }

      void markDirty(int start, int end) {
         if (applying) return;
         if (start < 0) start = 0;
         if (end < start) end = start;
         if (dirtyStart < 0) {
            dirtyStart = start;
            dirtyEnd = end;
         } else {
            if (start < dirtyStart) dirtyStart = start;
            if (end > dirtyEnd) dirtyEnd = end;
         }
      }

      void scheduleHighlight(Editable editable) {
         if (applying) return;
         scheduledTarget = editable;
         if (dirtyStart < 0) {
            return;
         }
         int dirtyLen = dirtyEnd - dirtyStart;
         if (dirtyLen >= FULL_HIGHLIGHT_THRESHOLD || dirtyLen >= editable.length() / 2 || containsBlockCommentDelimiter(editable)) {
            fullPending = true;
         }
         mainHandler.removeCallbacks(highlightRunnable);
         mainHandler.postDelayed(highlightRunnable, HIGHLIGHT_DEBOUNCE_MS);
      }

      void applyFullHighlight(Editable editable) {
         mainHandler.removeCallbacks(highlightRunnable);
         dirtyStart = -1;
         dirtyEnd = -1;
         fullPending = false;
         scheduledTarget = null;
         applyRange(editable, 0, editable.length(), true);
      }

      void cancel() {
         mainHandler.removeCallbacks(highlightRunnable);
         dirtyStart = -1;
         dirtyEnd = -1;
         fullPending = false;
         scheduledTarget = null;
      }

      private void runScheduledHighlight() {
         Editable editable = scheduledTarget;
         scheduledTarget = null;
         if (editable == null) return;
         int len = editable.length();
         if (len == 0) {
            dirtyStart = -1;
            dirtyEnd = -1;
            fullPending = false;
            return;
         }
         if (fullPending) {
            applyRange(editable, 0, len, true);
            dirtyStart = -1;
            dirtyEnd = -1;
            fullPending = false;
            return;
         }
         if (dirtyStart < 0) return;

         int start = expandToLineStart(editable, Math.max(0, Math.min(dirtyStart, len)));
         int end = expandToLineEnd(editable, Math.max(start, Math.min(dirtyEnd, len)));
         dirtyStart = -1;
         dirtyEnd = -1;
         applyRange(editable, start, end, false);
      }

      private void applyRange(Editable editable, int start, int end, boolean fullRescan) {
         if (start < 0) start = 0;
         if (end > editable.length()) end = editable.length();
         if (end <= start) return;
         applying = true;
         try {
            ForegroundColorSpan[] existing = editable.getSpans(start, end, ForegroundColorSpan.class);
            for (ForegroundColorSpan span : existing) {
               int spanStart = editable.getSpanStart(span);
               int spanEnd = editable.getSpanEnd(span);
               if (spanStart >= start && spanEnd <= end) {
                  editable.removeSpan(span);
                  recycle(span);
               } else {
                  editable.removeSpan(span);
                  recycle(span);
               }
            }
            for (EditorTheme.SyntaxRule rule : theme.syntaxRules) {
               applyRule(editable, rule, start, end, fullRescan);
            }
         } finally {
            applying = false;
         }
      }

      private void applyRule(Editable editable, EditorTheme.SyntaxRule rule, int start, int end, boolean fullRescan) {
         Matcher matcher = rule.pattern.matcher(editable);
         if (!fullRescan) {
            matcher.region(start, end);
            matcher.useAnchoringBounds(false);
            matcher.useTransparentBounds(true);
         }
         while (matcher.find()) {
            int mStart = matcher.start();
            int mEnd = matcher.end();
            if (mEnd <= mStart) continue;
            ForegroundColorSpan span = obtain(rule.color);
            editable.setSpan(span, mStart, mEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
         }
      }

      private int expandToLineStart(CharSequence text, int index) {
         int i = Math.max(0, Math.min(index, text.length()));
         while (i > 0 && text.charAt(i - 1) != '\n') {
            i--;
         }
         return i;
      }

      private int expandToLineEnd(CharSequence text, int index) {
         int i = Math.max(0, Math.min(index, text.length()));
         int len = text.length();
         while (i < len && text.charAt(i) != '\n') {
            i++;
         }
         return i;
      }

      private boolean containsBlockCommentDelimiter(CharSequence text) {
         for (int i = 1; i < text.length(); i++) {
            char previous = text.charAt(i - 1);
            char current = text.charAt(i);
            if ((previous == '/' && current == '*') || (previous == '*' && current == '/')) {
               return true;
            }
         }
         return false;
      }

      private ForegroundColorSpan obtain(int color) {
         ArrayDeque<ForegroundColorSpan> pool = spanPool.get(color);
         if (pool != null && !pool.isEmpty()) {
            return pool.pop();
         }
         return new ForegroundColorSpan(color);
      }

      private void recycle(ForegroundColorSpan span) {
         int color = span.getForegroundColor();
         ArrayDeque<ForegroundColorSpan> pool = spanPool.get(color);
         if (pool == null) {
            pool = new ArrayDeque<>(16);
            spanPool.put(color, pool);
         }
         if (pool.size() < 256) {
            pool.push(span);
         }
      }
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
         if ("}".equals(symbol)) { writeClosingBrace(); return; }
         if ("{".equals(symbol)) { writeOpeningBrace(); return; }
         if (";".equals(symbol)) { writeSemicolon(); return; }
         if (",".equals(symbol)) { writeComma(); return; }
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
         if (startOfLine) return false;
         if (pendingSpace) return true;
         if (previousToken == null) return false;
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
            if (pendingSpace) writeSpace();
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
         if (!"++".equals(symbol) && !"--".equals(symbol)) return false;
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
            if (i > 0) builder.append('\n');
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
                  if (source.charAt(i) == '\n') newlineCount++;
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
               while (i < source.length() && source.charAt(i) != '\n') i++;
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

            if (isHexColorLiteral(source, i)) {
               result.add(new Token(TokenType.NUMBER, source.substring(i, i + 7)));
               i += 7;
               continue;
            }

            if (c == '"' || c == '\'') {
               char quote = c;
               int start = i++;
               boolean escaping = false;
               while (i < source.length()) {
                  char current = source.charAt(i++);
                  if (escaping) escaping = false;
                  else if (current == '\\') escaping = true;
                  else if (current == quote) break;
               }
               result.add(new Token(quote == '"' ? TokenType.STRING : TokenType.CHAR, source.substring(start, i)));
               continue;
            }

            if (Character.isLetter(c) || c == '_' || c == '$') {
               int start = i++;
               while (i < source.length()) {
                  char current = source.charAt(i);
                  if (Character.isLetterOrDigit(current) || current == '_' || current == '$') i++;
                  else break;
               }
               result.add(new Token(TokenType.WORD, source.substring(start, i)));
               continue;
            }

            if (Character.isDigit(c)) {
               int start = i++;
               while (i < source.length()) {
                  char current = source.charAt(i);
                  if (Character.isLetterOrDigit(current) || current == '.' || current == '_') i++;
                  else break;
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

      private static boolean isHexColorLiteral(String source, int index) {
         if (source.charAt(index) != '#' || index + 7 > source.length()) {
            return false;
         }
         for (int i = index + 1; i < index + 7; i++) {
            char c = source.charAt(i);
            if (!Character.isDigit(c) && (Character.toLowerCase(c) < 'a' || Character.toLowerCase(c) > 'f')) {
               return false;
            }
         }
         if (index + 7 == source.length()) {
            return true;
         }
         char next = source.charAt(index + 7);
         return !Character.isLetterOrDigit(next) && next != '_' && next != '$';
      }

      private static String readOperator(String source, int index) {
         String[] operators = {
            ">>>=", "<<=", ">>=", "==", "!=", "<=", ">=", "&&", "||", "++", "--",
            "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "->", "::", "<<", ">>",
            ">>>"
         };
         for (String operator : operators) {
            if (source.startsWith(operator, index)) return operator;
         }
         return String.valueOf(source.charAt(index));
      }
   }

   private enum TokenType {
      WORD, NUMBER, STRING, CHAR, LINE_COMMENT, BLOCK_COMMENT, BLANK_LINE, SYMBOL, WHITESPACE
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
