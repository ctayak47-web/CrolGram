package org.telegram.messenger;

import android.text.TextUtils;

import org.telegram.tgnet.ConnectionsManager;
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

    // ------------------------------------------------------------ значки, эмодзи-статус, цвета

    private static TLRPC.User origUser;
    private static boolean oVerified, oPremium, oScam, oFake;
    private static TLRPC.EmojiStatus oStatus;
    private static TLRPC.PeerColor oColor, oProfileColor;

    private static long parseLong(String v) {
        try {
            return TextUtils.isEmpty(v) ? 0 : Long.parseLong(v.trim());
        } catch (Throwable e) {
            return 0;
        }
    }

    private static int parseInt(String v, int def) {
        try {
            return TextUtils.isEmpty(v) ? def : Integer.parseInt(v.trim());
        } catch (Throwable e) {
            return def;
        }
    }

    /** Применяет локальные значки, эмодзи-статус и цвета к СВОЕМУ пользователю. Серверу ничего не уходит. */
    public static void applySelf(TLRPC.User u) {
        if (!isSelf(u)) {
            return;
        }
        try {
            if (origUser != u) {
                origUser = u;
                oVerified = u.verified;
                oPremium = u.premium;
                oScam = u.scam;
                oFake = u.fake;
                oStatus = u.emoji_status;
                oColor = u.color;
                oProfileColor = u.profile_color;
            }
            int badge = CrolConfig.getInt("badge_mode", 0);
            u.verified = badge == 1 || (badge == 0 && oVerified);
            u.premium = badge == 2 || (badge == 0 && oPremium);
            u.scam = badge == 3 || (badge == 0 && oScam);
            u.fake = badge == 4 || (badge == 0 && oFake);
            if (badge == 3 || badge == 4) {
                u.verified = false;
            }

            long status = parseLong(CrolConfig.getString("emoji_status_id", ""));
            if (status != 0) {
                TLRPC.TL_emojiStatus st = new TLRPC.TL_emojiStatus();
                st.document_id = status;
                u.emoji_status = st;
            } else {
                u.emoji_status = oStatus;
            }

            int nameColor = parseInt(CrolConfig.getString("name_color_id", ""), -1);
            if (nameColor >= 0 && nameColor <= 20) {
                TLRPC.TL_peerColor c = new TLRPC.TL_peerColor();
                c.flags = 1;
                c.color = nameColor;
                u.color = c;
            } else {
                u.color = oColor;
            }

            int profColor = parseInt(CrolConfig.getString("profile_color_id", ""), -1);
            long pattern = parseLong(CrolConfig.getString("profile_emoji_id", ""));
            if ((profColor >= 0 && profColor <= 21) || pattern != 0) {
                TLRPC.TL_peerColor c = new TLRPC.TL_peerColor();
                if (profColor >= 0 && profColor <= 21) {
                    c.flags |= 1;
                    c.color = profColor;
                } else {
                    c.color = -1;
                }
                if (pattern != 0) {
                    c.flags |= 2;
                    c.background_emoji_id = pattern;
                }
                u.profile_color = c;
            } else {
                u.profile_color = oProfileColor;
            }
        } catch (Throwable e) {
            CrolConfig.log("applySelf: " + e);
        }
    }

    /** Применить настройки сейчас и обновить интерфейс. */
    public static void refreshSelf() {
        try {
            int account = UserConfig.selectedAccount;
            applySelf(UserConfig.getInstance(account).getCurrentUser());
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
        } catch (Throwable e) {
            CrolConfig.log("refreshSelf: " + e);
        }
    }

    // ------------------------------------------------------------ ID и дата-центр

    public static String idText(TLRPC.User user) {
        if (user == null) {
            return "";
        }
        if (user.self) {
            String v = CrolConfig.getString("id_custom", "");
            if (!TextUtils.isEmpty(v)) {
                return v.trim();
            }
        }
        return String.valueOf(user.id);
    }

    private static final String[] DC_PLACE = {
            "", "Miami, US", "Amsterdam, NL", "Miami, US", "Amsterdam, NL", "Singapore, SG"
    };

    private static int photoDc(TLRPC.Photo p) {
        return p != null ? p.dc_id : 0;
    }

    /** Например «DC2, Amsterdam, NL». Если дата-центр неизвестен: «DC неизвестен». */
    public static String dcText(TLRPC.User user, TLRPC.UserFull full) {
        int dc = 0;
        try {
            if (user != null && user.self) {
                dc = ConnectionsManager.getInstance(UserConfig.selectedAccount).getCurrentDatacenterId();
            }
            if (dc < 1 && user != null && user.photo != null) {
                dc = user.photo.dc_id;
            }
            if (dc < 1 && full != null) {
                dc = photoDc(full.profile_photo);
                if (dc < 1) {
                    dc = photoDc(full.personal_photo);
                }
                if (dc < 1) {
                    dc = photoDc(full.fallback_photo);
                }
            }
        } catch (Throwable e) {
            CrolConfig.log("dcText: " + e);
        }
        if (dc < 1 || dc >= DC_PLACE.length) {
            return "DC неизвестен";
        }
        return "DC" + dc + ", " + DC_PLACE[dc];
    }
}
