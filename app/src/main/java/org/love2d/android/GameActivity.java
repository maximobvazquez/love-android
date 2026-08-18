/*
 * Copyright (c) 2006-2024 LOVE Development Team
 *
 * This software is provided 'as-is', without any express or implied
 * warranty.  In no event will the authors be held liable for any damages
 * arising from the use of this software.
 *
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 *
 * 1. The origin of this software must not be misrepresented; you must not
 *    claim that you wrote the original software. If you use this software
 *    in a product, an acknowledgment in the product documentation would be
 *    appreciated but is not required.
 * 2. Altered source versions must be plainly marked as such, and must not be
 *    misrepresented as being the original software.
 * 3. This notice may not be removed or altered from any source distribution.
 */

package org.love2d.android;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Rect;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.WindowManager;

import androidx.annotation.Keep;
import androidx.core.app.ActivityCompat;

import org.libsdl.app.SDLActivity;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class GameActivity extends SDLActivity {
    private static final String TAG = "GameActivity";
    public static final int RECORD_AUDIO_REQUEST_CODE = 3;
    public static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1001;

    protected Vibrator vibrator;
    protected boolean shortEdgesMode;
    protected final int[] recordAudioRequestDummy = new int[1];
    private Uri delayedUri = null;
    private String[] args;
    private boolean isFused;

    private static native void nativeSetDefaultStreamValues(int sampleRate, int framesPerBurst);

    @Override
    protected String getMainSharedObject() {
        String[] libs = getLibraries();
        return "lib" + libs[libs.length - 1] + ".so";
    }

    @Override
    protected String[] getLibraries() {
        return new String[]{
            "c++_shared",
            "SDL3",
            "oboe",
            "openal",
            "luajit",
            "liblove",
            "love",
        };
    }

    @Override
    protected String[] getArguments() {
        return args;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "started");
        isFused = hasEmbeddedGame();
        args = new String[0];

        if (checkCallingOrSelfPermission(Manifest.permission.VIBRATE) == PackageManager.PERMISSION_GRANTED) {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }

        Intent intent = getIntent();
        handleIntent(intent, true);
        intent.setData(null);

        super.onCreate(savedInstanceState);

        if (mBrokenLibraries) {
            return;
        }

        nativeSetDefaultStreamValues(getAudioFreq(), getAudioSMP());

        if (android.os.Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams attr = getWindow().getAttributes();
            attr.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
            shortEdgesMode = false;
        }

        if (delayedUri != null) {
            sendUriAsDroppedFile(delayedUri);
            delayedUri = null;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent, false);
    }

    @Override
    protected void onDestroy() {
        if (vibrator != null) {
            Log.d(TAG, "Cancelling vibration");
            vibrator.cancel();
        }
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        if (vibrator != null) {
            Log.d(TAG, "Cancelling vibration");
            vibrator.cancel();
        }
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (grantResults.length > 0) {
            Log.d("GameActivity", "Received a request permission result");

            if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("GameActivity", "Mic permission granted");
                } else {
                    Log.d("GameActivity", "Did not get mic permission.");
                }

                Log.d("GameActivity", "Unlocking LÖVE thread");
                synchronized (recordAudioRequestDummy) {
                    recordAudioRequestDummy[0] = grantResults[0];
                    recordAudioRequestDummy.notify();
                }
            } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("GameActivity", "Notification permission granted");
                } else {
                    Log.d("GameActivity", "Did not get notification permission.");
                }
            } else {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }
    }

    @Keep
    public boolean hasEmbeddedGame() {
        AssetManager am = getAssets();
        InputStream inputStream;

        try {
            inputStream = am.open("main.lua");
        } catch (IOException e) {
            try {
                inputStream = am.open("game.love");
            } catch (IOException e2) {
                return false;
            }
        }

        try {
            inputStream.close();
        } catch (IOException ignored) {
        }

        return true;
    }

    @Keep
    public void vibrate(double seconds) {
        if (vibrator != null) {
            long duration = (long) (seconds * 1000.);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                VibrationEffect ve = VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE);
                vibrator.vibrate(ve);
            } else {
                vibrator.vibrate(duration);
            }
        }
    }

    @Keep
    public boolean hasBackgroundMusic() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        return audioManager.isMusicActive();
    }

    @Keep
    public String[] buildFileTree() {
        HashMap<String, Boolean> map = buildFileTree(getAssets(), "", new HashMap<>());
        ArrayList<String> result = new ArrayList<>();

        for (Map.Entry<String, Boolean> data : map.entrySet()) {
            result.add((data.getValue() ? "d" : "f") + data.getKey());
        }

        String[] r = new String[result.size()];
        result.toArray(r);
        return r;
    }

    @Keep
    public float getDPIScale() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return metrics.density;
    }

    @Keep
    public Rect getSafeArea() {
        Rect rect = null;

        if (android.os.Build.VERSION.SDK_INT >= 28) {
            DisplayCutout cutout = getWindow().getDecorView().getRootWindowInsets().getDisplayCutout();

            if (cutout != null) {
                rect = new Rect();
                rect.set(
                    cutout.getSafeInsetLeft(),
                    cutout.getSafeInsetTop(),
                    cutout.getSafeInsetRight(),
                    cutout.getSafeInsetBottom()
                );
            }
        }

        return rect;
    }

    @Keep
    public String getCRequirePath() {
        ApplicationInfo applicationInfo = getApplicationInfo();

        if (isNativeLibsExtracted()) {
            return applicationInfo.nativeLibraryDir + "/?.so";
        } else {
            String abi = android.os.Build.SUPPORTED_ABIS[0];
            return applicationInfo.sourceDir + "!/lib/" + abi + "/?.so";
        }
    }

    @Keep
    public void setImmersiveMode(boolean enable) {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams attr = getWindow().getAttributes();

            if (enable) {
                attr.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            } else {
                attr.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
            }
        }

        shortEdgesMode = enable;
    }

    @Keep
    public boolean getImmersiveMode() {
        return shortEdgesMode;
    }

    @Keep
    public boolean hasRecordAudioPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @Keep
    public void requestRecordAudioPermission() {
        if (ActivityCompat.checkSelfPermission(this,
            Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Log.d("GameActivity", "Requesting mic permission and locking LÖVE thread until we have an answer.");
        ActivityCompat.requestPermissions(this,
            new String[]{Manifest.permission.RECORD_AUDIO},
            RECORD_AUDIO_REQUEST_CODE);

        synchronized (recordAudioRequestDummy) {
            try {
                recordAudioRequestDummy.wait();
            } catch (InterruptedException e) {
                Log.d("GameActivity", "requesting mic permission", e);
            }
        }
    }

    // ========== NOTIFICATION METHODS ==========

    @Keep
    public void scheduleNotification(int id, long seconds, String title, String message) {
        Log.d(TAG, "Scheduling notification: id=" + id + ", seconds=" + seconds);
        NotificationScheduler.schedule(this, id, seconds, title, message);
    }

    @Keep
    public void cancelNotification(int id) {
        Log.d(TAG, "Cancelling notification: id=" + id);
        NotificationScheduler.cancel(this, id);
    }

    /**
     * Smart notification permission request:
     * - First time: shows the system dialog.
     * - Denied once: shows the system dialog again.
     * - Permanently denied: automatically opens the system settings screen
     *   so the player can re-enable notifications manually.
     */
    @Keep
    public void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {

                boolean askedBefore = getSharedPreferences("love_notifications", MODE_PRIVATE)
                    .getBoolean("notif_asked", false);
                boolean showRationale = shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS);

                if (!askedBefore || showRationale) {
                    // System dialog can still be shown
                    getSharedPreferences("love_notifications", MODE_PRIVATE).edit()
                        .putBoolean("notif_asked", true).apply();
                    Log.d(TAG, "Requesting notification permission via system dialog");
                    requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST_CODE
                    );
                } else {
                    // Permanently denied: open system settings automatically
                    Log.d(TAG, "Permission permanently denied, opening system settings");
                    openNotificationSettings();
                }
            } else {
                Log.d(TAG, "Notification permission already granted");
            }
        } else {
            Log.d(TAG, "Notification permission not required on this Android version");
        }
    }

    @Keep
    public boolean hasNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * Opens the Android system screen where the user can toggle
     * notifications for this app.
     */
    private void openNotificationSettings() {
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(intent);
        } catch (Exception e) {
            // Fallback: open the generic app details settings screen
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    // ========== END NOTIFICATION METHODS ==========

    public int getAudioSMP() {
        int smp = 256;
        AudioManager a = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        if (a != null) {
            int b = Integer.parseInt(a.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER));
            smp = b > 0 ? b : smp;
        }

        return smp;
    }

    public int getAudioFreq() {
        int freq = 44100;
        AudioManager a = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        if (a != null) {
            int b = Integer.parseInt(a.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE));
            freq = b > 0 ? b : freq;
        }

        return freq;
    }

    public boolean isNativeLibsExtracted() {
        ApplicationInfo appInfo = getApplicationInfo();
        return (appInfo.flags & ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS) != 0;
    }

    public void sendUriAsDroppedFile(Uri uri) {
        SDLActivity.onNativeDropFile(uri.toString());
    }

    private void handleIntent(Intent intent, boolean onCreate) {
        Uri game = intent.getData();
        if (game == null) {
            return;
        }

        if (onCreate) {
            if (isFused) {
                delayedUri = game;
            } else {
                processOpenGame(game);
            }
        } else {
            sendUriAsDroppedFile(game);
        }
    }

    private HashMap<String, Boolean> buildFileTree(AssetManager assetManager, String dir, HashMap<String, Boolean> map) {
        String strippedDir = dir.endsWith("/") ? dir.substring(0, dir.length() - 1) : dir;

        try {
            InputStream test = assetManager.open(strippedDir);
            test.close();
            map.put(strippedDir, false);
        } catch (FileNotFoundException e) {
            String[] list = null;

            try {
                list = assetManager.list(strippedDir);
            } catch (IOException e2) {
                Log.e(TAG, strippedDir, e2);
            }

            map.put(dir, true);

            if (!strippedDir.equals(dir)) {
                map.put(strippedDir, true);
            }

            if (list != null) {
                for (String path : list) {
                    buildFileTree(assetManager, dir + path + "/", map);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, dir, e);
        }

        return map;
    }

    private void processOpenGame(Uri game) {
        String scheme = game.getScheme();
        String path = game.getPath();

        if (scheme != null) {
            if (scheme.equals("content")) {
                args = new String[]{game.toString()};
            } else if (scheme.equals("file")) {
                args = new String[]{path};
            }
        }
    }
}
