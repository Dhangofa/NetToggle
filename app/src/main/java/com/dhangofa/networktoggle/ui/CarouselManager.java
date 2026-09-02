package com.dhangofa.networktoggle.ui;

/**
 * Handles the "Morphing View Carousel" at the top of the app.
 * It manages gesture detection, smooth color cross-fading, and button tinting so the main
 * activity stays clean.
 */

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dhangofa.networktoggle.R;

public class CarouselManager {
    public static class CarouselItem {
        public final String title;
        public final String desc;
        public final String btnText;
        public final String url;
        public final int iconRes;
        public final int bgColor;
        public final int accentColor;
        public final int btnBgColor;

        public CarouselItem(String title, String desc, String btnText, String url, int iconRes, int bgColor, int accentColor, int btnBgColor) {
            this.title = title;
            this.desc = desc;
            this.btnText = btnText;
            this.url = url;
            this.iconRes = iconRes;
            this.bgColor = bgColor;
            this.accentColor = accentColor;
            this.btnBgColor = btnBgColor;
        }
    }

    private final Activity activity;
    private final View carouselBackground;
    private final TextView carouselTitle;
    private final TextView carouselDesc;
    private final TextView carouselButtonText;
    private final ImageView carouselIcon;
    private final ImageView carouselButtonIcon;
    private final View carouselButton;
    private final View carouselIconContainer;
    private final LinearLayout carouselIndicators;

    private CarouselItem[] carouselItems;
    private int currentCarouselIndex = 0;
    private float touchStartX = 0f;
    private boolean isFirstCarouselRender = true;

    
    private View[] multiCards;
    private TextView[] multiTitles;
    private TextView[] multiDescs;
    private View[] multiIconContainers;
    private ImageView[] multiIcons;
    private View[] multiButtons;
    private TextView[] multiBtnTexts;
    private ImageView[] multiBtnIcons;
    private boolean isMultiMode = false;
    private View multiCarouselContainer;

    public CarouselManager(Activity activity) {
        this.activity = activity;
        this.carouselBackground = activity.findViewById(R.id.carouselBackground);
        this.carouselTitle = activity.findViewById(R.id.carouselTitle);
        this.carouselDesc = activity.findViewById(R.id.carouselDesc);
        this.carouselButtonText = activity.findViewById(R.id.carouselButtonText);
        this.carouselIcon = activity.findViewById(R.id.carouselIcon);
        this.carouselButtonIcon = activity.findViewById(R.id.carouselButtonIcon);
        this.carouselButton = activity.findViewById(R.id.carouselButton);
        this.carouselIconContainer = activity.findViewById(R.id.carouselIconContainer);
        this.carouselIndicators = activity.findViewById(R.id.carouselIndicators);
        this.multiCarouselContainer = activity.findViewById(R.id.multiCarouselContainer);
        if (this.multiCarouselContainer != null) {
            isMultiMode = true;
            multiCards = new View[]{activity.findViewById(R.id.staticCard1), activity.findViewById(R.id.staticCard2), activity.findViewById(R.id.staticCard3)};
            multiTitles = new TextView[]{activity.findViewById(R.id.staticTitle1), activity.findViewById(R.id.staticTitle2), activity.findViewById(R.id.staticTitle3)};
            multiDescs = new TextView[]{activity.findViewById(R.id.staticDesc1), activity.findViewById(R.id.staticDesc2), activity.findViewById(R.id.staticDesc3)};
            multiIconContainers = new View[]{activity.findViewById(R.id.staticIconContainer1), activity.findViewById(R.id.staticIconContainer2), activity.findViewById(R.id.staticIconContainer3)};
            multiIcons = new ImageView[]{activity.findViewById(R.id.staticIcon1), activity.findViewById(R.id.staticIcon2), activity.findViewById(R.id.staticIcon3)};
            multiButtons = new View[]{activity.findViewById(R.id.staticCardButton1), activity.findViewById(R.id.staticCardButton2), activity.findViewById(R.id.staticCardButton3)};
            multiBtnTexts = new TextView[]{activity.findViewById(R.id.staticBtnText1), activity.findViewById(R.id.staticBtnText2), activity.findViewById(R.id.staticBtnText3)};
            multiBtnIcons = new ImageView[]{activity.findViewById(R.id.staticBtnIcon1), activity.findViewById(R.id.staticBtnIcon2), activity.findViewById(R.id.staticBtnIcon3)};
        }

    }

