package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.CrolConfig;
import org.telegram.messenger.CrolPresets;
import org.telegram.messenger.CrolSettingsRegistry;
import org.telegram.messenger.CrolSettingsRegistry.Entry;
import org.telegram.messenger.CrolSettingsRegistry.Setting;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/**
 * Раздел настроек «CrolGram». Страницы строятся из реестра CrolSettingsRegistry:
 * главная (шапка, поиск, быстрые переключатели, избранное, разделы), страницы разделов, поиск, профили оформления.
 */
public class CrolGramSettingsActivity extends BaseFragment {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CHECK = 1;
    private static final int TYPE_VALUE = 2;
    private static final int TYPE_INFO = 3;
    private static final int TYPE_ICON = 4;
    private static final int TYPE_CARD = 5;
    private static final int TYPE_TILES = 6;
    private static final int TYPE_SEARCH = 7;
    private static final int TYPE_ICON_VALUE = 8;
    private static final int TYPE_PREVIEW = 9;

    private static final int K_HEADER = 0;
    private static final int K_SWITCH = 1;
    private static final int K_SELECT = 2;
    private static final int K_INPUT = 3;
    private static final int K_LINK = 4;
    private static final int K_ACTION = 5;
    private static final int K_INFO = 6;
    private static final int K_GHOST = 7;
    private static final int K_CARD = 8;
    private static final int K_TILES = 9;
    private static final int K_SEARCH = 10;
    private static final int K_RESULT = 11;
    private static final int K_SECTION = 12;
    private static final int K_BUILTIN = 13;
    private static final int K_SAVED = 14;
    private static final int K_PREVIEW = 15;

    private static final int ACT_RESET_PAGE = 100;

    private static final int ACCENT = 0xFF2EE66B;
    private static final String HL_TAG = "crol_hl";

    private static class Row {
        int kind;
        Setting setting;
        String title;
        String value;
        String page;
        int action;
        int icon;
        String arg;
    }

    private final String page;
    private final String highlightKey;
    private final ArrayList<Row> rows = new ArrayList<>();

    private RecyclerListView listView;
    private ListAdapter adapter;
    private LinearLayoutManager layoutManager;
    private EditTextBoldCursor searchField;
    private String query = "";
    private String flashKey;

    public CrolGramSettingsActivity() {
        this("main", null);
    }

    public CrolGramSettingsActivity(String page) {
        this(page, null);
    }

    public CrolGramSettingsActivity(String page, String highlightKey) {
        super();
        this.page = page == null ? "main" : page;
        this.highlightKey = highlightKey;
    }

    // ---------------------------------------------------------------- построение строк

    private static Row simple(int kind, String title) {
        Row r = new Row();
        r.kind = kind;
        r.title = title;
        return r;
    }

    private static Row settingRow(Setting s) {
        Row r = new Row();
        r.setting = s;
        r.title = s.title;
        r.kind = s.type == CrolSettingsRegistry.T_SWITCH ? K_SWITCH
                : s.type == CrolSettingsRegistry.T_SELECT ? K_SELECT : K_INPUT;
        return r;
    }

    private static Row linkRow(String title, String pageId) {
        Row r = simple(K_LINK, title);
        r.page = pageId;
        CrolSettingsRegistry.Page pg = CrolSettingsRegistry.getPage(pageId);
        r.icon = pg != null ? pg.icon : R.drawable.msg_info;
        return r;
    }

    private static Row actionRow(String title, int action) {
        Row r = simple(K_ACTION, title);
        r.action = action;
        return r;
    }

    private String pageTitle() {
        switch (page) {
            case "main":
                return "CrolGram";
            case "search":
                return "Поиск";
            default:
                return CrolSettingsRegistry.pageTitle(page);
        }
    }

    private void buildPage() {
        rows.clear();
        if ("main".equals(page)) {
            buildMain();
        } else if ("search".equals(page)) {
            buildSearch();
        } else if ("profiles".equals(page)) {
            buildProfiles();
        } else {
            buildFromRegistry();
        }
    }

