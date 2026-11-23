package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.CrolConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

/**
 * CrolGram: диалог «Изменить отображение». Поле со своим курсором и синими ползунками выделения,
 * анимацией набора и стирания текста. Все значения только локальные.
 */
public class CrolEditDialog {

    public static final int ACCENT_BLUE = 0xff3390ec;

    public interface Handler {
        void onSave(String value);

        void onReset();
    }

    /** Поле ввода с курсором, синими ползунками и анимацией набора. */
    public static EditTextBoldCursor createField(Context context, String hint, String value, int inputType) {
        EditTextBoldCursor et = new EditTextBoldCursor(context);
        et.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        et.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        et.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        et.setBackgroundDrawable(null);
        et.setSingleLine(true);
        et.setInputType(inputType);
        et.setImeOptions(EditorInfo.IME_ACTION_DONE);
        et.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        et.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));
        et.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField), ACCENT_BLUE, Theme.getColor(Theme.key_text_RedRegular));
        et.setCursorColor(ACCENT_BLUE);
        et.setCursorSize(AndroidUtilities.dp(22));
        et.setCursorWidth(1.8f);
        et.setHandlesColor(ACCENT_BLUE);
        if (hint != null) {
            et.setHintText(hint);
        }
        et.setText(value == null ? "" : value);
        et.setSelection(et.getText().length());
        attachTypingAnimation(et);
        return et;
    }

    /** Лёгкая анимация: при наборе поле «подпрыгивает», при стирании чуть сдвигается. */
    public static void attachTypingAnimation(final EditText et) {
        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!CrolConfig.getBool("anim_typing", true)) {
                    return;
                }
                et.animate().cancel();
                if (count > before) {
                    et.setScaleX(1.02f);
                    et.setScaleY(1.02f);
                    et.setPivotX(0);
                    et.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                } else if (count < before) {
                    et.setTranslationX(-AndroidUtilities.dp(3));
                    et.setAlpha(0.7f);
                    et.animate().translationX(0).alpha(1f).setDuration(140).start();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    /** Лёгкая версия для поля ввода чата: только сдвиг и прозрачность, без масштаба; синие ползунки выделения. */
    public static void attachChatAnimation(final EditTextBoldCursor et) {
        if (!CrolConfig.getBool("anim_typing", true)) {
            return;
        }
        et.setHandlesColor(ACCENT_BLUE);
        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!CrolConfig.getBool("anim_typing", true) || Math.abs(count - before) > 3) {
                    return;
                }
                et.animate().cancel();
                if (count > before) {
                    et.setAlpha(0.85f);
                    et.animate().alpha(1f).setDuration(100).start();
                } else if (count < before) {
                    et.setTranslationX(-AndroidUtilities.dp(2));
                    et.setAlpha(0.8f);
                    et.animate().translationX(0).alpha(1f).setDuration(120).start();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    public static void show(BaseFragment fragment, String title, String hint, String current, int inputType, Handler handler) {
        final Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), AndroidUtilities.dp(8));

        final EditTextBoldCursor field = createField(context, hint, current, inputType);
        box.addView(field, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView note = new TextView(context);
        note.setText("Только на этом устройстве: другие люди и серверы Telegram ничего не увидят.");
        note.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        note.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        note.setTypeface(Typeface.DEFAULT);
        note.setPadding(0, AndroidUtilities.dp(10), 0, 0);
        box.addView(note, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setView(box);
        builder.setPositiveButton("Сохранить", (d, w) -> {
            handler.onSave(field.getText().toString().trim());
            org.telegram.messenger.CrolProfile.refreshSelf();
        });
        builder.setNeutralButton("Сбросить", (d, w) -> {
            handler.onReset();
            org.telegram.messenger.CrolProfile.refreshSelf();
        });
        builder.setNegativeButton("Отмена", null);
        AlertDialog dialog = builder.create();
        fragment.showDialog(dialog);
        field.requestFocus();
        AndroidUtilities.showKeyboard(field);
    }

    public static void showKey(BaseFragment fragment, String title, String hint, String key, int inputType, Runnable after) {
        show(fragment, title, hint, CrolConfig.getString(key, ""), inputType, new Handler() {
            @Override
            public void onSave(String value) {
                CrolConfig.setString(key, value);
                if (after != null) {
                    after.run();
                }
            }

            @Override
            public void onReset() {
                CrolConfig.remove(key);
                if (after != null) {
                    after.run();
                }
            }
        });
    }
}
