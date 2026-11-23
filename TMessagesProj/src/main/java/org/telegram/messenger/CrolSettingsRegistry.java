package org.telegram.messenger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * CrolGram: реестр настроек. Каждая настройка описана один раз; из реестра строятся страницы,
 * поиск, избранное, статус разделов, сброс раздела. Ключи совпадают с прежними ключами CrolConfig.
 */
public class CrolSettingsRegistry {

    public static final int T_SWITCH = 0;
    public static final int T_SELECT = 1;
    public static final int T_INPUT = 2;

    public static final int E_HEADER = 0;
    public static final int E_SETTING = 1;
    public static final int E_LINK = 2;
    public static final int E_INFO = 3;
    public static final int E_ACTION = 4;
    public static final int E_GHOST = 5;

    public static final int ACT_EXPORT = 1;
    public static final int ACT_IMPORT = 2;
    public static final int ACT_COPY_LOG = 3;
    public static final int ACT_CLEAR_LOG = 4;
    public static final int ACT_FORGET_GIFTS = 5;
    public static final int ACT_RESET_ALL = 6;
    public static final int ACT_SAVE_PROFILE = 7;
    public static final int ACT_IMPORT_PROFILE = 8;
    public static final int ACT_APPLY_PROFILE = 9;
    public static final int ACT_OPEN_PROFILE = 10;

    public static final String NOTE_LOCAL = "Только на этом устройстве: другие люди и серверы Telegram ничего не увидят.";
    public static final String MARK_LOCAL = "Только на этом устройстве";

    public static class Setting {
        public int type;
        public String key;
        public String title;
        public String sub;
        public boolean defBool;
        public int defInt;
        public String defStr = "";
        public String[] items;
        public String keywords = "";
        public String dependsOn;
        public boolean localOnly;
        public boolean experimental;
        public boolean numeric;
        public String page;

        public Setting kw(String words) {
            keywords = words;
            return this;
        }

        public Setting local() {
            localOnly = true;
            return this;
        }

        public Setting exp() {
            experimental = true;
            return this;
        }

        public Setting dep(String key) {
            dependsOn = key;
            return this;
        }

        public Setting num() {
            numeric = true;
            return this;
        }
    }

    public static class Entry {
        public int kind;
        public String title;
        public Setting setting;
        public String page;
        public int action;
    }

    public static class Page {
        public String id;
        public String title;
        public int icon;
        public final ArrayList<Entry> entries = new ArrayList<>();

        public void header(String t) {
            Entry e = new Entry();
            e.kind = E_HEADER;
            e.title = t;
            entries.add(e);
        }

        public void info(String t) {
            Entry e = new Entry();
            e.kind = E_INFO;
            e.title = t;
            entries.add(e);
        }

        public void ghost() {
            Entry e = new Entry();
            e.kind = E_GHOST;
            e.title = "Режим призрака";
            entries.add(e);
        }

        public void link(String t, String pageId) {
            Entry e = new Entry();
            e.kind = E_LINK;
            e.title = t;
            e.page = pageId;
            entries.add(e);
        }

        public void action(String t, int act) {
            Entry e = new Entry();
            e.kind = E_ACTION;
            e.title = t;
            e.action = act;
            entries.add(e);
        }

        private Setting add(int type, String key, String title) {
            Setting s = new Setting();
            s.type = type;
            s.key = key;
            s.title = title;
            s.page = id;
            Entry e = new Entry();
            e.kind = E_SETTING;
            e.setting = s;
            e.title = title;
            entries.add(e);
            return s;
        }

        public Setting sw(String key, String title, String sub, boolean def) {
            Setting s = add(T_SWITCH, key, title);
            s.sub = sub;
            s.defBool = def;
            return s;
        }

        public Setting select(String key, String title, int def, String... items) {
            Setting s = add(T_SELECT, key, title);
            s.defInt = def;
            s.items = items;
            return s;
        }

        public Setting input(String key, String title, String def, String hint) {
            Setting s = add(T_INPUT, key, title);
            s.defStr = def;
            s.sub = hint;
            return s;
        }
    }

