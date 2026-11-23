package org.telegram.messenger;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.Stars.StarsController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CrolGram: локальные NFT-подарки, служебные сообщения, команды и баланс.
 * Только на этом устройстве: на серверы уходит лишь запрос на чтение данных подарка.
 */
public class CrolGifts {

    private static final Pattern SLUG = Pattern.compile("([A-Za-z0-9_]+-\\d+)");
    private static final HashMap<String, TL_stars.StarGift> cache = new HashMap<>();
    private static final HashSet<String> pending = new HashSet<>();
    private static final HashMap<String, TL_stars.TL_savedStarGift> fakes = new HashMap<>();

    public interface GiftDone {
        void run(TL_stars.StarGift gift);
    }

    public static ArrayList<String> parseSlugs(String text) {
        ArrayList<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(text)) {
            return out;
        }
        Matcher m = SLUG.matcher(text);
        while (m.find()) {
            String s = m.group(1);
            if (!out.contains(s)) {
                out.add(s);
            }
        }
        return out;
    }

    // ------------------------------------------------------------ подарки

    public static void fetchGift(final int account, final String slug, final GiftDone done) {
        TL_stars.StarGift g = cache.get(slug);
        if (g != null) {
            done.run(g);
            return;
        }
        TL_stars.getUniqueStarGift req = new TL_stars.getUniqueStarGift();
        req.slug = slug;
        ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            TL_stars.StarGift gift = null;
            if (res instanceof TL_stars.TL_payments_uniqueStarGift) {
                TL_stars.TL_payments_uniqueStarGift r = (TL_stars.TL_payments_uniqueStarGift) res;
                MessagesController.getInstance(account).putUsers(r.users, false);
                MessagesController.getInstance(account).putChats(r.chats, false);
                gift = r.gift;
                if (gift != null) {
                    cache.put(slug, gift);
                }
            } else {
                CrolConfig.log("gift " + slug + ": запрос не удался " + (err != null ? err.text : ""));
            }
            done.run(gift);
        }));
    }

    /** Подгружает подарки из настройки nft_gifts и обновляет список профиля. */
    public static void prefetch() {
        try {
            final int account = UserConfig.selectedAccount;
            for (final String slug : parseSlugs(CrolConfig.getString("nft_gifts", ""))) {
                if (cache.containsKey(slug) || !pending.add(slug)) {
                    continue;
                }
                fetchGift(account, slug, g -> {
                    pending.remove(slug);
                    if (g != null) {
                        refreshProfileList();
                    }
                });
            }
        } catch (Throwable e) {
            CrolConfig.log("prefetch: " + e);
        }
    }

    private static TL_stars.TL_savedStarGift makeSaved(String slug, TL_stars.StarGift gift) {
        TL_stars.TL_savedStarGift sg = fakes.get(slug);
        if (sg == null) {
            sg = new TL_stars.TL_savedStarGift();
            sg.gift = gift;
            sg.date = (int) (System.currentTimeMillis() / 1000);
            sg.flags = 1;
            sg.name_hidden = true;
            fakes.put(slug, sg);
        }
        sg.pinned_to_top = CrolConfig.getBool("gifts_pinned", true);
        return sg;
    }

    /** Подмешивает локальные NFT в список подарков своего профиля. Вызывается из StarsController.GiftsList. */
    public static void ensure(StarsController.GiftsList list) {
        try {
            if (list == null || list.isCollection) {
                return;
            }
            long self = UserConfig.getInstance(list.currentAccount).getClientUserId();
            if (list.dialogId != 0 && list.dialogId != self) {
                return;
            }
            ArrayList<String> slugs = parseSlugs(CrolConfig.getString("nft_gifts", ""));
            if (slugs.isEmpty()) {
                return;
            }
            boolean added = false;
            for (int i = slugs.size() - 1; i >= 0; i--) {
                TL_stars.StarGift gift = cache.get(slugs.get(i));
                if (gift == null) {
                    continue;
                }
                TL_stars.TL_savedStarGift sg = makeSaved(slugs.get(i), gift);
                if (!list.gifts.contains(sg)) {
                    list.gifts.add(0, sg);
                    list.totalCount++;
                    added = true;
                }
            }
            if (added) {
                CrolConfig.count("gifts.profile");
            }
        } catch (Throwable e) {
            CrolConfig.log("ensure gifts: " + e);
        }
    }

    public static void refreshProfileList() {
        try {
            int account = UserConfig.selectedAccount;
            long self = UserConfig.getInstance(account).getClientUserId();
            StarsController sc = StarsController.getInstance(account);
            for (long id : new long[]{self, 0}) {
                StarsController.GiftsList list = sc.getProfileGiftsList(id, false);
                if (list != null) {
                    ensure(list);
                    list.notifyUpdate();
                }
            }
        } catch (Throwable e) {
            CrolConfig.log("refresh gifts: " + e);
        }
    }

    /** Кнопка «Забыть локальные подарки». */
    public static void forgetLocal() {
        CrolConfig.remove("nft_gifts");
        CrolConfig.remove("local_msgs");
        fakes.clear();
        try {
            int account = UserConfig.selectedAccount;
            StarsController sc = StarsController.getInstance(account);
            long self = UserConfig.getInstance(account).getClientUserId();
            sc.invalidateProfileGifts(self);
        } catch (Throwable e) {
            CrolConfig.log("forget: " + e);
        }
    }

    // ------------------------------------------------------------ локальные сообщения

    private static MessageObject localMessage(int account, long dialogId, TLRPC.MessageAction action, int date) {
        UserConfig uc = UserConfig.getInstance(account);
        TLRPC.TL_messageService msg = new TLRPC.TL_messageService();
        int mid = uc.getNewMessageId();
        msg.id = mid;
        msg.local_id = mid;
        msg.out = true;
        msg.unread = false;
        msg.date = date > 0 ? date : (int) (System.currentTimeMillis() / 1000);
        msg.dialog_id = dialogId;
        TLRPC.TL_peerUser from = new TLRPC.TL_peerUser();
        from.user_id = uc.getClientUserId();
        msg.from_id = from;
        msg.peer_id = MessagesController.getInstance(account).getPeer(dialogId);
        msg.action = action;
        return new MessageObject(account, msg, true, true);
    }

    private static TLRPC.TL_messageActionCustomAction note(String text) {
        TLRPC.TL_messageActionCustomAction a = new TLRPC.TL_messageActionCustomAction();
        a.message = text;
        return a;
    }

    private static void post(final int account, final long dialogId, final TLRPC.MessageAction[] actions, final int date) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                ArrayList<MessageObject> list = new ArrayList<>();
                for (TLRPC.MessageAction a : actions) {
                    list.add(localMessage(account, dialogId, a, date));
                }
                MessagesController.getInstance(account).updateInterfaceWithMessages(dialogId, list, 0);
            } catch (Throwable e) {
                CrolConfig.log("post local: " + e);
            }
        });
    }

    public static void insertGift(int account, long dialogId, TL_stars.StarGift gift, int date) {
        TLRPC.TL_messageActionStarGiftUnique act = new TLRPC.TL_messageActionStarGiftUnique();
        act.gift = gift;
        act.transferred = true;
        post(account, dialogId, new TLRPC.MessageAction[]{act, note("Локальный NFT-подарок · видно только вам")}, date);
    }

    public static void insertGram(int account, long dialogId, double amount, int date) {
        TLRPC.TL_messageActionGiftTon act = new TLRPC.TL_messageActionGiftTon();
        act.cryptoCurrency = "TON";
        act.cryptoAmount = (long) (amount * 1e9);
        act.currency = "USD";
        act.amount = Math.round(amount * 120);
        post(account, dialogId, new TLRPC.MessageAction[]{act, note("Локальное сообщение · видно только вам")}, date);
    }

    public static void insertPrem(int account, long dialogId, int months, int date) {
        TLRPC.TL_messageActionGiftPremium act = new TLRPC.TL_messageActionGiftPremium();
        act.currency = "USD";
        act.amount = months == 12 ? 4499 : months == 6 ? 2499 : 1499;
        act.months = months;
        act.days = months * 30;
        post(account, dialogId, new TLRPC.MessageAction[]{act, note("Локальное сообщение · видно только вам")}, date);
    }

    // ------------------------------------------------------------ память локальных сообщений

    private static void remember(long dialogId, String kind, String value) {
        try {
            JSONArray arr = new JSONArray(CrolConfig.getString("local_msgs", "[]"));
            JSONObject o = new JSONObject();
            o.put("d", dialogId);
            o.put("k", kind);
            o.put("v", value);
            o.put("t", (int) (System.currentTimeMillis() / 1000));
            arr.put(o);
            JSONArray cut = new JSONArray();
            for (int i = Math.max(0, arr.length() - 60); i < arr.length(); i++) {
                cut.put(arr.get(i));
            }
            CrolConfig.setString("local_msgs", cut.toString());
        } catch (Throwable e) {
            CrolConfig.log("remember: " + e);
        }
    }

    private static final HashMap<Long, Long> lastReplay = new HashMap<>();

    /** Возвращает локальные сообщения в чат при его открытии. */
    public static void replay(final int account, final long dialogId) {
        try {
            long now = System.currentTimeMillis();
            Long last = lastReplay.get(dialogId);
            if (last != null && now - last < 3000) {
                return;
            }
            JSONArray arr = new JSONArray(CrolConfig.getString("local_msgs", "[]"));
            ArrayList<JSONObject> mine = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.optLong("d") == dialogId) {
                    mine.add(o);
                }
            }
            if (mine.isEmpty()) {
                return;
            }
            lastReplay.put(dialogId, now);
            for (int i = Math.max(0, mine.size() - 8); i < mine.size(); i++) {
                JSONObject o = mine.get(i);
                String k = o.optString("k");
                String v = o.optString("v");
                final int t = o.optInt("t");
                if ("gram".equals(k)) {
                    insertGram(account, dialogId, Double.parseDouble(v), t);
                } else if ("prem".equals(k)) {
                    insertPrem(account, dialogId, Integer.parseInt(v), t);
                } else if ("nft".equals(k)) {
                    fetchGift(account, v, g -> {
                        if (g != null) {
                            insertGift(account, dialogId, g, t);
                        }
                    });
                }
            }
        } catch (Throwable e) {
            CrolConfig.log("replay: " + e);
        }
    }

    // ------------------------------------------------------------ локальные ники

    public static String localName(long userId) {
        try {
            String raw = CrolConfig.getString("local_names", "");
            if (TextUtils.isEmpty(raw)) {
                return null;
            }
            String v = new JSONObject(raw).optString(String.valueOf(userId), "");
            return TextUtils.isEmpty(v) ? null : v;
        } catch (Throwable e) {
            return null;
        }
    }

    private static void setLocalName(long userId, String name) {
        try {
            String raw = CrolConfig.getString("local_names", "");
            JSONObject o = TextUtils.isEmpty(raw) ? new JSONObject() : new JSONObject(raw);
            if (TextUtils.isEmpty(name)) {
                o.remove(String.valueOf(userId));
            } else {
                o.put(String.valueOf(userId), name);
            }
            CrolConfig.setString("local_names", o.toString());
        } catch (Throwable e) {
            CrolConfig.log("local name: " + e);
        }
    }

    // ------------------------------------------------------------ команды

    private static void toast(String text) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                android.widget.Toast.makeText(ApplicationLoader.applicationContext, text, android.widget.Toast.LENGTH_SHORT).show();
            } catch (Throwable ignore) {
            }
        });
    }

    private static String lastCmd;
    private static long lastCmdTime;

    /** Перехват команд в момент отправки. Возвращает true, если сообщение на сервер НЕ уходит. */
    public static boolean interceptCommand(final int account, String text, final long dialogId) {
        try {
            if (!CrolConfig.getBool("gift_cmd", false) || text == null) {
                return false;
            }
            String s = text.trim();
            if (!s.startsWith(".")) {
                return false;
            }
            String[] parts = s.split("\\s+", 2);
            String cmd = parts[0].toLowerCase();
            String arg = parts.length > 1 ? parts[1].trim() : "";
            boolean known = cmd.equals(".призрак") || cmd.equals(".ghost") || cmd.equals(".нфт") || cmd.equals(".nft")
                    || cmd.equals(".грам") || cmd.equals(".gram") || cmd.equals(".ник") || cmd.equals(".nick")
                    || cmd.matches("^\\.(прем|prem)(3|6|12)?$");
            if (!known) {
                return false;
            }
            long now = System.currentTimeMillis();
            if (s.equals(lastCmd) && now - lastCmdTime < 1500) {
                return true;
            }
            lastCmd = s;
            lastCmdTime = now;

            if (cmd.equals(".призрак") || cmd.equals(".ghost")) {
                boolean on = !CrolConfig.isGhost();
                CrolConfig.setGhost(on);
                toast(on ? "Режим призрака включён" : "Режим призрака выключен");
                return true;
            }
            if (cmd.equals(".нфт") || cmd.equals(".nft")) {
                ArrayList<String> slugs = parseSlugs(arg);
                if (slugs.isEmpty() || dialogId == 0) {
                    toast("Формат: .нфт t.me/nft/Название-123");
                    return true;
                }
                final String slug = slugs.get(0);
                fetchGift(account, slug, g -> {
                    if (g == null) {
                        toast("Подарок не найден");
                    } else {
                        remember(dialogId, "nft", slug);
                        insertGift(account, dialogId, g, 0);
                    }
                });
                return true;
            }
            if (cmd.equals(".грам") || cmd.equals(".gram")) {
                Matcher m = Pattern.compile("\\d+(?:[.,]\\d+)?").matcher(arg);
                if (!m.find() || dialogId == 0) {
                    toast("Формат: .грам 100");
                    return true;
                }
                double amount = Double.parseDouble(m.group().replace(',', '.'));
                remember(dialogId, "gram", String.valueOf(amount));
                insertGram(account, dialogId, amount, 0);
                return true;
            }
            if (cmd.startsWith(".прем") || cmd.startsWith(".prem")) {
                String months = cmd.replaceAll("[^0-9]", "");
                if (months.isEmpty() && (arg.equals("3") || arg.equals("6") || arg.equals("12"))) {
                    months = arg;
                }
                if (months.isEmpty() || dialogId == 0) {
                    toast("Формат: .прем3, .прем6 или .прем12");
                    return true;
                }
                remember(dialogId, "prem", months);
                insertPrem(account, dialogId, Integer.parseInt(months), 0);
                return true;
            }
            if (cmd.equals(".ник") || cmd.equals(".nick")) {
                if (dialogId <= 0) {
                    toast(".ник работает в личных чатах");
                    return true;
                }
                setLocalName(dialogId, arg);
                toast(arg.isEmpty() ? "Локальный ник убран" : "Локальный ник: " + arg);
                AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL));
                return true;
            }
        } catch (Throwable e) {
            CrolConfig.log("command: " + e);
        }
        return false;
    }

    // ------------------------------------------------------------ баланс

    /** Подмена показываемого баланса звёзд / GRAM. Покупки всё равно проверяются сервером. */
    public static TL_stars.StarsAmount balance(boolean ton, TL_stars.StarsAmount orig) {
        try {
            String raw = CrolConfig.getString(ton ? "gram_balance" : "stars_balance", "").trim();
            String digits = raw.replaceAll("[^0-9.,]", "").replace(',', '.');
            if (digits.isEmpty()) {
                return orig;
            }
            double v = Double.parseDouble(digits);
            if (ton) {
                TL_stars.TL_starsTonAmount a = new TL_stars.TL_starsTonAmount();
                a.amount = (long) (v * 1e9);
                a.nanos = 0;
                return a;
            }
            return TL_stars.StarsAmount.ofStars((long) v);
        } catch (Throwable e) {
            return orig;
        }
    }
}
