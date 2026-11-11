package com.crb.tv;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Adapter RecyclerView untuk Menu Grid App Favorit (Pinggir Kiri Layar, di bawah Jam):
 * Hanya menampilkan aplikasi favorit pilihan pengguna beserta tombol "+ Favorit" & "Semua App".
 */
public final class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    public static final class AppEntry {
        public final String packageName;
        public final String activityName;
        public final String label;
        public final Drawable banner;
        public final Drawable icon;
        public final int vividColor;
        public final boolean isBuiltinAction;
        public final String builtinId;

        public AppEntry(
                String packageName, String activityName, String label,
                Drawable banner, Drawable icon, int vividColor,
                boolean isBuiltinAction, String builtinId) {
            this.packageName = packageName;
            this.activityName = activityName;
            this.label = label;
            this.banner = banner;
            this.icon = icon;
            this.vividColor = vividColor;
            this.isBuiltinAction = isBuiltinAction;
            this.builtinId = builtinId;
        }
    }

    public interface OnAppInteractionListener {
        void onAppFocused(AppEntry entry, View itemView, int position);
        void onAppClicked(AppEntry entry, int position);
        void onAppLongClicked(AppEntry entry, int position);
    }

    private final Context context;
    private final Config config;
    private final OnAppInteractionListener listener;
    private final List<AppEntry> items = new ArrayList<>();
    private final List<AppEntry> allInstalledApps = new ArrayList<>();
    private final Map<String, Integer> colorCache = new HashMap<>();
    private int selectedPosition = 0;

    public AppAdapter(Context context, Config config, OnAppInteractionListener listener) {
        this.context = context;
        this.config = config;
        this.listener = listener;
        setHasStableIds(true);
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public void setSelectedPosition(int newPos) {
        int oldPos = this.selectedPosition;
        this.selectedPosition = newPos;
        if (oldPos >= 0 && oldPos < items.size()) {
            notifyItemChanged(oldPos);
        }
        if (newPos >= 0 && newPos < items.size()) {
            notifyItemChanged(newPos);
        }
    }

    public List<AppEntry> getAllInstalledApps() {
        return new ArrayList<>(allInstalledApps);
    }

    public void reloadApps() {
        items.clear();
        allInstalledApps.clear();

        PackageManager pm = context.getPackageManager();
        Set<String> hidden = new HashSet<>(config.hiddenApps);
        hidden.add(context.getPackageName());

        Map<String, AppEntry> discovered = new HashMap<>();

        // 1. Query Android TV Leanback Launcher Apps
        Intent leanbackIntent = new Intent(Intent.ACTION_MAIN);
        leanbackIntent.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> leanbackList = pm.queryIntentActivities(leanbackIntent, 0);
        if (leanbackList != null) {
            for (ResolveInfo ri : leanbackList) {
                addResolveInfo(pm, ri, hidden, discovered);
            }
        }

        // 2. Query Standard Android Launcher Apps
        Intent mainIntent = new Intent(Intent.ACTION_MAIN);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> standardList = pm.queryIntentActivities(mainIntent, 0);
        if (standardList != null) {
            for (ResolveInfo ri : standardList) {
                addResolveInfo(pm, ri, hidden, discovered);
            }
        }

        // Simpan seluruh aplikasi terinstal secara alfabetis untuk dialog Semua App & Pilih Favorit
        allInstalledApps.addAll(discovered.values());
        Collections.sort(allInstalledApps, new Comparator<AppEntry>() {
            @Override
            public int compare(AppEntry a, AppEntry b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });

        // 3. Inisialisasi daftar App Favorit default pada penggunaan pertama
        if (!config.favoriteAppsInitialized || config.favoriteApps.isEmpty()) {
            initializeSmartDefaultFavorites(discovered);
        }

        // 4. Masukkan HANYA App Favorit ke dalam Menu Grid Kiri
        Set<String> placed = new HashSet<>();
        for (String favPkg : config.favoriteApps) {
            AppEntry entry = discovered.get(favPkg);
            if (entry != null && !placed.contains(favPkg)) {
                items.add(entry);
                placed.add(favPkg);
            }
        }

        // Jika daftar favorit yang terinstal kosong, tampilkan maksimal 5 aplikasi teratas (selain wallpaper bawaan)
        if (items.isEmpty() && !allInstalledApps.isEmpty()) {
            for (AppEntry e : allInstalledApps) {
                if (e.label.toLowerCase(Locale.US).contains("wallpaper")) continue;
                items.add(e);
                config.favoriteApps.add(e.packageName);
                if (items.size() >= 5) break;
            }
            config.saveFavoriteApps(context);
        }

        // 5. Tambahkan 2 Tombol Aksi Favorit & Semua Aplikasi di ujung bawah menu favorit
        items.add(new AppEntry(
                context.getPackageName(), "", "+ Favorit",
                null, context.getDrawable(R.drawable.ic_launcher),
                0xFFF59E0B, true, "builtin_manage_favorites"
        ));
        items.add(new AppEntry(
                context.getPackageName(), "", "Semua App",
                null, context.getDrawable(R.drawable.ic_launcher),
                0xFF38BDF8, true, "builtin_app_library"
        ));

        if (selectedPosition >= items.size()) {
            selectedPosition = Math.max(0, items.size() - 1);
        }
        notifyDataSetChanged();
    }

    private void initializeSmartDefaultFavorites(Map<String, AppEntry> discovered) {
        config.favoriteApps.clear();

        // Prioritaskan aplikasi favorit populer di TV pengguna (Play Store, TizenTube, YouTube, X-plore, dll.)
        for (AppEntry e : allInstalledApps) {
            String pkgLow = e.packageName.toLowerCase(Locale.US);
            String lblLow = e.label.toLowerCase(Locale.US);
            if (lblLow.contains("mi wallpaper") || pkgLow.contains("wallpaper")) {
                continue;
            }
            if (pkgLow.contains("vending") || lblLow.contains("play store")
                    || lblLow.contains("tizentube") || pkgLow.contains("youtube")
                    || lblLow.contains("x-plore") || pkgLow.contains("xplore")
                    || lblLow.contains("netflix") || lblLow.contains("vidio")
                    || lblLow.contains("patchwall") || pkgLow.contains("kodi")
                    || pkgLow.contains("spotify")) {
                if (!config.favoriteApps.contains(e.packageName)) {
                    config.favoriteApps.add(e.packageName);
                }
            }
        }

        // Tambahkan aplikasi lain hingga 5 aplikasi favorit jika masih kurang
        for (AppEntry e : allInstalledApps) {
            if (config.favoriteApps.size() >= 5) break;
            String lblLow = e.label.toLowerCase(Locale.US);
            if (lblLow.contains("wallpaper")) continue;
            if (!config.favoriteApps.contains(e.packageName)) {
                config.favoriteApps.add(e.packageName);
            }
        }

        config.saveFavoriteApps(context);
    }

    private void addResolveInfo(
            PackageManager pm, ResolveInfo ri,
            Set<String> hidden, Map<String, AppEntry> discovered) {
        if (ri == null || ri.activityInfo == null) return;
        ActivityInfo ai = ri.activityInfo;
        String pkg = ai.packageName;
        if (pkg == null || hidden.contains(pkg) || discovered.containsKey(pkg)) return;

        CharSequence labelCs = ri.loadLabel(pm);
        String label = labelCs != null ? labelCs.toString().trim() : pkg;
        Drawable icon = null;
        try {
            icon = ri.loadIcon(pm);
            if (icon == null && ai.applicationInfo != null) {
                icon = ai.applicationInfo.loadIcon(pm);
            }
        } catch (Exception ignored) {
        }
        Drawable banner = null;
        try {
            banner = ai.loadBanner(pm);
        } catch (Exception ignored) {
        }

        Drawable sampleSource = icon != null ? icon : banner;
        int vivid = sampleVividColor(pkg, sampleSource);

        discovered.put(pkg, new AppEntry(
                pkg, ai.name, label, banner, icon, vivid, false, null
        ));
    }

    private int sampleVividColor(String pkg, Drawable live) {
        Integer cached = colorCache.get(pkg);
        if (cached != null) return cached;
        if (live == null) return 0xFF38BDF8;

        try {
            Drawable d = live.getConstantState() != null
                    ? live.getConstantState().newDrawable().mutate()
                    : live;
            Bitmap bmp = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            d.setBounds(0, 0, 12, 12);
            d.draw(c);

            float bestScore = -1f;
            int bestColor = 0xFF38BDF8;
            float[] hsv = new float[3];

            for (int y = 0; y < 12; y++) {
                for (int x = 0; x < 12; x++) {
                    int p = bmp.getPixel(x, y);
                    if (Color.alpha(p) < 128) continue;
                    Color.colorToHSV(p, hsv);
                    float score = hsv[1] * Math.min(1f, hsv[2] * 1.4f);
                    if (score > bestScore) {
                        bestScore = score;
                        bestColor = p;
                    }
                }
            }
            bmp.recycle();

            if (bestScore < 0.15f) {
                bestColor = 0xFF38BDF8;
            }
            colorCache.put(pkg, bestColor);
            return bestColor;
        } catch (Exception e) {
            return 0xFF38BDF8;
        }
    }

    public List<AppEntry> getItems() {
        return items;
    }

    public boolean moveItem(int fromPosition, int toPosition) {
        if (fromPosition < 0 || fromPosition >= items.size() || toPosition < 0 || toPosition >= items.size()) {
            return false;
        }
        AppEntry fromEntry = items.get(fromPosition);
        AppEntry toEntry = items.get(toPosition);
        if (fromEntry.isBuiltinAction || toEntry.isBuiltinAction) {
            return false;
        }
        AppEntry moved = items.remove(fromPosition);
        items.add(toPosition, moved);
        selectedPosition = toPosition;

        // Simpan urutan favorit baru
        config.favoriteApps.clear();
        for (AppEntry e : items) {
            if (!e.isBuiltinAction) {
                config.favoriteApps.add(e.packageName);
            }
        }
        config.saveFavoriteApps(context);

        notifyItemMoved(fromPosition, toPosition);
        return true;
    }

    @Override
    public long getItemId(int position) {
        AppEntry e = items.get(position);
        String key = e.isBuiltinAction ? e.builtinId : e.packageName;
        return key != null ? key.hashCode() : position;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull final Holder holder, int position) {
        final AppEntry entry = items.get(position);
        final boolean isSelected = (position == selectedPosition);

        holder.labelView.setText(entry.label);
        holder.labelView.setAlpha(isSelected ? 1.0f : (config.alwaysShowNames ? 0.82f : 0.0f));
        holder.labelView.setTextColor(isSelected ? 0xFFFDE047 : 0xDDFFFFFF);

        float targetScale = isSelected ? 1.06f : 1.0f;
        holder.itemView.setScaleX(targetScale);
        holder.itemView.setScaleY(targetScale);
        holder.itemView.setSelected(isSelected);

        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(13f * density);

        int r = (int) (Color.red(entry.vividColor) * 0.28f + 18);
        int g = (int) (Color.green(entry.vividColor) * 0.28f + 22);
        int b = (int) (Color.blue(entry.vividColor) * 0.28f + 32);
        cardBg.setColor(Color.argb(isSelected ? 145 : 78, r, g, b));
        if (isSelected) {
            cardBg.setStroke((int) (2.2f * density), 0xFFFFFFFF);
        } else {
            cardBg.setStroke((int) (1f * density), (entry.vividColor & 0x00FFFFFF) | 0x55000000);
        }
        holder.cardContainer.setBackground(cardBg);

        holder.bannerView.setVisibility(View.GONE);
        holder.iconView.setVisibility(View.VISIBLE);
        if (entry.icon != null) {
            holder.iconView.setImageDrawable(entry.icon);
        } else if (entry.banner != null) {
            holder.iconView.setImageDrawable(entry.banner);
        } else {
            holder.iconView.setImageResource(R.drawable.ic_launcher);
        }

        if (entry.isBuiltinAction) {
            holder.badgeView.setVisibility(View.VISIBLE);
            if ("builtin_manage_favorites".equals(entry.builtinId)) {
                holder.badgeView.setText("FAV");
            } else {
                holder.badgeView.setText("ALL");
            }
        } else {
            holder.badgeView.setVisibility(View.GONE);
        }

        if (isSelected && listener != null) {
            holder.itemView.post(new Runnable() {
                @Override
                public void run() {
                    int pos = holder.getBindingAdapterPosition();
                    if (pos == selectedPosition && pos >= 0 && pos < items.size()) {
                        listener.onAppFocused(items.get(pos), holder.itemView, pos);
                    }
                }
            });
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos < items.size() && listener != null) {
                    setSelectedPosition(pos);
                    listener.onAppClicked(items.get(pos), pos);
                }
            }
        });

        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                int pos = holder.getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && pos < items.size() && listener != null) {
                    setSelectedPosition(pos);
                    listener.onAppLongClicked(items.get(pos), pos);
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static final class Holder extends RecyclerView.ViewHolder {
        final FrameLayout cardContainer;
        final ImageView bannerView;
        final ImageView iconView;
        final TextView badgeView;
        final TextView labelView;

        Holder(@NonNull View itemView) {
            super(itemView);
            cardContainer = itemView.findViewById(R.id.tile_card);
            bannerView = itemView.findViewById(R.id.tile_banner);
            iconView = itemView.findViewById(R.id.tile_icon);
            badgeView = itemView.findViewById(R.id.tile_badge);
            labelView = itemView.findViewById(R.id.tile_label);
        }
    }
}
