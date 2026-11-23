package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stories;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * CrolGram: хранилище настроек и локальные модули (приватность, защита).
 * Всё работает локально, на серверы ничего не отправляется.
 */
public class CrolConfig {

    public static final String PREFS_NAME = "crolgram";

    public static final String[] GHOST_KEYS = {
            "hide_typing", "hide_online", "hide_read", "hide_voice_read", "hide_stories"
    };

    public interface Listener {
        void onChanged(String key);
    }

    private static volatile SharedPreferences prefs;
    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private static SharedPreferences p() {
        SharedPreferences s = prefs;
        if (s == null) {
            synchronized (CrolConfig.class) {
                s = prefs;
                if (s == null) {
                    s = ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                    prefs = s;
                }
            }
        }
        return s;
    }

    // ---------------------------------------------------------------- типизированные геттеры и сеттеры

    /** Украшения, которые отключает режим «почти как Telegram» (значения сохраняются). */
    private static final HashSet<String> PLAIN_BOOL = new HashSet<>(java.util.Arrays.asList(
            "gradient_names", "grad_replies", "grad_dialogs", "grad_profile", "grad_title", "grad_drawer",
            "name_glow", "grad_anim", "anim_typing"));
    private static final HashSet<String> PLAIN_INT = new HashSet<>(java.util.Arrays.asList(
            "bubble_style", "avatar_shape", "grad_text", "banner_mode"));

    /** Значение с учётом режима «почти как Telegram» и тихих часов. */
    public static boolean getBool(String key, boolean def) {
        try {
            if (PLAIN_BOOL.contains(key) && p().getBoolean("plain_mode", false)) {
                return false;
            }
            if (isGhostKey(key) && isQuietNow()) {
                return true;
            }
            return p().getBoolean(key, def);
        } catch (Throwable e) {
            return def;
        }
    }

    public static int getInt(String key, int def) {
        try {
            if (PLAIN_INT.contains(key) && p().getBoolean("plain_mode", false)) {
                return 0;
            }
            return p().getInt(key, def);
        } catch (Throwable e) {
            return def;
        }
    }

    /** Сохранённое значение без наложений (для экрана настроек). */
    public static boolean getRawBool(String key, boolean def) {
        try {
            return p().getBoolean(key, def);
        } catch (Throwable e) {
            return def;
        }
    }

    public static int getRawInt(String key, int def) {
        try {
            return p().getInt(key, def);
        } catch (Throwable e) {
            return def;
        }
    }

