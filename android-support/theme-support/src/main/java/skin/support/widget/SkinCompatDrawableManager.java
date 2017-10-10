package skin.support.widget;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.graphics.drawable.AnimatedVectorDrawableCompat;
import android.support.graphics.drawable.VectorDrawableCompat;
import android.support.v4.graphics.ColorUtils;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.util.ArrayMap;
import android.support.v4.util.LongSparseArray;
import android.support.v4.util.LruCache;
import android.support.v4.util.SparseArrayCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

import skin.support.R;
import skin.support.content.res.SkinCompatResources;

import static android.support.v4.graphics.ColorUtils.compositeColors;
import static skin.support.widget.SkinCompatThemeUtils.getDisabledThemeAttrColor;
import static skin.support.widget.SkinCompatThemeUtils.getThemeAttrColor;
import static skin.support.widget.SkinCompatThemeUtils.getThemeAttrColorStateList;

public class SkinCompatDrawableManager {

    private interface InflateDelegate {
        Drawable createFromXmlInner(@NonNull Context context, @NonNull XmlPullParser parser,
                                    @NonNull AttributeSet attrs, @Nullable Resources.Theme theme);
    }

    private static final String TAG = "SkinCompatDrawableManager";
    private static final boolean DEBUG = false;
    private static final PorterDuff.Mode DEFAULT_MODE = PorterDuff.Mode.SRC_IN;
    private static final String SKIP_DRAWABLE_TAG = "appcompat_skip_skip";

    private static final String PLATFORM_VD_CLAZZ = "android.graphics.drawable.VectorDrawable";

    private static SkinCompatDrawableManager INSTANCE;

    public static SkinCompatDrawableManager get() {
        if (INSTANCE == null) {
            INSTANCE = new SkinCompatDrawableManager();
            installDefaultInflateDelegates(INSTANCE);
        }
        return INSTANCE;
    }

    private static void installDefaultInflateDelegates(@NonNull SkinCompatDrawableManager manager) {
        // This sdk version check will affect src:appCompat code path.
        // Although VectorDrawable exists in Android framework from Lollipop, AppCompat will use the
        // VectorDrawableCompat before Nougat to utilize the bug fixes in VectorDrawableCompat.
        if (Build.VERSION.SDK_INT < 24) {
            manager.addDelegate("vector", new VdcInflateDelegate());
            if (Build.VERSION.SDK_INT >= 11) {
                // AnimatedVectorDrawableCompat only works on API v11+
                manager.addDelegate("animated-vector", new AvdcInflateDelegate());
            }
        }
    }

    private static final ColorFilterLruCache COLOR_FILTER_CACHE = new ColorFilterLruCache(6);

    /**
     * Drawables which should be tinted with the value of {@code R.attr.colorControlNormal},
     * using the default mode using a raw color filter.
     */
    private static final int[] COLORFILTER_TINT_COLOR_CONTROL_NORMAL = {
            R.drawable.abc_textfield_search_default_mtrl_alpha,
            R.drawable.abc_textfield_default_mtrl_alpha,
            R.drawable.abc_ab_share_pack_mtrl_alpha
    };

    /**
     * Drawables which should be tinted with the value of {@code R.attr.colorControlNormal}, using
     * {@link DrawableCompat}'s tinting functionality.
     */
    private static final int[] TINT_COLOR_CONTROL_NORMAL = {
            R.drawable.abc_ic_commit_search_api_mtrl_alpha,
            R.drawable.abc_seekbar_tick_mark_material,
            R.drawable.abc_ic_menu_share_mtrl_alpha,
            R.drawable.abc_ic_menu_copy_mtrl_am_alpha,
            R.drawable.abc_ic_menu_cut_mtrl_alpha,
            R.drawable.abc_ic_menu_selectall_mtrl_alpha,
            R.drawable.abc_ic_menu_paste_mtrl_am_alpha
    };

    /**
     * Drawables which should be tinted with the value of {@code R.attr.colorControlActivated},
     * using a color filter.
     */
    private static final int[] COLORFILTER_COLOR_CONTROL_ACTIVATED = {
            R.drawable.abc_textfield_activated_mtrl_alpha,
            R.drawable.abc_textfield_search_activated_mtrl_alpha,
            R.drawable.abc_cab_background_top_mtrl_alpha,
            R.drawable.abc_text_cursor_material,
            R.drawable.abc_text_select_handle_left_mtrl_dark,
            R.drawable.abc_text_select_handle_middle_mtrl_dark,
            R.drawable.abc_text_select_handle_right_mtrl_dark,
            R.drawable.abc_text_select_handle_left_mtrl_light,
            R.drawable.abc_text_select_handle_middle_mtrl_light,
            R.drawable.abc_text_select_handle_right_mtrl_light
    };

