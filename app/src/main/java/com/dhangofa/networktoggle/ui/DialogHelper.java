package com.dhangofa.networktoggle.ui;

/**
 * Helper to build and show the various popup dialogs in the app
 * (like the permission bottom sheet or the diagnostic error dialog).
 */

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import com.dhangofa.networktoggle.R;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.telephony.SimResolver;
import com.dhangofa.networktoggle.util.DiagnosticReporter;

public final class DialogHelper {

    private DialogHelper() {}

    public static Dialog buildPermissionBottomSheet(Activity activity, Runnable onGrantClicked) {
        Dialog dialog = new Dialog(activity, R.style.TransparentBottomSheetStyle);
        View view = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_permission, null);

        view.findViewById(R.id.btnDismissPermission).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btnGrantPermission).setOnClickListener(v -> {
            dialog.dismiss();
            onGrantClicked.run();
        });

        dialog.setContentView(view);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return dialog;
    }

    public static Dialog buildDiagnosticDialog(Activity activity, AppPreferences appPreferences, SimResolver simResolver, Runnable onClose) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_diagnostic);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        TextView reportText = dialog.findViewById(R.id.diagnosticReportText);
        if (reportText != null) {
            String report = DiagnosticReporter.generateReport(appPreferences, simResolver);
            reportText.setText(report);
        }

        View btnClose = dialog.findViewById(R.id.btnCloseDiagnostic);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                dialog.dismiss();
                onClose.run();
            });
        }

        View btnCopy = dialog.findViewById(R.id.btnCopyDiagnostic);
        if (btnCopy != null) {
            btnCopy.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Activity.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Diagnostic Report", reportText.getText());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(activity, "Report copied to clipboard", Toast.LENGTH_SHORT).show();
            });
        }

        return dialog;
    }
}
