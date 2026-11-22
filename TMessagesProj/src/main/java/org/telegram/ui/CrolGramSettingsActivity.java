package org.telegram.ui;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.CrolConfig;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/**
 * Раздел настроек «CrolGram». Все страницы строятся из одного списка строк (см. buildPage).
 * Значения хранятся в CrolConfig (SharedPreferences "crolgram").
 */
public class CrolGramSettingsActivity extends BaseFragment {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CHECK = 1;
    private static final int TYPE_VALUE = 2;
    private static final int TYPE_INFO = 3;

    private static final int K_HEADER = 0;
    private static final int K_SWITCH = 1;
    private static final int K_SELECT = 2;
    private static final int K_INPUT = 3;
    private static final int K_LINK = 4;
    private static final int K_ACTION = 5;
    private static final int K_INFO = 6;
    private static final int K_GHOST = 7;

    private static final int ACT_EXPORT = 1;
    private static final int ACT_IMPORT = 2;
    private static final int ACT_COPY_LOG = 3;
    private static final int ACT_CLEAR_LOG = 4;

    private static final String NOTE_LATER = "Эти параметры уже сохраняются, но начнут действовать в следующих обновлениях CrolGram.";
    private static final String NOTE_LOCAL = "Только на этом устройстве: другие люди и серверы Telegram ничего не увидят.";

    private static final String[] PRESETS = org.telegram.messenger.CrolStyle.PRESET_NAMES;

    private static class Row {
        int kind;
        String key;
        String title;
        String sub;
        boolean defBool;
        int defInt;
        String defStr = "";
        String[] items;
        String page;
        int action;
    }

    private final String page;
    private final ArrayList<Row> rows = new ArrayList<>();

    private RecyclerListView listView;
    private ListAdapter adapter;

    public CrolGramSettingsActivity() {
        this("main");
    }

    public CrolGramSettingsActivity(String page) {
        super();
        this.page = page == null ? "main" : page;
    }

    // ---------------------------------------------------------------- описание страниц

    private static Row header(String title) {
        Row r = new Row();
        r.kind = K_HEADER;
        r.title = title;
        return r;
    }

    private static Row info(String text) {
        Row r = new Row();
        r.kind = K_INFO;
        r.title = text;
        return r;
    }

    private static Row sw(String key, String title, String sub, boolean def) {
        Row r = new Row();
        r.kind = K_SWITCH;
        r.key = key;
        r.title = title;
        r.sub = sub;
        r.defBool = def;
        return r;
    }

    private static Row select(String key, String title, int def, String... items) {
        Row r = new Row();
        r.kind = K_SELECT;
        r.key = key;
        r.title = title;
        r.defInt = def;
        r.items = items;
        return r;
    }

    private static Row input(String key, String title, String def, String hint) {
        Row r = new Row();
        r.kind = K_INPUT;
        r.key = key;
        r.title = title;
        r.defStr = def;
        r.sub = hint;
        return r;
    }

    private static Row link(String title, String page) {
        Row r = new Row();
        r.kind = K_LINK;
        r.title = title;
        r.page = page;
        return r;
    }

    private static Row action(String title, int action) {
        Row r = new Row();
        r.kind = K_ACTION;
        r.title = title;
        r.action = action;
        return r;
    }

    private static String[] withExtra(String[] base, String... extra) {
        String[] out = new String[base.length + extra.length];
        System.arraycopy(base, 0, out, 0, base.length);
        System.arraycopy(extra, 0, out, base.length, extra.length);
        return out;
    }

    private static String pageTitle(String page) {
        switch (page) {
            case "privacy": return "Приватность";
            case "protect": return "Защита";
            case "style": return "Стиль";
            case "grad": return "Градиенты";
            case "grad_me": return "Мой градиент";
            case "grad_others": return "Градиент для других";
            case "profile": return "Профиль";
            case "phone": return "Номер телефона";
            case "names": return "Имя и юзернеймы";
            case "badges": return "Значок и эмодзи";
            case "colors": return "Цвета профиля";
            case "banner": return "Баннер";
            case "gifts": return "Подарки и команды";
            case "balance": return "Баланс";
            case "tools": return "Инструменты";
            case "translate": return "Перевод";
            case "data": return "Резервная копия";
            case "debug": return "Отладка";
            default: return "CrolGram";
        }
    }