    private static final LinkedHashMap<String, Page> PAGES = new LinkedHashMap<>();
    private static final LinkedHashMap<String, Setting> BY_KEY = new LinkedHashMap<>();

    /** Порядок разделов на главной странице. */
    public static final String[] MAIN_SECTIONS = {
            "look", "chat", "profile", "privacy", "auto", "tools", "backup", "about"
    };

    private static Page page(String id, String title, int icon) {
        Page p = new Page();
        p.id = id;
        p.title = title;
        p.icon = icon;
        PAGES.put(id, p);
        return p;
    }

    private static String[] withExtra(String[] base, String... extra) {
        String[] out = new String[base.length + extra.length];
        System.arraycopy(base, 0, out, 0, base.length);
        System.arraycopy(extra, 0, out, base.length, extra.length);
        return out;
    }

    static {
        String[] presets = CrolStyle.PRESET_NAMES;
        Page p;

        // ------------------------------------------------ Внешний вид
        p = page("look", "Внешний вид", R.drawable.msg_palette);
        p.header("Режим");
        p.sw("plain_mode", "Почти как Telegram", "Отключает украшения (пузыри, аватарки, градиенты). Настройки не теряются", false)
                .kw("обычный стандартный отключить украшения plain classic").local();
        p.header("Оформление");
        p.select("bubble_style", "Стиль пузырей", 0, "Стандартный", "Pixel", "Nothing", "Glass", "Liquid Glass")
                .kw("пузыри сообщения bubble стекло nothing pixel").local();
        p.select("avatar_shape", "Форма аватарок", 0, "Круг", "Скруглённый квадрат", "Квадрат", "Капля", "Листик")
                .kw("аватар фото форма круг квадрат avatar").local();
        p.header("Разделы");
        p.link("Градиенты", "grad");
        p.link("Профили оформления", "profiles");
        p.info("Стили пузырей применяются сразу. Форма аватарок меняется в списках и чатах, которые вы откроете заново. " + NOTE_LOCAL);

        // ------------------------------------------------ Градиенты
        p = page("grad", "Градиенты", R.drawable.msg_customize);
        p.header("Где показывать");
        p.sw("gradient_names", "Ники в чатах", null, false).kw("цвет ник имя градиент").local();
        p.link("Мой градиент", "grad_me");
        p.link("Градиент для других", "grad_others");
        p.sw("grad_replies", "В ответах и пересылках", null, true).kw("ответ пересылка цитата").local();
        p.sw("grad_dialogs", "В списке чатов", "Нужны включённые «Ники в чатах»", false).kw("список чатов диалоги").dep("gradient_names").local();
        p.sw("grad_profile", "В профиле", null, false).kw("профиль имя").local();
        p.sw("grad_title", "В шапке чата", null, false).kw("шапка заголовок").local();
        p.sw("grad_drawer", "В Настройках (моё имя)", "В этой версии Telegram бокового меню нет", false).kw("меню настройки").local();
        p.header("Текст и эффекты");
        p.select("grad_text", "Текст сообщений", 0, "Нет", "Мои", "Чужие", "Все").kw("цвет текста градиент сообщения").local();
        p.sw("name_glow", "Свечение ников", null, false).kw("свечение glow неон").local();
        p.sw("grad_anim", "Анимация градиента", "Расходует больше батареи", false).kw("анимация батарея движение").local();
        p.info("Градиенты применяются сразу. " + NOTE_LOCAL);

        p = page("grad_me", "Мой градиент", R.drawable.msg_customize);
        p.header("Мой градиент");
        p.select("grad_me", "Пресет", 13, withExtra(presets, "Свой (HEX)")).kw("пресет кислота градиент мой").local();
        p.input("grad_me_custom", "Свои цвета (HEX)", "#FF512F,#F09819,#FF0099", "2–8 цветов через запятую").kw("hex цвет свой").local();
        p.info("Свои цвета используются при выборе «Свой (HEX)».");

        p = page("grad_others", "Градиент для других", R.drawable.msg_customize);
        p.header("Градиент для других");
        p.select("grad_others", "Пресет", 16, withExtra(presets, "Свой (HEX)", "Разный у каждого")).kw("пресет чужие собеседник градиент").local();
        p.input("grad_others_custom", "Свои цвета (HEX)", "#00C6FF,#0072FF,#7F00FF", "2–8 цветов через запятую").kw("hex цвет свой").local();
        p.info("Свои цвета используются при выборе «Свой (HEX)».");

        // ------------------------------------------------ Чат
        p = page("chat", "Чат", R.drawable.msg_msgbubble3);
        p.header("Поле ввода");
        p.sw("anim_typing", "Анимация набора и стирания", "Плавный отклик поля при вводе и удалении текста", true)
                .kw("ввод анимация набор клавиатура").local();
        p.info("Остальные настройки чата появятся в следующих обновлениях. " + NOTE_LOCAL);

        // ------------------------------------------------ Профиль
        p = page("profile", "Профиль", R.drawable.msg_openprofile);
        p.header("Мой профиль");
        p.link("Номер телефона", "phone");
        p.link("Имя, юзернейм и ID", "names");
        p.link("Значок и эмодзи-статус", "badges");
        p.link("Цвета профиля", "colors");
        p.link("Баннер", "banner");
        p.link("Подарки и команды", "gifts");
        p.header("Показ в профилях");
        p.sw("show_id", "Показывать ID и дата-центр", "Одна строка в профиле пользователя: ID, под ним DC", true).kw("id dc дата центр номер").local();
        p.info(NOTE_LOCAL + " В своём профиле нажмите на ID, телефон или юзернейм, чтобы изменить отображение.");

        p = page("phone", "Номер телефона", R.drawable.msg_contacts);
        p.header("Номер телефона");
        p.select("phone_mode", "Показывать номер", 0, "Как есть", "Анонимный +888", "Свой текст").kw("телефон номер анонимный 888").local();
        p.input("phone_custom", "Номер или текст", "", "Для +888: до 8 цифр").kw("телефон номер свой").local();
        p.info(NOTE_LOCAL);

        p = page("names", "Имя, юзернейм и ID", R.drawable.msg_edit);
        p.header("Имя, юзернейм и ID");
        p.input("name_custom", "Своё имя", "", "Имя").kw("имя ник name").local();
        p.input("username_custom", "Свой юзернейм", "", "Без @").kw("юзернейм username логин").local();
        p.input("id_custom", "Свой ID", "", "Число").kw("id айди идентификатор").num().local();
        p.input("usernames_extra", "Дополнительные юзернеймы", "", "Через запятую, без @").kw("юзернеймы дополнительные").local();
        p.info(NOTE_LOCAL + " Дополнительные юзернеймы показываются в строке юзернейма вашего профиля.");

        p = page("badges", "Значок и эмодзи", R.drawable.msg_premium_badge);
        p.header("Значок и эмодзи-статус");
        p.select("badge_mode", "Значок", 0, "Нет", "Верификация", "Premium", "Scam", "Fake").kw("значок верификация premium scam fake галочка").local();
        p.input("emoji_status_id", "Эмодзи-статус (ID)", "", "ID эмодзи").kw("эмодзи статус").num().local();
        p.input("verif_icon_id", "Иконка верификации (ID эмодзи)", "", "ID эмодзи").kw("верификация иконка").local();
        p.input("verif_desc", "Текст верификации", "", "Показывается при нажатии на значок").kw("верификация текст описание").local();
        p.input("level_custom", "Уровень профиля", "", "Число").kw("уровень профиль level").local();
        p.info(NOTE_LOCAL + " Значок Premium меняет только отображение, платные функции не включаются.");

        p = page("colors", "Цвета профиля", R.drawable.msg_palette);
        p.header("Цвета профиля");
        p.input("name_color_id", "Цвет ника (0–20)", "", "Число 0–20").kw("цвет ника имя").num().local();
        p.input("profile_color_id", "Цвет фона профиля (0–21)", "", "Число 0–21").kw("цвет фон профиль").num().local();
        p.input("profile_emoji_id", "Узор профиля (ID эмодзи)", "", "ID эмодзи").kw("узор профиль эмодзи").num().local();
        p.info(NOTE_LOCAL);

        p = page("banner", "Баннер", R.drawable.msg_photo_settings);
        p.header("Баннер профиля");
        p.select("banner_mode", "Тип", 0, "Нет", "Градиент", "Картинка из файла").kw("баннер шапка профиль").local();
        p.select("banner_grad", "Градиент", 0, presets).kw("баннер градиент").local();
        p.input("banner_image", "Путь к картинке", "", "/storage/emulated/0/Download/banner.jpg").kw("баннер картинка файл").local();
        p.info(NOTE_LOCAL + " Баннер рисуется в шапке вашего профиля. Быстрый выбор: долгое нажатие на имя в своём профиле.");

        p = page("gifts", "Подарки и команды", R.drawable.msg_gift_premium);
        p.header("Подарки");
        p.input("nft_gifts", "NFT-подарки в профиле", "", "Ссылки t.me/nft/...").kw("nft подарки ссылки").local();
        p.sw("gifts_pinned", "Закреплять в профиле", null, true).kw("подарки закрепить").local();
        p.link("Баланс", "balance");
        p.header("Команды");
        p.sw("gift_cmd", "Команды в чате", ".нфт  .грам  .прем3/6/12  .призрак  .ник", false).kw("команды .нфт .грам .прем .призрак .ник");
        p.action("Забыть локальные подарки", ACT_FORGET_GIFTS);
        p.info(NOTE_LOCAL + " Ссылки t.me/nft/... подтягиваются при открытии профиля. Команды в чате на сервер не отправляются.");

        p = page("balance", "Баланс", R.drawable.msg_filled_datausage);
        p.header("Баланс");
        p.input("stars_balance", "Баланс звёзд", "", "Только отображение").kw("звёзды stars баланс").local();
        p.input("gram_balance", "Баланс GRAM", "", "Только отображение").kw("gram грам баланс").local();
        p.info(NOTE_LOCAL + " Меняется только показ баланса, покупки сервер проверяет по настоящему балансу.");

        // ------------------------------------------------ Приватность и защита
        p = page("privacy", "Приватность и защита", R.drawable.msg_secret);
        p.ghost();
        p.header("Скрытность");
        p.sw("hide_typing", "Скрывать «печатает…»", null, false).kw("печатает typing призрак");
        p.sw("hide_online", "Всегда оффлайн", null, false).kw("онлайн оффлайн статус призрак");
        p.sw("hide_read", "Не отмечать прочитанным", null, false).kw("прочитано читать призрак");
        p.sw("hide_voice_read", "Не отмечать голосовые и кружки", null, false).kw("голосовые кружки прослушано");
        p.sw("hide_stories", "Не отмечать просмотр историй", null, false).kw("истории stories просмотр");
        p.header("Сообщения");
        p.sw("anti_delete", "Анти-удаление", "Удалённые собеседником сообщения остаются", false).kw("удаление удалённые сохранить");
        p.sw("deleted_mark", "Пометка удалённых", "Красная пометка «удалено» рядом со временем", true).kw("пометка удалено корзина").local();
        p.sw("anti_edit", "Анти-изменение", "Старые версии в меню сообщения «История правок»", false).kw("правки изменение история редактирование");
        p.sw("no_forward_bypass", "Снять запрет копирования", "Применяется к новым и заново загруженным чатам", false).kw("копирование запрет пересылка защита контента");
        p.sw("no_ads", "Убрать спонсорские сообщения", null, false).kw("реклама спонсор ads");
        p.sw("ttl_media", "Исчезающие медиа как обычные", "Фото и видео с таймером открываются как обычные", false).kw("исчезающие таймер медиа").exp();
        p.link("Тихие часы", "auto");
        p.info("Скрытность работает на этом устройстве: запросы просто не отправляются на сервер. Все пять пунктов разом включает «Режим призрака». Свои удаления работают как обычно.");

        // ------------------------------------------------ Автоматизация
        p = page("auto", "Автоматизация", R.drawable.msg_autodelete);
        p.header("Тихие часы");
        p.sw("quiet_on", "Тихие часы по расписанию", "В это время автоматически работает режим призрака", false).kw("расписание ночь тихие часы режим призрака сон");
        p.input("quiet_from", "Начало", "22:00", "Время в формате ЧЧ:ММ").kw("начало время тихие").dep("quiet_on");
        p.input("quiet_to", "Конец", "07:00", "Время в формате ЧЧ:ММ").kw("конец время тихие").dep("quiet_on");
        p.info("В тихие часы запросы режима призрака блокируются, сами переключатели на странице «Приватность» не меняются. Правила и шаблоны появятся в следующих обновлениях.");

        // ------------------------------------------------ Инструменты
        p = page("tools", "Инструменты", R.drawable.msg_settings_old);
        p.header("Меню сообщения");
        p.sw("copy_id", "«Скопировать ID» в меню сообщения", "Копирует ID отправителя", false).kw("id скопировать копировать идентификатор");
        p.sw("emoji_pick", "Выбор эмодзи из сообщения", "Пункты «Поставить как эмодзи-статус» и «Как узор профиля» для Premium-эмодзи", false).kw("эмодзи статус узор premium");
        p.info("«Перевести» уже есть в меню сообщения Telegram.");

        // ------------------------------------------------ Профили оформления (динамическая страница)
        page("profiles", "Профили оформления", R.drawable.msg_theme);

        // ------------------------------------------------ Резервные копии
        p = page("backup", "Резервные копии", R.drawable.msg_download);
        p.header("Профили оформления");
        p.link("Профили оформления", "profiles");
        p.header("Настройки CrolGram");
        p.action("Экспорт настроек (в буфер обмена)", ACT_EXPORT);
        p.action("Импорт настроек (из буфера обмена)", ACT_IMPORT);
        p.info("Экспорт копирует все настройки в формате JSON. Для импорта скопируйте такой JSON и нажмите «Импорт».");

        // ------------------------------------------------ О CrolGram
        p = page("about", "О CrolGram и отладка", R.drawable.msg_help);
        p.header("Журнал");
        p.action("Сообщить об ошибке (скопировать лог)", ACT_COPY_LOG);
        p.action("Очистить лог", ACT_CLEAR_LOG);
        p.header("Данные");
        p.action("Сбросить все настройки CrolGram", ACT_RESET_ALL);
        p.info("В логе счётчики заблокированных запросов и события модулей. В нём нет ваших сообщений и ключей.");

        // ------------------------------------------------ индекс по ключам
        for (Page pg : PAGES.values()) {
            for (Entry e : pg.entries) {
                if (e.kind == E_SETTING && e.setting != null && !BY_KEY.containsKey(e.setting.key)) {
                    BY_KEY.put(e.setting.key, e.setting);
                }
            }
        }
    }