    /**
     * Drawables which should be tinted with the value of {@code android.R.attr.colorBackground},
     * using the {@link android.graphics.PorterDuff.Mode#MULTIPLY} mode and a color filter.
     */
    private static final int[] COLORFILTER_COLOR_BACKGROUND_MULTIPLY = {
            R.drawable.abc_popup_background_mtrl_mult,
            R.drawable.abc_cab_background_internal_bg,
            R.drawable.abc_menu_hardkey_panel_mtrl_mult
    };

    /**
     * Drawables which should be tinted using a state list containing values of
     * {@code R.attr.colorControlNormal} and {@code R.attr.colorControlActivated}
     */
    private static final int[] TINT_COLOR_CONTROL_STATE_LIST = {
            R.drawable.abc_tab_indicator_material,
            R.drawable.abc_textfield_search_material
    };

    /**
     * Drawables which should be tinted using a state list containing values of
     * {@code R.attr.colorControlNormal} and {@code R.attr.colorControlActivated} for the checked
     * state.
     */
    private static final int[] TINT_CHECKABLE_BUTTON_LIST = {
            R.drawable.abc_btn_check_material,
            R.drawable.abc_btn_radio_material
    };

    private WeakHashMap<Context, SparseArrayCompat<ColorStateList>> mTintLists;
    private ArrayMap<String, InflateDelegate> mDelegates;
    private SparseArrayCompat<String> mKnownDrawableIdTags;

    private final Object mDrawableCacheLock = new Object();
    private final WeakHashMap<Context, LongSparseArray<WeakReference<Drawable.ConstantState>>>
            mDrawableCaches = new WeakHashMap<>(0);

    private TypedValue mTypedValue;

    private boolean mHasCheckedVectorDrawableSetup;

    public Drawable getDrawable(@NonNull Context context, @DrawableRes int resId) {
        return getDrawable(context, resId, false);
    }

    Drawable getDrawable(@NonNull Context context, @DrawableRes int resId,
                         boolean failIfNotKnown) {
        checkVectorDrawableSetup(context);

        Drawable drawable = loadDrawableFromDelegates(context, resId);
        if (drawable == null) {
            drawable = createDrawableIfNeeded(context, resId);
        }
        if (drawable == null) {
            drawable = SkinCompatResources.getInstance().getDrawable(resId);
        }

        if (drawable != null) {
            // Tint it if needed
            drawable = tintDrawable(context, resId, failIfNotKnown, drawable);
        }
        if (drawable != null) {
            // See if we need to 'fix' the drawable
            SkinCompatDrawableUtils.fixDrawable(drawable);
        }
        return drawable;
    }

    public void onConfigurationChanged(@NonNull Context context) {
        synchronized (mDrawableCacheLock) {
            LongSparseArray<WeakReference<Drawable.ConstantState>> cache = mDrawableCaches.get(context);
            if (cache != null) {
                // Crude, but we'll just clear the cache when the configuration changes
                cache.clear();
            }
        }
    }

    private static long createCacheKey(TypedValue tv) {
        return (((long) tv.assetCookie) << 32) | tv.data;
    }

    private Drawable createDrawableIfNeeded(@NonNull Context context,
                                            @DrawableRes final int resId) {
        if (mTypedValue == null) {
            mTypedValue = new TypedValue();
        }
        final TypedValue tv = mTypedValue;
        SkinCompatResources.getInstance().getValue(resId, tv, true);
        final long key = createCacheKey(tv);

        Drawable dr = getCachedDrawable(context, key);
        if (dr != null) {
            // If we got a cached drawable, return it
            return dr;
        }

        // Else we need to try and create one...
        if (resId == R.drawable.abc_cab_background_top_material) {
            dr = new LayerDrawable(new Drawable[]{
                    getDrawable(context, R.drawable.abc_cab_background_internal_bg),
                    getDrawable(context, R.drawable.abc_cab_background_top_mtrl_alpha)
            });
        }

        if (dr != null) {
            dr.setChangingConfigurations(tv.changingConfigurations);
            // If we reached here then we created a new drawable, add it to the cache
            addDrawableToCache(context, key, dr);
        }

        return dr;
    }