    private void buildPage() {
        rows.clear();
        switch (page) {
            case "privacy":
                rows.add(header("Скрытность"));
                rows.add(sw("hide_typing", "Скрывать «печатает…»", null, false));
                rows.add(sw("hide_online", "Всегда оффлайн", null, false));
                rows.add(sw("hide_read", "Не отмечать прочитанным", null, false));
                rows.add(sw("hide_voice_read", "Не отмечать голосовые и кружки", null, false));
                rows.add(sw("hide_stories", "Не отмечать просмотр историй", null, false));
                rows.add(info("Работает на этом устройстве: запросы просто не отправляются на сервер. Все пять пунктов разом включает «Режим призрака»."));
                break;
            case "protect":
                rows.add(header("Сообщения"));
                rows.add(sw("anti_delete", "Анти-удаление", "Удалённые собеседником сообщения остаются", false));
                rows.add(sw("deleted_mark", "Пометка удалённых", "Красная пометка «удалено» рядом со временем", true));
                rows.add(sw("anti_edit", "Анти-изменение", "Старые версии в меню сообщения «История правок»", false));
                rows.add(sw("no_forward_bypass", "Снять запрет копирования", "Применяется к новым и заново загруженным чатам", false));
                rows.add(sw("no_ads", "Убрать спонсорские сообщения", null, false));
                rows.add(sw("ttl_media", "Исчезающие медиа как обычные", "Экспериментально: фото и видео с таймером открываются как обычные", false));
                rows.add(info("Свои удаления работают как обычно. Остальные сообщения, удалённые собеседником, остаются только у вас."));
                break;
            case "style":
                rows.add(header("Оформление"));
                rows.add(select("bubble_style", "Стиль пузырей", 0, "Стандартный", "Pixel", "Nothing", "Glass", "Liquid Glass"));
                rows.add(select("avatar_shape", "Форма аватарок", 0, "Круг", "Скруглённый квадрат", "Квадрат", "Капля", "Листик"));
                rows.add(info("Стили пузырей применяются сразу. Форма аватарок меняется в списках и чатах, которые вы откроете заново. " + NOTE_LOCAL));
                break;
            case "grad":
                rows.add(header("Градиенты"));
                rows.add(sw("gradient_names", "Ники в чатах", null, false));
                rows.add(link("Мой градиент", "grad_me"));
                rows.add(link("Градиент для других", "grad_others"));
                rows.add(sw("grad_replies", "В ответах и пересылках", null, true));
                rows.add(sw("grad_dialogs", "В списке чатов", null, false));
                rows.add(sw("grad_profile", "В профиле", null, false));
                rows.add(sw("grad_title", "В шапке чата", null, false));
                rows.add(sw("grad_drawer", "В Настройках (моё имя)", "В этой версии Telegram бокового меню нет", false));
                rows.add(select("grad_text", "Текст сообщений", 0, "Нет", "Мои", "Чужие", "Все"));
                rows.add(sw("name_glow", "Свечение ников", null, false));
                rows.add(sw("grad_anim", "Анимация градиента", "Расходует больше батареи", false));
                rows.add(info("Градиенты применяются сразу. " + NOTE_LOCAL));
                break;
            case "grad_me":
                rows.add(header("Мой градиент"));
                rows.add(select("grad_me", "Пресет", 13, withExtra(PRESETS, "Свой (HEX)")));
                rows.add(input("grad_me_custom", "Свои цвета (HEX)", "#FF512F,#F09819,#FF0099", "2–8 цветов через запятую"));
                rows.add(info("Свои цвета используются при выборе «Свой (HEX)»."));
                break;
            case "grad_others":
                rows.add(header("Градиент для других"));
                rows.add(select("grad_others", "Пресет", 16, withExtra(PRESETS, "Свой (HEX)", "Разный у каждого")));
                rows.add(input("grad_others_custom", "Свои цвета (HEX)", "#00C6FF,#0072FF,#7F00FF", "2–8 цветов через запятую"));
                rows.add(info("Свои цвета используются при выборе «Свой (HEX)»."));
                break;
            case "profile":
                rows.add(header("Мой профиль"));
                rows.add(link("Номер телефона", "phone"));
                rows.add(link("Имя и юзернеймы", "names"));
                rows.add(link("Значок и эмодзи", "badges"));
                rows.add(link("Цвета профиля", "colors"));
                rows.add(link("Баннер", "banner"));
                rows.add(input("level_custom", "Уровень профиля", "", "Число, пусто = как есть"));
                rows.add(info(NOTE_LOCAL + " " + NOTE_LATER));
                break;
            case "phone":
                rows.add(header("Номер телефона"));
                rows.add(select("phone_mode", "Показывать номер", 0, "Как есть", "Анонимный +888", "Свой текст"));
                rows.add(input("phone_custom", "Номер или текст", "", "Для +888: до 8 цифр"));
                rows.add(info(NOTE_LOCAL + " " + NOTE_LATER));
                break;
            case "names":
                rows.add(header("Имя и юзернеймы"));
                rows.add(input("name_custom", "Своё имя", "", "Имя"));
                rows.add(input("username_custom", "Свой юзернейм", "", "Без @"));
                rows.add(input("usernames_extra", "Доп. юзернеймы", "", "Через запятую, без @"));
                rows.add(input("id_custom", "Свой ID", "", "Число"));
                rows.add(info(NOTE_LOCAL + " " + NOTE_LATER));
                break;
            case "badges":
                rows.add(header("Значок и эмодзи"));
                rows.add(select("badge_mode", "Значок", 0, "Нет", "Верификация", "Premium", "Scam", "Fake"));
                rows.add(input("emoji_status_id", "Эмодзи-статус (ID)", "", "ID эмодзи"));
                rows.add(input("verif_icon_id", "Иконка верификации (ID)", "", "ID эмодзи"));
                rows.add(input("verif_desc", "Текст верификации", "Верифицирован Major", "Текст"));
                rows.add(sw("emoji_pick", "Выбор эмодзи из сообщения", "Пункты в меню сообщения", false));
                rows.add(info(NOTE_LOCAL + " " + NOTE_LATER));
                break;
            case "colors":
                rows.add(header("Цвета профиля"));
                rows.add(input("name_color_id", "Цвет ника (0–20)", "", "Число 0–20"));
                rows.add(input("profile_color_id", "Цвет фона профиля (0–21)", "", "Число 0–21"));
                rows.add(input("profile_emoji_id", "Узор профиля (ID эмодзи)", "", "ID эмодзи"));
                rows.add(info(NOTE_LOCAL + " " + NOTE_LATER));
                break;
            case "banner":
                rows.add(header("Баннер"));
                rows.add(select("banner_mode", "Тип баннера", 0, "Нет", "Градиент", "Картинка"));
                rows.add(select("banner_grad", "Градиент", 0, PRESETS));
                rows.add(input("banner_image", "Путь к картинке", "", "/storage/emulated/0/Download/banner.jpg"));
                rows.add(info(NOTE_LOCAL + " " + NOTE_LATER));
                break;
            case "gifts":
                rows.add(header("Подарки"));
                rows.add(input("nft_gifts", "NFT-подарки в профиле", "", "Ссылки t.me/nft/..."));
                rows.add(sw("gifts_pinned", "Закреплять в профиле", null, true));
                rows.add(link("Баланс", "balance"));
                rows.add(header("Команды"));
                rows.add(sw("gift_cmd", "Команды в чате", ".нфт  .грам  .прем3/6/12  .призрак  .ник", false));
                rows.add(info(NOTE_LOCAL + " " + NOTE_LATER));
                break;
            case "balance":
                rows.add(header("Баланс"));
                rows.add(input("stars_balance", "Баланс звёзд", "", "Только отображение"));
                rows.add(input("gram_balance", "Баланс GRAM", "", "Только отображение"));
                rows.add(info(NOTE_LOCAL + " " + NOTE_LATER));
                break;
            case "tools":
                rows.add(header("Инструменты"));
                rows.add(link("Перевод", "translate"));
                rows.add(sw("copy_id", "«Скопировать ID» в меню", null, false));
                rows.add(info(NOTE_LATER));
                break;
            case "translate":
                rows.add(header("Перевод"));
                rows.add(sw("translate", "«Перевести» в меню сообщения", null, false));
                rows.add(input("translate_lang", "Язык перевода", "ru", "ru, en, uk, de, es…"));
                rows.add(info(NOTE_LATER));
                break;
            case "data":
                rows.add(header("Настройки CrolGram"));
                rows.add(action("Экспорт настроек (в буфер обмена)", ACT_EXPORT));
                rows.add(action("Импорт настроек (из буфера обмена)", ACT_IMPORT));
                rows.add(info("Экспорт копирует все настройки в формате JSON. Для импорта скопируйте такой JSON и нажмите «Импорт»."));
                break;
            case "debug":
                rows.add(header("Журнал"));
                rows.add(action("Скопировать лог", ACT_COPY_LOG));
                rows.add(action("Очистить лог", ACT_CLEAR_LOG));
                rows.add(info("В логе счётчики заблокированных запросов и события модулей. В нём нет ваших сообщений и ключей."));
                break;
            default:
                rows.add(header("Режим призрака"));
                Row g = new Row();
                g.kind = K_GHOST;
                g.title = "Режим призрака";
                g.sub = "Оффлайн, не читать, не «печатает», не слушать, не смотреть истории";
                rows.add(g);
                rows.add(info("Включает все пункты раздела «Приватность». Запросы не отправляются на сервер."));
                rows.add(header("Разделы"));
                rows.add(link("Приватность", "privacy"));
                rows.add(link("Защита", "protect"));
                rows.add(link("Стиль", "style"));
                rows.add(link("Градиенты", "grad"));
                rows.add(link("Профиль", "profile"));
                rows.add(link("Подарки и команды", "gifts"));
                rows.add(link("Инструменты", "tools"));
                rows.add(link("Резервная копия", "data"));
                rows.add(link("Отладка", "debug"));
                rows.add(info("CrolGram: неофициальный клиент Telegram. Косметические функции меняют только то, что видите вы на этом устройстве."));
                break;
        }
    }

