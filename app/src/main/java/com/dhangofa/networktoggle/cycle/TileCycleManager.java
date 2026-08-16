package com.dhangofa.networktoggle.cycle;

import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.NetworkMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class TileCycleManager {
    public static final int MIN_MODES = 2;
    public static final int MAX_MODES = 3;

    private static final List<NetworkMode> DEFAULT_CYCLE =
            Collections.unmodifiableList(Arrays.asList(
                    NetworkMode.FIVE_G_ONLY,
                    NetworkMode.FOUR_G_ONLY,
                    NetworkMode.PREFERRED_5G
            ));

    public enum ChangeResult {
        CHANGED,
        MINIMUM_REACHED,
        MAXIMUM_REACHED,
        INVALID_MODE,
        NO_CHANGE
    }

    private final AppPreferences appPreferences;

    public TileCycleManager(AppPreferences appPreferences) {
        this.appPreferences = appPreferences;
    }

    public List<NetworkMode> getCycle() {
        List<NetworkMode> saved = appPreferences.getTileCycleModes();
        if (!isValid(saved)) {
            appPreferences.setTileCycleModes(DEFAULT_CYCLE);
            return new ArrayList<>(DEFAULT_CYCLE);
        }
        return new ArrayList<>(saved);
    }

    public NetworkMode getFirstMode() {
        return getCycle().get(0);
    }

    public NetworkMode getNextMode(NetworkMode currentMode) {
        List<NetworkMode> cycle = getCycle();
        int currentIndex = cycle.indexOf(currentMode);
        if (currentIndex < 0) return cycle.get(0);
        return cycle.get((currentIndex + 1) % cycle.size());
    }

    public ChangeResult setSelected(NetworkMode mode, boolean selected) {
        if (mode == null || mode == NetworkMode.UNKNOWN) {
            return ChangeResult.INVALID_MODE;
        }

        List<NetworkMode> cycle = getCycle();
        boolean alreadySelected = cycle.contains(mode);
        if (selected == alreadySelected) return ChangeResult.NO_CHANGE;

        if (selected) {
            if (cycle.size() >= MAX_MODES) return ChangeResult.MAXIMUM_REACHED;
            cycle.add(mode);
        } else {
            if (cycle.size() <= MIN_MODES) return ChangeResult.MINIMUM_REACHED;
            cycle.remove(mode);
        }

        appPreferences.setTileCycleModes(cycle);
        // Cache is purposefully kept alive so UI changes can sync network state
        return ChangeResult.CHANGED;
    }

    public boolean forceRemoveUnsupportedAndAutoFill(AppPreferences.NetworkCapabilities caps) {
        List<NetworkMode> cycle = getCycle();
        boolean changed = false;

        if (!caps.supports5g) { 
            changed |= cycle.remove(NetworkMode.PREFERRED_5G); 
            changed |= cycle.remove(NetworkMode.FIVE_G_ONLY); 
        }
        if (!caps.supports3g) { 
            changed |= cycle.remove(NetworkMode.PREFERRED_3G); 
        }
        if (!caps.supports2g) { 
            changed |= cycle.remove(NetworkMode.TWO_G_ONLY); 
        }

        if (changed) {
            // Auto fill to reach MIN_MODES
            if (cycle.size() < MIN_MODES) {
                if (!cycle.contains(NetworkMode.PREFERRED_4G)) cycle.add(NetworkMode.PREFERRED_4G);
                if (cycle.size() < MIN_MODES && caps.supports5g && !cycle.contains(NetworkMode.PREFERRED_5G)) cycle.add(NetworkMode.PREFERRED_5G);
                if (cycle.size() < MIN_MODES && !cycle.contains(NetworkMode.FOUR_G_ONLY)) cycle.add(NetworkMode.FOUR_G_ONLY);
            }
            appPreferences.setTileCycleModes(cycle);
            // Cache is purposefully kept alive so UI changes can sync network state
            return true;
        }
        return false;
    }

    public boolean isValid(List<NetworkMode> cycle) {
        if (cycle == null || cycle.size() < MIN_MODES || cycle.size() > MAX_MODES) {
            return false;
        }

        List<NetworkMode> unique = new ArrayList<>();
        for (NetworkMode mode : cycle) {
            if (mode == null || mode == NetworkMode.UNKNOWN || unique.contains(mode)) {
                return false;
            }
            unique.add(mode);
        }
        return true;
    }
}
