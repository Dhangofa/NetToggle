package com.dhangofa.networktoggle.ui;

import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.OrientationEventListener;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.dhangofa.networktoggle.MainActivity;
import com.dhangofa.networktoggle.R;

// Skeleton for future migration
public class MainNavigationHelper {
    private final MainActivity activity;

    public MainNavigationHelper(MainActivity activity) {
        this.activity = activity;
    }
}
