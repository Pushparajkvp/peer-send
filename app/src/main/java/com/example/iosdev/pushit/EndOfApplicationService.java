package com.example.iosdev.pushit;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.example.iosdev.pushit.classes.Utils;

//To track user force closing the application
public class EndOfApplicationService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Utils.setIsUserBusy(false);
        stopSelf();
    }
}