    private Drawable tintDrawable(@NonNull Context context, @DrawableRes int resId,
                                  boolean failIfNotKnown, @NonNull Drawable drawable) {
        final ColorStateList tintList = getTintList(context, resId);
        if (tintList != null) {
            // First mutate the Drawable, then wrap it and set the tint list
            if (SkinCompatDrawableUtils.canSafelyMutateDrawable(drawable)) {
                drawable = drawable.mutate();
            }
            drawable = DrawableCompat.wrap(drawable);
            DrawableCompat.setTintList(drawable, tintList);

            // If there is a blending mode specified for the drawable, use it
            final PorterDuff.Mode tintMode = getTintMode(resId);
            if (tintMode != null) {
                DrawableCompat.setTintMode(drawable, tintMode);
            }
        } else if (resId == R.drawable.abc_seekbar_track_material) {
            LayerDrawable ld = (LayerDrawable) drawable;
            setPorterDuffColorFilter(ld.findDrawableByLayerId(android.R.id.background),
                    getThemeAttrColor(context, R.attr.colorControlNormal), DEFAULT_MODE);
            setPorterDuffColorFilter(ld.findDrawableByLayerId(android.R.id.secondaryProgress),
                    getThemeAttrColor(context, R.attr.colorControlNormal), DEFAULT_MODE);
            setPorterDuffColorFilter(ld.findDrawableByLayerId(android.R.id.progress),
                    getThemeAttrColor(context, R.attr.colorControlActivated), DEFAULT_MODE);
        } else if (resId == R.drawable.abc_ratingbar_material
                || resId == R.drawable.abc_ratingbar_indicator_material
                || resId == R.drawable.abc_ratingbar_small_material) {
            LayerDrawable ld = (LayerDrawable) drawable;
            setPorterDuffColorFilter(ld.findDrawableByLayerId(android.R.id.background),
                    getDisabledThemeAttrColor(context, R.attr.colorControlNormal),
                    DEFAULT_MODE);
            setPorterDuffColorFilter(ld.findDrawableByLayerId(android.R.id.secondaryProgress),
                    getThemeAttrColor(context, R.attr.colorControlActivated), DEFAULT_MODE);
            setPorterDuffColorFilter(ld.findDrawableByLayerId(android.R.id.progress),
                    getThemeAttrColor(context, R.attr.colorControlActivated), DEFAULT_MODE);
        } else {
            final boolean tinted = tintDrawableUsingColorFilter(context, resId, drawable);
            if (!tinted && failIfNotKnown) {
                // If we didn't tint using a ColorFilter, and we're set to fail if we don't
                // know the id, return null
                drawable = null;
            }
        }
        return drawable;
    }

