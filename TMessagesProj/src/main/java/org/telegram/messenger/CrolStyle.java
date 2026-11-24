package org.telegram.messenger;

import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.View;

import org.telegram.ui.ActionBar.Theme;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
 * CrolGram: оформление (этап 3). Стили пузырей, форма аватарок, градиенты ников и текста.
 * Всё работает только локально, на серверы ничего не отправляется.
 */
public class CrolStyle {

    public static final String[] PRESET_NAMES = {
            "Закат", "Океан", "Радуга", "Неон", "Огонь", "Лёд", "Сакура", "Лайм", "Космос", "Золото",
            "Мята", "Вишня", "Аврора", "Кислота", "Монохром"
    };

    /** «Кислота» (индекс 13) совпадает с цветами исходящих пузырей по умолчанию (Theme.setAccentColorOptions). */
    public static final int[][] PRESETS = {
            {0xFF512F, 0xF09819, 0xFF0099},
            {0x00C6FF, 0x0072FF, 0x7F00FF},
            {0xFF0000, 0xFFA500, 0xFFFF00, 0x00C800, 0x0090FF, 0x8000FF},
            {0x00FFA3, 0x03E1FF, 0xDC1FFF},
            {0xF12711, 0xF5AF19, 0xFF512F},
            {0x74EBD5, 0x9FACE6, 0xFFFFFF},
            {0xFFAFBD, 0xFFC3A0, 0xFF6FD8},
            {0xB2FF59, 0x00E676, 0x00BFA5},
            {0x8E2DE2, 0x4A00E0, 0x00C9FF},
            {0xF7971E, 0xFFD200, 0xFFF1B5},
            {0x00F5A0, 0x00D9F5, 0x7CFFCB},
            {0xEB3349, 0xF45C43, 0xFF9966},
            {0x00C9FF, 0x92FE9D, 0xFF6FD8},
            {0x2EE66B, 0x1FD6E0},
            {0xFFFFFF, 0x9E9E9E, 0xFFFFFF},
    };

    private static final HashMap<Integer, LinearGradient> shaders = new HashMap<>();
    private static final Matrix matrix = new Matrix();
    private static final ArrayList<Paint> activePaints = new ArrayList<>();

    // ---------------------------------------------------------------- цвета

    public static int[] parseColors(String text) {
        ArrayList<Integer> out = new ArrayList<>();
        if (text != null) {
            String[] parts = text.replace(';', ',').replace(' ', ',').split(",");
            for (String part : parts) {
                String s = part.trim();
                if (s.startsWith("#")) {
                    s = s.substring(1);
                }
                if (s.length() == 3) {
                    s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
                }
                if (s.length() != 6) {
                    continue;
                }
                try {
                    out.add(Integer.parseInt(s, 16) & 0xFFFFFF);
                } catch (Throwable ignore) {
                }
                if (out.size() >= 8) {
                    break;
                }
            }
        }
        int[] r = new int[out.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = out.get(i);
        }
        return r;
    }

    public static int[] colorsFor(boolean mine, long ident) {
        int n = PRESETS.length;
        int[] cols;
        if (mine) {
            int idx = CrolConfig.getInt("grad_me", 13);
            if (idx == n) {
                cols = parseColors(CrolConfig.getString("grad_me_custom", ""));
            } else {
                cols = PRESETS[idx >= 0 && idx < n ? idx : 0];
            }
        } else {
            int idx = CrolConfig.getInt("grad_others", n + 1);
            if (idx == n) {
                cols = parseColors(CrolConfig.getString("grad_others_custom", ""));
            } else if (idx == n + 1) {
                cols = PRESETS[(int) (Math.abs(ident) % n)];
            } else {
                cols = PRESETS[idx >= 0 && idx < n ? idx : 0];
            }
        }
        if (cols == null || cols.length < 2) {
            cols = PRESETS[0];
        }
        return cols;
    }

