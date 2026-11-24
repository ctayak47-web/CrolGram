package org.telegram.messenger;

import android.graphics.Color;
import android.os.Build;
import android.util.SparseIntArray;

import org.telegram.ui.ActionBar.Theme;

/**
 * CrolGram: слой токенов Material 3. Генерирует тональную палитру из исходного цвета (seed), собирает роли M3
 * (primary, surface, surfaceContainer*, onSurface и т. д.) и подменяет ими цвета Theme.key_*.
 * Палитра строится в HSL (приближение к HCT из Material Color Utilities, без внешних зависимостей).
 */
public class CrolMaterial {

    /** Значение «ключ не переопределён». */
    public static final int NONE = 0x7F00ABCD;

    // шкала форм M3, dp
    public static final int SHAPE_XS = 4;
    public static final int SHAPE_S = 8;
    public static final int SHAPE_M = 12;
    public static final int SHAPE_L = 16;
    public static final int SHAPE_XL = 28;

    // кривая и длительности движения M3 (emphasized)
    public static final int DURATION_SHORT = 200;
    public static final int DURATION_MEDIUM = 400;
    public static final int DURATION_LONG = 500;

    /** Роли M3. */
    public static class Roles {
        public int primary, onPrimary, primaryContainer, onPrimaryContainer;
        public int secondary, secondaryContainer, onSecondaryContainer;
        public int tertiary, tertiaryContainer;
        public int surface, surfaceContainerLowest, surfaceContainerLow, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest;
        public int onSurface, onSurfaceVariant, outline, outlineVariant;
        public int error, onError, errorContainer;
        public boolean dark;
    }

    private static final int[] SEEDS = {
            0xFF2EE66B, // динамическая: запасной seed ниже Android 12
            0xFF2EE66B, // зелёная CrolGram
            0xFF2EE66B, // свой цвет (читается из m3_seed)
            0xFF9BE400, // Кислота
            0xFF00FFA3, // Неон
            0xFF1B9E5A, // Лес
            0xFFFF7A18, // Закат
            0xFF3EB489  // Мята
    };

    private static final SparseIntArray map = new SparseIntArray();
    private static boolean ready;
    private static boolean active;
    private static boolean lastDark;
    private static long lastDarkCheck;
    private static Roles roles;

    // ---------------------------------------------------------------- цвет в HSL