    public static Page getPage(String id) {
        return PAGES.get(id);
    }

    public static Setting getSetting(String key) {
        return BY_KEY.get(key);
    }

    public static ArrayList<Setting> allSettings() {
        return new ArrayList<>(BY_KEY.values());
    }

    // ---------------------------------------------------------------- значения

    public static boolean isOn(Setting s) {
        switch (s.type) {
            case T_SWITCH:
                return CrolConfig.getRawBool(s.key, s.defBool);
            case T_SELECT:
                return CrolConfig.getRawInt(s.key, s.defInt) != s.defInt;
            default: {
                String v = CrolConfig.getString(s.key, s.defStr);
                return v != null && !v.isEmpty() && !v.equals(s.defStr);
            }
        }
    }

    /** Все настройки раздела и вложенных страниц (по ссылкам). */
    public static void collect(String pageId, ArrayList<Setting> out, ArrayList<String> visited) {
        if (visited.contains(pageId)) {
            return;
        }
        visited.add(pageId);
        Page pg = PAGES.get(pageId);
        if (pg == null) {
            return;
        }
        for (Entry e : pg.entries) {
            if (e.kind == E_SETTING && e.setting != null) {
                if (!out.contains(e.setting)) {
                    out.add(e.setting);
                }
            } else if (e.kind == E_LINK && e.page != null) {
                collect(e.page, out, visited);
            }
        }
    }

