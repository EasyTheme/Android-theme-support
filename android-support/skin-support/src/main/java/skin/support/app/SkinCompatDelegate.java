package skin.support.app;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.view.LayoutInflaterFactory;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.VectorEnabledTintResources;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import skin.support.widget.SkinCompatSupportable;

/**
 * Created by ximsfei on 2017/1/9.
 */

public class SkinCompatDelegate implements LayoutInflaterFactory {
    private final Activity mActivity;
    private SkinCompatViewInflater mSkinCompatViewInflater;
    private List<WeakReference<SkinCompatSupportable>> mSkinHelpers = new ArrayList<>();

    private SkinCompatDelegate(Activity activity) {
        mActivity = activity;
    }

    @Override
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        View view = callActivityOnCreateView(parent, name, context, attrs);

        if (view == null) {
            view = createView(parent, name, context, attrs);
        }

        if (view instanceof SkinCompatSupportable) {
            mSkinHelpers.add(new WeakReference<>((SkinCompatSupportable) view));
        }

        return view;
    }

    View callActivityOnCreateView(View parent, String name, Context context, AttributeSet attrs) {
        // Let the Activity's LayoutInflater.Factory try and handle it
        if (mActivity.getWindow().getCallback() instanceof LayoutInflater.Factory) {
            final View result = ((LayoutInflater.Factory) mActivity.getWindow().getCallback())
                    .onCreateView(name, context, attrs);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    public View createView(View parent, final String name, @NonNull Context context,
                           @NonNull AttributeSet attrs) {
        final boolean isPre21 = Build.VERSION.SDK_INT < 21;

        if (mSkinCompatViewInflater == null) {
            mSkinCompatViewInflater = new SkinCompatViewInflater();
        }

        // We only want the View to inherit its context if we're running pre-v21
        final boolean inheritContext = isPre21 && shouldInheritContext((ViewParent) parent);

        return mSkinCompatViewInflater.createView(parent, name, context, attrs, inheritContext,
                isPre21, /* Only read android:theme pre-L (L+ handles this anyway) */
                true, /* Read read app:theme as a fallback at all times for legacy reasons */
                VectorEnabledTintResources.shouldBeUsed() /* Only tint wrap the context if enabled */
        );
    }

    private boolean shouldInheritContext(ViewParent parent) {
        if (parent == null) {
            // The initial parent is null so just return false
            return false;
        }
        final View windowDecor = mActivity.getWindow().getDecorView();
        while (true) {
            if (parent == null) {
                // Bingo. We've hit a view which has a null parent before being terminated from
                // the loop. This is (most probably) because it's the root view in an inflation
                // call, therefore we should inherit. This works as the inflated layout is only
                // added to the hierarchy at the end of the inflate() call.
                return true;
            } else if (parent == windowDecor || !(parent instanceof View)
                    || ViewCompat.isAttachedToWindow((View) parent)) {
                // We have either hit the window's decor view, a parent which isn't a View
                // (i.e. ViewRootImpl), or an attached view, so we know that the original parent
                // is currently added to the view hierarchy. This means that it has not be
                // inflated in the current inflate() call and we should not inherit the context.
                return false;
            }
            parent = parent.getParent();
        }
    }

    public static SkinCompatDelegate create(Activity activity) {
        return new SkinCompatDelegate(activity);
    }

    public void applySkin() {
        if (mSkinHelpers != null && !mSkinHelpers.isEmpty()) {
            for (WeakReference ref : mSkinHelpers) {
                if (ref != null && ref.get() != null) {
                    ((SkinCompatSupportable) ref.get()).applySkin();
                }
            }
        }
    }
}