    private static Shader shader(int[] cols) {
        int key = Arrays.hashCode(cols);
        LinearGradient sh = shaders.get(key);
        if (sh == null) {
            int[] argb = new int[cols.length];
            for (int i = 0; i < cols.length; i++) {
                argb[i] = 0xFF000000 | (cols[i] & 0xFFFFFF);
            }
            sh = new LinearGradient(0, 0, 400, 0, argb, null, Shader.TileMode.MIRROR);
            shaders.put(key, sh);
        }
        if (animEnabled()) {
            matrix.setTranslate((SystemClock.uptimeMillis() * 0.14f) % 800f, 0);
        } else {
            matrix.reset();
        }
        sh.setLocalMatrix(matrix);
        return sh;
    }

    public static boolean animEnabled() {
        return CrolConfig.getBool("grad_anim", false);
    }

    private static long selfId() {
        try {
            return UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        } catch (Throwable e) {
            return 0;
        }
    }

    // ---------------------------------------------------------------- градиенты в ячейках

    private static void addPaint(Paint p, Shader sh, int glowColor) {
        if (p == null) {
            return;
        }
        p.setShader(sh);
        if (glowColor != 0) {
            p.setShadowLayer(AndroidUtilities.dp(3), 0, 0, glowColor);
        }
        activePaints.add(p);
    }

    /** Перед отрисовкой ячейки сообщения: включает градиент на ники и текст. Возвращает true, если что-то включено. */
    public static boolean beginMessageCell(MessageObject mo) {
        if (mo == null || mo.messageOwner == null) {
            return false;
        }
        try {
            boolean names = CrolConfig.getBool("gradient_names", false);
            int textMode = CrolConfig.getInt("grad_text", 0);
            if (!names && textMode == 0) {
                return false;
            }
            boolean mine = mo.messageOwner.out;
            long ident;
            try {
                ident = mo.getFromChatId();
            } catch (Throwable e) {
                ident = mo.getId();
            }
            int[] cols = colorsFor(mine, ident);
            Shader sh = shader(cols);
            boolean any = false;
            if (names) {
                int glow = CrolConfig.getBool("name_glow", false) ? (0xFF000000 | cols[0]) : 0;
                addPaint(Theme.chat_namePaint, sh, glow);
                if (CrolConfig.getBool("grad_replies", true)) {
                    addPaint(Theme.chat_replyNamePaint, sh, glow);
                    addPaint(Theme.chat_forwardNamePaint, sh, glow);
                }
                any = true;
            }
            if (textMode != 0 && (textMode == 3 || (textMode == 1 && mine) || (textMode == 2 && !mine))) {
                addPaint(Theme.chat_msgTextPaint, sh, 0);
                any = true;
            }
            return any && !activePaints.isEmpty();
        } catch (Throwable e) {
            CrolConfig.logOnce("grad_msg", "message gradient error: " + e);
            clearActive();
            return false;
        }
    }

    /** Перед отрисовкой ячейки чата в списке: градиент на название. */
    public static boolean beginDialogCell(long dialogId) {
        try {
            if (!CrolConfig.getBool("gradient_names", false) || !CrolConfig.getBool("grad_dialogs", false)) {
                return false;
            }
            Shader sh = shader(colorsFor(false, dialogId));
            if (Theme.dialogs_namePaint != null) {
                for (TextPaint p : Theme.dialogs_namePaint) {
                    addPaint(p, sh, 0);
                }
            }
            return !activePaints.isEmpty();
        } catch (Throwable e) {
            CrolConfig.logOnce("grad_dlg", "dialog gradient error: " + e);
            clearActive();
            return false;
        }
    }

    public static void end(boolean applied, View view) {
        if (!applied) {
            return;
        }
        clearActive();
        if (view != null && animEnabled()) {
            view.postInvalidateOnAnimation();
        }
    }

    private static void clearActive() {
        for (int i = 0; i < activePaints.size(); i++) {
            Paint p = activePaints.get(i);
            p.setShader(null);
            p.clearShadowLayer();
        }
        activePaints.clear();
    }

    // ---------------------------------------------------------------- градиенты на отдельных надписях