    /** Короткий статус раздела: «3 из 8 включено» или «2 из 5 задано». */
    public static String status(String pageId) {
        if ("backup".equals(pageId) || "about".equals(pageId)) {
            return "";
        }
        ArrayList<Setting> list = new ArrayList<>();
        collect(pageId, list, new ArrayList<>());
        int sw = 0;
        int swOn = 0;
        int on = 0;
        for (Setting s : list) {
            boolean o = isOn(s);
            if (o) {
                on++;
            }
            if (s.type == T_SWITCH) {
                sw++;
                if (o) {
                    swOn++;
                }
            }
        }
        if (list.isEmpty()) {
            return "";
        }
        if (sw > 0) {
            return swOn + " из " + sw + " включено";
        }
        return on + " из " + list.size() + " задано";
    }

    /** Сбрасывает настройки раздела (и вложенных страниц) к значениям по умолчанию. */
    public static int resetPage(String pageId) {
        ArrayList<Setting> list = new ArrayList<>();
        collect(pageId, list, new ArrayList<>());
        for (Setting s : list) {
            CrolConfig.remove(s.key);
        }
        if ("privacy".equals(pageId)) {
            CrolConfig.remove("ghost_mode");
        }
        return list.size();
    }

    public static int resetAll() {
        int n = 0;
        for (Setting s : BY_KEY.values()) {
            CrolConfig.remove(s.key);
            n++;
        }
        CrolConfig.remove("ghost_mode");
        CrolConfig.remove("fav_keys");
        CrolConfig.remove("active_profile");
        return n;
    }

