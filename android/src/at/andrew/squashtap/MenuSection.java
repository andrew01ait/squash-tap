package at.andrew.squashtap;

import android.content.res.ColorStateList;
import android.graphics.Color;
import androidx.core.graphics.ColorUtils;

/** Destination colours identify Home tiles, menu titles and their groups. */
enum MenuSection {
    HOME(0xff374555, 0xffd6e5f3),
    NEW_MATCH(0xff116748, 0xff95e5b8),
    CURRENT_MATCH(0xff165cb2, 0xff9fc9ff),
    SPORTS(0xff934117, 0xffffb17f),
    RESULTS(0xff006669, 0xff7fe5dc),
    PLAYERS(0xff67427d, 0xffdab0f3),
    STATISTICS(0xff775a10, 0xfff4d46f),
    SHARING(0xff8a3369, 0xffe8c5d9),
    FAVOURITES(0xff474e99, 0xffbac3ff),
    RULES(0xff516021, 0xffd7e58c),
    SETTINGS(0xff474747, 0xffd3d3d3);

    final int surface, accent, playerRow, selectedPlayerRow;
    final ColorStateList fill;

    MenuSection(int surface, int accent) {
        this.surface = surface;
        this.accent = accent;
        playerRow = ColorUtils.blendARGB(0xff080d17, surface, .32f);
        selectedPlayerRow = ColorUtils.blendARGB(0xff080d17, surface, .63f);
        fill = filled(surface);
    }

    static ColorStateList filled(int colour) {
        return states(colour, ColorUtils.blendARGB(colour, Color.WHITE, .08f),
                ColorUtils.blendARGB(colour, Color.BLACK, .42f));
    }

    static ColorStateList states(int normal, int active, int disabled) {
        return new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {android.R.attr.state_pressed},
                {android.R.attr.state_focused}, {}}, new int[]{disabled, active, active, normal});
    }

    static ColorStateList text(int normal, int disabled) {
        return new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}}, new int[]{disabled, normal});
    }
}
