package com.openprop.game;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class VibrationManager {
    private final Vibrator vibrator;
    public boolean enabled = true;

    public VibrationManager(Context context) {
        Vibrator v = null;
        try {
            v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Exception ignored) {}
        vibrator = v;
    }

    public void tick() {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(15, 120));
            } else {
                vibrator.vibrate(15);
            }
        } catch (Exception ignored) {}
    }

    public void click() {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(25, 160));
            } else {
                vibrator.vibrate(25);
            }
        } catch (Exception ignored) {}
    }

    public void pulse() {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(45, 200));
            } else {
                vibrator.vibrate(45);
            }
        } catch (Exception ignored) {}
    }

    public void heavy() {
        if (!enabled || vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(90, 255));
            } else {
                vibrator.vibrate(90);
            }
        } catch (Exception ignored) {}
    }
}
