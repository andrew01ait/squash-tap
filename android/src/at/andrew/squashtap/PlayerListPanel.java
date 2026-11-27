package at.andrew.squashtap;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/** Search and paginate the same roster in management, assignment and history filters. */
final class PlayerListPanel {
    interface Row { void add(LinearLayout list, OpponentDirectory.Entry entry); }
    private static final String[] ORDERS = {"recent", "az", "za"};
    private static final String[] LABELS = {"Most recent activity", "A–Z", "Z–A"};
    private static final int PAGE_SIZE = 10;
    private final MainActivity activity;
    private final WatchLanguage language;
    private final SharedPreferences preferences;
    private final MenuLocation location;
    private final BiFunction<String,Boolean,List<OpponentDirectory.Entry>> load;
    private final Row row;
    private final Map<String,String> searchNames = new HashMap<>();
    private final LinearLayout choices, rows;
    private final Button sort;
    private final Drawable sortArrow;
    private List<OpponentDirectory.Entry> roster;
    private String order;

    PlayerListPanel(MainActivity activity, LinearLayout parent, WatchLanguage language, SharedPreferences preferences,
                    MenuLocation location, BiFunction<String,Boolean,List<OpponentDirectory.Entry>> load, Row row) {
        this.activity = activity;
        this.language = language;
        this.preferences = preferences;
        this.location = location;
        this.load = load;
        this.row = row;
        order = preferences.getString("player_sort", "az");
        roster = load.apply(order, location.recentPlayersOnly);
        // Focusing the menu itself lets the keyboard stay closed until the search field is tapped.
        parent.setFocusableInTouchMode(true);
        activity.section(parent, "Search & sort");
        LinearLayout tools = column(parent);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xff171c25);
        background.setCornerRadius(activity.dp(15));
        tools.setBackground(background);
        int inset = activity.dp(6);
        tools.setPaddingRelative(inset, inset, inset, 0);
        EditText input = activity.menuInput(tools, activity.tr("Search players"), activity.tr("Search players"), InputType.TYPE_CLASS_TEXT);
        input.setText(location.playerQuery);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        CheckBox recency = new CheckBox(activity);
        recency.setText(activity.tr("Last 30 days"));
        recency.setContentDescription(activity.tr("Played in the last 30 days"));
        recency.setTextColor(MainActivity.MENU_CONTROL_TEXT);
        recency.setTextSize(14);
        recency.setMinHeight(activity.dp(48));
        recency.setButtonTintList(new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{location.section.accent, MainActivity.MENU_CONTROL_TEXT.getDefaultColor()}));
        recency.setChecked(location.recentPlayersOnly);
        tools.addView(recency, new LinearLayout.LayoutParams(-1, -2));
        sort = activity.navigationButton(tools, "", this::toggleChoices);
        sortArrow = activity.getDrawable(R.drawable.ic_disclosure_arrow).mutate();
        sortArrow.setTintList(MainActivity.MENU_CONTROL_TEXT);
        sort.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, sortArrow, null);
        choices = column(tools);
        setChoicesExpanded(false);
        activity.section(parent, "Saved players");
        rows = column(parent);
        updateSortLabel();
        render();
        recency.setOnCheckedChangeListener((button,checked) -> {
            location.recentPlayersOnly = checked;
            reload();
        });
        input.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                location.playerQuery = text.toString();
                location.playerPage = 0;
                render();
            }
            public void afterTextChanged(Editable text) {}
        });
    }

    private LinearLayout column(LinearLayout parent) {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        parent.addView(column, new LinearLayout.LayoutParams(-1, -2));
        return column;
    }

    private void updateSortLabel() {
        int index = order.equals("recent") ? 0 : order.equals("za") ? 2 : 1;
        sort.setText(language.format("Sort: {0}", "Sortierung: {0}", "Tri : {0}", "Orden: {0}", activity.tr(LABELS[index])));
    }

    private void toggleChoices() {
        if (choices.getVisibility() == View.VISIBLE) {
            setChoicesExpanded(false);
            return;
        }
        choices.removeAllViews();
        for (int i = 0; i < ORDERS.length; i++) {
            final String value = ORDERS[i];
            activity.choiceButton(choices, activity.tr(LABELS[i]), order.equals(value), () -> {
                if (!order.equals(value)) {
                    order = value;
                    preferences.edit().putString("player_sort", value).apply();
                    updateSortLabel();
                    reload();
                }
                setChoicesExpanded(false);
            });
        }
        setChoicesExpanded(true);
    }

    private void setChoicesExpanded(boolean expanded) {
        choices.setVisibility(expanded ? View.VISIBLE : View.GONE);
        sortArrow.setLevel(expanded ? 10000 : 0);
        sort.setStateDescription(expanded
                ? language.pick("Expanded", "Ausgeklappt", "Déplié", "Expandido")
                : language.pick("Collapsed", "Eingeklappt", "Replié", "Contraído"));
    }

    private void reload() {
        roster = load.apply(order, location.recentPlayersOnly);
        location.playerPage = 0;
        render();
    }

    private void render() {
        rows.removeAllViews();
        String query = OpponentDirectory.searchName(location.playerQuery, language.locale);
        List<OpponentDirectory.Entry> matches = new ArrayList<>();
        for (OpponentDirectory.Entry e : roster) {
            String name = searchNames.computeIfAbsent(e.id, id -> OpponentDirectory.searchName(e.name, language.locale));
            if (name.contains(query)) matches.add(e);
        }
        int last = Math.max(0, (matches.size() - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(location.playerPage, last));
        location.playerPage = page;
        if (matches.isEmpty()) activity.paragraph(rows, activity.tr(location.recentPlayersOnly && query.isEmpty() ? "No players in the last 30 days." : "No matching players"));
        for (int i = page * PAGE_SIZE; i < Math.min((page + 1) * PAGE_SIZE, matches.size()); i++)
            row.add(rows, matches.get(i));
        if (page > 0) activity.secondaryButton(rows, activity.tr("Previous names"), () -> changePage(page - 1));
        if (page < last) activity.secondaryButton(rows, activity.tr("More names"), () -> changePage(page + 1));
    }

    private void changePage(int page) {
        location.playerPage = page;
        render();
        rows.post(() -> {
            if (rows.isAttachedToWindow() && rows.getChildCount() > 0)
                rows.getChildAt(0).requestRectangleOnScreen(new android.graphics.Rect(0, 0,
                        rows.getWidth(), rows.getChildAt(0).getHeight()), true);
        });
    }
}