    private static boolean isGhostKey(String key) {
        for (String k : GHOST_KEYS) {
            if (k.equals(key)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- тихие часы

    private static int parseMinutes(String text, int def) {
        try {
            String[] a = text.trim().split("[:.]");
            int h = Integer.parseInt(a[0].trim());
            int m = a.length > 1 ? Integer.parseInt(a[1].trim()) : 0;
            if (h < 0 || h > 23 || m < 0 || m > 59) {
                return def;
            }
            return h * 60 + m;
        } catch (Throwable e) {
            return def;
        }
    }

    /** Сейчас действуют тихие часы (расписание включено и время попадает в интервал). */
    public static boolean isQuietNow() {
        try {
            SharedPreferences s = p();
            if (!s.getBoolean("quiet_on", false)) {
                return false;
            }
            int from = parseMinutes(s.getString("quiet_from", "22:00"), 22 * 60);
            int to = parseMinutes(s.getString("quiet_to", "07:00"), 7 * 60);
            java.util.Calendar c = java.util.Calendar.getInstance();
            int now = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE);
            if (from == to) {
                return false;
            }
            if (from < to) {
                return now >= from && now < to;
            }
            return now >= from || now < to;
        } catch (Throwable e) {
            return false;
        }
    }

    public static String getString(String key, String def) {
        try {
            return p().getString(key, def);
        } catch (Throwable e) {
            return def;
        }
    }

    public static void setBool(String key, boolean value) {
        p().edit().putBoolean(key, value).apply();
        notifyChanged(key);
    }

    public static void setInt(String key, int value) {
        p().edit().putInt(key, value).apply();
        notifyChanged(key);
    }

    public static void setString(String key, String value) {
        if (value == null) {
            p().edit().remove(key).apply();
        } else {
            p().edit().putString(key, value).apply();
        }
        notifyChanged(key);
    }

    public static void remove(String key) {
        p().edit().remove(key).apply();
        notifyChanged(key);
    }

    public static void addListener(Listener l) {
        if (l != null) {
            listeners.addIfAbsent(l);
        }
    }

    public static void removeListener(Listener l) {
        listeners.remove(l);
    }

    private static void notifyChanged(String key) {
        if (key == null || "plain_mode".equals(key) || "bubble_style".equals(key) || "avatar_shape".equals(key) || key.startsWith("grad") || "gradient_names".equals(key) || "name_glow".equals(key)) {
            CrolStyle.refreshUi();
        }
        for (Listener l : listeners) {
            try {
                l.onChanged(key);
            } catch (Throwable ignore) {
            }
        }
    }

    // ---------------------------------------------------------------- режим призрака

    public static boolean isGhost() {
        for (String k : GHOST_KEYS) {
            if (!getRawBool(k, false)) {
                return false;
            }
        }
        return true;
    }

    public static void setGhost(boolean value) {
        SharedPreferences.Editor e = p().edit();
        e.putBoolean("ghost_mode", value);
        for (String k : GHOST_KEYS) {
            e.putBoolean(k, value);
        }
        e.apply();
        notifyChanged("ghost_mode");
        log("ghost mode " + (value ? "on" : "off"));
    }

    // ---------------------------------------------------------------- перехват запросов

    /**
     * Вызывается в начале ConnectionsManager.sendRequestInternal.
     * Возвращает true, если запрос нужно НЕ отправлять.
     */
    public static boolean interceptRequest(TLObject object, RequestDelegate onComplete) {
        if (object == null) {
            return false;
        }
        try {
            if (object instanceof TL_account.updateStatus) {
                if (getBool("hide_online", false)) {
                    ((TL_account.updateStatus) object).offline = true;
                    count("offline");
                }
                return false;
            }
            if (object instanceof TLRPC.TL_messages_setTyping || object instanceof TLRPC.TL_messages_setEncryptedTyping) {
                if (getBool("hide_typing", false)) {
                    count("blocked typing");
                    if (onComplete != null) {
                        onComplete.run(new TLRPC.TL_boolTrue(), null);
                    }
                    return true;
                }
                return false;
            }
            if (object instanceof TLRPC.TL_messages_readHistory
                    || object instanceof TLRPC.TL_channels_readHistory
                    || object instanceof TLRPC.TL_messages_readEncryptedHistory) {
                if (getBool("hide_read", false)) {
                    count("blocked readHistory");
                    return true;
                }
                return false;
            }
            if (object instanceof TLRPC.TL_messages_readMessageContents
                    || object instanceof TLRPC.TL_channels_readMessageContents) {
                if (getBool("hide_voice_read", false)) {
                    count("blocked readContents");
                    return true;
                }
                return false;
            }
            if (object instanceof TL_stories.TL_stories_readStories) {
                if (getBool("hide_stories", false)) {
                    count("blocked readStories");
                    return true;
                }
                return false;
            }
            if (object instanceof TLRPC.TL_messages_getSponsoredMessages) {
                if (getBool("no_ads", false)) {
                    count("blocked sponsored");
                    if (onComplete != null) {
                        onComplete.run(new TLRPC.TL_messages_sponsoredMessagesEmpty(), null);
                    }
                    return true;
                }
                return false;
            }
        } catch (Throwable e) {
            logOnce("intercept_err", "intercept error: " + e);
        }
        return false;
    }

    // ---------------------------------------------------------------- запрет копирования

    public static boolean noForwardsBypass() {
        return getBool("no_forward_bypass", false);
    }

    // ---------------------------------------------------------------- анти-удаление

    private static volatile long ownDeleteUntil = 0;

    /** Вызывается, когда пользователь сам удаляет сообщения: на несколько секунд разрешаем обычное удаление. */
    public static void markOwnDelete() {
        ownDeleteUntil = System.currentTimeMillis() + 8000;
    }

    /** true, если входящее удаление нужно проигнорировать (сообщения остаются). */
    public static boolean shouldKeepDeleted() {
        return getBool("anti_delete", false) && System.currentTimeMillis() > ownDeleteUntil;
    }

    private static HashSet<String> deletedMarks;
    private static final Object marksLock = new Object();

    private static File marksFile() {
        return new File(ApplicationLoader.applicationContext.getFilesDir(), "crol_deleted.txt");
    }

    private static HashSet<String> marks() {
        synchronized (marksLock) {
            if (deletedMarks == null) {
                deletedMarks = new HashSet<>();
                File f = marksFile();
                if (f.exists()) {
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            if (!line.isEmpty()) {
                                deletedMarks.add(line);
                            }
                        }
                    } catch (Throwable e) {
                        logOnce("marks_load", "load marks failed: " + e);
                    }
                }
            }
            return deletedMarks;
        }
    }

