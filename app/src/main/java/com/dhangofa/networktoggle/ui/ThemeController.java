package com.dhangofa.networktoggle.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;

import com.dhangofa.networktoggle.R;

public class ThemeController {
    private final Activity activity;
    private final SharedPreferences prefs;
    private int currentThemeMode = -1;
    private ImageView btnThemeToggle;
    private View navIndicatorPill;

    public ThemeController(Activity activity, SharedPreferences prefs) {
        this.activity = activity;
        this.prefs = prefs;
        this.currentThemeMode = prefs.getInt("app_theme", 0);
        activity.setTheme(R.style.Theme_NetToggle);
    }

    public int getCurrentThemeMode() {
        return currentThemeMode;
    }

    public boolean isAmoled() {
        return currentThemeMode == 3;
    }

    public void bindViews(ImageView btnThemeToggle, View navIndicatorPill) {
        this.btnThemeToggle = btnThemeToggle;
        this.navIndicatorPill = navIndicatorPill;
        if (this.btnThemeToggle != null) {
            this.btnThemeToggle.setOnClickListener(v -> cycleThemeMode());
        }
        updateThemeIcon();
        applyThemeOverrides();
    }

    private void cycleThemeMode() {
        currentThemeMode = (currentThemeMode + 1) % 4;
        prefs.edit().putInt("app_theme", currentThemeMode).apply();
        activity.recreate();
    }

    private void updateThemeIcon() {
        if (btnThemeToggle == null) return;
        if (currentThemeMode == 0) {
            btnThemeToggle.setImageResource(R.drawable.ic_theme_auto);
        } else if (currentThemeMode == 1) {
            btnThemeToggle.setImageResource(R.drawable.ic_theme_light);
        } else if (currentThemeMode == 2) {
            btnThemeToggle.setImageResource(R.drawable.ic_theme_dark);
        } else {
            btnThemeToggle.setImageResource(R.drawable.ic_theme_amoled);
        }
    }

    
    public void configureStatusBar() {
        int flags;
        if (android.os.Build.VERSION.SDK_INT < 23) {
            return;
        }
        boolean isNight = (activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        Window window = activity.getWindow();
        if (this.currentThemeMode == 3) {
            window.setStatusBarColor(-16777216);
            window.setNavigationBarColor(-16777216);
        } else {
            window.setStatusBarColor(activity.getColor(R.color.card_surface));
            window.setNavigationBarColor(activity.getColor(R.color.surface_background));
        }
        int n = flags = isNight ? 0 : 8192; // SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (!isNight && android.os.Build.VERSION.SDK_INT >= 26) {
            flags |= 16; // SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    public void applyThemeOverrides() {
        View btnAdd = activity.findViewById(R.id.btnAddShortcut);
        if (btnAdd != null) {
            btnAdd.setBackgroundResource(currentThemeMode == 3 ? R.drawable.shape_fab_wavy_amoled : R.drawable.shape_fab_wavy);
            btnAdd.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(android.view.View view, android.graphics.Outline outline) {
                    int insetX = view.getWidth() / 8;
                    int insetY = view.getHeight() / 8;
                    outline.setOval(insetX, insetY, view.getWidth() - insetX, view.getHeight() - insetY);
                }
            });
        }
        ImageView iconAdd = activity.findViewById(R.id.iconAddShortcut);
        if (iconAdd != null && currentThemeMode == 3) {
            iconAdd.setImageTintList(ColorStateList.valueOf(activity.getColor(R.color.fab_add_shortcut_icon_amoled)));
        }
        if (currentThemeMode == 3) {
            int black = -16777216;
            View root = activity.findViewById(R.id.mainRoot);
            if (root != null) root.setBackgroundColor(black);
            
            Window window = activity.getWindow();
            if (window != null) {
                window.setStatusBarColor(black);
                window.setNavigationBarColor(black);
            }
            
            View topBar = activity.findViewById(R.id.topBar);
            if (topBar != null) topBar.setBackgroundColor(black);
            
            View bottomNavPill = activity.findViewById(R.id.bottomNavPill);
            if (bottomNavPill != null && bottomNavPill.getBackground() instanceof GradientDrawable) {
                GradientDrawable gd = (GradientDrawable) bottomNavPill.getBackground().mutate();
                gd.setColor(black);
                gd.setStroke((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1.0f, activity.getResources().getDisplayMetrics()), activity.getColor(R.color.nav_pill_stroke_amoled));
            }
            View edgeBarContainer = activity.findViewById(R.id.edgeBarContainer);
            if (edgeBarContainer != null) edgeBarContainer.setBackgroundColor(black);
            
            View cornerBlankBlock = activity.findViewById(R.id.cornerBlankBlock);
            if (cornerBlankBlock != null) cornerBlankBlock.setBackgroundColor(black);
            
            if (navIndicatorPill != null && navIndicatorPill.getBackground() instanceof GradientDrawable) {
                ((GradientDrawable) navIndicatorPill.getBackground().mutate())
                    .setColor(activity.getColor(R.color.nav_item_selected_bg_amoled));
            }
        }
    }
}
