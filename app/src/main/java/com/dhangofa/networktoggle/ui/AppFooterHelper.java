package com.dhangofa.networktoggle.ui;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.dhangofa.networktoggle.R;

public class AppFooterHelper {
    public static void setupFooter(Activity activity) {
        TextView appVersionText = activity.findViewById(R.id.appVersionText);
        if (appVersionText != null) {
            appVersionText.setText(activity.getString(R.string.app_version_format, getAppVersionName(activity)));
        }

        ImageView githubLink = activity.findViewById(R.id.githubLink);
        if (githubLink != null) {
            githubLink.setOnClickListener(v -> GuidesTabHelper.openUrl(activity, "https://github.com/Dhangofa/NetToggle"));
        }

        ImageView telegramLink = activity.findViewById(R.id.telegramLink);
        if (telegramLink != null) {
            telegramLink.setOnClickListener(v -> GuidesTabHelper.openUrl(activity, "https://t.me/dhangofas_projects_chat"));
        }

        View developerName = activity.findViewById(R.id.developerNameText);
        if (developerName != null) {
            developerName.setOnClickListener(v -> GuidesTabHelper.openUrl(activity, "https://github.com/Dhangofa"));
        }

        View appLicenseText = activity.findViewById(R.id.appLicenseText);
        if (appLicenseText != null) {
            appLicenseText.setOnClickListener(v -> GuidesTabHelper.openUrl(activity, "https://www.gnu.org/licenses/gpl-3.0.html"));
        }
    }

    private static String getAppVersionName(Activity activity) {
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            return info.versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }
}
