package com.dhangofa.networktoggle.ui;

import android.util.TypedValue;
import android.view.Surface;
import android.os.Build;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import com.dhangofa.networktoggle.MainActivity;
import com.dhangofa.networktoggle.R;

public class MainNavigationController {
    private final MainActivity activity;
    private int currentTabIndex = 0;
    private int savedAutomationSubTab = -1;
    private View bottomNavPill;
    private View navIndicatorPill;
    private View mainContentFrame;
    private float currentScrollOffset = 0f;
    private ValueAnimator pageScrollAnimator;
    private boolean isDraggingPage = false;
    private VelocityTracker velocityTracker;
    private int touchSlop = 0;
    private boolean isSwipeDisabledForCurrentTouch = false;
    private float touchDownX = 0f;
    private float touchDownY = 0f;
    
    private View tabHome;
    private View tabAutomation;
    private View tabGuides;
    
    private LinearLayout navHome;
    private LinearLayout navAutomation;
    private LinearLayout navGuides;
    private TextView textNavHome;
    private TextView textNavAutomation;
    private TextView textNavGuides;
    private ImageView iconNavHome;
    private ImageView iconNavAutomation;
    private ImageView iconNavGuides;
    
    private final Handler keyboardHandler = new Handler(Looper.getMainLooper());
    private Runnable showNavPillRunnable;
    private boolean isKeyboardCurrentlyOpen = false;
    
    private int fullTextWidthHome = -1;
    private int fullTextWidthAuto = -1;
    private int fullTextWidthGuides = -1;
    private int fullMarginStart = -1;
    private int baseNavWidth = -1;
    
    private ViewTreeObserver.OnGlobalLayoutListener keyboardLayoutListener;
    private final int[] reusableTouchLoc = new int[2];
    private OrientationEventListener orientationListener;
    private int lastOrientationRotation = -1;
    private boolean isDestroyed = false;

    public MainNavigationController(MainActivity activity, int savedTab, int savedAutomationSubTab) {
        this.activity = activity;
        this.currentTabIndex = savedTab;
        this.savedAutomationSubTab = savedAutomationSubTab;
    }
    


    public int getCurrentTabIndex() {
        return currentTabIndex;
    }

    
    public void destroy() {
        MainNavigationController.this.isDestroyed = true;
        if (this.showNavPillRunnable != null) {
            this.keyboardHandler.removeCallbacks(this.showNavPillRunnable);
            this.showNavPillRunnable = null;
        }
        if (this.keyboardLayoutListener != null) {
            View mainRoot = activity.findViewById(R.id.mainRoot);
            if (mainRoot != null) {
                mainRoot.getViewTreeObserver().removeOnGlobalLayoutListener(this.keyboardLayoutListener);
            }
            this.keyboardLayoutListener = null;
        }
        if (this.pageScrollAnimator != null && this.pageScrollAnimator.isRunning()) {
            this.pageScrollAnimator.cancel();
        }
        if (this.velocityTracker != null) {
            this.velocityTracker.recycle();
            this.velocityTracker = null;
        }
        if (this.orientationListener != null) {
            this.orientationListener.disable();
            this.orientationListener = null;
        }
    }

