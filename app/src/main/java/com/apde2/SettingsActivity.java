package com.apde2;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.activity.ComponentActivity;

import java.util.List;

public final class SettingsActivity extends ComponentActivity {
   private static final String STATE_SCROLL_Y = "scroll_y";

   private EditorTheme theme;
   private AppSettings settings;
   private AppStrings strings;
   private ScrollView scrollView;
   private int restoredScrollY;
   private String appliedThemeMode;
   private ValueAnimator themeAnimator;

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      settings = new AppSettings(this);
      appliedThemeMode = EditorTheme.resolveThemeId(this, settings.themeMode());
      if (!appliedThemeMode.equals(settings.themeMode())) {
         settings.setThemeMode(appliedThemeMode);
      }
      theme = EditorTheme.load(this, appliedThemeMode);
      strings = new AppStrings(settings.language());
      applySystemBarColors();
      restoredScrollY = savedInstanceState == null ? 0 : savedInstanceState.getInt(STATE_SCROLL_Y, 0);
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
   protected void onSaveInstanceState(Bundle outState) {
      if (scrollView != null) {
         outState.putInt(STATE_SCROLL_Y, scrollView.getScrollY());
      }
      super.onSaveInstanceState(outState);
   }

   @Override
   protected void onDestroy() {
      cancelThemeAnimation();
      super.onDestroy();
   }

