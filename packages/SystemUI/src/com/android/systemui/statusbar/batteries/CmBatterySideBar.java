package com.android.systemui.statusbar.batteries;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.BatteryManager;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.ProgressBar;

public class CmBatterySideBar extends ProgressBar implements Animatable, Runnable {

    private static final String TAG = CmBatterySideBar.class.getSimpleName();

    // Total animation duration
    private static final int ANIMS_DURATION = 5000; // 5 seconds

    // Duration between frames of charging animation
    private static final int FRAMES_DURATION = ANIMS_DURATION / 100;

    // Are we listening for actions?
    private boolean mAttacheds = false;

    // Should we show this?
    private boolean mShowCmBatterySideBar = false;

    // Current battery level
    private int mBatteryLevels = 0;

    // Current "step" of charging animation
    private int mChargingLevels = -1;

    // Are we charging?
    private boolean mBatteryChargings = false;

    private Handler mHandler = new Handler();

    class SettingsObserver extends ContentObserver {

        public SettingsObserver(Handler handler) {
            super(handler);
        }

        void observer() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_BATTERY), false, this);
            resolver.registerContentObserver(
                    Settings.System.getUriFor(Settings.System.STATUS_BAR_BATTERY_COLOR), false,
                    this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    public CmBatterySideBar(Context context) {
        this(context, null);
    }

    public CmBatterySideBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CmBatterySideBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        SettingsObserver observer = new SettingsObserver(mHandler);
        observer.observer();
        updateSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttacheds) {
            mAttacheds = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            getContext().registerReceiver(mIntentReceiver, filter, null, getHandler());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        if (mAttacheds) {
            mAttacheds = false;
            getContext().unregisterReceiver(mIntentReceiver);
        }
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String actions = intent.getAction();
            if (Intent.ACTION_BATTERY_CHANGED.equals(actions)) {
                mBatteryLevels = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                mBatteryChargings = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0) == BatteryManager.BATTERY_STATUS_CHARGING;
                if (mBatteryChargings && mBatteryLevels < 100) {
                    start();
                } else {
                    stop();
                }
            } else if (Intent.ACTION_SCREEN_OFF.equals(actions)) {
                stop();
            } else if (Intent.ACTION_SCREEN_ON.equals(actions)) {
                if (mBatteryChargings && mBatteryLevels < 100) {
                    start();
                }
            }
        }
    };

    private void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();
        mShowCmBatterySideBar = (Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_BATTERY, 0) == 4);
        if (mShowCmBatterySideBar) {
            setVisibility(VISIBLE);
        } else {
            setVisibility(GONE);
        }

        Drawable sd = getProgressDrawable();
        if (sd instanceof LayerDrawable) {
            Drawable sidebar = ((LayerDrawable) sd)
                    .findDrawableByLayerId(com.android.internal.R.id.progress);
            if (sidebar != null) {
                String color = Settings.System
                        .getString(resolver, Settings.System.STATUS_BAR_BATTERY_COLOR);
                Integer sidebarColor = null;
                if (!TextUtils.isEmpty(color)) {
                    try {
                        sidebarColor = Color.parseColor(color);
                    } catch (IllegalArgumentException e) {
                    }
                }
                if (sidebarColor != null) {
                    sidebar.setColorFilter(sidebarColor, PorterDuff.Mode.SRC);
                } else {
                    sidebar.clearColorFilter();
                }
                invalidate();
            }
        }

        if (mBatteryChargings && mBatteryLevels < 100) {
            start();
        } else {
            stop();
        }
    }

    @Override
    public void run() {
        mChargingLevels++;
        if (mChargingLevels > 100) {
            mChargingLevels = mBatteryLevels;
        }
        setProgress(mChargingLevels);
        mHandler.postDelayed(this, FRAMES_DURATION);
    }

    @Override
    public void start() {
        if (!isRunning()) {
            mHandler.removeCallbacks(this);
            mChargingLevels = mBatteryLevels;
            mHandler.postDelayed(this, FRAMES_DURATION);
        }
    }

    @Override
    public void stop() {
        if (isRunning()) {
            mHandler.removeCallbacks(this);
            mChargingLevels = -1;
        }
        setProgress(mBatteryLevels);
    }

    @Override
    public boolean isRunning() {
        return mChargingLevels != -1;
    }

}