    private static String markKey(int account, long channelId, int mid) {
        return channelId != 0 ? ("a" + account + "_c" + channelId + "_" + mid) : ("a" + account + "_m" + mid);
    }

    /** Помечает сообщения как удалённые собеседником. channelId = 0 для личных чатов и обычных групп. */
    public static void markDeleted(int account, long channelId, ArrayList<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        final StringBuilder out = new StringBuilder();
        synchronized (marksLock) {
            HashSet<String> set = marks();
            for (int i = 0; i < ids.size(); i++) {
                String key = markKey(account, channelId, ids.get(i));
                if (set.add(key)) {
                    out.append(key).append('\n');
                }
            }
        }
        if (out.length() > 0) {
            count("kept deleted");
            Utilities.globalQueue.postRunnable(() -> {
                try (FileOutputStream fos = new FileOutputStream(marksFile(), true)) {
                    fos.write(out.toString().getBytes("UTF-8"));
                } catch (Throwable e) {
                    logOnce("marks_save", "save marks failed: " + e);
                }
            });
        }
    }

    public static boolean isDeletedMarked(MessageObject obj) {
        if (obj == null || obj.messageOwner == null || obj.getId() <= 0) {
            return false;
        }
        if (!getBool("deleted_mark", true)) {
            return false;
        }
        HashSet<String> set;
        synchronized (marksLock) {
            set = marks();
            if (set.isEmpty()) {
                return false;
            }
            long channelId = 0;
            if (obj.messageOwner.peer_id != null) {
                channelId = obj.messageOwner.peer_id.channel_id;
            }
            return set.contains(markKey(obj.currentAccount, channelId, obj.getId()));
        }
    }

    // ---------------------------------------------------------------- анти-изменение

    private static LinkedHashMap<String, ArrayList<String>> edits;
    private static final Object editsLock = new Object();
    private static final int EDITS_MAX_KEYS = 800;
    private static final int EDITS_MAX_VERSIONS = 20;

    private static File editsFile() {
        return new File(ApplicationLoader.applicationContext.getFilesDir(), "crol_edits.json");
    }

