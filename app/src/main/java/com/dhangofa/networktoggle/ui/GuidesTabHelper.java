package com.dhangofa.networktoggle.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.dhangofa.networktoggle.R;

public class GuidesTabHelper {

    public static void setupTab(Activity activity) {
        setupGuideTile(activity, R.id.guideExecution, R.drawable.ic_terminal, activity.getString(R.string.guide_title_execution_mode), activity.getString(R.string.guide_desc_execution_mode), "https://github.com/Dhangofa/NetToggle/wiki/1.-Execution-Mode-Configuration", R.color.carousel_page1_bg, R.color.carousel_page1_accent);
        setupGuideTile(activity, R.id.guideSim, R.drawable.ic_sim_card, activity.getString(R.string.guide_title_target_sim), activity.getString(R.string.guide_desc_target_sim), "https://github.com/Dhangofa/NetToggle/wiki/2.-Target-SIM-Setup-&-Quick-Tile-Cycle-Guide", R.color.carousel_page2_bg, R.color.carousel_page2_accent);
        setupGuideTile(activity, R.id.guideTile, R.drawable.ic_quick_tile, activity.getString(R.string.guide_title_quick_tile), activity.getString(R.string.guide_desc_quick_tile), "https://github.com/Dhangofa/NetToggle/wiki/3.-Adding-the-Tile-to-Quick-Settings", R.color.carousel_page3_bg, R.color.carousel_page3_accent);
        setupGuideTile(activity, R.id.guideShortcuts, R.drawable.ic_magic_wand, activity.getString(R.string.guide_title_app_shortcuts), activity.getString(R.string.guide_desc_app_shortcuts), "https://github.com/Dhangofa/NetToggle/wiki/4.-App-Shortcuts-&-Built%E2%80%90in-OS-Routines", R.color.carousel_page4_bg, R.color.carousel_page4_accent);
        setupGuideTile(activity, R.id.guideBroadcast, R.drawable.ic_broadcast, activity.getString(R.string.guide_title_broadcasts), activity.getString(R.string.guide_desc_broadcasts), "https://github.com/Dhangofa/NetToggle/wiki/5.-Broadcast-Automation-(Tasker,-MacroDroid,-Automate)", R.color.carousel_page5_bg, R.color.carousel_page5_accent);
        setupGuideTile(activity, R.id.guideTroubleshoot, R.drawable.ic_help_outline, activity.getString(R.string.guide_title_troubleshooting), activity.getString(R.string.guide_desc_troubleshooting), "https://github.com/Dhangofa/NetToggle/wiki/Frequently-Asked-Questions-(FAQ)", R.color.carousel_page6_bg, R.color.carousel_page6_accent);
    }

    private static void setupGuideTile(Activity activity, int rootId, int iconRes, String title, String desc, String url, int bgTintRes, int accentRes) {
        View root = activity.findViewById(rootId);
        if (root == null) {
            return;
        }
        ImageView icon = root.findViewById(R.id.tileIcon);
        TextView titleText = root.findViewById(R.id.tileTitle);
        TextView descText = root.findViewById(R.id.tileDesc);
        View btn = root.findViewById(R.id.tileBtn);
        TextView btnText = root.findViewById(R.id.tileBtnText);
        ImageView btnIcon = root.findViewById(R.id.tileBtnIcon);
        
        int accentColor = activity.getColor(accentRes);
        int bgColor = activity.getColor(bgTintRes);
        
        if (icon != null) {
            icon.setImageResource(iconRes);
            icon.setColorFilter(accentColor);
        }
        if (titleText != null) {
            titleText.setText(title);
        }
        if (descText != null) {
            descText.setText(desc);
        }
        if (btn != null) {
            btn.setBackgroundTintList(ColorStateList.valueOf(bgColor));
            btn.setOnClickListener(v -> openUrl(activity, url));
        }
        if (btnText != null) {
            btnText.setTextColor(accentColor);
        }
        if (btnIcon != null) {
            btnIcon.setColorFilter(accentColor);
        }
        root.setOnClickListener(v -> {
            v.animate()
                .scaleX(1.04f)
                .scaleY(1.04f)
                .setDuration(90)
                .withEndAction(() -> {
                    v.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(120)
                        .start();
                })
                .start();
        });
    }

    public static void openUrl(Activity activity, String url) {
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            activity.startActivity(browserIntent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
