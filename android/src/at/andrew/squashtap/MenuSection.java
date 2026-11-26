package at.andrew.squashtap;

import android.content.res.ColorStateList;
import android.graphics.Color;
import androidx.core.graphics.ColorUtils;

/** Destination colours are shared by Home, menu links, headings and nested controls. */
enum MenuSection {
    HOME(0xff374555, 0xffd6e5f3),
    NEW_MATCH(0xff116748, 0xff95e5b8),
    CURRENT_MATCH(0xff165cb2, 0xff9fc9ff),
    SPORTS(0xff934117, 0xffffb17f),
    RESULTS(0xff006669, 0xff7fe5dc),
    PLAYERS(0xff67427d, 0xffdab0f3),
    STATISTICS(0xff775a10, 0xfff4d46f),
    SHARING(0xff8a3369, 0xfff9a6d4),
    FAVOURITES(0xff474e99, 0xffbac3ff),
    RULES(0xff516021, 0xffd7e58c),
    SETTINGS(0xff474747, 0xffd3d3d3);

    final int surface, accent, subtle, border;
    final ColorStateList fill, actionFill, plain, ink, linkInk;

    MenuSection(int surface, int accent) {
        this.surface = surface;
        this.accent = accent;
        subtle = ColorUtils.blendARGB(0xff080d17, surface, .35f);
        border = ColorUtils.blendARGB(0xff080d17, accent, .46f);
        fill = filled(surface);
        actionFill = filled(ColorUtils.blendARGB(surface, Color.BLACK, .22f));
        plain = states(Color.TRANSPARENT, subtle, Color.TRANSPARENT);
        ink = text(Color.WHITE, ColorUtils.blendARGB(accent, Color.WHITE, .18f));
        linkInk = text(accent, ColorUtils.blendARGB(accent, 0xff080d17, .26f));
    }

    static ColorStateList filled(int colour) {
        return states(colour, ColorUtils.blendARGB(colour, Color.WHITE, .08f),
                ColorUtils.blendARGB(colour, Color.BLACK, .42f));
    }

    private static ColorStateList states(int normal, int active, int disabled) {
        return new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {android.R.attr.state_pressed},
                {android.R.attr.state_focused}, {}}, new int[]{disabled, active, active, normal});
    }

    private static ColorStateList text(int normal, int disabled) {
        return new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}}, new int[]{disabled, normal});
    }
}