    private static LinkedHashMap<String, ArrayList<String>> edits() {
        synchronized (editsLock) {
            if (edits == null) {
                edits = new LinkedHashMap<>();
                File f = editsFile();
                if (f.exists()) {
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
                        StringBuilder sb = new StringBuilder();
                        char[] buf = new char[4096];
                        int n;
                        while ((n = r.read(buf)) > 0) {
                            sb.append(buf, 0, n);
                        }
                        JSONObject o = new JSONObject(sb.toString());
                        Iterator<String> it = o.keys();
                        while (it.hasNext()) {
                            String k = it.next();
                            JSONArray a = o.getJSONArray(k);
                            ArrayList<String> list = new ArrayList<>();
                            for (int i = 0; i < a.length(); i++) {
                                list.add(a.getString(i));
                            }
                            edits.put(k, list);
                        }
                    } catch (Throwable e) {
                        logOnce("edits_load", "load edits failed: " + e);
                    }
                }
            }
            return edits;
        }
    }

    private static String editKey(int account, long dialogId, int mid) {
        return account + "_" + dialogId + "_" + mid;
    }

    /** Сохраняет прежний текст сообщения перед применением правки. Вызывать вне главного потока. */
    public static void trackEdit(int account, TLRPC.Message newMessage) {
        try {
            if (!getBool("anti_edit", false) || newMessage == null || newMessage.id <= 0) {
                return;
            }
            long dialogId = MessageObject.getDialogId(newMessage);
            TLRPC.Message old = MessagesStorage.getInstance(account).getMessage(dialogId, newMessage.id);
            if (old == null || old.message == null) {
                return;
            }
            String oldText = old.message;
            String newText = newMessage.message == null ? "" : newMessage.message;
            if (oldText.isEmpty() || oldText.equals(newText)) {
                return;
            }
            if (oldText.length() > 4000) {
                oldText = oldText.substring(0, 4000);
            }
            String key = editKey(account, dialogId, newMessage.id);
            String json;
            synchronized (editsLock) {
                LinkedHashMap<String, ArrayList<String>> map = edits();
                ArrayList<String> list = map.remove(key);
                if (list == null) {
                    list = new ArrayList<>();
                }
                if (list.isEmpty() || !list.get(list.size() - 1).equals(oldText)) {
                    list.add(oldText);
                }
                while (list.size() > EDITS_MAX_VERSIONS) {
                    list.remove(0);
                }
                map.put(key, list);
                while (map.size() > EDITS_MAX_KEYS) {
                    Iterator<Map.Entry<String, ArrayList<String>>> it = map.entrySet().iterator();
                    it.next();
                    it.remove();
                }
                JSONObject o = new JSONObject();
                for (Map.Entry<String, ArrayList<String>> en : map.entrySet()) {
                    JSONArray a = new JSONArray();
                    for (String s : en.getValue()) {
                        a.put(s);
                    }
                    o.put(en.getKey(), a);
                }
                json = o.toString();
            }
            count("saved edit");
            final String toWrite = json;
            Utilities.globalQueue.postRunnable(() -> {
                try (FileOutputStream fos = new FileOutputStream(editsFile(), false)) {
                    fos.write(toWrite.getBytes("UTF-8"));
                } catch (Throwable e) {
                    logOnce("edits_save", "save edits failed: " + e);
                }
            });
        } catch (Throwable e) {
            logOnce("track_edit", "trackEdit error: " + e);
        }
    }

    public static ArrayList<String> getEditHistory(int account, long dialogId, int mid) {
        synchronized (editsLock) {
            ArrayList<String> list = edits().get(editKey(account, dialogId, mid));
            return list == null ? new ArrayList<>() : new ArrayList<>(list);
        }
    }

    public static boolean hasEditHistory(int account, long dialogId, int mid) {
        if (!getBool("anti_edit", false)) {
            return false;
        }
        synchronized (editsLock) {
            ArrayList<String> list = edits().get(editKey(account, dialogId, mid));
            return list != null && !list.isEmpty();
        }
    }

    // ---------------------------------------------------------------- резервная копия

    public static String exportJson() {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, ?> en : p().getAll().entrySet()) {
                Object v = en.getValue();
                if (v instanceof Boolean || v instanceof Integer || v instanceof Long || v instanceof String || v instanceof Float) {
                    o.put(en.getKey(), v);
                }
            }
            return o.toString(2);
        } catch (Throwable e) {
            return "{}";
        }
    }

    /** Возвращает число применённых значений или -1 при ошибке разбора. */
    public static int importJson(String text) {
        try {
            JSONObject o = new JSONObject(text.trim());
            SharedPreferences.Editor e = p().edit();
            int n = 0;
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                Object v = o.get(k);
                if (v instanceof Boolean) {
                    e.putBoolean(k, (Boolean) v);
                } else if (v instanceof Integer) {
                    e.putInt(k, (Integer) v);
                } else if (v instanceof Long) {
                    e.putLong(k, (Long) v);
                } else if (v instanceof Double) {
                    e.putFloat(k, ((Double) v).floatValue());
                } else if (v instanceof String) {
                    e.putString(k, (String) v);
                } else {
                    continue;
                }
                n++;
            }
            e.apply();
            notifyChanged(null);
            return n;
        } catch (Throwable e) {
            return -1;
        }
    }

    // ---------------------------------------------------------------- журнал отладки

    private static final ArrayList<String> logBuf = new ArrayList<>();
    private static final HashSet<String> logOnceKeys = new HashSet<>();
    private static final LinkedHashMap<String, Integer> counters = new LinkedHashMap<>();

    public static void log(String msg) {
        synchronized (logBuf) {
            logBuf.add(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date()) + " " + msg);
            while (logBuf.size() > 300) {
                logBuf.remove(0);
            }
        }
    }

    public static void logOnce(String key, String msg) {
        synchronized (logBuf) {
            if (!logOnceKeys.add(key)) {
                return;
            }
        }
        log(msg);
    }

    public static void count(String name) {
        synchronized (logBuf) {
            Integer c = counters.get(name);
            counters.put(name, c == null ? 1 : c + 1);
        }
    }

    public static String dumpLog() {
        StringBuilder sb = new StringBuilder();
        synchronized (logBuf) {
            sb.append("CrolGram log\n");
            sb.append("api_id=").append(BuildVars.APP_ID).append(" debug=").append(BuildVars.DEBUG_VERSION).append('\n');
            if (!counters.isEmpty()) {
                sb.append("-- counters --\n");
                for (Map.Entry<String, Integer> en : counters.entrySet()) {
                    sb.append(en.getKey()).append(": ").append(en.getValue()).append('\n');
                }
            }
            sb.append("-- events --\n");
            for (String s : logBuf) {
                sb.append(s).append('\n');
            }
        }
        return sb.toString();
    }

    public static void clearLog() {
        synchronized (logBuf) {
            logBuf.clear();
            logOnceKeys.clear();
            counters.clear();
        }
    }
}
