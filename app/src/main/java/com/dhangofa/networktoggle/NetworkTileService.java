package com.dhangofa.networktoggle;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.telephony.NetworkModeController;
import com.dhangofa.networktoggle.telephony.NetworkModeReader;
import com.dhangofa.networktoggle.telephony.SimResolver;
import com.dhangofa.networktoggle.cycle.TileCycleManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class NetworkTileService extends TileService {
    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor();
    private static final AtomicBoolean IS_SWITCHING =
            new AtomicBoolean(false);

    private static Icon icon4g;
    private static Icon icon5g;
    private static Icon iconP5g;
    private static Icon iconP4g;
    private static Icon iconP3g;
    private static Icon icon2g;
    private static Icon iconUnknown;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AppPreferences appPreferences;
    private NetworkModeReader networkModeReader;
    private NetworkModeController networkModeController;
    private TileCycleManager tileCycleManager;

    @Override
    public void onCreate() {
        super.onCreate();

        appPreferences = new AppPreferences(this);
        tileCycleManager = new TileCycleManager(appPreferences);
        SimResolver simResolver = new SimResolver(appPreferences);
        networkModeReader = new NetworkModeReader(appPreferences, simResolver);
        networkModeController = new NetworkModeController(simResolver);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();

        NetworkMode cachedMode = appPreferences.getCachedNetworkMode();
        updateTileUI(cachedMode);

        if (appPreferences.getExecutionMode() == ExecutionMode.NONE
                || cachedMode != NetworkMode.UNKNOWN) {
            return;
        }

        EXECUTOR.execute(() -> {
            NetworkMode realMode = networkModeReader.readCurrentMode();

            mainHandler.post(() -> {
                /*
                 * A tile click or another operation may have updated the state
                 * while this asynchronous readback was running.
                 */
                if (appPreferences.getCachedNetworkMode()
                        != NetworkMode.UNKNOWN) {
                    return;
                }

                if (realMode != NetworkMode.UNKNOWN) {
                    appPreferences.setCachedNetworkMode(realMode);
                    updateTileUI(realMode);
                }
            });
        });
    }

    @Override
    public void onClick() {
        super.onClick();

        if (!IS_SWITCHING.compareAndSet(false, true)) {
            updateTileSwitchingUI();
            return;
        }

        ExecutionMode executionMode = appPreferences.getExecutionMode();
        if (executionMode == ExecutionMode.NONE) {
            IS_SWITCHING.set(false);
            updateTileUI(NetworkMode.UNKNOWN);
            return;
        }

        NetworkMode currentMode = appPreferences.getCachedNetworkMode();
        NetworkMode nextMode = tileCycleManager.getNextMode(currentMode);
        updateTileSwitchingUI();

        EXECUTOR.execute(() -> {
            CommandResult result = networkModeController.apply(
                    nextMode,
                    executionMode
            );

            mainHandler.post(() -> {
                try {
                    if (result.isSuccess()) {
                        appPreferences.setCachedNetworkMode(nextMode);
                        appPreferences.setAutoSimError(false);
                        updateTileUI(nextMode);
                    } else {
                        updateTileUI(currentMode);
                        if (appPreferences.hasAutoSimError()) {
                            showAutoSimErrorToast();
                        }
                    }
                } finally {
                    IS_SWITCHING.set(false);
                }
            });
        });
    }

    private Icon createTextOnlyIcon(String text) {
        int size = 256;
        Bitmap bitmap = Bitmap.createBitmap(
                size,
                size,
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setTypeface(Typeface.create(
                "sans-serif-condensed",
                Typeface.BOLD
        ));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(190f);

        float width = paint.measureText(text);
        if (width > 240f) {
            paint.setTextScaleX(240f / width);
        }

        Paint.FontMetrics metrics = paint.getFontMetrics();
        float y = (size / 2f) - (metrics.descent + metrics.ascent) / 2f;
        canvas.drawText(text, size / 2f, y, paint);

        return Icon.createWithBitmap(bitmap);
    }

    private Icon getCachedIcon(String text) {
        switch (text) {
            case "4G":
                if (icon4g == null) icon4g = createTextOnlyIcon("4G");
                return icon4g;
            case "5G":
                if (icon5g == null) icon5g = createTextOnlyIcon("5G");
                return icon5g;
            case "P5G":
                if (iconP5g == null) iconP5g = createTextOnlyIcon("P5G");
                return iconP5g;
            case "P4G":
                if (iconP4g == null) iconP4g = createTextOnlyIcon("P4G");
                return iconP4g;
            case "P3G":
                if (iconP3g == null) iconP3g = createTextOnlyIcon("P3G");
                return iconP3g;
            case "2G":
                if (icon2g == null) icon2g = createTextOnlyIcon("2G");
                return icon2g;
            default:
                if (iconUnknown == null) {
                    iconUnknown = createTextOnlyIcon("?");
                }
                return iconUnknown;
        }
    }

    private void updateTileSwitchingUI() {
        Tile tile = getQsTile();
        if (tile == null) return;

        tile.setState(Tile.STATE_INACTIVE);
        tile.setLabel("Switching...");
        tile.setIcon(getCachedIcon("?"));
        tile.updateTile();
    }

    private void updateTileUI(NetworkMode mode) {
        Tile tile = getQsTile();

        if (tile == null) {
            return;
        }

        if (mode == NetworkMode.UNKNOWN) {
            if (appPreferences.getExecutionMode() == ExecutionMode.NONE) {
                tile.setState(Tile.STATE_UNAVAILABLE);
                tile.setLabel("Setup Required");
            } else {
                NetworkMode firstMode = tileCycleManager.getFirstMode();

                tile.setState(Tile.STATE_INACTIVE);
                tile.setLabel("Tap to Set " + firstMode.getTileLabel());
            }

            tile.setIcon(getCachedIcon("?"));
        } else {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel(mode.getTileLabel());
            tile.setIcon(getCachedIcon(mode.getIconText()));
        }

        tile.updateTile();
    }

    private void showAutoSimErrorToast() {
        Toast.makeText(
                this,
                "Unable to detect active data SIM automatically. "
                        + "Please choose SIM from the app.",
                Toast.LENGTH_LONG
        ).show();
    }
}
