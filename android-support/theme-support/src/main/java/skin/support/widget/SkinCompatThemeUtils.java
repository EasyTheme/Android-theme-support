package skin.support.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.widget.TintTypedArray;
import android.util.TypedValue;

import skin.support.content.res.SkinCompatResources;

import static skin.support.widget.SkinCompatHelper.INVALID_ID;

/**
 * Created by ximsfei on 2017/3/25.
 */

public class SkinCompatThemeUtils {
    private static final int[] APPCOMPAT_COLOR_PRIMARY_ATTRS = {
            android.support.v7.appcompat.R.attr.colorPrimary
    };
    private static final int[] APPCOMPAT_COLOR_PRIMARY_DARK_ATTRS = {
            android.support.v7.appcompat.R.attr.colorPrimaryDark
    };
    private static final int[] APPCOMPAT_COLOR_ACCENT_ATTRS = {
            android.support.v7.appcompat.R.attr.colorAccent
    };

    public static int getColorPrimaryResId(Context context) {
        return getResId(context, APPCOMPAT_COLOR_PRIMARY_ATTRS);
    }

    public static int getColorPrimaryDarkResId(Context context) {
        return getResId(context, APPCOMPAT_COLOR_PRIMARY_DARK_ATTRS);
    }

    public static int getColorAccentResId(Context context) {
        return getResId(context, APPCOMPAT_COLOR_ACCENT_ATTRS);
    }

    public static int getTextColorPrimaryResId(Context context) {
        return getResId(context, new int[]{android.R.attr.textColorPrimary});
    }

    public static int getColorPrimary(Context context) {
        int colorPrimary = 0;
        TypedArray a = SkinCompatResources.getInstance()
                .obtainStyledAttributes(context, APPCOMPAT_COLOR_PRIMARY_ATTRS);
        if (a.hasValue(0)) {
            colorPrimary = a.getColor(0, 0);
        }
        a.recycle();
        return colorPrimary;
    }

    public static ColorStateList getColorAccentList(Context context) {
        ColorStateList colorAccent = null;
        TypedArray a = SkinCompatResources.getInstance()
                .obtainStyledAttributes(context, APPCOMPAT_COLOR_ACCENT_ATTRS);
        if (a.hasValue(0)) {
            colorAccent = a.getColorStateList(0);
        }
        a.recycle();
        return colorAccent;
    }

    public static int getColorAccent(Context context) {
        int colorAccent = 0;
        TypedArray a = SkinCompatResources.getInstance()
                .obtainStyledAttributes(context, APPCOMPAT_COLOR_ACCENT_ATTRS);
        if (a.hasValue(0)) {
            colorAccent = a.getColor(0, 0);
        }
        a.recycle();
        return colorAccent;
    }

    public static int getStatusBarColor(Context context) {
        int color = 0;
        TypedArray a;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            a = SkinCompatResources.getInstance()
                    .obtainStyledAttributes(context, new int[]{android.R.attr.statusBarColor});
            if (a.hasValue(0)) {
                color = a.getColor(0, 0);
            }
            a.recycle();
        }
        if (color == 0) {
            a = SkinCompatResources.getInstance()
                    .obtainStyledAttributes(context, APPCOMPAT_COLOR_PRIMARY_ATTRS);
            if (a.hasValue(0)) {
                color = a.getColor(0, 0);
            }
            a.recycle();
        }
        return color;
    }

    public static Drawable getWindowBackgroundDrawable(Context context) {
        Drawable drawable = null;
        TypedArray a = SkinCompatResources.getInstance()
                .obtainStyledAttributes(context, new int[]{android.R.attr.windowBackground});
        if (a.hasValue(0)) {
            drawable = a.getDrawable(0);
        }
        a.recycle();
        return drawable;
    }

    private static int getResId(Context context, int[] attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs);
        final int resId = a.getResourceId(0, INVALID_ID);
        a.recycle();
        return resId;
    }


    private static final ThreadLocal<TypedValue> TL_TYPED_VALUE = new ThreadLocal<>();

    static final int[] DISABLED_STATE_SET = new int[]{-android.R.attr.state_enabled};
    static final int[] FOCUSED_STATE_SET = new int[]{android.R.attr.state_focused};
    static final int[] ACTIVATED_STATE_SET = new int[]{android.R.attr.state_activated};
    static final int[] PRESSED_STATE_SET = new int[]{android.R.attr.state_pressed};
    static final int[] CHECKED_STATE_SET = new int[]{android.R.attr.state_checked};
    static final int[] SELECTED_STATE_SET = new int[]{android.R.attr.state_selected};
    static final int[] NOT_PRESSED_OR_FOCUSED_STATE_SET = new int[]{
            -android.R.attr.state_pressed, -android.R.attr.state_focused};
    static final int[] EMPTY_STATE_SET = new int[0];

    private static final int[] TEMP_ARRAY = new int[1];

    public static ColorStateList createDisabledStateList(int textColor, int disabledTextColor) {
        // Now create a new ColorStateList with the default color, and the new disabled
        // color
        final int[][] states = new int[2][];
        final int[] colors = new int[2];
        int i = 0;

        // Disabled state
        states[i] = DISABLED_STATE_SET;
        colors[i] = disabledTextColor;
        i++;

        // Default state
        states[i] = EMPTY_STATE_SET;
        colors[i] = textColor;
        i++;

        return new ColorStateList(states, colors);
    }

    public static int getThemeAttrColor(Context context, int attr) {
        TEMP_ARRAY[0] = attr;
        TypedArray a = SkinCompatResources.getInstance().obtainStyledAttributes(context, TEMP_ARRAY);
        try {
            return a.getColor(0, 0);
        } finally {
            a.recycle();
        }
    }

    public static ColorStateList getThemeAttrColorStateList(Context context, int attr) {
        TEMP_ARRAY[0] = attr;
        TypedArray a = SkinCompatResources.getInstance().obtainStyledAttributes(context, TEMP_ARRAY);
        try {
            return a.getColorStateList(0);
        } finally {
            a.recycle();
        }
    }

    public static int getDisabledThemeAttrColor(Context context, int attr) {
        final ColorStateList csl = getThemeAttrColorStateList(context, attr);
        if (csl != null && csl.isStateful()) {
            // If the CSL is stateful, we'll assume it has a disabled state and use it
            return csl.getColorForState(DISABLED_STATE_SET, csl.getDefaultColor());
        } else {
            // Else, we'll generate the color using disabledAlpha from the theme

            final TypedValue tv = getTypedValue();
            // Now retrieve the disabledAlpha value from the theme
            SkinCompatResources.getInstance().newCompatTheme(context).resolveAttribute(android.R.attr.disabledAlpha, tv, true);
            final float disabledAlpha = tv.getFloat();

            return getThemeAttrColor(context, attr, disabledAlpha);
        }
    }

    private static TypedValue getTypedValue() {
        TypedValue typedValue = TL_TYPED_VALUE.get();
        if (typedValue == null) {
            typedValue = new TypedValue();
            TL_TYPED_VALUE.set(typedValue);
        }
        return typedValue;
    }

    static int getThemeAttrColor(Context context, int attr, float alpha) {
        final int color = getThemeAttrColor(context, attr);
        final int originalAlpha = Color.alpha(color);
        return ColorUtils.setAlphaComponent(color, Math.round(originalAlpha * alpha));
    }
}