    // ---------------------------------------------------------------- жизненный цикл

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        buildPage();
        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(pageTitle(page));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;

        listView = new RecyclerListView(context);
        listView.setSectionsDrawBackground(true);
        listView.setSections();
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position) -> onRowClicked(position));
        frameLayout.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT
        ));
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    // ---------------------------------------------------------------- действия

    private void onRowClicked(int position) {
        if (position < 0 || position >= rows.size()) {
            return;
        }
        Row r = rows.get(position);
        switch (r.kind) {
            case K_GHOST:
                CrolConfig.setGhost(!CrolConfig.isGhost());
                adapter.notifyDataSetChanged();
                break;
            case K_SWITCH:
                CrolConfig.setBool(r.key, !CrolConfig.getBool(r.key, r.defBool));
                adapter.notifyDataSetChanged();
                break;
            case K_SELECT:
                showSelect(r);
                break;
            case K_INPUT:
                showInput(r);
                break;
            case K_LINK:
                presentFragment(new CrolGramSettingsActivity(r.page));
                break;
            case K_ACTION:
                runAction(r.action);
                break;
            default:
                break;
        }
    }

    private void showSelect(Row r) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(r.title);
        final CharSequence[] labels = new CharSequence[r.items.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = r.items[i];
        }
        builder.setItems(labels, (dialog, index) -> {
            CrolConfig.setInt(r.key, index);
            adapter.notifyDataSetChanged();
        });
        showDialog(builder.create());
    }

    private void showInput(Row r) {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        final EditText editText = new EditText(context);
        editText.setInputType(InputType.TYPE_CLASS_TEXT);
        editText.setText(CrolConfig.getString(r.key, r.defStr));
        editText.setSelection(editText.getText().length());
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        if (r.sub != null) {
            editText.setHint(r.sub);
        }
        editText.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(r.title);
        builder.setView(editText);
        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            CrolConfig.setString(r.key, editText.getText().toString().trim());
            adapter.notifyDataSetChanged();
        });
        builder.setNeutralButton("Сбросить", (dialog, which) -> {
            CrolConfig.remove(r.key);
            adapter.notifyDataSetChanged();
        });
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }

    private void toast(String text) {
        if (getParentActivity() != null) {
            Toast.makeText(getParentActivity(), text, Toast.LENGTH_SHORT).show();
        }
    }

    private void runAction(int action) {
        switch (action) {
            case ACT_EXPORT:
                AndroidUtilities.addToClipboard(CrolConfig.exportJson());
                toast("Настройки скопированы");
                break;
            case ACT_IMPORT: {
                Context context = getParentActivity();
                if (context == null) {
                    return;
                }
                String text = null;
                try {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
                        CharSequence cs = cm.getPrimaryClip().getItemAt(0).coerceToText(context);
                        text = cs == null ? null : cs.toString();
                    }
                } catch (Throwable ignore) {
                }
                if (text == null || text.trim().isEmpty()) {
                    toast("В буфере обмена пусто");
                    return;
                }
                int n = CrolConfig.importJson(text);
                if (n < 0) {
                    toast("Не удалось разобрать JSON");
                } else {
                    toast("Импортировано значений: " + n);
                    adapter.notifyDataSetChanged();
                }
                break;
            }
            case ACT_COPY_LOG:
                AndroidUtilities.addToClipboard(CrolConfig.dumpLog());
                toast("Лог скопирован");
                break;
            case ACT_CLEAR_LOG:
                CrolConfig.clearLog();
                toast("Лог очищен");
                break;
            default:
                break;
        }
    }

    // ---------------------------------------------------------------- адаптер

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        private ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == TYPE_CHECK || type == TYPE_VALUE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == TYPE_HEADER) {
                view = new HeaderCell(context);
            } else if (viewType == TYPE_CHECK) {
                view = new TextCheckCell(context);
            } else if (viewType == TYPE_VALUE) {
                TextSettingsCell cell = new TextSettingsCell(context);
                cell.setCanDisable(true);
                view = cell;
            } else {
                view = new TextInfoPrivacyCell(context);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
            ));
            return new RecyclerListView.Holder(view);
        }

        private boolean hasDivider(int position) {
            if (position + 1 >= rows.size()) {
                return false;
            }
            int next = rows.get(position + 1).kind;
            return next != K_INFO && next != K_HEADER;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row r = rows.get(position);
            int type = holder.getItemViewType();
            boolean divider = hasDivider(position);
            if (type == TYPE_HEADER) {
                ((HeaderCell) holder.itemView).setText(r.title);
            } else if (type == TYPE_CHECK) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                boolean checked = r.kind == K_GHOST ? CrolConfig.isGhost() : CrolConfig.getBool(r.key, r.defBool);
                if (r.sub != null) {
                    cell.setTextAndValueAndCheck(r.title, r.sub, checked, true, divider);
                } else {
                    cell.setTextAndCheck(r.title, checked, divider);
                }
            } else if (type == TYPE_VALUE) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                if (r.kind == K_SELECT) {
                    int idx = CrolConfig.getInt(r.key, r.defInt);
                    if (idx < 0 || idx >= r.items.length) {
                        idx = Math.max(0, Math.min(r.defInt, r.items.length - 1));
                    }
                    cell.setTextAndValue(r.title, r.items[idx], divider);
                } else if (r.kind == K_INPUT) {
                    String value = CrolConfig.getString(r.key, r.defStr);
                    if (value == null || value.isEmpty()) {
                        value = "не задано";
                    }
                    cell.setTextAndValue(r.title, value, divider);
                } else {
                    cell.setText(r.title, divider);
                }
            } else {
                ((TextInfoPrivacyCell) holder.itemView).setText(r.title);
            }
        }

        @Override
        public int getItemViewType(int position) {
            int kind = rows.get(position).kind;
            switch (kind) {
                case K_HEADER:
                    return TYPE_HEADER;
                case K_SWITCH:
                case K_GHOST:
                    return TYPE_CHECK;
                case K_SELECT:
                case K_INPUT:
                case K_LINK:
                case K_ACTION:
                    return TYPE_VALUE;
                default:
                    return TYPE_INFO;
            }
        }
    }

    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }
}
