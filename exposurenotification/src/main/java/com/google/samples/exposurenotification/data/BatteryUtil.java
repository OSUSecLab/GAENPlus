package com.google.samples.exposurenotification.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import com.google.samples.exposurenotification.crypto.CryptoException;
import com.google.samples.exposurenotification.nearby.AdvertisementPacket;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class BatteryUtil {

    public static float prev_batteryPct = -1;
    public static BroadcastReceiver batteryStateReceiver;

    public static double getBatteryPercentage(Context context) {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        double batteryPct = level * 100 / (float)scale;
        return batteryPct;
    }

    public static void startRecordingBatteryPercentage(Context context) {
        int recordingInterval = 20000; // every 20s
        long startTime = System.currentTimeMillis();
        double initialBatteryPercentage = getBatteryPercentage(context);
        Timer timer = new Timer();
        String outPath = Environment.getExternalStorageDirectory().getPath() + "/Download" +
                "/batteryLog.txt";

        // clear log
        FileUtil.writeToFile(outPath, "", false);

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // calculate power
                double elapsedTime =
                        (double) (System.currentTimeMillis() - startTime) / 1000 / 3600; // in hours
                double currentBatteryPercentage = getBatteryPercentage(context);
                double percentageDiff = initialBatteryPercentage - currentBatteryPercentage;
                double averagePower = getAveragePower(context, percentageDiff, elapsedTime);

                String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                Log.i("BatteryUtil", currentTime + "\t battery level: " + currentBatteryPercentage);
                // record battery status
                FileUtil.writeToFile(outPath,
                        currentTime + "\t battery level: " + currentBatteryPercentage + "\t " +
                                "average power: " + averagePower + "W",
                        true);
            }
        }, 0, recordingInterval);
    }

    public static double getAveragePower(Context context, double percentage, double time) {
        if (time == 0 || percentage == 0)
            return 0;
        double capacity = getBatteryCapacity(context);
        double voltage = getVoltage(context);

        double averageCurrent = capacity * percentage / 100 / 1000 / time; // in A
        return averageCurrent * voltage; // W = A*V
    }

    public static double getBatteryCapacity(Context context) {
        // return in mAh
        Object mPowerProfile;
        double batteryCapacity = 0;
        final String POWER_PROFILE_CLASS = "com.android.internal.os.PowerProfile";

        try {
            mPowerProfile = Class.forName(POWER_PROFILE_CLASS)
                    .getConstructor(Context.class)
                    .newInstance(context);

            batteryCapacity = (double) Class
                    .forName(POWER_PROFILE_CLASS)
                    .getMethod("getBatteryCapacity")
                    .invoke(mPowerProfile);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return batteryCapacity;
    }

    public static double getVoltage(Context context)
    {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent b = context.registerReceiver(null, ifilter);
        return b.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) * 0.001; // in V
    }

}
