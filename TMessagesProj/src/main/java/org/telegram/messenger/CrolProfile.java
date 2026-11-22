package org.telegram.messenger;

import android.text.TextUtils;

import org.telegram.tgnet.TLRPC;

/**
 * CrolGram: локальная подмена отображения СВОЕГО профиля (этап 4).
 * Только на этом устройстве: на серверы ничего не отправляется.
 */
public class CrolProfile {

    private static boolean isSelf(TLRPC.User user) {
        return user != null && user.self;
    }

    /** Своё имя или null, если подмена не задана. */
    public static String customName(TLRPC.User user) {
        if (!isSelf(user)) {
            return null;
        }
        String v = CrolConfig.getString("name_custom", "");
        return TextUtils.isEmpty(v) ? null : v;
    }

    public static String customUsername(TLRPC.User user) {
        if (!isSelf(user)) {
            return null;
        }
        String v = CrolConfig.getString("username_custom", "");
        if (TextUtils.isEmpty(v)) {
            return null;
        }
        return v.startsWith("@") ? v.substring(1) : v;
    }

    /** Подмена показа собственного номера. Возвращает null, если номер не свой или подмена выключена. */
    public static String customPhone(String formattedInput) {
        try {
            int mode = CrolConfig.getInt("phone_mode", 0);
            if (mode == 0 || formattedInput == null) {
                return null;
            }
            TLRPC.User me = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
            if (me == null || TextUtils.isEmpty(me.phone)) {
                return null;
            }
            String digits = formattedInput.replaceAll("[^0-9]", "");
            if (!digits.equals(me.phone)) {
                return null;
            }
            String custom = CrolConfig.getString("phone_custom", "");
            if (mode == 1) {
                String d = custom.replaceAll("[^0-9]", "");
                if (d.length() > 8) {
                    d = d.substring(0, 8);
                }
                while (d.length() < 8) {
                    d = d + "0";
                }
                return "+888 " + d.substring(0, 4) + " " + d.substring(4);
            }
            return TextUtils.isEmpty(custom) ? null : custom;
        } catch (Throwable e) {
            return null;
        }
    }
}
