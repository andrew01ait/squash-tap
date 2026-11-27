package at.andrew.squashtap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One visit to a panel. Returning to an ancestor restores its list and search. */
final class MenuLocation {
    final MenuLocation parent;
    final String key;
    String title;
    final MenuSection section;
    String playerQuery = "";
    int playerPage;
    boolean recentPlayersOnly;

    private MenuLocation(MenuLocation parent, String key, String title, MenuSection section) {
        this.parent = parent;
        this.key = key;
        this.title = title;
        this.section = section != null ? section : parent == null ? MenuSection.CURRENT_MATCH : parent.section;
    }

    static MenuLocation open(MenuLocation from, String key, String title, MenuSection section) {
        for (MenuLocation step = from; step != null; step = step.parent) {
            if (step.key.equals(key)) {
                step.title = title;
                return step;
            }
        }
        return new MenuLocation(from, key, title, section);
    }

    String path(java.util.function.Function<String,String> translate) {
        List<String> names = new ArrayList<>();
        for (MenuLocation step = this; step != null; step = step.parent)
            names.add(step.key.startsWith("player:") ? step.title : translate.apply(step.title));
        Collections.reverse(names);
        return String.join(" › ", names);
    }
}
