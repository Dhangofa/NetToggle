package com.dhangofa.networktoggle.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;

import com.dhangofa.networktoggle.R;

import java.util.List;

public class SetupCarouselAdapter extends RecyclerView.Adapter<SetupCarouselAdapter.ViewHolder> {

    public static class SetupItem {
        public String title;
        public String description;
        public String buttonText;
        public String url;
        public int iconRes;
        public int bgColorRes;
        public int accentColorRes;
        public int btnBgColorRes;

        public SetupItem(String title, String description, String buttonText, String url, int iconRes, int bgColorRes, int accentColorRes, int btnBgColorRes) {
            this.title = title;
            this.description = description;
            this.buttonText = buttonText;
            this.url = url;
            this.iconRes = iconRes;
            this.bgColorRes = bgColorRes;
            this.accentColorRes = accentColorRes;
            this.btnBgColorRes = btnBgColorRes;
        }
    }

    private List<SetupItem> items;
    private Context context;

    public SetupCarouselAdapter(Context context, List<SetupItem> items) {
        this.context = context;
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_setup_carousel, parent, false);
        // Important: Ensure the item fills the ViewPager2 width
        view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SetupItem item = items.get(position);
        holder.title.setText(item.title);
        holder.desc.setText(item.description);
        holder.buttonText.setText(item.buttonText);
        holder.icon.setImageResource(item.iconRes);

        int bgColor = ContextCompat.getColor(context, item.bgColorRes);
        int accentColor = ContextCompat.getColor(context, item.accentColorRes);

        holder.background.setBackgroundTintList(ColorStateList.valueOf(bgColor));
        
        holder.buttonText.setTextColor(accentColor);
        holder.buttonIcon.setColorFilter(accentColor);
        holder.icon.setColorFilter(accentColor);
        int btnBgColor = ContextCompat.getColor(context, item.btnBgColorRes);
        holder.button.setBackgroundTintList(ColorStateList.valueOf(btnBgColor));
        holder.iconContainer.setBackgroundTintList(ColorStateList.valueOf(btnBgColor));

        holder.button.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(item.url));
            try { context.startActivity(browserIntent); } catch (Exception e) { android.widget.Toast.makeText(context, "No web browser installed to open this link.", android.widget.Toast.LENGTH_SHORT).show(); }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View background;
        TextView title, desc, buttonText;
        ImageView icon, buttonIcon;
        LinearLayout button;
        View iconContainer;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            background = itemView.findViewById(R.id.carouselBackground);
            title = itemView.findViewById(R.id.carouselTitle);
            desc = itemView.findViewById(R.id.carouselDesc);
            buttonText = itemView.findViewById(R.id.carouselButtonText);
            icon = itemView.findViewById(R.id.carouselIcon);
            buttonIcon = itemView.findViewById(R.id.carouselButtonIcon);
            button = itemView.findViewById(R.id.carouselButton);
            iconContainer = itemView.findViewById(R.id.carouselIconContainer);
        }
    }
}