    public static String pageTitle(String id) {
        Page p = PAGES.get(id);
        return p == null ? "CrolGram" : p.title;
    }

    // ---------------------------------------------------------------- поиск

    public static ArrayList<Setting> search(String query) {
        ArrayList<Setting> res = new ArrayList<>();
        if (query == null) {
            return res;
        }
        String q = query.trim().toLowerCase(Locale.ROOT).replace('ё', 'е');
        if (q.isEmpty()) {
            return res;
        }
        String[] words = q.split("\\s+");
        for (Setting s : BY_KEY.values()) {
            String hay = ((s.title == null ? "" : s.title) + " " + (s.sub == null ? "" : s.sub) + " " + s.keywords + " "
                    + pageTitle(s.page)).toLowerCase(Locale.ROOT).replace('ё', 'е');
            boolean all = true;
            for (String w : words) {
                if (!hay.contains(w)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                res.add(s);
            }
        }
        return res;
    }

    // ---------------------------------------------------------------- избранное

    public static ArrayList<String> favorites() {
        ArrayList<String> out = new ArrayList<>();
        String v = CrolConfig.getString("fav_keys", "");
        if (v != null && !v.isEmpty()) {
            for (String k : v.split(",")) {
                if (!k.isEmpty() && BY_KEY.containsKey(k) && !out.contains(k)) {
                    out.add(k);
                }
            }
        }
        return out;
    }

    /** Возвращает true, если после вызова настройка в избранном. */
    public static boolean toggleFavorite(String key) {
        ArrayList<String> list = favorites();
        boolean now;
        if (list.contains(key)) {
            list.remove(key);
            now = false;
        } else {
            list.add(key);
            now = true;
        }
        StringBuilder sb = new StringBuilder();
        for (String k : list) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(k);
        }
        CrolConfig.setString("fav_keys", sb.toString());
        return now;
    }
}
