package com.apde2;

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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.activity.ComponentActivity;

public final class SettingsActivity extends ComponentActivity {
   private static final String STATE_SCROLL_Y = "scroll_y";

   private final EditorTheme theme = EditorTheme.dark();
   private AppSettings settings;
   private AppStrings strings;
   private ScrollView scrollView;
   private int restoredScrollY;

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      settings = new AppSettings(this);
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

      root.addView(sectionTitle(s(AppStrings.Key.GENERAL)));
      root.addView(choiceRow(s(AppStrings.Key.INTERFACE_LANGUAGE), settings.language(), s(AppStrings.Key.ENGLISH), AppSettings.LANGUAGE_EN, s(AppStrings.Key.RUSSIAN), AppSettings.LANGUAGE_RU, value -> {
         settings.setLanguage(value);
         recreate();
      }));
      root.addView(toggleRow(s(AppStrings.Key.HAPTIC_FEEDBACK), settings.hapticEnabled(), settings::setHapticEnabled));

      root.addView(sectionTitle(s(AppStrings.Key.EDITOR)));
      root.addView(numberRow(s(AppStrings.Key.FONT_SIZE), settings.editorFontSizeSp(), 10, 28, settings::setEditorFontSizeSp));
      root.addView(numberRow(s(AppStrings.Key.TAB_SIZE), settings.tabSize(), 2, 8, settings::setTabSize));
      root.addView(toggleRow(s(AppStrings.Key.AUTO_CLOSE_BRACKETS_AND_QUOTES), settings.autoClosePairs(), settings::setAutoClosePairs));

      root.addView(sectionTitle(s(AppStrings.Key.CONSOLE)));
      root.addView(numberRow(s(AppStrings.Key.FONT_SIZE), settings.consoleFontSizeSp(), 10, 24, settings::setConsoleFontSizeSp));

      root.addView(sectionTitle(s(AppStrings.Key.APPEARANCE)));
      root.addView(choiceRow(s(AppStrings.Key.THEME), settings.themeMode(), s(AppStrings.Key.DARK), AppSettings.THEME_DARK, s(AppStrings.Key.LIGHT), AppSettings.THEME_LIGHT, settings::setThemeMode));

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
      LinearLayout wrapper = new LinearLayout(this);
      wrapper.setOrientation(LinearLayout.VERTICAL);
      wrapper.setPadding(dp(14), dp(12), dp(14), dp(12));
      UiStyles.roundedStroke(wrapper, theme.surface, theme.border, dp(1), dp(12));
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      params.setMargins(0, 0, 0, dp(8));
      wrapper.setLayoutParams(params);

      TextView text = rowLabel(label);
      wrapper.addView(text);

      LinearLayout choices = new LinearLayout(this);
      choices.setOrientation(LinearLayout.HORIZONTAL);
      choices.setPadding(0, dp(10), 0, 0);
      wrapper.addView(choices, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

      TextView first = choiceButton(firstLabel, selected.equals(firstValue));
      first.setOnClickListener(view -> {
         action.apply(firstValue);
         recreate();
      });
      choices.addView(first, new LinearLayout.LayoutParams(0, dp(40), 1f));

      TextView second = choiceButton(secondLabel, selected.equals(secondValue));
      second.setOnClickListener(view -> {
         action.apply(secondValue);
         recreate();
      });
      LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
      secondParams.setMargins(dp(8), 0, 0, 0);
      choices.addView(second, secondParams);
      return wrapper;
   }

   private TextView choiceButton(String label, boolean active) {
      TextView button = new TextView(this);
      button.setText(label);
      button.setTextColor(active ? theme.background : theme.text);
      button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
      button.setGravity(Gravity.CENTER);
      UiStyles.roundedStroke(button, active ? theme.accent : theme.surfaceSoft, active ? theme.accent : theme.border, dp(1), dp(10));
      return button;
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
