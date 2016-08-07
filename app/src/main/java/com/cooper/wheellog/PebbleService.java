package com.cooper.wheellog;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.cooper.wheellog.Utils.Constants;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.util.UUID;

import timber.log.Timber;

public class PebbleService extends Service {

    private static final UUID APP_UUID = UUID.fromString("185c8ae9-7e72-451a-a1c7-8f1e81df9a3d");

    static final int KEY_SPEED = 0;
    static final int KEY_BATTERY = 1;
    static final int KEY_TEMPERATURE = 2;
    static final int KEY_FAN_STATE = 3;
    static final int KEY_BT_STATE = 4;

    private Handler mHandler = new Handler();
    private static PebbleService instance = null;

    int lastSpeed = 0;
    int lastBattery = 0;
    int lastTemperature = 0;
    int lastFanStatus = 0;
    int lastBluetooth = 0;

    public static boolean isInstanceCreated() {
        return instance != null;
    }

    private Runnable mSendPebbleData = new Runnable() {
        @Override
        public void run() {
            PebbleDictionary outgoing = new PebbleDictionary();

            if (lastSpeed != WheelData.getInstance().getSpeed())
            {
                lastSpeed = WheelData.getInstance().getSpeed();
                outgoing.addInt32(KEY_SPEED, lastSpeed);
            }

            if (lastBattery != WheelData.getInstance().getBatteryLevel())
            {
                lastBattery = WheelData.getInstance().getBatteryLevel();
                outgoing.addInt32(KEY_BATTERY, lastBattery);
            }

            if (lastTemperature != WheelData.getInstance().getTemperature())
            {
                lastTemperature = WheelData.getInstance().getTemperature();
                outgoing.addInt32(KEY_TEMPERATURE, lastTemperature);
            }

            if (lastFanStatus != WheelData.getInstance().getFanStatus())
            {
                lastFanStatus = WheelData.getInstance().getFanStatus();
                outgoing.addInt32(KEY_FAN_STATE, lastFanStatus);
            }

            if (lastBluetooth != WheelData.getInstance().getConnectionState())
            {
                lastBluetooth = WheelData.getInstance().getConnectionState();
                outgoing.addInt32(KEY_BT_STATE, lastBluetooth);
            }

            if (outgoing.size() > 0)
            {
                PebbleKit.sendDataToPebble(getApplicationContext(), APP_UUID, outgoing);
            }
            mHandler.postDelayed(mSendPebbleData, 100);
        }
    };

/*
    private void sendPebbleAlert(final String text) {
        // Push a notification
        final Intent i = new Intent("com.getpebble.action.SEND_NOTIFICATION");

        final Map data = new HashMap();
        data.put("title", "Test Message");
        data.put("body", text);
        final JSONObject jsonData = new JSONObject(data);
        final String notificationData = new JSONArray().put(jsonData).toString();

        i.putExtra("messageType", "PEBBLE_ALERT");
        i.putExtra("sender", "PebbleKit Android");
        i.putExtra("notificationData", notificationData);
        sendBroadcast(i);
    }
*/

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        instance = this;
//        PebbleKit.registerReceivedAckHandler(this, ackReceiver);
        PebbleKit.registerReceivedNackHandler(this, nackReceiver);

        PebbleKit.startAppOnPebble(this, APP_UUID);
        mHandler.post(mSendPebbleData);

        Intent serviceStartedIntent = new Intent(Constants.ACTION_PEBBLE_SERVICE_TOGGLED);
        serviceStartedIntent.putExtra(Constants.INTENT_EXTRA_IS_RUNNING, true);
        sendBroadcast(serviceStartedIntent);

        Timber.d("PebbleConnectivity Started");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Intent serviceStartedIntent = new Intent(Constants.ACTION_PEBBLE_SERVICE_TOGGLED);
        serviceStartedIntent.putExtra(Constants.INTENT_EXTRA_IS_RUNNING, false);
        sendBroadcast(serviceStartedIntent);
        instance = null;
        PebbleKit.closeAppOnPebble(this, APP_UUID);
        mHandler.removeCallbacksAndMessages(null);
//        unregisterReceiver(ackReceiver);
        unregisterReceiver(nackReceiver);
        Timber.d("PebbleConnectivity Stopped");
    }

    private PebbleKit.PebbleNackReceiver nackReceiver = new PebbleKit.PebbleNackReceiver(APP_UUID) {
        @Override
        public void receiveNack(Context context, int transactionId) {
            lastSpeed = -1;
            lastBattery = -1;
            lastTemperature = -1;
            lastBluetooth = -1;
            lastFanStatus = -1;
        }
    };
}