    private static int hsl(float h, float s, float tone) {
        h = ((h % 360f) + 360f) % 360f;
        s = Math.max(0f, Math.min(1f, s));
        float l = Math.max(0f, Math.min(1f, tone / 100f));
        float c = (1f - Math.abs(2f * l - 1f)) * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = l - c / 2f;
        float r, g, b;
        if (h < 60) {
            r = c; g = x; b = 0;
        } else if (h < 120) {
            r = x; g = c; b = 0;
        } else if (h < 180) {
            r = 0; g = c; b = x;
        } else if (h < 240) {
            r = 0; g = x; b = c;
        } else if (h < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }
        return Color.rgb(Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
    }

    private static int withAlpha(int color, float a) {
        return (color & 0xFFFFFF) | (Math.round(255 * a) << 24);
    }

    // ---------------------------------------------------------------- настройки

    private static int seedColor() {
        int scheme = CrolConfig.getRawInt("m3_scheme", 1);
        if (scheme == 0 && Build.VERSION.SDK_INT >= 31) {
            try {
                return ApplicationLoader.applicationContext.getResources().getColor(android.R.color.system_accent1_500, null);
            } catch (Throwable ignore) {
            }
        }
        if (scheme == 2) {
            try {
                String v = CrolConfig.getString("m3_seed", "#2EE66B").trim();
                if (!v.startsWith("#")) {
                    v = "#" + v;
                }
                return Color.parseColor(v);
            } catch (Throwable ignore) {
                return SEEDS[1];
            }
        }
        return SEEDS[Math.max(0, Math.min(SEEDS.length - 1, scheme))];
    }

    /** Строит роли M3 для seed, режима и уровня контраста (0 стандартный, 1 средний, 2 высокий). */
    public static Roles build(int seed, boolean dark, boolean amoled, int contrast) {
        float[] hsv = new float[3];
        Color.colorToHSV(seed, hsv);
        float h = hsv[0];
        float sp = Math.max(0.45f, Math.min(0.9f, hsv[1]));
        float c = contrast;
        Roles r = new Roles();
        r.dark = dark;
        float nS = 0.07f;
        float nvS = 0.10f;
        if (!dark) {
            r.primary = hsl(h, sp, 40 - 6 * c);
            r.onPrimary = hsl(h, sp, 100);
            r.primaryContainer = hsl(h, sp, 90 - 2 * c);
            r.onPrimaryContainer = hsl(h, sp, 10 - 4 * c);
            r.secondary = hsl(h, 0.25f, 40 - 6 * c);
            r.secondaryContainer = hsl(h, 0.30f, 90);
            r.onSecondaryContainer = hsl(h, 0.30f, 10);
            r.tertiary = hsl(h + 60, 0.5f, 40 - 6 * c);
            r.tertiaryContainer = hsl(h + 60, 0.55f, 90);
            r.surface = hsl(h, nS, 98);
            r.surfaceContainerLowest = hsl(h, nS, 100);
            r.surfaceContainerLow = hsl(h, nS, 96);
            r.surfaceContainer = hsl(h, nS, 94);
            r.surfaceContainerHigh = hsl(h, nS, 92);
            r.surfaceContainerHighest = hsl(h, nS, 90);
            r.onSurface = hsl(h, nS, 10 - 5 * c);
            r.onSurfaceVariant = hsl(h, nvS, 30 - 5 * c);
            r.outline = hsl(h, nvS, 50 - 10 * c);
            r.outlineVariant = hsl(h, nvS, 80 - 10 * c);
            r.error = 0xFFB3261E;
            r.onError = 0xFFFFFFFF;
            r.errorContainer = 0xFFF9DEDC;
        } else {
            r.primary = hsl(h, sp, 80 + 3 * c);
            r.onPrimary = hsl(h, sp, 20 - 5 * c);
            r.primaryContainer = hsl(h, sp, 30 - 4 * c);
            r.onPrimaryContainer = hsl(h, sp, 90 + 3 * c);
            r.secondary = hsl(h, 0.30f, 80);
            r.secondaryContainer = hsl(h, 0.25f, 30);
            r.onSecondaryContainer = hsl(h, 0.30f, 90);
            r.tertiary = hsl(h + 60, 0.55f, 80);
            r.tertiaryContainer = hsl(h + 60, 0.45f, 30);
            r.surface = amoled ? 0xFF000000 : hsl(h, nS, 6);
            r.surfaceContainerLowest = amoled ? 0xFF000000 : hsl(h, nS, 4);
            r.surfaceContainerLow = amoled ? hsl(h, nS, 4) : hsl(h, nS, 10);
            r.surfaceContainer = amoled ? hsl(h, nS, 7) : hsl(h, nS, 12);
            r.surfaceContainerHigh = amoled ? hsl(h, nS, 10) : hsl(h, nS, 17);
            r.surfaceContainerHighest = amoled ? hsl(h, nS, 14) : hsl(h, nS, 22);
            r.onSurface = hsl(h, nS, 90 + 5 * c);
            r.onSurfaceVariant = hsl(h, nvS, 80 + 7 * c);
            r.outline = hsl(h, nvS, 60 + 10 * c);
            r.outlineVariant = hsl(h, nvS, 30 + 5 * c);
            r.error = 0xFFF2B8B5;
            r.onError = 0xFF601410;
            r.errorContainer = 0xFF8C1D18;
        }
        return r;
    }

    // ---------------------------------------------------------------- отображение на ключи Theme

    private static void put(int key, int color) {
        map.put(key, color);
    }

    private static void rebuild() {
        map.clear();
        ready = true;
        active = false;
        try {
            if (!CrolConfig.getRawBool("design_m3", true)) {
                return;
            }
            int mode = CrolConfig.getRawInt("m3_mode", 0);
            boolean dark = mode == 2 || mode == 3 || (mode == 0 && darkNow());
            lastDark = darkNow();
            Roles r = build(seedColor(), dark, mode == 3 || (mode == 0 && dark && CrolConfig.getRawBool("m3_amoled_auto", false)),
                    CrolConfig.getRawInt("m3_contrast", 0));
            roles = r;
            int ripple = withAlpha(r.onSurface, 0.10f);

            put(Theme.key_windowBackgroundWhite, r.surface);
            put(Theme.key_windowBackgroundGray, r.surfaceContainer);
            put(Theme.key_windowBackgroundWhiteBlackText, r.onSurface);
            put(Theme.key_windowBackgroundWhiteGrayText, r.onSurfaceVariant);
            put(Theme.key_windowBackgroundWhiteGrayText2, r.onSurfaceVariant);
            put(Theme.key_windowBackgroundWhiteGrayText3, r.onSurfaceVariant);
            put(Theme.key_windowBackgroundWhiteGrayText4, r.onSurfaceVariant);
            put(Theme.key_windowBackgroundWhiteBlueText, r.primary);
            put(Theme.key_windowBackgroundWhiteBlueText2, r.primary);
            put(Theme.key_windowBackgroundWhiteBlueText3, r.primary);
            put(Theme.key_windowBackgroundWhiteBlueText4, r.primary);
            put(Theme.key_windowBackgroundWhiteBlueHeader, r.primary);
            put(Theme.key_windowBackgroundWhiteBlueButton, r.primary);
            put(Theme.key_windowBackgroundWhiteLinkText, r.primary);
            put(Theme.key_windowBackgroundWhiteInputField, r.outline);
            put(Theme.key_windowBackgroundWhiteInputFieldActivated, r.primary);
            put(Theme.key_windowBackgroundWhiteGrayIcon, r.onSurfaceVariant);
            put(Theme.key_windowBackgroundWhiteBlueIcon, r.primary);
            put(Theme.key_divider, r.outlineVariant);
            put(Theme.key_listSelector, ripple);
            put(Theme.key_progressCircle, r.primary);

            put(Theme.key_actionBarDefault, r.surface);
            put(Theme.key_actionBarDefaultTitle, r.onSurface);
            put(Theme.key_actionBarDefaultIcon, r.onSurface);
            put(Theme.key_actionBarDefaultSubtitle, r.onSurfaceVariant);
            put(Theme.key_actionBarDefaultSelector, ripple);
            put(Theme.key_actionBarDefaultSearch, r.onSurface);
            put(Theme.key_actionBarDefaultSearchPlaceholder, r.onSurfaceVariant);

            put(Theme.key_chat_inBubble, r.surfaceContainerHigh);
            put(Theme.key_chat_inBubbleSelected, r.surfaceContainerHighest);
            put(Theme.key_chat_outBubble, r.primaryContainer);
            put(Theme.key_chat_outBubbleSelected, r.primaryContainer);
            put(Theme.key_chat_messageTextIn, r.onSurface);
            put(Theme.key_chat_messageTextOut, r.onPrimaryContainer);
            put(Theme.key_chat_messageLinkIn, r.primary);
            put(Theme.key_chat_messageLinkOut, r.onPrimaryContainer);
            put(Theme.key_chat_inTimeText, r.onSurfaceVariant);
            put(Theme.key_chat_outTimeText, withAlpha(r.onPrimaryContainer, 0.7f));
            put(Theme.key_chat_outSentCheck, r.primary);
            put(Theme.key_chat_outSentCheckRead, r.primary);
            put(Theme.key_chat_inSentClock, r.onSurfaceVariant);
            put(Theme.key_chat_outSentClock, withAlpha(r.onPrimaryContainer, 0.7f));
            put(Theme.key_chat_inReplyLine, r.primary);
            put(Theme.key_chat_outReplyLine, r.onPrimaryContainer);
            put(Theme.key_chat_inReplyNameText, r.primary);
            put(Theme.key_chat_outReplyNameText, r.onPrimaryContainer);
            put(Theme.key_chat_inReplyMessageText, r.onSurface);
            put(Theme.key_chat_outReplyMessageText, r.onPrimaryContainer);
            put(Theme.key_chat_inLoader, r.primary);
            put(Theme.key_chat_outLoader, r.onPrimaryContainer);
            put(Theme.key_chat_inPreviewInstantText, r.primary);

            put(Theme.key_chat_messagePanelBackground, r.surfaceContainer);
            put(Theme.key_chat_messagePanelText, r.onSurface);
            put(Theme.key_chat_messagePanelHint, r.onSurfaceVariant);
            put(Theme.key_chat_messagePanelIcons, r.onSurfaceVariant);
            put(Theme.key_chat_messagePanelSend, r.primary);

            put(Theme.key_chats_name, r.onSurface);
            put(Theme.key_chats_message, r.onSurfaceVariant);
            put(Theme.key_chats_date, r.onSurfaceVariant);
            put(Theme.key_chats_nameMessage, r.primary);
            put(Theme.key_chats_actionMessage, r.primary);
            put(Theme.key_chats_sentCheck, r.primary);
            put(Theme.key_chats_pinnedIcon, r.onSurfaceVariant);
            put(Theme.key_chats_unreadCounter, r.primary);
            put(Theme.key_chats_unreadCounterText, r.onPrimary);
            put(Theme.key_chats_unreadCounterMuted, r.outline);
            put(Theme.key_chats_actionBackground, r.primaryContainer);
            put(Theme.key_chats_actionIcon, r.onPrimaryContainer);
            put(Theme.key_chats_actionPressedBackground, r.primary);

            put(Theme.key_switchTrackChecked, r.primary);
            put(Theme.key_switchTrack, r.surfaceContainerHighest);

            put(Theme.key_dialogBackground, r.surfaceContainerHigh);
            put(Theme.key_dialogBackgroundGray, r.surfaceContainerHighest);
            put(Theme.key_dialogTextBlack, r.onSurface);
            put(Theme.key_dialogTextGray2, r.onSurfaceVariant);
            put(Theme.key_dialogTextBlue, r.primary);
            put(Theme.key_dialogButton, r.primary);
            put(Theme.key_dialogGrayLine, r.outlineVariant);

            put(Theme.key_featuredStickers_addButton, r.primary);
            put(Theme.key_featuredStickers_buttonText, r.onPrimary);
            active = true;
        } catch (Throwable e) {
            map.clear();
            CrolConfig.logOnce("m3", "material rebuild error: " + e);
        }
    }

    private static boolean darkNow() {
        try {
            return Theme.isCurrentThemeDark();
        } catch (Throwable e) {
            return false;
        }
    }

    /** Значение M3 для ключа Theme или NONE. Вызывается из Theme.getColor, поэтому без выделения памяти. */
    public static int get(int key) {
        if (!ready) {
            rebuild();
        } else if (active) {
            long now = android.os.SystemClock.uptimeMillis();
            if (now - lastDarkCheck > 400) {
                lastDarkCheck = now;
                if (darkNow() != lastDark && CrolConfig.getRawInt("m3_mode", 0) == 0) {
                    rebuild();
                }
            }
        }
        if (!active) {
            return NONE;
        }
        int i = map.indexOfKey(key);
        return i >= 0 ? map.valueAt(i) : NONE;
    }

    public static Roles roles() {
        if (!ready) {
            rebuild();
        }
        return roles;
    }

    public static boolean isActive() {
        if (!ready) {
            rebuild();
        }
        return active;
    }

    /** Сбрасывает кэш после смены настроек. */
    public static void invalidate() {
        ready = false;
    }

    /** Форма углов пузырей из шкалы: 0 острые, 1 умеренные, 2 круглые, 3 максимально круглые. */
    public static void applyCornerStyle() {
        try {
            if (!CrolConfig.getRawBool("design_m3", true)) {
                return;
            }
            int style = CrolConfig.getRawInt("m3_corner", 3);
            int[] radii = {3, 9, 14, 17};
            int radius = radii[Math.max(0, Math.min(3, style))];
            if (SharedConfig.bubbleRadius != radius) {
                SharedConfig.bubbleRadius = radius;
                android.content.SharedPreferences.Editor e = MessagesController.getGlobalMainSettings().edit();
                e.putInt("bubbleRadius", radius);
                e.apply();
            }
        } catch (Throwable ignore) {
        }
    }
}