    public void setupCarousel(boolean isUIAuthorized) {
        if (carouselBackground == null && !isMultiMode) return;
        carouselItems = new CarouselItem[]{
            new CarouselItem(activity.getString(R.string.carousel_title_1), activity.getString(R.string.carousel_desc_1), activity.getString(R.string.carousel_btn_1), "https://github.com/Dhangofa/NetToggle/wiki/1.-Execution-Mode-Configuration", R.drawable.ic_terminal, R.color.carousel_page1_bg, R.color.carousel_page1_accent, R.color.carousel_button_bg),
            new CarouselItem(activity.getString(R.string.carousel_title_2), activity.getString(R.string.carousel_desc_2), activity.getString(R.string.carousel_btn_1), "https://github.com/Dhangofa/NetToggle/wiki/2.-Target-SIM-Setup-&-Quick-Tile-Cycle-Guide", R.drawable.ic_sim_card, R.color.carousel_page2_bg, R.color.carousel_page2_accent, R.color.carousel_button_bg),
            new CarouselItem(activity.getString(R.string.carousel_title_3), activity.getString(R.string.carousel_desc_3), activity.getString(R.string.carousel_btn_1), "https://github.com/Dhangofa/NetToggle/wiki/3.-Adding-the-Tile-to-Quick-Settings", R.drawable.ic_network_bars, R.color.carousel_page3_bg, R.color.carousel_page3_accent, R.color.carousel_button_bg),
            new CarouselItem(activity.getString(R.string.carousel_title_4), activity.getString(R.string.carousel_desc_4), activity.getString(R.string.carousel_btn_1), "https://github.com/Dhangofa/NetToggle/wiki/4.-App-Shortcuts-&-Built%E2%80%90in-OS-Routines", R.drawable.ic_shortcut_network, R.color.carousel_page4_bg, R.color.carousel_page4_accent, R.color.carousel_button_bg),
            new CarouselItem(activity.getString(R.string.carousel_title_5), activity.getString(R.string.carousel_desc_5), activity.getString(R.string.carousel_btn_1), "https://github.com/Dhangofa/NetToggle/wiki/5.-Broadcast-Automation-(Tasker,-MacroDroid,-Automate)", R.drawable.ic_magic_wand, R.color.carousel_page5_bg, R.color.carousel_page5_accent, R.color.carousel_button_bg)
        };
        
        setupCarouselIndicators(carouselItems.length);
        renderCarouselPage(isUIAuthorized ? 1 : 0, false, 0);
        
        View swipeTarget = isMultiMode ? multiCarouselContainer : carouselBackground;
        if (swipeTarget != null) {
            swipeTarget.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        touchStartX = event.getX();
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        float deltaX = event.getX() - touchStartX;
                        if (Math.abs(deltaX) > 100) { 
                            if (deltaX > 0) {
                                currentCarouselIndex = (currentCarouselIndex - 1 + carouselItems.length) % carouselItems.length;
                                renderCarouselPage(currentCarouselIndex, true, -1);
                            } else if (deltaX < 0) {
                                currentCarouselIndex = (currentCarouselIndex + 1) % carouselItems.length;
                                renderCarouselPage(currentCarouselIndex, true, 1);
                            }
                        } else if (event.getAction() == MotionEvent.ACTION_UP) {
                            v.performClick();
                        }
                        return true;
                }
                return false;
            });
            swipeTarget.setOnClickListener(v -> {});
        }
    }

    private void renderCarouselPage(int index, boolean animate, int direction) {
        if (isMultiMode) {
            for (int i = 0; i < 3; i++) {
                int itemIndex = (index + i) % carouselItems.length;
                CarouselItem item = carouselItems[itemIndex];
                
                multiTitles[i].setText(item.title);
                multiDescs[i].setText(item.desc);
                multiBtnTexts[i].setText(item.btnText);
                multiBtnTexts[i].setTextColor(activity.getColor(item.accentColor));
                
                multiIcons[i].setImageResource(item.iconRes);
                multiIcons[i].setColorFilter(activity.getColor(item.accentColor));
                multiBtnIcons[i].setColorFilter(activity.getColor(item.accentColor));
                
                multiCards[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(activity.getColor(item.bgColor)));
                multiButtons[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(activity.getColor(item.btnBgColor)));
                multiIconContainers[i].setBackgroundTintList(android.content.res.ColorStateList.valueOf(activity.getColor(item.btnBgColor)));
                
                multiButtons[i].setOnClickListener(v -> {
                    try { activity.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(item.url))); } catch (Exception e) {}
                });
            }
            updateCarouselIndicators((index + 1) % carouselItems.length);
            return;
        }

        CarouselItem item = carouselItems[index];
        Runnable updateViews = () -> {
            carouselTitle.setText(item.title);
            carouselDesc.setText(item.desc);
            carouselButtonText.setText(item.btnText);
            carouselButtonText.setTextColor(activity.getColor(item.accentColor));
            
            carouselIcon.setImageResource(item.iconRes);
            carouselIcon.setColorFilter(activity.getColor(item.accentColor));
            carouselButtonIcon.setColorFilter(activity.getColor(item.accentColor));
            
            carouselBackground.setBackgroundTintList(ColorStateList.valueOf(activity.getColor(item.bgColor)));
            carouselButton.setBackgroundTintList(ColorStateList.valueOf(activity.getColor(item.btnBgColor)));
            carouselIconContainer.setBackgroundTintList(ColorStateList.valueOf(activity.getColor(item.btnBgColor)));
            
            carouselButton.setOnClickListener(v -> {
                try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(item.url))); } catch (Exception e) {}
            });
            updateCarouselIndicators(index);
        };

        if (animate) {
            float moveOutX = direction * -100f;
            float moveInX = direction * 100f;
            long duration = 200;

            ValueAnimator colorAnim = ValueAnimator.ofObject(
                new ArgbEvaluator(),
                carouselBackground.getBackgroundTintList().getDefaultColor(),
                activity.getColor(item.bgColor)
            );
            colorAnim.setDuration(duration);
            colorAnim.addUpdateListener(animator -> {
                carouselBackground.setBackgroundTintList(ColorStateList.valueOf((int) animator.getAnimatedValue()));
            });
            colorAnim.start();

            View[] viewsToAnimate = {carouselTitle, carouselDesc, carouselButton, carouselIconContainer};
            for (View view : viewsToAnimate) {
                view.animate().translationX(moveOutX).alpha(0f).setDuration(duration / 2)
                    .setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> {
                        if (view == carouselTitle) updateViews.run();
                        view.setTranslationX(moveInX);
                        view.animate().translationX(0f).alpha(1f).setDuration(duration / 2)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                    }).start();
            }
        } else {
            updateViews.run();
        }
    }

    private void setupCarouselIndicators(int count) {
        if (carouselIndicators == null) return;
        carouselIndicators.removeAllViews();
        for (int i = 0; i < count; i++) {
            ImageView dot = new ImageView(activity);
            dot.setImageDrawable(activity.getDrawable(R.drawable.shape_dot_inactive));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(16, 16);
            params.setMargins(8, 0, 8, 0);
            dot.setLayoutParams(params);
            carouselIndicators.addView(dot);
        }
    }

    private void updateCarouselIndicators(int position) {
        if (carouselIndicators == null) return;
        for (int i = 0; i < carouselIndicators.getChildCount(); i++) {
            ImageView dot = (ImageView) carouselIndicators.getChildAt(i);
            if (i == position) {
                dot.setImageDrawable(activity.getDrawable(R.drawable.shape_dot_active));
            } else {
                dot.setImageDrawable(activity.getDrawable(R.drawable.shape_dot_inactive));
            }
        }
    }

    public void updateCarouselContext(boolean authorized) {
        if (carouselBackground == null || carouselItems == null) return;

        boolean animate = !isFirstCarouselRender;
        isFirstCarouselRender = false;

        if (!authorized) {
            if (currentCarouselIndex != 0 || !animate) {
                currentCarouselIndex = 0;
                renderCarouselPage(0, animate, -1);
            }
        } else {
            if (currentCarouselIndex != 1 || !animate) {
                currentCarouselIndex = 1;
                renderCarouselPage(1, animate, 1);
            }
        }
    }
}