    private void buildMain() {
        rows.add(simple(K_CARD, null));
        rows.add(simple(K_HEADER, "Быстрые переключатели"));
        rows.add(simple(K_TILES, null));
        Row s = linkRow("Поиск по настройкам", "search");
        s.icon = R.drawable.msg_search;
        rows.add(s);
        rows.add(simple(K_HEADER, "Избранное"));
        ArrayList<String> favs = CrolSettingsRegistry.favorites();
        if (favs.isEmpty()) {
            rows.add(simple(K_INFO, "Удерживайте любой пункт настроек, чтобы закрепить его здесь."));
        } else {
            for (String k : favs) {
                Setting st = CrolSettingsRegistry.getSetting(k);
                if (st != null) {
                    rows.add(settingRow(st));
                }
            }
            rows.add(simple(K_INFO, "Удерживайте пункт, чтобы убрать его из избранного."));
        }
        rows.add(simple(K_HEADER, "Разделы"));
        for (String id : CrolSettingsRegistry.MAIN_SECTIONS) {
            CrolSettingsRegistry.Page pg = CrolSettingsRegistry.getPage(id);
            if (pg == null) {
                continue;
            }
            Row r = simple(K_SECTION, pg.title);
            r.page = id;
            r.icon = pg.icon;
            r.value = CrolSettingsRegistry.status(id);
            rows.add(r);
        }
        rows.add(simple(K_INFO, "CrolGram: неофициальный клиент Telegram. Косметические функции меняют только то, что видите вы на этом устройстве."));
    }

    private void buildSearch() {
        rows.clear();
        rows.add(simple(K_SEARCH, null));
        appendSearchResults();
    }