    private static final ArrayList<WeakReference<View>> animViews = new ArrayList<>();
    private static boolean ticking;
    private static final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            ticking = false;
            if (!animEnabled()) {
                animViews.clear();
                return;
            }
            for (int i = animViews.size() - 1; i >= 0; i--) {
                View v = animViews.get(i).get();
                if (v == null || !v.isAttachedToWindow()) {
                    animViews.remove(i);
                } else {
                    v.invalidate();
                }
            }
            if (!animViews.isEmpty()) {
                ticking = true;
                AndroidUtilities.runOnUIThread(this, 33);
            }
        }
    };

    private static void registerAnimated(View v) {
        for (int i = 0; i < animViews.size(); i++) {
            if (animViews.get(i).get() == v) {
                return;
            }
        }
        animViews.add(new WeakReference<>(v));
        if (!ticking) {
            ticking = true;
            AndroidUtilities.runOnUIThread(ticker, 33);
        }
    }

    /** Включает или выключает градиент на надписи (шапка чата, профиль, имя в настройках). */
    public static void applyToPaint(TextPaint paint, View view, boolean on, boolean mine, long ident, boolean glow) {
        if (paint == null) {
            return;
        }
        try {
            if (on) {
                int[] cols = colorsFor(mine, ident);
                paint.setShader(shader(cols));
                if (glow) {
                    paint.setShadowLayer(AndroidUtilities.dp(3), 0, 0, 0xFF000000 | cols[0]);
                } else {
                    paint.clearShadowLayer();
                }
                if (view != null && animEnabled()) {
                    registerAnimated(view);
                }
            } else {
                paint.setShader(null);
                paint.clearShadowLayer();
            }
            if (view != null) {
                view.invalidate();
            }
        } catch (Throwable e) {
            CrolConfig.logOnce("grad_paint", "gradient on label error: " + e);
        }
    }

    public static void applyChatTitle(org.telegram.ui.ActionBar.SimpleTextView title, long dialogId) {
        if (title == null) {
            return;
        }
        boolean on = CrolConfig.getBool("grad_title", false);
        applyToPaint(title.getTextPaint(), title, on, false, dialogId, CrolConfig.getBool("name_glow", false));
    }

    public static void applyProfileName(org.telegram.ui.ActionBar.SimpleTextView name, long id) {
        if (name == null) {
            return;
        }
        boolean on = CrolConfig.getBool("grad_profile", false);
        applyToPaint(name.getTextPaint(), name, on, id == selfId(), id, CrolConfig.getBool("name_glow", false));
    }

    public static void applySelfName(android.widget.TextView name) {
        if (name == null) {
            return;
        }
        boolean on = CrolConfig.getBool("grad_drawer", false);
        applyToPaint(name.getPaint(), name, on, true, selfId(), false);
    }

    // ---------------------------------------------------------------- форма аватарок

    /** Радиусы углов [tl, tr, br, bl] для аватарки с радиусом круга r, либо null, если форма «Круг». */
    public static int[] avatarRadii(int r) {
        int shape = CrolConfig.getInt("avatar_shape", 0);
        if (shape <= 0 || r < AndroidUtilities.dp(14) || r > AndroidUtilities.dp(80)) {
            return null;
        }
        switch (shape) {
            case 1:
                int a = (int) (r * 0.55f);
                return new int[]{a, a, a, a};
            case 2:
                int b = (int) (r * 0.2f);
                return new int[]{b, b, b, b};
            case 3: {
                int small = (int) (r * 0.15f);
                return new int[]{r, r, r, small};
            }
            case 4: {
                int small = (int) (r * 0.15f);
                return new int[]{r, small, r, small};
            }
            default:
                return null;
        }
    }

    private static final Path avatarPath = new Path();
    private static final RectF avatarRect = new RectF();

    /** Рисует фон аватарки без фото (буква на цвете) в выбранной форме. Возвращает false, если форма «Круг». */
    public static boolean drawAvatarBackground(Canvas canvas, float size, Paint paint) {
        int[] radii = avatarRadii((int) (size / 2f));
        if (radii == null) {
            return false;
        }
        avatarRect.set(0, 0, size, size);
        avatarPath.reset();
        avatarPath.addRoundRect(avatarRect, new float[]{
                radii[0], radii[0], radii[1], radii[1], radii[2], radii[2], radii[3], radii[3]
        }, Path.Direction.CW);
        canvas.drawPath(avatarPath, paint);
        return true;
    }

    // ---------------------------------------------------------------- стили пузырей

    private static Paint fillPaint;
    private static Paint strokePaint;
    private static final RectF rectF = new RectF();
    private static final RectF rectF2 = new RectF();

    private static int argb(int a, int rgb) {
        return ((a & 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int c1, int c2, float t) {
        int r = (int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = (int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static float lum(int c) {
        return (0.299f * ((c >> 16) & 255) + 0.587f * ((c >> 8) & 255) + 0.114f * (c & 255)) / 255f;
    }

    private static LinearGradient lg(float x0, float y0, float x1, float y1, int[] colors, float[] stops) {
        return new LinearGradient(x0, y0, x1, y1, colors, stops, Shader.TileMode.CLAMP);
    }

    public static int bubbleStyle() {
        return CrolConfig.getInt("bubble_style", 0);
    }

    /**
     * Рисует фон пузыря выбранным стилем (1 Pixel, 2 Nothing, 3 Glass, 4 Liquid Glass).
     * Возвращает true, если фон нарисован и оригинальную отрисовку нужно пропустить.
     */
    public static boolean drawBubble(Canvas canvas, int style, Rect bounds, boolean out, boolean selected, int baseColor, int textColor, int alpha) {
        if (style < 1 || style > 4 || bounds == null) {
            return false;
        }
        if (fillPaint == null) {
            fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            fillPaint.setStyle(Paint.Style.FILL);
            strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            strokePaint.setStyle(Paint.Style.STROKE);
        }
        Paint f = fillPaint;
        Paint s = strokePaint;
        f.setShader(null);
        f.setPathEffect(null);
        f.clearShadowLayer();
        s.setShader(null);
        s.setPathEffect(null);
        s.clearShadowLayer();

        float tail = AndroidUtilities.dp(4);
        float x0 = bounds.left + (out ? AndroidUtilities.dp(2) : tail);
        float x1 = bounds.right - (out ? tail : AndroidUtilities.dp(2));
        float y0 = bounds.top + AndroidUtilities.dp(1);
        float y1 = bounds.bottom - AndroidUtilities.dp(1);
        float w = x1 - x0;
        float h = y1 - y0;
        if (w < AndroidUtilities.dp(16) || h < AndroidUtilities.dp(12)) {
            return false;
        }
        rectF.set(x0, y0, x1, y1);
        int white = 0xFFFFFF;
        int base = baseColor & 0xFFFFFF;
        int saved = -1;
        if (alpha < 255) {
            saved = canvas.saveLayerAlpha(x0 - 4, y0 - 4, x1 + 4, y1 + 4, alpha);
        }
        float rad;
        if (style == 1) {
            rad = Math.min(AndroidUtilities.dp(22), h / 2);
            f.setColor(argb(0xFF, mix(base, white, 0.06f)));
            canvas.drawRoundRect(rectF, rad, rad, f);
        } else if (style == 2) {
            rad = Math.min(AndroidUtilities.dp(10), h / 2);
            boolean lightText = lum(textColor) >= 0.5f;
            int fill = lightText ? 0x0D0D0D : 0xF2F2F2;
            f.setColor(argb(out ? 0xFF : 0x59, fill));
            canvas.drawRoundRect(rectF, rad, rad, f);
            s.setStrokeWidth(AndroidUtilities.dpf2(1.2f));
            s.setStrokeCap(Paint.Cap.ROUND);
            s.setPathEffect(new DashPathEffect(new float[]{AndroidUtilities.dpf2(1.2f), AndroidUtilities.dp(3)}, 0));
            s.setColor(argb(0xB0, textColor));
            canvas.drawRoundRect(rectF, rad, rad, s);
            s.setPathEffect(null);
            if (out) {
                f.setColor(argb(0xFF, 0xD71921));
                canvas.drawCircle(x1 - AndroidUtilities.dp(8), y0 + AndroidUtilities.dp(8), AndroidUtilities.dpf2(2.2f), f);
            }
        } else if (style == 3) {
            rad = Math.min(AndroidUtilities.dp(18), h / 2);
            f.setColor(argb(0x5A, mix(base, white, 0.35f)));
            canvas.drawRoundRect(rectF, rad, rad, f);
            f.setShader(lg(x0, y0, x1, y1, new int[]{argb(0x4D, white), argb(0x00, white), argb(0x14, white)}, new float[]{0f, 0.55f, 1f}));
            canvas.drawRoundRect(rectF, rad, rad, f);
            f.setShader(null);
            s.setStrokeWidth(AndroidUtilities.dp(1));
            s.setShader(lg(x0, y0, x1, y1, new int[]{argb(0xA0, white), argb(0x20, white)}, null));
            canvas.drawRoundRect(rectF, rad, rad, s);
            s.setShader(null);
        } else {
            rad = Math.min(AndroidUtilities.dp(26), h / 2);
            f.setShadowLayer(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(2), argb(0x40, 0));
            f.setColor(argb(0x4A, mix(base, white, 0.25f)));
            canvas.drawRoundRect(rectF, rad, rad, f);
            f.clearShadowLayer();
            f.setShader(new RadialGradient(x0 + w * 0.28f, y0 + h * 0.18f, Math.max(w, h) * 0.65f,
                    new int[]{argb(0x73, white), argb(0x00, white)}, null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(rectF, rad, rad, f);
            f.setShader(lg(0, y1 - h * 0.35f, 0, y1, new int[]{argb(0x00, white), argb(0x38, white)}, null));
            canvas.drawRoundRect(rectF, rad, rad, f);
            f.setShader(null);
            s.setStrokeWidth(AndroidUtilities.dpf2(1.4f));
            s.setShader(lg(x0, y0, x1, y1, new int[]{argb(0xD0, white), argb(0x30, white), argb(0x90, white)}, new float[]{0f, 0.5f, 1f}));
            canvas.drawRoundRect(rectF, rad, rad, s);
            s.setShader(null);
            s.setStrokeWidth(AndroidUtilities.dp(1));
            s.setColor(argb(0x14, 0));
            float inset = AndroidUtilities.dpf2(1.2f);
            rectF2.set(x0 + inset, y0 + inset, x1 - inset, y1 - inset);
            float r2 = Math.max(rad - inset, 0f);
            canvas.drawRoundRect(rectF2, r2, r2, s);
        }
        if (selected) {
            f.setShader(null);
            f.setColor(argb(0x40, 0x3390EC));
            canvas.drawRoundRect(rectF, rad, rad, f);
        }
        if (saved >= 0) {
            canvas.restoreToCount(saved);
        }
        return true;
    }

    /** Применить изменения настроек стиля без перезапуска: перерисовать открытые экраны. */
    private static android.graphics.Typeface dotFace;
    private static boolean dotTried;

    private static android.graphics.Typeface dotFace() {
        if (!dotTried) {
            dotTried = true;
            try {
                dotFace = android.graphics.Typeface.createFromAsset(ApplicationLoader.applicationContext.getAssets(), "fonts/doto.ttf");
            } catch (Throwable e) {
                CrolConfig.logOnce("dotfont", "dot font load error: " + e);
            }
        }
        return dotFace;
    }

    /** Точечный шрифт (Doto, SIL OFL) для времени и счётчиков непрочитанных в списке чатов. */
    public static void applyDotFont() {
        try {
            android.graphics.Typeface t = CrolConfig.getBool("dot_font", false) ? dotFace() : null;
            if (Theme.dialogs_timePaint != null) {
                Theme.dialogs_timePaint.setTypeface(t);
            }
            if (Theme.dialogs_countTextPaint != null) {
                Theme.dialogs_countTextPaint.setTypeface(t != null ? t : AndroidUtilities.bold());
            }
            if (Theme.dialogs_countTextPaint2 != null) {
                Theme.dialogs_countTextPaint2.setTypeface(t != null ? t : AndroidUtilities.bold());
            }
        } catch (Throwable e) {
            CrolConfig.logOnce("dotapply", "dot font apply error: " + e);
        }
    }

    public static void refreshUi() {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewTheme, false);
            } catch (Throwable ignore) {
            }
        });
    }
}