    private Drawable loadDrawableFromDelegates(@NonNull Context context, @DrawableRes int resId) {
        if (mDelegates != null && !mDelegates.isEmpty()) {
            if (mKnownDrawableIdTags != null) {
                final String cachedTagName = mKnownDrawableIdTags.get(resId);
                if (SKIP_DRAWABLE_TAG.equals(cachedTagName)
                        || (cachedTagName != null && mDelegates.get(cachedTagName) == null)) {
                    // If we don't have a delegate for the drawable tag, or we've been set to
                    // skip it, fail fast and return null
                    if (DEBUG) {
                        Log.d(TAG, "[loadDrawableFromDelegates] Skipping drawable: "
                                + context.getResources().getResourceName(resId));
                    }
                    return null;
                }
            } else {
                // Create an id cache as we'll need one later
                mKnownDrawableIdTags = new SparseArrayCompat<>();
            }

            if (mTypedValue == null) {
                mTypedValue = new TypedValue();
            }
            final TypedValue tv = mTypedValue;
            SkinCompatResources.getInstance().getValue(resId, tv, true);

            final long key = createCacheKey(tv);

            Drawable dr = getCachedDrawable(context, key);
            if (dr != null) {
                if (DEBUG) {
                    Log.i(TAG, "[loadDrawableFromDelegates] Returning cached drawable: " +
                            context.getResources().getResourceName(resId));
                }
                // We have a cached drawable, return it!
                return dr;
            }

            if (tv.string != null && tv.string.toString().endsWith(".xml")) {
                // If the resource is an XML file, let's try and parse it
                try {
                    final XmlPullParser parser = SkinCompatResources.getInstance().getXml(resId);
                    final AttributeSet attrs = Xml.asAttributeSet(parser);
                    int type;
                    while ((type = parser.next()) != XmlPullParser.START_TAG &&
                            type != XmlPullParser.END_DOCUMENT) {
                        // Empty loop
                    }
                    if (type != XmlPullParser.START_TAG) {
                        throw new XmlPullParserException("No start tag found");
                    }

                    final String tagName = parser.getName();
                    // Add the tag name to the cache
                    mKnownDrawableIdTags.append(resId, tagName);

                    // Now try and find a delegate for the tag name and inflate if found
                    final InflateDelegate delegate = mDelegates.get(tagName);
                    if (delegate != null) {
                        dr = delegate.createFromXmlInner(context, parser, attrs,
                                context.getTheme());
                    }
                    if (dr != null) {
                        // Add it to the drawable cache
                        dr.setChangingConfigurations(tv.changingConfigurations);
                        if (addDrawableToCache(context, key, dr) && DEBUG) {
                            Log.i(TAG, "[loadDrawableFromDelegates] Saved drawable to cache: " +
                                    context.getResources().getResourceName(resId));
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Exception while inflating drawable", e);
                }
            }
            if (dr == null) {
                // If we reach here then the delegate inflation of the resource failed. Mark it as
                // bad so we skip the id next time
                mKnownDrawableIdTags.append(resId, SKIP_DRAWABLE_TAG);
            }
            return dr;
        }

        return null;
    }

    private Drawable getCachedDrawable(@NonNull final Context context, final long key) {
        synchronized (mDrawableCacheLock) {
            final LongSparseArray<WeakReference<Drawable.ConstantState>> cache
                    = mDrawableCaches.get(context);
            if (cache == null) {
                return null;
            }

            final WeakReference<Drawable.ConstantState> wr = cache.get(key);
            if (wr != null) {
                // We have the key, and the secret
                Drawable.ConstantState entry = wr.get();
                if (entry != null) {
                    return entry.newDrawable(context.getResources());
                } else {
                    // Our entry has been purged
                    cache.delete(key);
                }
            }
        }
        return null;
    }

    private boolean addDrawableToCache(@NonNull final Context context, final long key,
                                       @NonNull final Drawable drawable) {
        final Drawable.ConstantState cs = drawable.getConstantState();
        if (cs != null) {
            synchronized (mDrawableCacheLock) {
                LongSparseArray<WeakReference<Drawable.ConstantState>> cache = mDrawableCaches.get(context);
                if (cache == null) {
                    cache = new LongSparseArray<>();
                    mDrawableCaches.put(context, cache);
                }
                cache.put(key, new WeakReference<>(cs));
            }
            return true;
        }
        return false;
    }

    Drawable onDrawableLoadedFromResources(@NonNull Context context,
                                           @NonNull SkinCompatVectorEnabledTintResources resources, @DrawableRes final int resId) {
        Drawable drawable = loadDrawableFromDelegates(context, resId);
        if (drawable == null) {
            drawable = resources.superGetDrawable(resId);
        }
        if (drawable != null) {
            return tintDrawable(context, resId, false, drawable);
        }
        return null;
    }

    static boolean tintDrawableUsingColorFilter(@NonNull Context context,
                                                @DrawableRes final int resId, @NonNull Drawable drawable) {
        PorterDuff.Mode tintMode = DEFAULT_MODE;
        boolean colorAttrSet = false;
        int colorAttr = 0;
        int alpha = -1;

        if (arrayContains(COLORFILTER_TINT_COLOR_CONTROL_NORMAL, resId)) {
            colorAttr = R.attr.colorControlNormal;
            colorAttrSet = true;
        } else if (arrayContains(COLORFILTER_COLOR_CONTROL_ACTIVATED, resId)) {
            colorAttr = R.attr.colorControlActivated;
            colorAttrSet = true;
        } else if (arrayContains(COLORFILTER_COLOR_BACKGROUND_MULTIPLY, resId)) {
            colorAttr = android.R.attr.colorBackground;
            colorAttrSet = true;
            tintMode = PorterDuff.Mode.MULTIPLY;
        } else if (resId == R.drawable.abc_list_divider_mtrl_alpha) {
            colorAttr = android.R.attr.colorForeground;
            colorAttrSet = true;
            alpha = Math.round(0.16f * 255);
        } else if (resId == R.drawable.abc_dialog_material_background) {
            colorAttr = android.R.attr.colorBackground;
            colorAttrSet = true;
        }

        if (colorAttrSet) {
            if (SkinCompatDrawableUtils.canSafelyMutateDrawable(drawable)) {
                drawable = drawable.mutate();
            }

            final int color = getThemeAttrColor(context, colorAttr);
            drawable.setColorFilter(getPorterDuffColorFilter(color, tintMode));

            if (alpha != -1) {
                drawable.setAlpha(alpha);
            }

            if (DEBUG) {
                Log.d(TAG, "[tintDrawableUsingColorFilter] Tinted "
                        + context.getResources().getResourceName(resId) +
                        " with color: #" + Integer.toHexString(color));
            }
            return true;
        }
        return false;
    }

    private void addDelegate(@NonNull String tagName, @NonNull InflateDelegate delegate) {
        if (mDelegates == null) {
            mDelegates = new ArrayMap<>();
        }
        mDelegates.put(tagName, delegate);
    }

    private void removeDelegate(@NonNull String tagName, @NonNull InflateDelegate delegate) {
        if (mDelegates != null && mDelegates.get(tagName) == delegate) {
            mDelegates.remove(tagName);
        }
    }

    private static boolean arrayContains(int[] array, int value) {
        for (int id : array) {
            if (id == value) {
                return true;
            }
        }
        return false;
    }

    static PorterDuff.Mode getTintMode(final int resId) {
        PorterDuff.Mode mode = null;

        if (resId == R.drawable.abc_switch_thumb_material) {
            mode = PorterDuff.Mode.MULTIPLY;
        }

        return mode;
    }

    public ColorStateList getTintList(@NonNull Context context, @DrawableRes int resId) {
        // Try the cache first (if it exists)
        ColorStateList tint = getTintListFromCache(context, resId);

        if (tint == null) {
            // ...if the cache did not contain a color state list, try and create one
            if (resId == R.drawable.abc_edit_text_material) {
//                tint = SkinCompatResources.getInstance().getColorStateList(R.color.abc_tint_edittext);
                tint = createPressedColorStateList(context, getThemeAttrColor(context, R.attr.colorControlActivated));
            } else if (resId == R.drawable.abc_switch_track_mtrl_alpha) {
//                tint = SkinCompatResources.getInstance().getColorStateList(R.color.abc_tint_switch_track);
                int colorForeground = getThemeAttrColor(context, android.R.attr.colorForeground);
                int colorControlActivated = getThemeAttrColor(context, R.attr.colorControlActivated);
                tint = createCheckableColorStateList(ColorUtils.setAlphaComponent(colorForeground, Math.round(Color.alpha(colorForeground) * 0.1f)),
                        ColorUtils.setAlphaComponent(colorControlActivated, Math.round(Color.alpha(colorControlActivated) * 0.3f)),
                        ColorUtils.setAlphaComponent(colorForeground, Math.round(Color.alpha(colorForeground) * 0.3f)));
            } else if (resId == R.drawable.abc_switch_thumb_material) {
//                tint = SkinCompatResources.getInstance().getColorStateList(R.color.abc_tint_switch_thumb);
                int colorControlActivated = getThemeAttrColor(context, R.attr.colorControlActivated);
                tint = createCheckableColorStateList(getDisabledThemeAttrColor(context, R.attr.colorSwitchThumbNormal),
                        ColorUtils.setAlphaComponent(colorControlActivated, Math.round(Color.alpha(colorControlActivated) * 0.3f)),
                        getThemeAttrColor(context, R.attr.colorSwitchThumbNormal));
            } else if (resId == R.drawable.abc_btn_default_mtrl_shape) {
                tint = createDefaultButtonColorStateList(context);
            } else if (resId == R.drawable.abc_btn_borderless_material) {
                tint = createBorderlessButtonColorStateList(context);
            } else if (resId == R.drawable.abc_btn_colored_material) {
                tint = createColoredButtonColorStateList(context);
            } else if (resId == R.drawable.abc_spinner_mtrl_am_alpha
                    || resId == R.drawable.abc_spinner_textfield_background_material) {
//                tint = SkinCompatResources.getInstance().getColorStateList(R.color.abc_tint_spinner);
                tint = createPressedColorStateList(context, getThemeAttrColor(context, R.attr.colorControlActivated));
            } else if (arrayContains(TINT_COLOR_CONTROL_NORMAL, resId)) {
                tint = getThemeAttrColorStateList(context, R.attr.colorControlNormal);
            } else if (arrayContains(TINT_COLOR_CONTROL_STATE_LIST, resId)) {
//                tint = SkinCompatResources.getInstance().getColorStateList(R.color.abc_tint_default);
                tint = createDefaultColorStateList(context, getThemeAttrColor(context, R.attr.colorControlNormal));
            } else if (arrayContains(TINT_CHECKABLE_BUTTON_LIST, resId)) {
                tint = createCheckableColorStateList(context, getThemeAttrColor(context, R.attr.colorButtonNormal));
            } else if (resId == R.drawable.abc_seekbar_thumb_material) {
//                tint = SkinCompatResources.getInstance().getColorStateList(R.color.abc_tint_seek_thumb);
                tint = createSeekThumbColorStateList(context, getThemeAttrColor(context, R.attr.colorControlActivated));
            }

            if (tint != null) {
                addTintListToCache(context, resId, tint);
            }
        }
        return tint;
    }

    private ColorStateList getTintListFromCache(@NonNull Context context, @DrawableRes int resId) {
        if (mTintLists != null) {
            final SparseArrayCompat<ColorStateList> tints = mTintLists.get(context);
            return tints != null ? tints.get(resId) : null;
        }
        return null;
    }

    private void addTintListToCache(@NonNull Context context, @DrawableRes int resId,
                                    @NonNull ColorStateList tintList) {
        if (mTintLists == null) {
            mTintLists = new WeakHashMap<>();
        }
        SparseArrayCompat<ColorStateList> themeTints = mTintLists.get(context);
        if (themeTints == null) {
            themeTints = new SparseArrayCompat<>();
            mTintLists.put(context, themeTints);
        }
        themeTints.append(resId, tintList);
    }

    private ColorStateList createDefaultButtonColorStateList(@NonNull Context context) {
        return createButtonColorStateList(context,
                getThemeAttrColor(context, R.attr.colorButtonNormal));
    }

    private ColorStateList createBorderlessButtonColorStateList(@NonNull Context context) {
        // We ignore the custom tint for borderless buttons
        return createButtonColorStateList(context, Color.TRANSPARENT);
    }

    private ColorStateList createColoredButtonColorStateList(@NonNull Context context) {
        return createButtonColorStateList(context,
                getThemeAttrColor(context, R.attr.colorAccent));
    }

    private ColorStateList createButtonColorStateList(@NonNull final Context context,
                                                      @ColorInt final int baseColor) {
        final int[][] states = new int[4][];
        final int[] colors = new int[4];
        int i = 0;

        final int colorControlHighlight = getThemeAttrColor(context, R.attr.colorControlHighlight);
        final int disabledColor = getDisabledThemeAttrColor(context, R.attr.colorButtonNormal);

        // Disabled state
        states[i] = SkinCompatThemeUtils.DISABLED_STATE_SET;
        colors[i] = disabledColor;
        i++;

        states[i] = SkinCompatThemeUtils.PRESSED_STATE_SET;
        colors[i] = compositeColors(colorControlHighlight, baseColor);
        i++;

        states[i] = SkinCompatThemeUtils.FOCUSED_STATE_SET;
        colors[i] = compositeColors(colorControlHighlight, baseColor);
        i++;

        // Default enabled state
        states[i] = SkinCompatThemeUtils.EMPTY_STATE_SET;
        colors[i] = baseColor;
        i++;

        return new ColorStateList(states, colors);
    }

    private ColorStateList createPressedColorStateList(@NonNull final Context context,
                                                       @ColorInt final int baseColor) {
        final int[][] states = new int[4][];
        final int[] colors = new int[4];
        int i = 0;

        final int colorControlNormal = getThemeAttrColor(context, R.attr.colorControlNormal);
        final int disabledColor = getDisabledThemeAttrColor(context, R.attr.colorControlNormal);

        // Disabled state
        states[i] = SkinCompatThemeUtils.DISABLED_STATE_SET;
        colors[i] = disabledColor;
        i++;

        states[i] = SkinCompatThemeUtils.PRESSED_STATE_SET;
        colors[i] = compositeColors(colorControlNormal, baseColor);
        i++;

        states[i] = SkinCompatThemeUtils.FOCUSED_STATE_SET;
        colors[i] = compositeColors(colorControlNormal, baseColor);
        i++;

        // Default enabled state
        states[i] = SkinCompatThemeUtils.EMPTY_STATE_SET;
        colors[i] = baseColor;
        i++;

        return new ColorStateList(states, colors);
    }

    private ColorStateList createCheckableColorStateList(@NonNull final Context context,
                                                         @ColorInt final int baseColor) {
        final int[][] states = new int[3][];
        final int[] colors = new int[3];
        int i = 0;

        final int colorControlActivated = getThemeAttrColor(context, R.attr.colorControlActivated);
        final int disabledColor = getDisabledThemeAttrColor(context, R.attr.colorControlNormal);

        // Disabled state
        states[i] = SkinCompatThemeUtils.DISABLED_STATE_SET;
        colors[i] = disabledColor;
        i++;

        states[i] = SkinCompatThemeUtils.CHECKED_STATE_SET;
        colors[i] = compositeColors(colorControlActivated, baseColor);
        i++;

        // Default enabled state
        states[i] = SkinCompatThemeUtils.EMPTY_STATE_SET;
        colors[i] = baseColor;
        i++;

        return new ColorStateList(states, colors);
    }

    private ColorStateList createCheckableColorStateList(@ColorInt final int disabledColor,
                                                         @ColorInt final int colorControlActivated,
                                                         @ColorInt final int baseColor) {
        final int[][] states = new int[3][];
        final int[] colors = new int[3];
        int i = 0;

        // Disabled state
        states[i] = SkinCompatThemeUtils.DISABLED_STATE_SET;
        colors[i] = disabledColor;
        i++;

        states[i] = SkinCompatThemeUtils.CHECKED_STATE_SET;
        colors[i] = compositeColors(colorControlActivated, baseColor);
        i++;

        // Default enabled state
        states[i] = SkinCompatThemeUtils.EMPTY_STATE_SET;
        colors[i] = baseColor;
        i++;

        return new ColorStateList(states, colors);
    }

    private ColorStateList createSeekThumbColorStateList(@NonNull Context context,
                                                         @ColorInt final int baseColor) {
        final int[][] states = new int[2][];
        final int[] colors = new int[2];
        int i = 0;

        final int disabledColor = getDisabledThemeAttrColor(context, R.attr.colorControlActivated);

        // Disabled state
        states[i] = SkinCompatThemeUtils.DISABLED_STATE_SET;
        colors[i] = disabledColor;
        i++;

        // Default enabled state
        states[i] = SkinCompatThemeUtils.EMPTY_STATE_SET;
        colors[i] = baseColor;
        i++;

        return new ColorStateList(states, colors);
    }

    private ColorStateList createDefaultColorStateList(@NonNull Context context,
                                                       @ColorInt int baseColor) {
        final int[][] states = new int[7][];
        final int[] colors = new int[7];
        int i = 0;

        final int disabledColor = getDisabledThemeAttrColor(context, R.attr.colorControlNormal);
        final int colorControlActivated = getThemeAttrColor(context, R.attr.colorControlActivated);

        // Disabled state
        states[i] = SkinCompatThemeUtils.DISABLED_STATE_SET;
        colors[i] = disabledColor;
        i++;

        states[i] = SkinCompatThemeUtils.PRESSED_STATE_SET;
        colors[i] = compositeColors(colorControlActivated, baseColor);
        i++;

        states[i] = SkinCompatThemeUtils.FOCUSED_STATE_SET;
        colors[i] = compositeColors(colorControlActivated, baseColor);
        i++;

        states[i] = SkinCompatThemeUtils.ACTIVATED_STATE_SET;
        colors[i] = compositeColors(colorControlActivated, baseColor);
        i++;

        states[i] = SkinCompatThemeUtils.SELECTED_STATE_SET;
        colors[i] = compositeColors(colorControlActivated, baseColor);
        i++;

        states[i] = SkinCompatThemeUtils.CHECKED_STATE_SET;
        colors[i] = compositeColors(colorControlActivated, baseColor);
        i++;

        // Default enabled state
        states[i] = SkinCompatThemeUtils.EMPTY_STATE_SET;
        colors[i] = baseColor;
        i++;

        return new ColorStateList(states, colors);
    }

    private static class ColorFilterLruCache extends LruCache<Integer, PorterDuffColorFilter> {

        public ColorFilterLruCache(int maxSize) {
            super(maxSize);
        }

        PorterDuffColorFilter get(int color, PorterDuff.Mode mode) {
            return get(generateCacheKey(color, mode));
        }

        PorterDuffColorFilter put(int color, PorterDuff.Mode mode, PorterDuffColorFilter filter) {
            return put(generateCacheKey(color, mode), filter);
        }

        private static int generateCacheKey(int color, PorterDuff.Mode mode) {
            int hashCode = 1;
            hashCode = 31 * hashCode + color;
            hashCode = 31 * hashCode + mode.hashCode();
            return hashCode;
        }
    }

    static void tintDrawable(Drawable drawable, SkinCompatTintInfo tint, int[] state) {
        if (SkinCompatDrawableUtils.canSafelyMutateDrawable(drawable)
                && drawable.mutate() != drawable) {
            Log.d(TAG, "Mutated drawable is not the same instance as the input.");
            return;
        }

        if (tint.mHasTintList || tint.mHasTintMode) {
            drawable.setColorFilter(createTintFilter(
                    tint.mHasTintList ? tint.mTintList : null,
                    tint.mHasTintMode ? tint.mTintMode : DEFAULT_MODE,
                    state));
        } else {
            drawable.clearColorFilter();
        }

        if (Build.VERSION.SDK_INT <= 23) {
            // Pre-v23 there is no guarantee that a state change will invoke an invalidation,
            // so we force it ourselves
            drawable.invalidateSelf();
        }
    }

    private static PorterDuffColorFilter createTintFilter(ColorStateList tint,
                                                          PorterDuff.Mode tintMode, final int[] state) {
        if (tint == null || tintMode == null) {
            return null;
        }
        final int color = tint.getColorForState(state, Color.TRANSPARENT);
        return getPorterDuffColorFilter(color, tintMode);
    }

    public static PorterDuffColorFilter getPorterDuffColorFilter(int color, PorterDuff.Mode mode) {
        // First, lets see if the cache already contains the color filter
        PorterDuffColorFilter filter = COLOR_FILTER_CACHE.get(color, mode);

        if (filter == null) {
            // Cache miss, so create a color filter and add it to the cache
            filter = new PorterDuffColorFilter(color, mode);
            COLOR_FILTER_CACHE.put(color, mode, filter);
        }

        return filter;
    }

    private static void setPorterDuffColorFilter(Drawable d, int color, PorterDuff.Mode mode) {
        if (SkinCompatDrawableUtils.canSafelyMutateDrawable(d)) {
            d = d.mutate();
        }
        d.setColorFilter(getPorterDuffColorFilter(color, mode == null ? DEFAULT_MODE : mode));
    }

    private void checkVectorDrawableSetup(@NonNull Context context) {
        if (mHasCheckedVectorDrawableSetup) {
            // We've already checked so return now...
            return;
        }
        // Here we will check that a known Vector drawable resource inside AppCompat can be
        // correctly decoded
        mHasCheckedVectorDrawableSetup = true;
        final Drawable d = getDrawable(context, R.drawable.abc_vector_test);
        if (d == null || !isVectorDrawable(d)) {
            mHasCheckedVectorDrawableSetup = false;
            throw new IllegalStateException("This app has been built with an incorrect "
                    + "configuration. Please configure your build for VectorDrawableCompat.");
        }
    }

    private static boolean isVectorDrawable(@NonNull Drawable d) {
        return d instanceof VectorDrawableCompat
                || PLATFORM_VD_CLAZZ.equals(d.getClass().getName());
    }

    private static class VdcInflateDelegate implements InflateDelegate {
        VdcInflateDelegate() {
        }

        @SuppressLint("NewApi")
        @Override
        public Drawable createFromXmlInner(@NonNull Context context, @NonNull XmlPullParser parser,
                                           @NonNull AttributeSet attrs, @Nullable Resources.Theme theme) {
            try {
                return VectorDrawableCompat
                        .createFromXmlInner(SkinCompatResources.getInstance().getSkinResources(), parser, attrs, SkinCompatResources.getInstance().newCompatTheme(context).getTheme());
            } catch (Exception e) {
                Log.e("VdcInflateDelegate", "Exception while inflating <vector>", e);
                return null;
            }
        }
    }

    @RequiresApi(11)
    @TargetApi(11)
    private static class AvdcInflateDelegate implements InflateDelegate {
        AvdcInflateDelegate() {
        }

        @SuppressLint("NewApi")
        @Override
        public Drawable createFromXmlInner(@NonNull Context context, @NonNull XmlPullParser parser,
                                           @NonNull AttributeSet attrs, @Nullable Resources.Theme theme) {
            try {
                return AnimatedVectorDrawableCompat
                        .createFromXmlInner(context, SkinCompatResources.getInstance().getSkinResources(), parser, attrs, SkinCompatResources.getInstance().newCompatTheme(context).getTheme());
            } catch (Exception e) {
                Log.e("AvdcInflateDelegate", "Exception while inflating <animated-vector>", e);
                return null;
            }
        }
    }

    public void reset() {
        mDrawableCaches.clear();
        if (mTintLists != null) {
            mTintLists.clear();
        }
        COLOR_FILTER_CACHE.evictAll();
    }
}