    private void appendSearchResults() {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            rows.add(simple(K_INFO, "Введите название настройки или слово из описания, например «градиент», «призрак» или «аватар»."));
            return;
        }
        ArrayList<Setting> found = CrolSettingsRegistry.search(q);
        if (found.isEmpty()) {
            rows.add(simple(K_INFO, "Ничего не найдено."));
            return;
        }
        rows.add(simple(K_HEADER, "Найдено: " + found.size()));
        for (Setting s : found) {
            Row r = simple(K_RESULT, s.title);
            r.setting = s;
            r.value = CrolSettingsRegistry.pageTitle(s.page);
            CrolSettingsRegistry.Page pg = CrolSettingsRegistry.getPage(s.page);
            r.icon = pg != null ? pg.icon : R.drawable.msg_info;
            rows.add(r);
        }
    }

    private void buildFromRegistry() {
        CrolSettingsRegistry.Page pg = CrolSettingsRegistry.getPage(page);
        if (pg == null) {
            rows.add(simple(K_INFO, "Страница не найдена."));
            return;
        }
        boolean hasSettings = false;
        for (Entry e : pg.entries) {
            switch (e.kind) {
                case CrolSettingsRegistry.E_HEADER:
                    rows.add(simple(K_HEADER, e.title));
                    break;
                case CrolSettingsRegistry.E_INFO:
                    rows.add(simple(K_INFO, e.title));
                    break;
                case CrolSettingsRegistry.E_PREVIEW:
                    rows.add(simple(K_PREVIEW, null));
                    break;
                case CrolSettingsRegistry.E_GHOST: {
                    Row g = simple(K_GHOST, "Режим призрака");
                    rows.add(g);
                    break;
                }
                case CrolSettingsRegistry.E_LINK:
                    rows.add(linkRow(e.title, e.page));
                    break;
                case CrolSettingsRegistry.E_ACTION:
                    rows.add(actionRow(e.title, e.action));
                    break;
                case CrolSettingsRegistry.E_SETTING:
                    rows.add(settingRow(e.setting));
                    hasSettings = true;
                    break;
                default:
                    break;
            }
        }
        if (hasSettings || "privacy".equals(page)) {
            rows.add(simple(K_HEADER, "Сброс"));
            rows.add(actionRow("Сбросить раздел", ACT_RESET_PAGE));
            rows.add(simple(K_INFO, "Вернёт настройки этого раздела к значениям по умолчанию."));
        }
    }

    private void buildProfiles() {
        String active = CrolConfig.getString("active_profile", "");
        rows.add(simple(K_HEADER, "Встроенные"));
        for (String n : CrolPresets.BUILTIN) {
            Row r = simple(K_BUILTIN, n);
            r.value = n.equals(active) ? "активен" : "";
            r.icon = R.drawable.msg_theme;
            rows.add(r);
        }
        ArrayList<String> saved = CrolPresets.savedNames();
        rows.add(simple(K_HEADER, "Мои профили"));
        if (saved.isEmpty()) {
            rows.add(simple(K_INFO, "Пока нет сохранённых профилей."));
        } else {
            for (String n : saved) {
                Row r = simple(K_SAVED, n);
                r.value = n.equals(active) ? "активен" : "";
                r.icon = R.drawable.msg_saved;
                rows.add(r);
            }
        }
        rows.add(simple(K_HEADER, "Действия"));
        rows.add(actionRow("Сохранить текущее оформление как профиль", CrolSettingsRegistry.ACT_SAVE_PROFILE));
        rows.add(actionRow("Импорт профиля (из буфера обмена)", CrolSettingsRegistry.ACT_IMPORT_PROFILE));
        rows.add(simple(K_INFO, "В профиль входят стиль пузырей, форма аватарок, градиенты, баннер и анимация ввода. "
                + CrolSettingsRegistry.NOTE_LOCAL));
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
        simpleActionBar.setTitle(pageTitle());
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
        layoutManager = new LinearLayoutManager(context);
        listView.setLayoutManager(layoutManager);
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position) -> onRowClicked(position));
        listView.setOnItemLongClickListener((view, position) -> onRowLongClicked(position));
        frameLayout.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT
        ));

        if (highlightKey != null) {
            flashKey = highlightKey;
            listView.post(() -> {
                for (int i = 0; i < rows.size(); i++) {
                    Row r = rows.get(i);
                    if (r.setting != null && highlightKey.equals(r.setting.key)) {
                        layoutManager.scrollToPositionWithOffset(i, AndroidUtilities.dp(120));
                        adapter.notifyItemChanged(i);
                        break;
                    }
                }
            });
            AndroidUtilities.runOnUIThread(() -> {
                flashKey = null;
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }, 1800);
        }
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        CrolConfig.maybeAutoBackup();
        if (!"search".equals(page) && adapter != null) {
            buildPage();
            adapter.notifyDataSetChanged();
        }
    }

    // ---------------------------------------------------------------- действия

    private boolean isDisabled(Row r) {
        if (r.setting == null || r.setting.dependsOn == null) {
            return false;
        }
        Setting dep = CrolSettingsRegistry.getSetting(r.setting.dependsOn);
        return dep != null && !CrolConfig.getRawBool(dep.key, dep.defBool);
    }

    private void refreshRows() {
        if (adapter == null) {
            return;
        }
        buildPage();
        adapter.notifyDataSetChanged();
    }

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
            case K_SELECT:
            case K_INPUT:
                if (isDisabled(r)) {
                    Setting dep = CrolSettingsRegistry.getSetting(r.setting.dependsOn);
                    toast("Сначала включите: " + (dep != null ? dep.title : ""));
                    break;
                }
                if (r.kind == K_SWITCH) {
                    CrolConfig.setBool(r.setting.key, !CrolConfig.getRawBool(r.setting.key, r.setting.defBool));
                    refreshRows();
                } else if (r.kind == K_SELECT) {
                    showSelect(r);
                } else {
                    showInput(r);
                }
                break;
            case K_RESULT:
                presentFragment(new CrolGramSettingsActivity(r.setting.page, r.setting.key));
                break;
            case K_LINK:
            case K_SECTION:
                presentFragment(new CrolGramSettingsActivity(r.page));
                break;
            case K_ACTION:
                runAction(r.action, r.arg);
                break;
            case K_BUILTIN:
                if (CrolPresets.applyBuiltin(r.title)) {
                    toast("Применён профиль «" + r.title + "»");
                }
                refreshRows();
                break;
            case K_SAVED:
                showSavedMenu(r.title);
                break;
            default:
                break;
        }
    }

    private boolean onRowLongClicked(int position) {
        if (position < 0 || position >= rows.size()) {
            return false;
        }
        Row r = rows.get(position);
        if (r.setting == null) {
            return false;
        }
        boolean now = CrolSettingsRegistry.toggleFavorite(r.setting.key);
        toast(now ? "Добавлено в избранное" : "Убрано из избранного");
        if ("main".equals(page)) {
            refreshRows();
        }
        return true;
    }

    private void showSelect(Row r) {
        if (getParentActivity() == null) {
            return;
        }
        final Setting s = r.setting;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(s.title);
        final CharSequence[] labels = new CharSequence[s.items.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = s.items[i];
        }
        builder.setItems(labels, (dialog, index) -> {
            CrolConfig.setInt(s.key, index);
            org.telegram.messenger.CrolProfile.refreshSelf();
            refreshRows();
        });
        showDialog(builder.create());
    }

    private void showInput(Row r) {
        if (getParentActivity() == null) {
            return;
        }
        final Setting s = r.setting;
        CrolEditDialog.show(this, s.title, s.sub, CrolConfig.getString(s.key, s.defStr),
                s.numeric ? InputType.TYPE_CLASS_NUMBER : InputType.TYPE_CLASS_TEXT,
                new CrolEditDialog.Handler() {
                    @Override
                    public void onSave(String value) {
                        CrolConfig.setString(s.key, value);
                        refreshRows();
                    }

                    @Override
                    public void onReset() {
                        CrolConfig.remove(s.key);
                        refreshRows();
                    }
                });
    }

    private void showSavedMenu(final String name) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(name);
        builder.setItems(new CharSequence[]{"Применить", "Экспорт в буфер обмена", "Удалить"}, (dialog, index) -> {
            if (index == 0) {
                if (CrolPresets.applySaved(name)) {
                    toast("Применён профиль «" + name + "»");
                }
            } else if (index == 1) {
                String json = CrolPresets.exportSaved(name);
                if (json != null) {
                    AndroidUtilities.addToClipboard(json);
                    toast("Профиль скопирован");
                }
            } else {
                CrolPresets.deleteSaved(name);
                toast("Профиль удалён");
            }
            refreshRows();
        });
        showDialog(builder.create());
    }

    private void confirm(String title, String message, Runnable onYes) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton("Сбросить", (d, w) -> onYes.run());
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }

    private void toast(String text) {
        if (getParentActivity() != null) {
            Toast.makeText(getParentActivity(), text, Toast.LENGTH_SHORT).show();
        }
    }

    private void runAction(int action, String arg) {
        switch (action) {
            case ACT_RESET_PAGE:
                confirm("Сбросить раздел?", "Настройки раздела «" + pageTitle() + "» вернутся к значениям по умолчанию.", () -> {
                    int n = CrolSettingsRegistry.resetPage(page);
                    org.telegram.messenger.CrolProfile.refreshSelf();
                    toast("Сброшено настроек: " + n);
                    refreshRows();
                });
                break;
            case CrolSettingsRegistry.ACT_RESET_ALL:
                confirm("Сбросить всё?", "Все настройки CrolGram вернутся к значениям по умолчанию. Профили оформления сохранятся.", () -> {
                    CrolSettingsRegistry.resetAll();
                    org.telegram.messenger.CrolProfile.refreshSelf();
                    toast("Настройки сброшены");
                    refreshRows();
                });
                break;
            case CrolSettingsRegistry.ACT_AUTO_THEME:
                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_NIGHT));
                break;
            case CrolSettingsRegistry.ACT_WALLPAPERS:
                presentFragment(new WallpapersListActivity(WallpapersListActivity.TYPE_ALL));
                break;
            case CrolSettingsRegistry.ACT_BACKUP_NOW: {
                String path = CrolConfig.backupNow();
                toast(path == null ? "Не удалось сохранить копию" : "Копия сохранена: " + path);
                break;
            }
            case CrolSettingsRegistry.ACT_RESTORE_BACKUP: {
                int n = CrolConfig.restoreBackup();
                if (n < 0) {
                    toast("Копия не найдена или повреждена");
                } else {
                    org.telegram.messenger.CrolProfile.refreshSelf();
                    toast("Восстановлено значений: " + n);
                    refreshRows();
                }
                break;
            }
            case CrolSettingsRegistry.ACT_EXPORT:
                AndroidUtilities.addToClipboard(CrolConfig.exportJson());
                toast("Настройки скопированы");
                break;
            case CrolSettingsRegistry.ACT_IMPORT: {
                String text = readClipboard();
                if (text == null || text.trim().isEmpty()) {
                    toast("В буфере обмена пусто");
                    return;
                }
                int n = CrolConfig.importJson(text);
                if (n < 0) {
                    toast("Не удалось разобрать JSON");
                } else {
                    org.telegram.messenger.CrolProfile.refreshSelf();
                    toast("Импортировано значений: " + n);
                    refreshRows();
                }
                break;
            }
            case CrolSettingsRegistry.ACT_COPY_LOG:
                AndroidUtilities.addToClipboard("CrolGram " + BuildVars.BUILD_VERSION_STRING + "\n" + CrolConfig.dumpLog());
                toast("Лог скопирован");
                break;
            case CrolSettingsRegistry.ACT_CLEAR_LOG:
                CrolConfig.clearLog();
                toast("Лог очищен");
                break;
            case CrolSettingsRegistry.ACT_FORGET_GIFTS:
                org.telegram.messenger.CrolGifts.forgetLocal();
                toast("Локальные подарки забыты");
                refreshRows();
                break;
            case CrolSettingsRegistry.ACT_SAVE_PROFILE:
                CrolEditDialog.show(this, "Сохранить как профиль", "Название профиля", "", InputType.TYPE_CLASS_TEXT,
                        new CrolEditDialog.Handler() {
                            @Override
                            public void onSave(String value) {
                                if (CrolPresets.saveCurrent(value)) {
                                    toast("Профиль сохранён");
                                } else {
                                    toast("Введите название до 40 символов");
                                }
                                refreshRows();
                            }

                            @Override
                            public void onReset() {
                            }
                        });
                break;
            case CrolSettingsRegistry.ACT_IMPORT_PROFILE: {
                String text = readClipboard();
                if (text == null || text.trim().isEmpty()) {
                    toast("В буфере обмена пусто");
                    return;
                }
                String name = CrolPresets.importProfile(text);
                toast(name == null ? "Не удалось разобрать профиль" : "Импортирован профиль «" + name + "»");
                refreshRows();
                break;
            }
            default:
                break;
        }
    }

    private String readClipboard() {
        Context context = getParentActivity();
        if (context == null) {
            return null;
        }
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence cs = cm.getPrimaryClip().getItemAt(0).coerceToText(context);
                return cs == null ? null : cs.toString();
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    // ---------------------------------------------------------------- вспомогательные виды

    private View createCard(Context context) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        TextView logo = new TextView(context);
        logo.setText("C");
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        logo.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 34);
        logo.setTextColor(Color.WHITE);
        logo.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), 0xFF0A9E4B));
        box.addView(logo, LayoutHelper.createLinear(68, 68, Gravity.CENTER_HORIZONTAL));

        TextView name = new TextView(context);
        name.setText("CrolGram");
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        box.addView(name, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 10, 0, 0));

        TextView ver = new TextView(context);
        ver.setText("Версия " + BuildVars.BUILD_VERSION_STRING);
        ver.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        ver.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        box.addView(ver, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 2, 0, 0));

        TextView btn = new TextView(context);
        btn.setText("Сообщить об ошибке");
        btn.setGravity(Gravity.CENTER);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(Color.WHITE);
        btn.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        btn.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), 0xFF0A9E4B));
        btn.setOnClickListener(v -> runAction(CrolSettingsRegistry.ACT_COPY_LOG, null));
        box.addView(btn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 36, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));
        return box;
    }

    private boolean tileState(int i) {
        switch (i) {
            case 0:
                return CrolConfig.isGhost();
            case 1:
                return CrolConfig.getRawBool("anti_delete", false);
            case 2:
                return CrolConfig.getRawBool("plain_mode", false);
            default:
                return CrolConfig.getRawBool("quiet_on", false);
        }
    }

    private void tileToggle(int i) {
        switch (i) {
            case 0:
                CrolConfig.setGhost(!CrolConfig.isGhost());
                break;
            case 1:
                CrolConfig.setBool("anti_delete", !CrolConfig.getRawBool("anti_delete", false));
                break;
            case 2:
                CrolConfig.setBool("plain_mode", !CrolConfig.getRawBool("plain_mode", false));
                break;
            default:
                CrolConfig.setBool("quiet_on", !CrolConfig.getRawBool("quiet_on", false));
                break;
        }
    }

    private LinearLayout createTiles(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        final String[] labels = {"Призрак", "Анти-удаление", "Как Telegram", "Тихие часы"};
        final int[] icons = {R.drawable.msg_secret, R.drawable.msg_delete, R.drawable.msg_theme, R.drawable.msg_mute};
        for (int i = 0; i < 4; i++) {
            final int index = i;
            LinearLayout tile = new LinearLayout(context);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(10), AndroidUtilities.dp(4), AndroidUtilities.dp(10));
            ImageView icon = new ImageView(context);
            icon.setImageResource(icons[i]);
            icon.setScaleType(ImageView.ScaleType.CENTER);
            tile.addView(icon, LayoutHelper.createLinear(28, 28, Gravity.CENTER_HORIZONTAL));
            TextView label = new TextView(context);
            label.setText(labels[i]);
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            tile.addView(label, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));
            styleTile(tile, icon, label, tileState(i));
            tile.setOnClickListener(v -> {
                tileToggle(index);
                boolean on = tileState(index);
                styleTile(tile, icon, label, on);
                toast(labels[index] + (on ? ": включено" : ": выключено"));
            });
            row.addView(tile, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, i == 0 ? 0 : 8, 0, 0, 0));
        }
        return row;
    }

    private void styleTile(View tile, ImageView icon, TextView label, boolean on) {
        tile.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14),
                on ? Theme.multAlpha(ACCENT, 0.28f) : Theme.getColor(Theme.key_windowBackgroundGray)));
        int c = on ? ACCENT : Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon);
        icon.setColorFilter(new PorterDuffColorFilter(c, PorterDuff.Mode.MULTIPLY));
        label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
    }

    private View createSearchField(Context context) {
        FrameLayout box = new FrameLayout(context);
        box.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(8));
        searchField = CrolEditDialog.createField(context, "Поиск по настройкам", query, InputType.TYPE_CLASS_TEXT);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String now = s == null ? "" : s.toString();
                if (now.equals(query)) {
                    return;
                }
                query = now;
                int old = rows.size();
                while (rows.size() > 1) {
                    rows.remove(rows.size() - 1);
                }
                appendSearchResults();
                if (adapter != null) {
                    if (old > 1) {
                        adapter.notifyItemRangeRemoved(1, old - 1);
                    }
                    if (rows.size() > 1) {
                        adapter.notifyItemRangeInserted(1, rows.size() - 1);
                    }
                }
            }
        });
        box.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        AndroidUtilities.runOnUIThread(() -> {
            if (searchField != null) {
                searchField.requestFocus();
                AndroidUtilities.showKeyboard(searchField);
            }
        }, 250);
        return box;
    }


    /** Живой предпросмотр: два пузыря и аватарка с текущими настройками оформления. */
    private static class PreviewView extends View {
        private final android.graphics.Paint fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint stroke = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.text.TextPaint text = new android.text.TextPaint(android.text.TextPaint.ANTI_ALIAS_FLAG);
        private final android.text.TextPaint name = new android.text.TextPaint(android.text.TextPaint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF rect = new android.graphics.RectF();
        private final android.graphics.Path path = new android.graphics.Path();
        private final float[] radii = new float[8];
        private android.graphics.LinearGradient meShader;
        private android.graphics.LinearGradient nameShader;
        private int bubbleStyle;
        private int avatarShape;
        private boolean gradNames;
        private int avatarColor;
        private int bgColor;
        private int inColor;
        private int inText;
        private int strokeColor;

        PreviewView(Context context) {
            super(context);
            stroke.setStyle(android.graphics.Paint.Style.STROKE);
            stroke.setStrokeWidth(AndroidUtilities.dp(1));
            text.setTextSize(AndroidUtilities.dp(15));
            name.setTextSize(AndroidUtilities.dp(13));
            name.setTypeface(Typeface.DEFAULT_BOLD);
            setMinimumHeight(AndroidUtilities.dp(176));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(176));
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            rebuild();
        }

        void rebuild() {
            bubbleStyle = CrolConfig.getInt("bubble_style", 0);
            avatarShape = CrolConfig.getInt("avatar_shape", 0);
            gradNames = CrolConfig.getBool("gradient_names", false);
            bgColor = Theme.getColor(Theme.key_windowBackgroundGray);
            inColor = Theme.getColor(Theme.key_windowBackgroundWhite);
            inText = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
            strokeColor = Theme.multAlpha(inText, 0.25f);
            int w = getMeasuredWidth();
            int[] me = org.telegram.messenger.CrolStyle.colorsFor(true, 0);
            int[] other = org.telegram.messenger.CrolStyle.colorsFor(false, 1);
            avatarColor = 0xFF000000 | other[0];
            if (w > 0) {
                float left = w - AndroidUtilities.dp(16 + 200);
                meShader = new android.graphics.LinearGradient(left, 0, w - AndroidUtilities.dp(16), 0, opaque(me), null, android.graphics.Shader.TileMode.CLAMP);
                nameShader = new android.graphics.LinearGradient(AndroidUtilities.dp(64), 0, AndroidUtilities.dp(124), 0, opaque(other), null, android.graphics.Shader.TileMode.CLAMP);
            }
            invalidate();
        }

        private static int[] opaque(int[] cols) {
            int[] out = new int[cols.length];
            for (int i = 0; i < cols.length; i++) {
                out[i] = 0xFF000000 | cols[i];
            }
            return out;
        }

        private float bubbleRadius() {
            switch (bubbleStyle) {
                case 1:
                    return AndroidUtilities.dp(4);
                case 2:
                    return AndroidUtilities.dp(10);
                case 4:
                    return AndroidUtilities.dp(24);
                default:
                    return AndroidUtilities.dp(18);
            }
        }

        private void drawBubble(android.graphics.Canvas c, float l, float t, float r, float b, boolean mine) {
            rect.set(l, t, r, b);
            float rad = bubbleRadius();
            fill.setShader(null);
            if (mine && meShader != null) {
                fill.setShader(meShader);
                fill.setAlpha(bubbleStyle >= 3 ? 190 : 255);
            } else {
                fill.setColor(inColor);
                fill.setAlpha(bubbleStyle >= 3 ? 190 : 255);
            }
            c.drawRoundRect(rect, rad, rad, fill);
            if (bubbleStyle == 2 || bubbleStyle >= 3) {
                stroke.setColor(strokeColor);
                c.drawRoundRect(rect, rad, rad, stroke);
            }
        }

        private void drawAvatar(android.graphics.Canvas c, float x, float y, float size) {
            float s = size / 2f;
            float small = AndroidUtilities.dp(4);
            switch (avatarShape) {
                case 1:
                    java.util.Arrays.fill(radii, size * 0.3f);
                    break;
                case 2:
                    java.util.Arrays.fill(radii, small);
                    break;
                case 3:
                    radii[0] = radii[1] = radii[2] = radii[3] = radii[4] = radii[5] = s;
                    radii[6] = radii[7] = small;
                    break;
                case 4:
                    radii[0] = radii[1] = s;
                    radii[2] = radii[3] = small;
                    radii[4] = radii[5] = s;
                    radii[6] = radii[7] = small;
                    break;
                default:
                    java.util.Arrays.fill(radii, s);
                    break;
            }
            rect.set(x, y, x + size, y + size);
            path.reset();
            path.addRoundRect(rect, radii, android.graphics.Path.Direction.CW);
            fill.setShader(null);
            fill.setColor(avatarColor);
            c.drawPath(path, fill);
            text.setColor(Color.WHITE);
            text.setTextAlign(android.graphics.Paint.Align.CENTER);
            c.drawText("К", x + s, y + s + AndroidUtilities.dp(5), text);
            text.setTextAlign(android.graphics.Paint.Align.LEFT);
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            int w = getMeasuredWidth();
            fill.setShader(null);
            fill.setColor(bgColor);
            canvas.drawRect(0, 0, w, getMeasuredHeight(), fill);
            drawAvatar(canvas, AndroidUtilities.dp(16), AndroidUtilities.dp(18), AndroidUtilities.dp(40));
            float l = AndroidUtilities.dp(64);
            drawBubble(canvas, l, AndroidUtilities.dp(14), l + AndroidUtilities.dp(190), AndroidUtilities.dp(78), false);
            if (gradNames && nameShader != null) {
                name.setShader(nameShader);
            } else {
                name.setShader(null);
                name.setColor(0xFF2EA6FF);
            }
            canvas.drawText("Кроль", l + AndroidUtilities.dp(12), AndroidUtilities.dp(34), name);
            text.setColor(inText);
            canvas.drawText("Как тебе оформление?", l + AndroidUtilities.dp(12), AndroidUtilities.dp(60), text);
            float r = w - AndroidUtilities.dp(16);
            drawBubble(canvas, r - AndroidUtilities.dp(200), AndroidUtilities.dp(94), r, AndroidUtilities.dp(142), true);
            text.setColor(Color.WHITE);
            canvas.drawText("Выглядит отлично", r - AndroidUtilities.dp(188), AndroidUtilities.dp(124), text);
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
            return type == TYPE_CHECK || type == TYPE_VALUE || type == TYPE_ICON || type == TYPE_ICON_VALUE;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == TYPE_HEADER) {
                view = new HeaderCell(context);
            } else if (viewType == TYPE_CHECK) {
                view = new TextCheckCell(context);
            } else if (viewType == TYPE_ICON || viewType == TYPE_ICON_VALUE) {
                view = new TextCell(context);
            } else if (viewType == TYPE_VALUE) {
                TextSettingsCell cell = new TextSettingsCell(context);
                cell.setCanDisable(true);
                view = cell;
            } else if (viewType == TYPE_CARD) {
                view = createCard(context);
            } else if (viewType == TYPE_TILES) {
                view = createTiles(context);
            } else if (viewType == TYPE_SEARCH) {
                view = createSearchField(context);
            } else if (viewType == TYPE_PREVIEW) {
                view = new PreviewView(context);
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

        private String switchSub(Setting s) {
            StringBuilder sb = new StringBuilder();
            if (s.sub != null) {
                sb.append(s.sub);
            }
            if (s.experimental) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append("Экспериментально");
            }
            if (s.localOnly) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(CrolSettingsRegistry.MARK_LOCAL);
            }
            return sb.length() == 0 ? null : sb.toString();
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row r = rows.get(position);
            int type = holder.getItemViewType();
            boolean divider = hasDivider(position);
            boolean disabled = isDisabled(r);
            holder.itemView.setAlpha(disabled ? 0.5f : 1f);
            if (holder.itemView.getTag() != null && HL_TAG.equals(holder.itemView.getTag())) {
                holder.itemView.setBackground(null);
                holder.itemView.setTag(null);
            }
            if (flashKey != null && r.setting != null && flashKey.equals(r.setting.key)) {
                holder.itemView.setBackgroundColor(Theme.multAlpha(ACCENT, 0.22f));
                holder.itemView.setTag(HL_TAG);
            }
            if (type == TYPE_HEADER) {
                ((HeaderCell) holder.itemView).setText(r.title);
            } else if (type == TYPE_CHECK) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                boolean checked = r.kind == K_GHOST ? CrolConfig.isGhost() : CrolConfig.getRawBool(r.setting.key, r.setting.defBool);
                String sub = r.kind == K_GHOST
                        ? "Оффлайн, не читать, не «печатает», не слушать, не смотреть истории"
                        : switchSub(r.setting);
                if (sub != null) {
                    cell.setTextAndValueAndCheck(r.title, sub, checked, true, divider);
                } else {
                    cell.setTextAndCheck(r.title, checked, divider);
                }
            } else if (type == TYPE_ICON) {
                ((TextCell) holder.itemView).setTextAndIcon(r.title, r.icon, divider);
            } else if (type == TYPE_ICON_VALUE) {
                ((TextCell) holder.itemView).setTextAndValueAndIcon(r.title, r.value == null ? "" : r.value, r.icon, divider);
            } else if (type == TYPE_VALUE) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                if (r.kind == K_SELECT) {
                    Setting s = r.setting;
                    int idx = CrolConfig.getRawInt(s.key, s.defInt);
                    if (idx < 0 || idx >= s.items.length) {
                        idx = Math.max(0, Math.min(s.defInt, s.items.length - 1));
                    }
                    cell.setTextAndValue(r.title, s.items[idx], divider);
                } else if (r.kind == K_INPUT) {
                    String value = CrolConfig.getString(r.setting.key, r.setting.defStr);
                    if (value == null || value.isEmpty()) {
                        value = "не задано";
                    }
                    cell.setTextAndValue(r.title, value, divider);
                } else {
                    cell.setText(r.title, divider);
                }
            } else if (type == TYPE_INFO) {
                ((TextInfoPrivacyCell) holder.itemView).setText(r.title);
            } else if (type == TYPE_PREVIEW) {
                ((PreviewView) holder.itemView).rebuild();
            } else if (type == TYPE_TILES) {
                LinearLayout row = (LinearLayout) holder.itemView;
                for (int i = 0; i < row.getChildCount() && i < 4; i++) {
                    View tile = row.getChildAt(i);
                    if (tile instanceof LinearLayout && ((LinearLayout) tile).getChildCount() >= 2) {
                        LinearLayout t = (LinearLayout) tile;
                        styleTile(t, (ImageView) t.getChildAt(0), (TextView) t.getChildAt(1), tileState(i));
                    }
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            Row r = rows.get(position);
            switch (r.kind) {
                case K_HEADER:
                    return TYPE_HEADER;
                case K_SWITCH:
                case K_GHOST:
                    return TYPE_CHECK;
                case K_SELECT:
                case K_INPUT:
                case K_ACTION:
                    return TYPE_VALUE;
                case K_LINK:
                    return TYPE_ICON;
                case K_SECTION:
                case K_RESULT:
                case K_BUILTIN:
                case K_SAVED:
                    return TYPE_ICON_VALUE;
                case K_CARD:
                    return TYPE_CARD;
                case K_TILES:
                    return TYPE_TILES;
                case K_PREVIEW:
                    return TYPE_PREVIEW;
                case K_SEARCH:
                    return TYPE_SEARCH;
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
