package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * CrolGram: профили оформления. Набор косметических настроек под именем: переключается в один тап,
 * экспортируется и импортируется в JSON. Все значения только локальные.
 */
public class CrolPresets {

    public static final String[] BUILTIN = {"Кислота", "Неон", "Лес", "Закат", "Минимализм"};

    /** Ключи, которые входят в профиль оформления. */
    public static final String[] KEYS = {
            "bubble_style", "avatar_shape", "gradient_names", "grad_me", "grad_me_custom", "grad_others",
            "grad_others_custom", "grad_replies", "grad_dialogs", "grad_profile", "grad_title", "grad_drawer",
            "grad_text", "name_glow", "grad_anim", "anim_typing", "banner_mode", "banner_grad"
    };

    private static SharedPreferences p() {
        return ApplicationLoader.applicationContext.getSharedPreferences(CrolConfig.PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- встроенные

    private static JSONObject builtin(String name) throws Exception {
        JSONObject o = new JSONObject();
        int custom = CrolStyle.PRESETS.length;
        switch (name) {
            case "Кислота":
                o.put("gradient_names", true).put("grad_me", 13).put("grad_others", custom + 1)
                        .put("grad_dialogs", true).put("grad_replies", true).put("bubble_style", 0);
                break;
            case "Неон":
                o.put("gradient_names", true).put("grad_me", 7).put("grad_others", 10)
                        .put("grad_dialogs", true).put("grad_replies", true).put("name_glow", true)
                        .put("bubble_style", 4).put("avatar_shape", 1);
                break;
            case "Лес":
                o.put("gradient_names", true).put("grad_me", custom).put("grad_me_custom", "#1B9E5A,#0B5D2E")
                        .put("grad_others", custom).put("grad_others_custom", "#2E7D32,#1B5E20")
                        .put("grad_replies", true).put("bubble_style", 2).put("avatar_shape", 3);
                break;
            case "Закат":
                o.put("gradient_names", true).put("grad_me", custom).put("grad_me_custom", "#2EE66B,#FFD200")
                        .put("grad_others", 9).put("grad_dialogs", true).put("grad_replies", true)
                        .put("grad_text", 1).put("bubble_style", 1);
                break;
            default:
                // «Минимализм»: все украшения выключены (остальные ключи сбрасываются при применении)
                o.put("bubble_style", 0).put("avatar_shape", 0).put("gradient_names", false)
                        .put("grad_text", 0).put("name_glow", false).put("grad_anim", false).put("banner_mode", 0);
                break;
        }
        return o;
    }

    // ---------------------------------------------------------------- применение и снимок

    private static void applyJson(JSONObject o) {
        SharedPreferences.Editor e = p().edit();
        for (String k : KEYS) {
            if (!o.has(k)) {
                e.remove(k);
                continue;
            }
            try {
                Object v = o.get(k);
                if (v instanceof Boolean) {
                    e.putBoolean(k, (Boolean) v);
                } else if (v instanceof Integer) {
                    e.putInt(k, (Integer) v);
                } else if (v instanceof Long) {
                    e.putInt(k, (int) ((Long) v).longValue());
                } else if (v instanceof String) {
                    e.putString(k, (String) v);
                } else {
                    e.remove(k);
                }
            } catch (Throwable t) {
                e.remove(k);
            }
        }
        e.apply();
        CrolStyle.refreshUi();
    }

    public static JSONObject snapshot() {
        JSONObject o = new JSONObject();
        SharedPreferences s = p();
        for (String k : KEYS) {
            try {
                if (!s.contains(k)) {
                    continue;
                }
                Object v = s.getAll().get(k);
                if (v instanceof Boolean || v instanceof Integer || v instanceof String) {
                    o.put(k, v);
                }
            } catch (Throwable ignore) {
            }
        }
        return o;
    }

    public static boolean applyBuiltin(String name) {
        try {
            applyJson(builtin(name));
            CrolConfig.setString("active_profile", name);
            CrolConfig.log("profile applied: " + name);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- сохранённые

    private static JSONObject saved() {
        try {
            String v = CrolConfig.getString("profiles_saved", "");
            if (v != null && !v.isEmpty()) {
                return new JSONObject(v);
            }
        } catch (Throwable ignore) {
        }
        return new JSONObject();
    }

    public static ArrayList<String> savedNames() {
        ArrayList<String> out = new ArrayList<>();
        Iterator<String> it = saved().keys();
        while (it.hasNext()) {
            out.add(it.next());
        }
        return out;
    }

    public static boolean saveCurrent(String name) {
        try {
            name = name == null ? "" : name.trim();
            if (name.isEmpty() || name.length() > 40) {
                return false;
            }
            JSONObject all = saved();
            all.put(name, snapshot());
            CrolConfig.setString("profiles_saved", all.toString());
            CrolConfig.setString("active_profile", name);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean applySaved(String name) {
        try {
            JSONObject o = saved().optJSONObject(name);
            if (o == null) {
                return false;
            }
            applyJson(o);
            CrolConfig.setString("active_profile", name);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public static void deleteSaved(String name) {
        try {
            JSONObject all = saved();
            all.remove(name);
            CrolConfig.setString("profiles_saved", all.toString());
            if (name.equals(CrolConfig.getString("active_profile", ""))) {
                CrolConfig.remove("active_profile");
            }
        } catch (Throwable ignore) {
        }
    }

    public static String exportSaved(String name) {
        try {
            JSONObject o = saved().optJSONObject(name);
            if (o == null) {
                return null;
            }
            JSONObject out = new JSONObject();
            out.put("crolgram_profile", name);
            out.put("values", o);
            return out.toString(2);
        } catch (Throwable e) {
            return null;
        }
    }

    /** Возвращает имя импортированного профиля или null. */
    public static String importProfile(String text) {
        try {
            JSONObject o = new JSONObject(text.trim());
            String name = o.optString("crolgram_profile", "");
            JSONObject values = o.optJSONObject("values");
            if (name.isEmpty() || values == null) {
                return null;
            }
            if (name.length() > 40) {
                name = name.substring(0, 40);
            }
            JSONObject clean = new JSONObject();
            for (String k : KEYS) {
                if (values.has(k)) {
                    clean.put(k, values.get(k));
                }
            }
            JSONObject all = saved();
            all.put(name, clean);
            CrolConfig.setString("profiles_saved", all.toString());
            return name;
        } catch (Throwable e) {
            return null;
        }
    }
}