   private void buildUi() {
      ScrollView scroll = new ScrollView(this);
      scrollView = scroll;
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
      title.setText(s(AppStrings.Key.SETTINGS));
      title.setTextColor(theme.text);
      title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
      title.setGravity(Gravity.CENTER_VERTICAL);
      LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
      titleParams.setMargins(dp(8), 0, 0, 0);
      header.addView(title, titleParams);
      root.addView(header, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

      addSection(root, s(AppStrings.Key.GENERAL),
         choiceRow(s(AppStrings.Key.INTERFACE_LANGUAGE), settings.language(), s(AppStrings.Key.ENGLISH), AppSettings.LANGUAGE_EN, s(AppStrings.Key.RUSSIAN), AppSettings.LANGUAGE_RU, value -> {
            settings.setLanguage(value);
            recreate();
         }),
         toggleRow(s(AppStrings.Key.HAPTIC_FEEDBACK), settings.hapticEnabled(), settings::setHapticEnabled));

      addSection(root, s(AppStrings.Key.EDITOR),
         numberRow(s(AppStrings.Key.FONT_SIZE), settings.editorFontSizeSp(), 10, 28, settings::setEditorFontSizeSp),
         numberRow(s(AppStrings.Key.TAB_SIZE), settings.tabSize(), 2, 8, settings::setTabSize),
         toggleRow(s(AppStrings.Key.AUTO_CLOSE_BRACKETS_AND_QUOTES), settings.autoClosePairs(), settings::setAutoClosePairs));

      addSection(root, s(AppStrings.Key.CONSOLE),
         numberRow(s(AppStrings.Key.FONT_SIZE), settings.consoleFontSizeSp(), 10, 24, settings::setConsoleFontSizeSp));

      addSection(root, s(AppStrings.Key.APPEARANCE),
         themeGridRow(s(AppStrings.Key.THEME), settings.themeMode(), EditorTheme.listThemes(this), this::changeThemeMode));

      setContentView(scroll);
      scroll.requestApplyInsets();
      if (restoredScrollY > 0) {
         scroll.setAlpha(0f);
         scroll.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
               scroll.getViewTreeObserver().removeOnPreDrawListener(this);
               scroll.scrollTo(0, restoredScrollY);
               scroll.setAlpha(1f);
               return true;
            }
         });
      }
   }

   private void changeThemeMode(String themeMode) {
      if (themeMode.equals(appliedThemeMode)) {
         return;
      }
      settings.setThemeMode(themeMode);
      animateThemeChange(themeMode);
   }

   private void animateThemeChange(String themeMode) {
      EditorTheme from = theme;
      EditorTheme to = EditorTheme.load(this, themeMode);
      cancelThemeAnimation();
      appliedThemeMode = themeMode;
      themeAnimator = ValueAnimator.ofFloat(0f, 1f);
      themeAnimator.setDuration(220L);
      themeAnimator.addUpdateListener(animator -> applyAnimatedTheme(EditorTheme.interpolate(from, to, (Float) animator.getAnimatedValue())));
      final boolean[] canceled = {false};
      themeAnimator.addListener(new AnimatorListenerAdapter() {
         @Override
         public void onAnimationEnd(Animator animation) {
            if (canceled[0]) {
               return;
            }
            themeAnimator = null;
            applyAnimatedTheme(to);
         }

         @Override
         public void onAnimationCancel(Animator animation) {
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

   private void applyAnimatedTheme(EditorTheme nextTheme) {
      EditorTheme previousTheme = theme;
      theme = nextTheme;
      applySystemBarColors();
      applyThemeToTree(scrollView, previousTheme, theme);
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
      if (color == previousTheme.background) return nextTheme.background;
      if (color == previousTheme.surface) return nextTheme.surface;
      if (color == previousTheme.surfaceSoft) return nextTheme.surfaceSoft;
      if (color == previousTheme.border) return nextTheme.border;
      if (sameRgb(color, previousTheme.border)) return withAlpha(nextTheme.border, Color.alpha(color));
      if (color == previousTheme.text) return nextTheme.text;
      if (color == previousTheme.textMuted) return nextTheme.textMuted;
      if (color == previousTheme.accent) return nextTheme.accent;
      if (color == previousTheme.codeAccent) return nextTheme.codeAccent;
      if (color == previousTheme.error) return nextTheme.error;
      return color;
   }

   private boolean sameRgb(int left, int right) {
      return Color.red(left) == Color.red(right)
         && Color.green(left) == Color.green(right)
         && Color.blue(left) == Color.blue(right);
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

   private LinearLayout toggleRow(String label, boolean checked, BooleanAction action) {
      LinearLayout row = baseRow();
      TextView text = rowLabel(label);
      row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
      Switch toggle = new Switch(this);
      toggle.setChecked(checked);
      toggle.setOnCheckedChangeListener((buttonView, isChecked) -> action.apply(isChecked));
      row.addView(toggle);
      row.setClickable(true);
      row.setFocusable(true);
      row.setOnClickListener(view -> toggle.setChecked(!toggle.isChecked()));
      return row;
   }

   private LinearLayout numberRow(String label, int value, int min, int max, IntAction action) {
      LinearLayout row = baseRow();
      TextView text = rowLabel(label);
      row.addView(text, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

      EditText input = new EditText(this);
      input.setSingleLine(true);
      input.setText(String.valueOf(value));
      input.setTextColor(theme.text);
      input.setHintTextColor(theme.textMuted);
      input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
      input.setInputType(InputType.TYPE_CLASS_NUMBER);
      input.setImeOptions(EditorInfo.IME_ACTION_DONE);
      input.setGravity(Gravity.CENTER);
      input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(2)});
      input.setSelectAllOnFocus(true);
      UiStyles.roundedStroke(input, theme.surfaceSoft, theme.border, dp(1), dp(10));
      LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(dp(72), dp(40));
      row.addView(input, inputParams);

      View.OnFocusChangeListener listener = (view, hasFocus) -> {
         if (!hasFocus) {
            int parsed = parseInt(((EditText) view).getText().toString(), value, min, max);
            ((EditText) view).setText(String.valueOf(parsed));
            action.apply(parsed);
         }
      };
      input.setOnFocusChangeListener(listener);
      input.setOnEditorActionListener((v, actionId, event) -> {
         if (actionId != EditorInfo.IME_ACTION_DONE) {
            return false;
         }
         v.clearFocus();
         InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
         if (imm != null) {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
         }
         return true;
      });
      return row;
   }

   private LinearLayout choiceRow(String label, String selected, String firstLabel, String firstValue, String secondLabel, String secondValue, StringAction action) {
      return choiceRow(label, selected, firstLabel, firstValue, secondLabel, secondValue, action, true);
   }

   private LinearLayout choiceRow(String label, String selected, String firstLabel, String firstValue, String secondLabel, String secondValue, StringAction action, boolean recreateOnClick) {
      LinearLayout wrapper = new LinearLayout(this);
      wrapper.setOrientation(LinearLayout.VERTICAL);
      wrapper.setPadding(dp(14), dp(12), dp(14), dp(12));
      wrapper.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

      TextView text = rowLabel(label);
      wrapper.addView(text);

      LinearLayout choices = new LinearLayout(this);
      choices.setOrientation(LinearLayout.HORIZONTAL);
      choices.setPadding(0, dp(10), 0, 0);
      wrapper.addView(choices, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

      TextView first = choiceButton(firstLabel, selected.equals(firstValue));
      first.setOnClickListener(view -> {
         action.apply(firstValue);
         if (recreateOnClick) {
            recreate();
         }
      });
      choices.addView(first, new LinearLayout.LayoutParams(0, dp(40), 1f));

      TextView second = choiceButton(secondLabel, selected.equals(secondValue));
      second.setOnClickListener(view -> {
         action.apply(secondValue);
         if (recreateOnClick) {
            recreate();
         }
      });
      LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
      secondParams.setMargins(dp(8), 0, 0, 0);
      choices.addView(second, secondParams);
      return wrapper;
   }

   private LinearLayout choiceRow(String label, String selected, String firstLabel, String firstValue, String secondLabel, String secondValue, String thirdLabel, String thirdValue, StringAction action) {
      LinearLayout wrapper = new LinearLayout(this);
      wrapper.setOrientation(LinearLayout.VERTICAL);
      wrapper.setPadding(dp(14), dp(12), dp(14), dp(12));
      wrapper.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

      TextView text = rowLabel(label);
      wrapper.addView(text);

      LinearLayout choices = new LinearLayout(this);
      choices.setOrientation(LinearLayout.HORIZONTAL);
      choices.setPadding(0, dp(10), 0, 0);
      wrapper.addView(choices, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

      TextView first = choiceButton(firstLabel, selected.equals(firstValue));
      TextView second = choiceButton(secondLabel, selected.equals(secondValue));
      TextView third = choiceButton(thirdLabel, selected.equals(thirdValue));
      TextView[] buttons = {first, second, third};
      String[] values = {firstValue, secondValue, thirdValue};

      first.setOnClickListener(view -> {
         applyChoiceSelection(buttons, values, firstValue);
         action.apply(firstValue);
      });
      choices.addView(first, new LinearLayout.LayoutParams(0, dp(40), 1f));

      second.setOnClickListener(view -> {
         applyChoiceSelection(buttons, values, secondValue);
         action.apply(secondValue);
      });
      LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
      secondParams.setMargins(dp(8), 0, 0, 0);
      choices.addView(second, secondParams);

      third.setOnClickListener(view -> {
         applyChoiceSelection(buttons, values, thirdValue);
         action.apply(thirdValue);
      });
      LinearLayout.LayoutParams thirdParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
      thirdParams.setMargins(dp(8), 0, 0, 0);
      choices.addView(third, thirdParams);
      return wrapper;
   }

   private LinearLayout themeGridRow(String label, String selected, List<EditorTheme.ThemeInfo> themes, StringAction action) {
      LinearLayout wrapper = new LinearLayout(this);
      wrapper.setOrientation(LinearLayout.VERTICAL);
      wrapper.setPadding(dp(14), dp(12), dp(14), dp(12));
      wrapper.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

      TextView text = rowLabel(label);
      wrapper.addView(text);

      GridLayout choices = new GridLayout(this);
      int columns = 2;
      choices.setColumnCount(columns);
      choices.setPadding(0, dp(10), 0, 0);
      wrapper.addView(choices, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

      TextView[] buttons = new TextView[themes.size()];
      String[] values = new String[themes.size()];
      for (int i = 0; i < themes.size(); i++) {
         EditorTheme.ThemeInfo themeInfo = themes.get(i);
         ChoiceButton button = choiceButton(themeInfo.name, selected.equals(themeInfo.id));
         button.setSingleLine(true);
         button.setMaxLines(1);
         button.setEllipsize(TextUtils.TruncateAt.END);
         button.setContentDescription(themeInfo.name);
         buttons[i] = button;
         values[i] = themeInfo.id;

         button.setOnClickListener(view -> {
            applyChoiceSelection(buttons, values, themeInfo.id);
            action.apply(themeInfo.id);
         });

         int row = i / columns;
         int column = i % columns;
         GridLayout.LayoutParams params = new GridLayout.LayoutParams(GridLayout.spec(row), GridLayout.spec(column, 1f));
         params.width = 0;
         params.height = dp(40);
         params.setMargins(column == 0 ? 0 : dp(8), row == 0 ? 0 : dp(8), 0, 0);
         choices.addView(button, params);
      }
      return wrapper;
   }

   private void applyChoiceSelection(TextView[] buttons, String[] values, String selected) {
      for (int i = 0; i < buttons.length; i++) {
         styleChoiceButton(buttons[i], values[i].equals(selected));
      }
   }

   private ChoiceButton choiceButton(String label, boolean active) {
      ChoiceButton button = new ChoiceButton(this);
      button.setText(label);
      button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
      button.setGravity(Gravity.CENTER);
      button.setPadding(dp(8), 0, dp(8), 0);
      button.setChoiceActive(active);
      return button;
   }

   private void styleChoiceButton(TextView button, boolean active) {
      if (button instanceof ChoiceButton) {
         ((ChoiceButton) button).setChoiceActive(active);
         return;
      }
      button.setTextColor(active ? theme.background : theme.text);
      UiStyles.roundedStroke(button, active ? theme.accent : theme.surfaceSoft, active ? theme.accent : theme.border, dp(1), dp(10));
   }

   private LinearLayout baseRow() {
      LinearLayout row = new LinearLayout(this);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(dp(14), dp(12), dp(14), dp(12));
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
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

   private int parseInt(String value, int fallback, int min, int max) {
      try {
         return Math.max(min, Math.min(max, Integer.parseInt(value.trim())));
      } catch (RuntimeException exception) {
         return fallback;
      }
   }

   private String s(AppStrings.Key key) {
      return strings.text(key);
   }

   private int dp(int value) {
      return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
   }

   private static int withAlpha(int color, int alpha) {
      return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
   }

   private final class ChoiceButton extends TextView implements ThemeAware {
      private boolean active;

      ChoiceButton(android.content.Context context) {
         super(context);
      }

      void setChoiceActive(boolean active) {
         this.active = active;
         applyTheme(theme);
      }

      @Override
      public void applyTheme(EditorTheme theme) {
         setTextColor(active ? theme.background : theme.text);
         UiStyles.roundedStroke(this, active ? theme.accent : theme.surfaceSoft, active ? theme.accent : theme.border, dp(1), dp(10));
      }
   }

   private interface BooleanAction {
      void apply(boolean value);
   }

   private interface IntAction {
      void apply(int value);
   }

   private interface StringAction {
      void apply(String value);
   }
}
