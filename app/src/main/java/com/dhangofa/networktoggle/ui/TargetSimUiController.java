package com.dhangofa.networktoggle.ui;

/** Handles Target SIM selection, validation, separators, and the Auto SIM warning. */
import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.dhangofa.networktoggle.R;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.TargetSim;

import java.util.List;

public final class TargetSimUiController {
    private final Activity activity;
    private final AppPreferences prefs;
    private final Runnable changed;

    private final RadioGroup group;
    private final RadioButton auto;
    private final RadioButton sim1;
    private final RadioButton sim2;
    private final RadioButton simBoth;
    private final TextView warning;
    private final View sep1;
    private final View sep2;
    private final View sep3;
    private final View lockBadge;

    private boolean updating;
    private Boolean authorized = null;

    public TargetSimUiController(Activity activity, AppPreferences prefs, Runnable changed) {
        this.activity = activity;
        this.prefs = prefs;
        this.changed = changed;

        this.group = activity.findViewById(R.id.targetSimRadioGroup);
        this.auto = activity.findViewById(R.id.radioSimAuto);
        this.sim1 = activity.findViewById(R.id.radioSim1);
        this.sim2 = activity.findViewById(R.id.radioSim2);
        this.simBoth = activity.findViewById(R.id.radioSimBoth);
        this.warning = activity.findViewById(R.id.autoSimWarningText);
        this.lockBadge = activity.findViewById(R.id.targetSimLockBadge);
        this.sep1 = activity.findViewById(R.id.separatorAutoSim1);
        this.sep2 = activity.findViewById(R.id.separatorSim1Sim2);
        this.sep3 = activity.findViewById(R.id.separatorSim2Both);
    }

