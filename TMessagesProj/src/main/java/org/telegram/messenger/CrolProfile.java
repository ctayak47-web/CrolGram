package org.telegram.messenger;

import android.text.TextUtils;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;
import org.telegram.tgnet.tl.TL_stars;

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
    private static java.util.ArrayList<TLRPC.TL_username> oUsernames;

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
                oUsernames = u.usernames;
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

            java.util.ArrayList<String> extra = extraUsernames();
            if (!extra.isEmpty()) {
                java.util.ArrayList<TLRPC.TL_username> list = new java.util.ArrayList<>();
                String main = customUsername(u);
                if (TextUtils.isEmpty(main)) {
                    main = u.username;
                }
                if (!TextUtils.isEmpty(main)) {
                    list.add(makeUsername(main));
                }
                for (String name : extra) {
                    if (!name.equalsIgnoreCase(main)) {
                        list.add(makeUsername(name));
                    }
                }
                u.usernames = list;
            } else {
                u.usernames = oUsernames != null ? oUsernames : new java.util.ArrayList<>();
            }
        } catch (Throwable e) {
            CrolConfig.log("applySelf: " + e);
        }
    }

    private static TLRPC.TL_username makeUsername(String name) {
        TLRPC.TL_username tu = new TLRPC.TL_username();
        tu.username = name;
        tu.active = true;
        tu.editable = false;
        tu.flags = 2;
        return tu;
    }

    /** Дополнительные юзернеймы из настройки usernames_extra (через запятую, пробел или «;»). */
    public static java.util.ArrayList<String> extraUsernames() {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        String raw = CrolConfig.getString("usernames_extra", "");
        if (TextUtils.isEmpty(raw)) {
            return out;
        }
        for (String part : raw.replace(";", ",").replace(" ", ",").split(",")) {
            String v = part.trim();
            if (v.startsWith("@")) {
                v = v.substring(1);
            }
            if (!v.isEmpty()) {
                out.add(v);
            }
        }
        return out;
    }

    private static TLRPC.UserFull origFull;
    private static TL_stars.Tl_starsRating oRating;
    private static TL_bots.BotVerification oVerification;

    /** Локальный уровень профиля и иконка/текст верификации в СВОЁМ UserFull. Серверу ничего не уходит. */
    public static void applyFull(TLRPC.UserFull full) {
        if (full == null) {
            return;
        }
        try {
            long selfId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            if (full.id != selfId) {
                return;
            }
            CrolGifts.prefetch();
            if (origFull != full) {
                origFull = full;
                oRating = full.stars_rating;
                oVerification = full.bot_verification;
            }
            String lvl = CrolConfig.getString("level_custom", "").trim();
            if (!lvl.isEmpty() && TextUtils.isDigitsOnly(lvl) && lvl.length() < 5) {
                int level = Integer.parseInt(lvl);
                TL_stars.Tl_starsRating r = new TL_stars.Tl_starsRating();
                long cur = 25L * level * level;
                long next = 25L * (level + 1) * (level + 1);
                r.flags = 1;
                r.level = level;
                r.current_level_stars = cur;
                r.stars = cur + (next - cur) / 2;
                r.next_level_stars = next;
                full.stars_rating = r;
            } else {
                full.stars_rating = oRating;
            }
            long icon = parseLong(CrolConfig.getString("verif_icon_id", ""));
            if (icon != 0) {
                TL_bots.BotVerification bv = new TL_bots.BotVerification();
                bv.bot_id = selfId;
                bv.icon = icon;
                bv.description = new TLRPC.TL_textWithEntities();
                bv.description.text = CrolConfig.getString("verif_desc", "");
                full.bot_verification = bv;
            } else {
                full.bot_verification = oVerification;
            }
        } catch (Throwable e) {
            CrolConfig.log("applyFull: " + e);
        }
    }

    /** Применить настройки сейчас и обновить интерфейс. */
    public static void refreshSelf() {
        try {
            int account = UserConfig.selectedAccount;
            applySelf(UserConfig.getInstance(account).getCurrentUser());
            applyFull(MessagesController.getInstance(account).getUserFull(UserConfig.getInstance(account).getClientUserId()));
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