    public void initialize() {
        this.bottomNavPill = MainNavigationController.this.activity.findViewById(R.id.bottomNavPill);
        if (this.bottomNavPill == null) {
            this.bottomNavPill = MainNavigationController.this.activity.findViewById(R.id.edgeBarContainer);
        }
        this.navIndicatorPill = MainNavigationController.this.activity.findViewById(R.id.navIndicatorPill);
        this.mainContentFrame = MainNavigationController.this.activity.findViewById(R.id.mainContentFrame);
        this.touchSlop = ViewConfiguration.get(MainNavigationController.this.activity).getScaledTouchSlop();
        if (MainNavigationController.this.activity.isAmoled() && this.navIndicatorPill != null && this.navIndicatorPill.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) this.navIndicatorPill.getBackground().mutate())
                .setColor(MainNavigationController.this.activity.getColor(R.color.nav_item_selected_bg_amoled));
        }
        this.tabHome = MainNavigationController.this.activity.findViewById(R.id.tabHome);
        this.tabAutomation = MainNavigationController.this.activity.findViewById(R.id.tabAutomation);
        this.tabGuides = MainNavigationController.this.activity.findViewById(R.id.tabGuides);
        this.navHome = (LinearLayout)MainNavigationController.this.activity.findViewById(R.id.navHome);
        this.navAutomation = (LinearLayout)MainNavigationController.this.activity.findViewById(R.id.navAutomation);
        this.navGuides = (LinearLayout)MainNavigationController.this.activity.findViewById(R.id.navGuides);
        this.textNavHome = (TextView)MainNavigationController.this.activity.findViewById(R.id.textNavHome);
        this.textNavAutomation = (TextView)MainNavigationController.this.activity.findViewById(R.id.textNavAutomation);
        this.textNavGuides = (TextView)MainNavigationController.this.activity.findViewById(R.id.textNavGuides);
        this.iconNavHome = (ImageView)MainNavigationController.this.activity.findViewById(R.id.iconNavHome);
        this.iconNavAutomation = (ImageView)MainNavigationController.this.activity.findViewById(R.id.iconNavAutomation);
        this.iconNavGuides = (ImageView)MainNavigationController.this.activity.findViewById(R.id.iconNavGuides);
        if (this.navHome == null) {
            return;
        }
        this.navHome.setOnClickListener(v -> this.switchTab(0));
        this.navAutomation.setOnClickListener(v -> this.switchTab(1));
        this.navGuides.setOnClickListener(v -> this.switchTab(2));
        RadioGroup automationSegmentGroup = (RadioGroup)MainNavigationController.this.activity.findViewById(R.id.automationSegmentGroup);
        View pageShortcuts = MainNavigationController.this.activity.findViewById(R.id.pageShortcuts);
        View pageBroadcasts = MainNavigationController.this.activity.findViewById(R.id.pageBroadcasts);
        if (automationSegmentGroup != null) {
            if (this.savedAutomationSubTab != -1) {
                automationSegmentGroup.check(this.savedAutomationSubTab);
            }
            int initialCheckedId = automationSegmentGroup.getCheckedRadioButtonId();
            if (pageShortcuts != null) {
                pageShortcuts.setVisibility(initialCheckedId == R.id.radioShortcuts ? View.VISIBLE : View.GONE);
            }
            if (pageBroadcasts != null) {
                pageBroadcasts.setVisibility(initialCheckedId == R.id.radioBroadcasts ? View.VISIBLE : View.GONE);
            }
            automationSegmentGroup.setOnCheckedChangeListener((group, checkedId) -> {
                if (pageShortcuts != null) {
                    pageShortcuts.setVisibility(checkedId == R.id.radioShortcuts ? View.VISIBLE : View.GONE);
                }
                if (pageBroadcasts != null) {
                    pageBroadcasts.setVisibility(checkedId == R.id.radioBroadcasts ? View.VISIBLE : View.GONE);
                }
                if (MainNavigationController.this.activity.getShortcutTabHelper() != null) {
                    MainNavigationController.this.activity.getShortcutTabHelper().updateFabVisibility();
                }
            });
        }

        if (MainNavigationController.this.isLandscape()) {
            MainNavigationController.this.updateLandscapeEdgeNavProgress((float) this.currentTabIndex);
        } else {
            this.ensureNavDimensions();
            this.updateNavPillProgress((float) this.currentTabIndex);
        }
        if (this.bottomNavPill != null) {
            this.bottomNavPill.post(() -> {
                if (MainNavigationController.this.isLandscape()) {
                    MainNavigationController.this.updateLandscapeEdgeNavProgress((float) this.currentTabIndex);
                } else {
                    this.ensureNavDimensions();
                    this.updateNavPillProgress((float) this.currentTabIndex);
                }
            });
        }
        
        if (this.mainContentFrame != null) {
            this.mainContentFrame.post(() -> {
                if (MainNavigationController.this.isLandscape()) {
                    MainNavigationController.this.updateEdgeBarPosition();
                    if (this.tabHome != null) {
                        this.tabHome.setTranslationX(0f);
                        this.tabHome.setVisibility(this.currentTabIndex == 0 ? android.view.View.VISIBLE : android.view.View.GONE);
                        this.tabHome.setAlpha(this.currentTabIndex == 0 ? 1f : 0f);
                    }
                    if (this.tabAutomation != null) {
                        this.tabAutomation.setTranslationX(0f);
                        this.tabAutomation.setVisibility(this.currentTabIndex == 1 ? android.view.View.VISIBLE : android.view.View.GONE);
                        this.tabAutomation.setAlpha(this.currentTabIndex == 1 ? 1f : 0f);
                    }
                    if (this.tabGuides != null) {
                        this.tabGuides.setTranslationX(0f);
                        this.tabGuides.setVisibility(this.currentTabIndex == 2 ? android.view.View.VISIBLE : android.view.View.GONE);
                        this.tabGuides.setAlpha(this.currentTabIndex == 2 ? 1f : 0f);
                    }
                    MainNavigationController.this.updateLandscapeEdgeNavProgress((float) this.currentTabIndex);
                    MainNavigationController.this.onTabSettled(this.currentTabIndex);
                } else {
                    int width = this.mainContentFrame.getWidth();
                    if (width <= 0) {
                        width = this.activity.getResources().getDisplayMetrics().widthPixels;
                    }
                    this.currentScrollOffset = (float) this.currentTabIndex * width;
                    MainNavigationController.this.applyScrollOffset(this.currentScrollOffset);
                    MainNavigationController.this.onTabSettled(this.currentTabIndex);
                }
            });
        }
        this.setupLandscapeHomeHeightSync();
    }

    public void setupLandscapeHomeHeightSync() {
        if (!isLandscape()) {
            return;
        }
        View tabHomeView = this.tabHome != null ? this.tabHome : this.activity.findViewById(R.id.tabHome);
        if (tabHomeView == null) {
            return;
        }
        View leftCol = tabHomeView.findViewById(R.id.homeLeftColumn);
        View rightCol = tabHomeView.findViewById(R.id.homeRightColumn);
        View cardTargetSim = tabHomeView.findViewById(R.id.cardTargetSim);
        View cardTileCycle = tabHomeView.findViewById(R.id.cardTileCycle);
        if (leftCol == null || rightCol == null || cardTileCycle == null) {
            return;
        }

        tabHomeView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (!MainNavigationController.this.isLandscape() || MainNavigationController.this.isDestroyed) {
                    return;
                }
                int leftH = leftCol.getHeight();
                int rightH = rightCol.getHeight();
                int maxH = Math.max(leftH, rightH);
                if (maxH <= 0) {
                    return;
                }

                int simH = cardTargetSim != null ? cardTargetSim.getHeight() : 0;
                int margin = (int) (12 * MainNavigationController.this.activity.getResources().getDisplayMetrics().density);
                int targetTileCycleH = maxH - simH - margin;
                if (targetTileCycleH > 0 && cardTileCycle.getHeight() < targetTileCycleH) {
                    cardTileCycle.setMinimumHeight(targetTileCycleH);
                }
            }
        });
    }

    public void setupKeyboardListener() {
        View mainRoot = MainNavigationController.this.activity.findViewById(R.id.mainRoot);
        if (mainRoot == null) {
            return;
        }

        this.keyboardLayoutListener = () -> {
            if (MainNavigationController.this.isDestroyed) {
                return;
            }
            Rect r = new Rect();
            mainRoot.getWindowVisibleDisplayFrame(r);
            int screenHeight = mainRoot.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;
            boolean isKeyboardOpen = keypadHeight > screenHeight * 0.15;

            if (isKeyboardOpen) {
                if (!this.isKeyboardCurrentlyOpen) {
                    this.isKeyboardCurrentlyOpen = true;
                    if (MainNavigationController.this.activity.getShortcutTabHelper() != null) {
                        MainNavigationController.this.activity.getShortcutTabHelper().setKeyboardOpen(true);
                    }
                    View btnAdd = MainNavigationController.this.activity.findViewById(R.id.btnAddShortcut);
                    if (btnAdd != null) {
                        btnAdd.animate().cancel();
                        btnAdd.setVisibility(View.GONE);
                    }
                    if (!MainNavigationController.this.isLandscape()) {
                        if (this.showNavPillRunnable != null) {
                            this.keyboardHandler.removeCallbacks(this.showNavPillRunnable);
                            this.showNavPillRunnable = null;
                        }
                        if (this.bottomNavPill != null) {
                            this.bottomNavPill.animate().cancel();
                            this.bottomNavPill.setVisibility(View.GONE);
                        }
                    }
                }
            } else {
                if (this.isKeyboardCurrentlyOpen || (!MainNavigationController.this.isLandscape() && this.bottomNavPill != null && this.bottomNavPill.getVisibility() == View.GONE)) {
                    this.isKeyboardCurrentlyOpen = false;
                    if (MainNavigationController.this.activity.getShortcutTabHelper() != null) {
                        MainNavigationController.this.activity.getShortcutTabHelper().setKeyboardOpen(false);
                    }
                    if (!MainNavigationController.this.isLandscape()) {
                        if (this.showNavPillRunnable != null) {
                            this.keyboardHandler.removeCallbacks(this.showNavPillRunnable);
                        }
                        this.showNavPillRunnable = () -> {
                            if (MainNavigationController.this.isDestroyed || this.bottomNavPill == null || this.isKeyboardCurrentlyOpen) {
                                return;
                            }
                            int offset = this.bottomNavPill.getHeight();
                            if (offset <= 0) {
                                offset = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 80, MainNavigationController.this.activity.getResources().getDisplayMetrics());
                            }
                            if (this.bottomNavPill.getVisibility() != View.VISIBLE) {
                                this.bottomNavPill.setVisibility(View.VISIBLE);
                                this.bottomNavPill.setTranslationY((float) offset);
                                this.bottomNavPill.setAlpha(0f);
                                this.bottomNavPill.animate()
                                        .translationY(0f)
                                        .alpha(1f)
                                        .setDuration(220)
                                        .setInterpolator(new DecelerateInterpolator())
                                        .start();
                            }
                            if (MainNavigationController.this.activity.getShortcutTabHelper() != null) {
                                MainNavigationController.this.activity.getShortcutTabHelper().updateFabVisibility();
                            }
                            View btnAdd = MainNavigationController.this.activity.findViewById(R.id.btnAddShortcut);
                            if (btnAdd != null && btnAdd.getVisibility() == View.VISIBLE) {
                                btnAdd.setTranslationY((float) offset);
                                btnAdd.setAlpha(0f);
                                btnAdd.animate()
                                        .translationY(0f)
                                        .alpha(1f)
                                        .setDuration(220)
                                        .setInterpolator(new DecelerateInterpolator())
                                        .start();
                            }
                        };
                        // Delaying by 180ms allows the system IME dismiss animation and window layout to settle before the nav bar glides up
                        this.keyboardHandler.postDelayed(this.showNavPillRunnable, 180);
                    } else {
                        if (MainNavigationController.this.activity.getShortcutTabHelper() != null) {
                            MainNavigationController.this.activity.getShortcutTabHelper().updateFabVisibility();
                        }
                    }
                }
            }
        };
        mainRoot.getViewTreeObserver().addOnGlobalLayoutListener(this.keyboardLayoutListener);
    }

    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (MainNavigationController.this.isLandscape()) {
            return MainNavigationController.this.activity.superDispatchTouchEvent(ev);
        }
        if (this.mainContentFrame == null) {
            return MainNavigationController.this.activity.superDispatchTouchEvent(ev);
        }
        int width = this.mainContentFrame.getWidth();
        if (width <= 0) {
            width = MainNavigationController.this.activity.getResources().getDisplayMetrics().widthPixels;
        }

        int action = ev.getActionMasked();

        if (action == MotionEvent.ACTION_DOWN) {
            this.isSwipeDisabledForCurrentTouch = this.isTouchInsideInteractiveChild(ev.getRawX(), ev.getRawY());
            android.util.Log.d("SwipeDebug", "ACTION_DOWN disabled=" + this.isSwipeDisabledForCurrentTouch + " x=" + ev.getRawX() + " y=" + ev.getRawY());

            this.isDraggingPage = false;
            this.touchDownX = ev.getRawX();
            this.touchDownY = ev.getRawY();
            if (this.velocityTracker == null) {
                this.velocityTracker = VelocityTracker.obtain();
            } else {
                this.velocityTracker.clear();
            }
            this.velocityTracker.addMovement(ev);
            if (this.pageScrollAnimator != null && this.pageScrollAnimator.isRunning()) {
                this.pageScrollAnimator.cancel();
            }
        }

        if (this.isSwipeDisabledForCurrentTouch) {
            return MainNavigationController.this.activity.superDispatchTouchEvent(ev);
        }

        if (this.velocityTracker != null) {
            this.velocityTracker.addMovement(ev);
        }


        if (action == MotionEvent.ACTION_MOVE) {
            float deltaX = ev.getRawX() - this.touchDownX;
            float deltaY = ev.getRawY() - this.touchDownY;
            android.util.Log.d("SwipeDebug", "ACTION_MOVE deltaX=" + deltaX + " deltaY=" + deltaY + " dragging=" + this.isDraggingPage);


            if (!this.isDraggingPage) {
                if (Math.abs(deltaX) > this.touchSlop && Math.abs(deltaX) > Math.abs(deltaY) * 1.25f) {
                    boolean canDrag = true;
                    if (this.currentTabIndex == 0 && deltaX > 0) {
                        canDrag = false;
                    } else if (this.currentTabIndex == 2 && deltaX < 0) {
                        canDrag = false;
                    }



                    if (canDrag) {
                        android.util.Log.d("SwipeDebug", "DRAG START!");


                        this.isDraggingPage = true;
                        View currentFocus = MainNavigationController.this.activity.getCurrentFocus();
                        if (currentFocus != null) {
                            InputMethodManager imm = (InputMethodManager) MainNavigationController.this.activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                            if (imm != null) {
                                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
                            }
                            currentFocus.clearFocus();
                        }
                        MotionEvent cancelEvent = MotionEvent.obtain(ev);
                        cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
                        MainNavigationController.this.activity.superDispatchTouchEvent(cancelEvent);
                        cancelEvent.recycle();
                    }
                }
            }

            if (this.isDraggingPage) {
                float targetOffset = (float) this.currentTabIndex * width - deltaX;
                targetOffset = Math.max(0f, Math.min(2.0f * width, targetOffset));
                MainNavigationController.this.applyScrollOffset(targetOffset);
                return true;
            }
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (this.isDraggingPage) {
                float xVelocity = 0f;
                if (this.velocityTracker != null) {
                    this.velocityTracker.computeCurrentVelocity(1000);
                    xVelocity = this.velocityTracker.getXVelocity();
                }

                float currentPage = width > 0 ? (this.currentScrollOffset / (float) width) : (float) this.currentTabIndex;
                int targetIndex = this.currentTabIndex;

                if (Math.abs(xVelocity) > 800) {
                    if (xVelocity < 0 && this.currentTabIndex < 2) {
                        targetIndex = this.currentTabIndex + 1;
                    } else if (xVelocity > 0 && this.currentTabIndex > 0) {
                        targetIndex = this.currentTabIndex - 1;
                    }
                } else {
                    targetIndex = Math.round(currentPage);
                }

                targetIndex = Math.max(0, Math.min(2, targetIndex));
                this.animateScrollToTab(targetIndex);
                this.isDraggingPage = false;
                if (this.velocityTracker != null) {
                    this.velocityTracker.recycle();
                    this.velocityTracker = null;
                }
                return true;
            }

            if (this.velocityTracker != null) {
                this.velocityTracker.recycle();
                this.velocityTracker = null;
            }
        }

        return MainNavigationController.this.activity.superDispatchTouchEvent(ev);
    }

    private void applyScrollOffset(float offset) {
        if (this.mainContentFrame == null) {
            return;
        }
        int width = this.mainContentFrame.getWidth();
        if (width <= 0) {
            width = MainNavigationController.this.activity.getResources().getDisplayMetrics().widthPixels;
        }
        this.currentScrollOffset = offset;
        float pagePosition = width > 0 ? (offset / (float) width) : 0f;

        // 1. Pages translation & visibility
        if (this.tabHome != null) {
            float tx = 0f - offset;
            this.tabHome.setTranslationX(tx);
            this.tabHome.setVisibility(Math.abs(tx) < width ? View.VISIBLE : View.INVISIBLE);
        }
        if (this.tabAutomation != null) {
            float tx = (float) width - offset;
            this.tabAutomation.setTranslationX(tx);
            this.tabAutomation.setVisibility(Math.abs(tx) < width ? View.VISIBLE : View.INVISIBLE);
        }
        if (this.tabGuides != null) {
            float tx = (float) (2 * width) - offset;
            this.tabGuides.setTranslationX(tx);
            this.tabGuides.setVisibility(Math.abs(tx) < width ? View.VISIBLE : View.INVISIBLE);
        }

        // 2. Synchronize sliding nav pill & icon/text colors
        this.updateNavPillProgress(pagePosition);

        // 3. In portrait mode, fade the FAB if we swipe away from the automation tab
        if (!MainNavigationController.this.isLandscape() && MainNavigationController.this.activity.getShortcutTabHelper() != null) {
            View btnAdd = MainNavigationController.this.activity.findViewById(R.id.btnAddShortcut);
            if (btnAdd != null) {
                float distFromAuto = Math.abs(offset - width);
                if (distFromAuto > width * 0.4f) {
                    if (btnAdd.getVisibility() == View.VISIBLE) {
                        btnAdd.setVisibility(View.GONE);
                    }
                } else if (MainNavigationController.this.activity.getShortcutTabHelper().isShortcutTabActive() && !this.isKeyboardCurrentlyOpen && MainNavigationController.this.activity.getShortcutTabHelper().getShortcutCount() < 4) {
                    float alpha = Math.max(0f, 1f - (distFromAuto / (width * 0.4f)));
                    btnAdd.setVisibility(View.VISIBLE);
                    btnAdd.setAlpha(alpha);
                    btnAdd.setScaleX(0.7f + 0.3f * alpha);
                    btnAdd.setScaleY(0.7f + 0.3f * alpha);
                }
            }
        }
    }

    private void ensureNavDimensions() {
        if (this.fullTextWidthHome > 0 && this.baseNavWidth > 0) {
            return;
        }
        if (this.textNavHome == null || this.textNavAutomation == null || this.textNavGuides == null) {
            return;
        }

        if (this.navHome != null && this.iconNavHome != null) {
            int iconW = this.iconNavHome.getLayoutParams() != null && this.iconNavHome.getLayoutParams().width > 0
                    ? this.iconNavHome.getLayoutParams().width
                    : (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 22, MainNavigationController.this.activity.getResources().getDisplayMetrics());
            int padL = this.navHome.getPaddingLeft() > 0
                    ? this.navHome.getPaddingLeft()
                    : (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, MainNavigationController.this.activity.getResources().getDisplayMetrics());
            int padR = this.navHome.getPaddingRight() > 0
                    ? this.navHome.getPaddingRight()
                    : (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, MainNavigationController.this.activity.getResources().getDisplayMetrics());
            this.baseNavWidth = padL + iconW + padR;
        } else {
            this.baseNavWidth = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 50, MainNavigationController.this.activity.getResources().getDisplayMetrics());
        }

        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) this.textNavHome.getLayoutParams();
        if (mlp != null && mlp.getMarginStart() > 0) {
            this.fullMarginStart = mlp.getMarginStart();
        } else {
            this.fullMarginStart = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, MainNavigationController.this.activity.getResources().getDisplayMetrics());
        }

        this.fullTextWidthHome = this.measureTextFullWidth(this.textNavHome);
        this.fullTextWidthAuto = this.measureTextFullWidth(this.textNavAutomation);
        this.fullTextWidthGuides = this.measureTextFullWidth(this.textNavGuides);
    }

    private int measureTextFullWidth(TextView tv) {
        if (tv == null) {
            return 0;
        }
        tv.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        int m = tv.getMeasuredWidth();
        if (m <= 0) {
            CharSequence cs = tv.getText();
            String s = cs != null ? cs.toString() : "";
            m = (int) Math.ceil(tv.getPaint().measureText(s)) + tv.getPaddingLeft() + tv.getPaddingRight();
        }
        return Math.max(m, (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30, MainNavigationController.this.activity.getResources().getDisplayMetrics()));
    }

    private void updateNavPillProgress(float pagePosition) {
        if (this.navIndicatorPill == null || this.navHome == null || this.navAutomation == null || this.navGuides == null) {
            return;
        }

        this.ensureNavDimensions();

        float clamped = Math.max(0f, Math.min(2f, pagePosition));
        float w0;
        float w1;
        float w2;
        if (clamped <= 1.0f) {
            w0 = 1.0f - clamped;
            w1 = clamped;
            w2 = 0f;
        } else {
            w0 = 0f;
            w1 = 2.0f - clamped;
            w2 = clamped - 1.0f;
        }

        // Dynamically and smoothly adjust text widths, start margins, and alpha on every frame
        this.updateTabLabel(this.textNavHome, w0, this.fullTextWidthHome, this.fullMarginStart);
        this.updateTabLabel(this.textNavAutomation, w1, this.fullTextWidthAuto, this.fullMarginStart);
        this.updateTabLabel(this.textNavGuides, w2, this.fullTextWidthGuides, this.fullMarginStart);

        // Dynamically compute exact item widths as they smoothly morph without sudden jumps
        int item0Width = this.baseNavWidth + Math.round((this.fullMarginStart + this.fullTextWidthHome) * w0);
        int item1Width = this.baseNavWidth + Math.round((this.fullMarginStart + this.fullTextWidthAuto) * w1);
        int item2Width = this.baseNavWidth + Math.round((this.fullMarginStart + this.fullTextWidthGuides) * w2);

        float targetX;
        float targetW;
        if (clamped <= 1.0f) {
            targetX = clamped * (float) item0Width;
            targetW = (float) item0Width + clamped * (float) (item1Width - item0Width);
        } else {
            float f = clamped - 1.0f;
            targetX = (float) item0Width + f * (float) item1Width;
            targetW = (float) item1Width + f * (float) (item2Width - item1Width);
        }

        this.navIndicatorPill.setTranslationX(targetX);
        ViewGroup.LayoutParams lp = this.navIndicatorPill.getLayoutParams();
        int roundedTargetW = Math.round(targetW);
        if (lp != null && lp.width != roundedTargetW) {
            lp.width = roundedTargetW;
            this.navIndicatorPill.setLayoutParams(lp);
        }

        boolean isAmoled = MainNavigationController.this.activity.isAmoled();
        int selectedColor = MainNavigationController.this.activity.getColor(isAmoled ? R.color.nav_item_selected_content_amoled : R.color.nav_item_selected_content);
        int unselectedColor = MainNavigationController.this.activity.getColor(isAmoled ? R.color.nav_item_unselected_icon_amoled : R.color.nav_item_unselected_icon);

        int c0 = this.blendColors(unselectedColor, selectedColor, w0);
        int c1 = this.blendColors(unselectedColor, selectedColor, w1);
        int c2 = this.blendColors(unselectedColor, selectedColor, w2);

        if (this.iconNavHome != null) {
            this.iconNavHome.setColorFilter(c0);
            this.iconNavHome.setImageResource(w0 > 0.5f ? R.drawable.ic_nav_home_filled : R.drawable.ic_nav_home_outline);
        }
        if (this.textNavHome != null) {
            this.textNavHome.setTextColor(c0);
        }

        if (this.iconNavAutomation != null) {
            this.iconNavAutomation.setColorFilter(c1);
            this.iconNavAutomation.setImageResource(w1 > 0.5f ? R.drawable.ic_nav_automate_filled : R.drawable.ic_nav_automate_outline);
        }
        if (this.textNavAutomation != null) {
            this.textNavAutomation.setTextColor(c1);
        }

        if (this.iconNavGuides != null) {
            this.iconNavGuides.setColorFilter(c2);
            this.iconNavGuides.setImageResource(w2 > 0.5f ? R.drawable.ic_nav_guides_filled : R.drawable.ic_nav_guides_outline);
        }
        if (this.textNavGuides != null) {
            this.textNavGuides.setTextColor(c2);
        }
    }

    private void updateTabLabel(TextView textView, float weight, int fullWidth, int fullMargin) {
        if (textView == null) {
            return;
        }
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) textView.getLayoutParams();
        if (lp == null) {
            return;
        }

        if (weight <= 0.005f) {
            if (textView.getVisibility() != View.GONE) {
                textView.setVisibility(View.GONE);
            }
            if (lp.width != 0 || lp.getMarginStart() != 0) {
                lp.width = 0;
                lp.setMarginStart(0);
                textView.setLayoutParams(lp);
            }
        } else {
            int targetW = Math.round(fullWidth * weight);
            int targetM = Math.round(fullMargin * weight);
            if (lp.width != targetW || lp.getMarginStart() != targetM) {
                lp.width = targetW;
                lp.setMarginStart(targetM);
                textView.setLayoutParams(lp);
            }
            if (textView.getVisibility() != View.VISIBLE) {
                textView.setVisibility(View.VISIBLE);
            }
            textView.setAlpha(weight);
        }
    }

    private int blendColors(int from, int to, float ratio) {
        float clamped = Math.max(0f, Math.min(1f, ratio));
        float inv = 1f - clamped;
        float a = Color.alpha(from) * inv + Color.alpha(to) * clamped;
        float r = Color.red(from) * inv + Color.red(to) * clamped;
        float g = Color.green(from) * inv + Color.green(to) * clamped;
        float b = Color.blue(from) * inv + Color.blue(to) * clamped;
        return Color.argb((int) a, (int) r, (int) g, (int) b);
    }

    private void animateScrollToTab(int targetIndex) {
        int width = this.mainContentFrame != null ? this.mainContentFrame.getWidth() : 0;
        if (width <= 0) {
            width = MainNavigationController.this.activity.getResources().getDisplayMetrics().widthPixels;
        }
        final float endOffset = (float) targetIndex * width;
        final float startOffset = this.currentScrollOffset;

        if (Math.abs(startOffset - endOffset) < 1f) {
            MainNavigationController.this.currentTabIndex = targetIndex;
            MainNavigationController.this.onTabSettled(targetIndex);
            MainNavigationController.this.applyScrollOffset(endOffset);
            return;
        }

        if (this.pageScrollAnimator != null && this.pageScrollAnimator.isRunning()) {
            this.pageScrollAnimator.cancel();
        }

        this.pageScrollAnimator = ValueAnimator.ofFloat(startOffset, endOffset);
        this.pageScrollAnimator.setDuration(280);
        this.pageScrollAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
        this.pageScrollAnimator.addUpdateListener(animation -> {
            float val = (Float) animation.getAnimatedValue();
            MainNavigationController.this.applyScrollOffset(val);
        });
        this.pageScrollAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                MainNavigationController.this.currentTabIndex = targetIndex;
                MainNavigationController.this.onTabSettled(targetIndex);
                MainNavigationController.this.applyScrollOffset(endOffset);
            }
        });
        this.pageScrollAnimator.start();
    }

    private void onTabSettled(int index) {
        if (MainNavigationController.this.isLandscape()) {
            MainNavigationController.this.updateLandscapeEdgeNavProgress((float) index);
            for (int i = 0; i < 3; i++) {
                View v = this.getTabViewByIndex(i);
                if (v != null) {
                    if (i == index) {
                        v.setVisibility(View.VISIBLE);
                        v.setAlpha(1f);
                    } else {
                        v.setVisibility(View.GONE);
                        v.setAlpha(0f);
                    }
                }
            }
        } else {
            this.updateNavPillProgress((float) index);
        }
        if (index == 1) {
            if (MainNavigationController.this.activity.getBroadcastTabHelper() != null) {
                MainNavigationController.this.activity.getBroadcastTabHelper().refreshCapabilities();
            }
            if (MainNavigationController.this.activity.getShortcutTabHelper() != null) {
                MainNavigationController.this.activity.getShortcutTabHelper().setAuthorized(MainNavigationController.this.activity.isExecutionAuthorized());
                MainNavigationController.this.activity.getShortcutTabHelper().updateFabVisibility();
            }
        } else {
            if (MainNavigationController.this.activity.getShortcutTabHelper() != null) {
                MainNavigationController.this.activity.getShortcutTabHelper().updateFabVisibility();
            }
        }
    }

    public void switchTab(int index) {
        View currentFocus = MainNavigationController.this.activity.getCurrentFocus();
        if (currentFocus != null) {
            InputMethodManager imm = (InputMethodManager) MainNavigationController.this.activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
            }
            currentFocus.clearFocus();
        }
        if (MainNavigationController.this.isLandscape()) {
            this.switchTabLandscape(index);
        } else {
            this.animateScrollToTab(index);
        }
    }

    private boolean isLandscape() {
        return MainNavigationController.this.activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    public void setupOrientationListener() {
        this.orientationListener = new OrientationEventListener(MainNavigationController.this.activity) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (!MainNavigationController.this.isLandscape() || MainNavigationController.this.isDestroyed) {
                    return;
                }
                int currentRotation = Surface.ROTATION_0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (MainNavigationController.this.activity.getDisplay() != null) {
                        currentRotation = MainNavigationController.this.activity.getDisplay().getRotation();
                    }
                } else {
                    currentRotation = MainNavigationController.this.activity.getWindowManager().getDefaultDisplay().getRotation();
                }
                if (currentRotation != MainNavigationController.this.lastOrientationRotation) {
                    MainNavigationController.this.lastOrientationRotation = currentRotation;
                    MainNavigationController.this.updateEdgeBarPosition();
                }
            }
        };
        if (this.orientationListener.canDetectOrientation()) {
            this.orientationListener.enable();
        }
    }

    private void updateEdgeBarPosition() {
        if (!MainNavigationController.this.isLandscape()) {
            return;
        }
        LinearLayout mainRoot = MainNavigationController.this.activity.findViewById(R.id.mainRoot);
        View edgeBarContainer = MainNavigationController.this.activity.findViewById(R.id.edgeBarContainer);
        View mainContentWrapper = MainNavigationController.this.activity.findViewById(R.id.mainContentWrapper);
        if (mainRoot == null || edgeBarContainer == null || mainContentWrapper == null) {
            return;
        }
        int rotation = Surface.ROTATION_0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (MainNavigationController.this.activity.getDisplay() != null) {
                rotation = MainNavigationController.this.activity.getDisplay().getRotation();
            }
        } else {
            rotation = MainNavigationController.this.activity.getWindowManager().getDefaultDisplay().getRotation();
        }

        boolean dockRight = (rotation == Surface.ROTATION_270);
        int currentEdgeIndex = mainRoot.indexOfChild(edgeBarContainer);
        int expectedEdgeIndex = dockRight ? 1 : 0;
        if (currentEdgeIndex != expectedEdgeIndex && currentEdgeIndex != -1) {
            mainRoot.removeView(edgeBarContainer);
            mainRoot.removeView(mainContentWrapper);
            if (dockRight) {
                mainRoot.addView(mainContentWrapper);
                mainRoot.addView(edgeBarContainer);
            } else {
                mainRoot.addView(edgeBarContainer);
                mainRoot.addView(mainContentWrapper);
            }
        }
    }

    private View getTabViewByIndex(int index) {
        if (index == 0) return this.tabHome;
        if (index == 1) return this.tabAutomation;
        if (index == 2) return this.tabGuides;
        return null;
    }

    private void switchTabLandscape(int targetIndex) {
        if (targetIndex == this.currentTabIndex) {
            return;
        }
        final int fromIndex = this.currentTabIndex;
        final int toIndex = targetIndex;
        MainNavigationController.this.currentTabIndex = targetIndex;

        if (this.pageScrollAnimator != null && this.pageScrollAnimator.isRunning()) {
            this.pageScrollAnimator.cancel();
        }

        View toView = this.getTabViewByIndex(toIndex);

        // Instantly hide and cancel animations on all non-target tabs
        for (int i = 0; i < 3; i++) {
            View v = this.getTabViewByIndex(i);
            if (v != null) {
                v.animate().cancel();
                if (i != toIndex) {
                    v.setVisibility(View.GONE);
                    v.setAlpha(0f);
                }
            }
        }

        // Show and bring active tab to front
        if (toView != null) {
            toView.animate().cancel();
            toView.setVisibility(View.VISIBLE);
            toView.setAlpha(1f);
            toView.setTranslationX(0f);
            toView.setTranslationY(0f);
            toView.bringToFront();
        }

        // Edge nav bar morphing & continuous progress
        this.pageScrollAnimator = ValueAnimator.ofFloat((float) fromIndex, (float) toIndex);
        this.pageScrollAnimator.setDuration(240);
        this.pageScrollAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
        this.pageScrollAnimator.addUpdateListener(anim -> {
            float p = (Float) anim.getAnimatedValue();
            MainNavigationController.this.updateLandscapeEdgeNavProgress(p);
        });
        this.pageScrollAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                MainNavigationController.this.updateLandscapeEdgeNavProgress((float) toIndex);
                MainNavigationController.this.onTabSettled(toIndex);
            }
        });
        this.pageScrollAnimator.start();
    }

    private void updateLandscapeEdgeNavProgress(float progress) {
        if (this.navIndicatorPill == null || this.navHome == null || this.navAutomation == null || this.navGuides == null) {
            return;
        }

        float clamped = Math.max(0f, Math.min(2f, progress));
        float w0;
        float w1;
        float w2;
        if (clamped <= 1.0f) {
            w0 = 1.0f - clamped;
            w1 = clamped;
            w2 = 0f;
        } else {
            w0 = 0f;
            w1 = 2.0f - clamped;
            w2 = clamped - 1.0f;
        }

        float density = MainNavigationController.this.activity.getResources().getDisplayMetrics().density;
        int baseHeight = Math.round(48 * density);
        int expandHeight = Math.round(16 * density);

        int h0 = baseHeight + Math.round(expandHeight * w0);
        int h1 = baseHeight + Math.round(expandHeight * w1);
        int h2 = baseHeight + Math.round(expandHeight * w2);

        this.updateVerticalTabLabel(this.textNavHome, w0);
        this.updateVerticalTabLabel(this.textNavAutomation, w1);
        this.updateVerticalTabLabel(this.textNavGuides, w2);

        this.setItemHeight(this.navHome, h0);
        this.setItemHeight(this.navAutomation, h1);
        this.setItemHeight(this.navGuides, h2);

        float targetY;
        float targetH;
        if (clamped <= 1.0f) {
            targetY = clamped * (float) h0;
            targetH = (float) h0 + clamped * (float) (h1 - h0);
        } else {
            float f = clamped - 1.0f;
            targetY = (float) h0 + f * (float) h1;
            targetH = (float) h1 + f * (float) (h2 - h1);
        }

        this.navIndicatorPill.setTranslationY(targetY);
        ViewGroup.LayoutParams lp = this.navIndicatorPill.getLayoutParams();
        int roundedH = Math.round(targetH);
        if (lp != null && lp.height != roundedH) {
            lp.height = roundedH;
            this.navIndicatorPill.setLayoutParams(lp);
        }

        boolean isAmoled = MainNavigationController.this.activity.isAmoled();
        int selectedColor = MainNavigationController.this.activity.getColor(isAmoled ? R.color.nav_item_selected_content_amoled : R.color.nav_item_selected_content);
        int unselectedColor = MainNavigationController.this.activity.getColor(isAmoled ? R.color.nav_item_unselected_icon_amoled : R.color.nav_item_unselected_icon);

        int c0 = this.blendColors(unselectedColor, selectedColor, w0);
        int c1 = this.blendColors(unselectedColor, selectedColor, w1);
        int c2 = this.blendColors(unselectedColor, selectedColor, w2);

        if (this.iconNavHome != null) {
            this.iconNavHome.setColorFilter(c0);
            this.iconNavHome.setImageResource(w0 > 0.5f ? R.drawable.ic_nav_home_filled : R.drawable.ic_nav_home_outline);
        }
        if (this.textNavHome != null) {
            this.textNavHome.setTextColor(c0);
        }

        if (this.iconNavAutomation != null) {
            this.iconNavAutomation.setColorFilter(c1);
            this.iconNavAutomation.setImageResource(w1 > 0.5f ? R.drawable.ic_nav_automate_filled : R.drawable.ic_nav_automate_outline);
        }
        if (this.textNavAutomation != null) {
            this.textNavAutomation.setTextColor(c1);
        }

        if (this.iconNavGuides != null) {
            this.iconNavGuides.setColorFilter(c2);
            this.iconNavGuides.setImageResource(w2 > 0.5f ? R.drawable.ic_nav_guides_filled : R.drawable.ic_nav_guides_outline);
        }
        if (this.textNavGuides != null) {
            this.textNavGuides.setTextColor(c2);
        }
    }

    private int fullVerticalTextHeight = 0;

    private void updateVerticalTabLabel(TextView textView, float weight) {
        if (textView == null) {
            return;
        }
        float density = MainNavigationController.this.activity.getResources().getDisplayMetrics().density;
        if (this.fullVerticalTextHeight <= 0) {
            this.fullVerticalTextHeight = Math.max(textView.getLineHeight(), Math.round(15 * density));
        }

        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) textView.getLayoutParams();
        if (mlp == null) return;

        if (weight <= 0.01f) {
            if (textView.getVisibility() != View.GONE) {
                textView.setVisibility(View.GONE);
            }
            if (mlp.height != 0 || mlp.topMargin != 0) {
                mlp.height = 0;
                mlp.topMargin = 0;
                textView.setLayoutParams(mlp);
            }
        } else {
            if (textView.getVisibility() != View.VISIBLE) {
                textView.setVisibility(View.VISIBLE);
            }
            int targetH = (weight >= 0.99f) ? ViewGroup.LayoutParams.WRAP_CONTENT : Math.round(this.fullVerticalTextHeight * weight);
            int targetMargin = Math.round(2 * density * weight);
            if (mlp.height != targetH || mlp.topMargin != targetMargin) {
                mlp.height = targetH;
                mlp.topMargin = targetMargin;
                textView.setLayoutParams(mlp);
            }
            textView.setAlpha(weight);
            textView.setScaleX(0.75f + 0.25f * weight);
            textView.setScaleY(0.75f + 0.25f * weight);
        }
    }

    private void setItemHeight(View view, int height) {
        if (view == null) return;
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp != null && lp.height != height) {
            lp.height = height;
            view.setLayoutParams(lp);
        }
    }

    private boolean isTouchInsideInteractiveChild(float rawX, float rawY) {
        if (this.isViewUnder(this.bottomNavPill, rawX, rawY)) {
            return true;
        }
        View currentTabView = null;
        if (this.currentTabIndex == 0) {
            currentTabView = this.tabHome;
        } else if (this.currentTabIndex == 1) {
            currentTabView = this.tabAutomation;
        } else if (this.currentTabIndex == 2) {
            currentTabView = this.tabGuides;
        }

        return this.isInteractiveChildUnder(currentTabView, rawX, rawY);
    }

    private boolean isInteractiveChildUnder(View view, float rawX, float rawY) {
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return false;
        }
        view.getLocationOnScreen(this.reusableTouchLoc);
        if (rawX < this.reusableTouchLoc[0] || rawX > this.reusableTouchLoc[0] + view.getWidth() ||
            rawY < this.reusableTouchLoc[1] || rawY > this.reusableTouchLoc[1] + view.getHeight()) {
            return false;
        }
        if (view instanceof EditText || view instanceof HorizontalScrollView || view instanceof Spinner) {
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (this.isInteractiveChildUnder(group.getChildAt(i), rawX, rawY)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isViewUnder(View view, float rawX, float rawY) {
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return false;
        }
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        return rawX >= loc[0] && rawX <= loc[0] + view.getWidth() &&
               rawY >= loc[1] && rawY <= loc[1] + view.getHeight();
    }



}