    public void initialize() {
        TargetSim targetSim = prefs.getTargetSim();

        if (targetSim == TargetSim.SIM_1) {
            sim1.setChecked(true);
        } else if (targetSim == TargetSim.SIM_2) {
            sim2.setChecked(true);
        } else if (targetSim == TargetSim.BOTH) {
            simBoth.setChecked(true);
        } else {
            auto.setChecked(true);
        }

        View.OnTouchListener lock = (v, e) -> {
            boolean isAuth = authorized != null && authorized;
            if (!isAuth && e.getAction() == MotionEvent.ACTION_DOWN) {
                Toast.makeText(activity, activity.getString(R.string.toast_auth_required_sim), Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        };

        auto.setOnTouchListener(lock);
        sim1.setOnTouchListener(lock);
        sim2.setOnTouchListener(lock);
        simBoth.setOnTouchListener(lock);

        group.setOnCheckedChangeListener((g, id) -> select(id));
        updateSeparators();
        updateAutoSimWarning();
    }

    private void select(int id) {
        if (updating) return;

        TargetSim targetSim = TargetSim.AUTO;
        if (id == R.id.radioSim1) {
            targetSim = TargetSim.SIM_1;
        } else if (id == R.id.radioSim2) {
            targetSim = TargetSim.SIM_2;
        } else if (id == R.id.radioSimBoth) {
            targetSim = TargetSim.BOTH;
        }

        if (targetSim != TargetSim.AUTO && !exists(targetSim)) {
            if (targetSim == TargetSim.BOTH) {
                Toast.makeText(activity, activity.getString(R.string.toast_both_sims_required), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, activity.getString(targetSim.getManualSlotIndex() == 0 ? R.string.toast_no_sim_slot_1 : R.string.toast_no_sim_slot_2), Toast.LENGTH_SHORT).show();
            }
            updating = true;
            auto.setChecked(true);
            updating = false;
            targetSim = TargetSim.AUTO;
        }

        updateSeparators();
        prefs.onTargetSimChanged(targetSim);
        updateAutoSimWarning();

        if (changed != null) {
            changed.run();
        }
    }

    private boolean exists(TargetSim target) {
        if (activity.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return true;
        }

        SubscriptionManager sm = activity.getSystemService(SubscriptionManager.class);
        if (sm == null) {
            return true;
        }

        try {
            List<SubscriptionInfo> infos = sm.getActiveSubscriptionInfoList();
            if (infos == null) return false;

            if (target == TargetSim.BOTH) {
                boolean hasSim1 = false;
                boolean hasSim2 = false;
                for (SubscriptionInfo info : infos) {
                    if (info.getSimSlotIndex() == 0) hasSim1 = true;
                    if (info.getSimSlotIndex() == 1) hasSim2 = true;
                }
                return hasSim1 && hasSim2;
            }

            for (SubscriptionInfo info : infos) {
                if (info.getSimSlotIndex() == target.getManualSlotIndex()) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return true;
        }
        return false;
    }

    public void setAuthorized(boolean value) {
        if (this.authorized != null && this.authorized == value) return;
        boolean animate = (this.authorized != null);
        this.authorized = value;
        float alpha = value ? 1.0f : 0.75f;
        View card = activity.findViewById(R.id.cardTargetSim);
        if (card != null) {
            if (animate) {
                if (value) {
                    float offset = 6f * activity.getResources().getDisplayMetrics().density;
                    card.setTranslationY(offset);
                    card.animate()
                            .alpha(alpha)
                            .translationY(0f)
                            .setDuration(320)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                } else {
                    card.animate()
                            .alpha(alpha)
                            .translationY(0f)
                            .setDuration(320)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                }
            } else {
                card.setAlpha(alpha);
                card.setTranslationY(0f);
            }
        }
        if (this.lockBadge != null) {
            if (value) {
                if (animate && lockBadge.getVisibility() == View.VISIBLE) {
                    this.lockBadge.animate()
                            .alpha(0f)
                            .scaleX(0.4f)
                            .scaleY(0.4f)
                            .setDuration(220)
                            .withEndAction(() -> {
                                this.lockBadge.setVisibility(View.GONE);
                                this.lockBadge.setScaleX(1f);
                                this.lockBadge.setScaleY(1f);
                                this.lockBadge.setAlpha(1f);
                            })
                            .start();
                } else {
                    this.lockBadge.setVisibility(View.GONE);
                }
            } else {
                if (animate && lockBadge.getVisibility() != View.VISIBLE) {
                    this.lockBadge.setVisibility(View.VISIBLE);
                    this.lockBadge.setAlpha(0f);
                    this.lockBadge.setScaleX(0.4f);
                    this.lockBadge.setScaleY(0.4f);
                    this.lockBadge.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(250)
                            .setInterpolator(new OvershootInterpolator(1.2f))
                            .start();
                } else {
                    this.lockBadge.setVisibility(View.VISIBLE);
                    this.lockBadge.setAlpha(1f);
                    this.lockBadge.setScaleX(1f);
                    this.lockBadge.setScaleY(1f);
                }
            }
        }
    }

    public void updateAutoSimWarning() {
        if (warning == null) return;

        boolean show = prefs.hasAutoSimError() && prefs.getTargetSim() == TargetSim.AUTO;
        warning.setVisibility(show ? View.VISIBLE : View.GONE);

        if (show) {
            warning.setText(R.string.warning_auto_sim_failed);
            warning.setTextColor(activity.getColor(R.color.status_error_text));
        }
    }

    private void updateSeparators() {
        int id = group.getCheckedRadioButtonId();

        if (id == -1) {
            sep1.setVisibility(View.VISIBLE);
            sep2.setVisibility(View.VISIBLE);
            sep3.setVisibility(View.VISIBLE);
        } else if (id == R.id.radioSimAuto) {
            sep1.setVisibility(View.INVISIBLE);
            sep2.setVisibility(View.VISIBLE);
            sep3.setVisibility(View.VISIBLE);
        } else if (id == R.id.radioSim1) {
            sep1.setVisibility(View.INVISIBLE);
            sep2.setVisibility(View.INVISIBLE);
            sep3.setVisibility(View.VISIBLE);
        } else if (id == R.id.radioSim2) {
            sep1.setVisibility(View.VISIBLE);
            sep2.setVisibility(View.INVISIBLE);
            sep3.setVisibility(View.INVISIBLE);
        } else if (id == R.id.radioSimBoth) {
            sep1.setVisibility(View.VISIBLE);
            sep2.setVisibility(View.VISIBLE);
            sep3.setVisibility(View.INVISIBLE);
        } else {
            sep1.setVisibility(View.VISIBLE);
            sep2.setVisibility(View.VISIBLE);
            sep3.setVisibility(View.VISIBLE);
        }
    }
}
