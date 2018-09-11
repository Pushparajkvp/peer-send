package com.example.iosdev.pushit;

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import com.example.iosdev.pushit.classes.Utils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.iid.FirebaseInstanceId;

import java.util.HashMap;


public class AppClass extends Application {
    AlarmManager alarms;
    //Common shared preference for the application
    private static final String TAG="ThisTagShouldBeUnique";
    public static SharedPreferences preferences;
    @Override
    public void onCreate() {
        super.onCreate();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                Log.i(TAG,"exception :"+throwable.getMessage());
            }
        });
        alarms = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        removeAlarm();
        if(!checkAlarm()){
            setUpAlarm();
        }
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        FirebaseDatabase.getInstance().getReference().keepSynced(true);
        String uid = FirebaseAuth.getInstance().getUid();
        if(uid!=null) {
            String token = FirebaseInstanceId.getInstance().getToken();
            if(token!=null)
                FirebaseDatabase.getInstance().getReference("users").child(uid).child("token").setValue(token);
        }
        //Instantiating shared preference
        AppClass.preferences = getSharedPreferences( getPackageName() + "_preferences", MODE_PRIVATE);
        //Toast.makeText(this,"YEs",Toast.LENGTH_LONG).show();
        startService(new Intent(getBaseContext(), EndOfApplicationService.class));
        Utils.setIsUserBusy(false);
    }
    private void setUpAlarm() {
        Intent i = new Intent(this, AlarmReceiver.class);
        i.setAction("com.example.iosdev.pushit");
        PendingIntent recurringLl24 = PendingIntent.getBroadcast(this, 0, i, PendingIntent.FLAG_CANCEL_CURRENT);
        alarms.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(),1000*180, recurringLl24);
    }
    private void removeAlarm() {
        if(checkAlarm()) {
            Intent intent = new Intent(this, AlarmReceiver.class);//the same as up
            intent.setAction("com.example.iosdev.pushit");//the same as up
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);//the same as up
            alarms.cancel(pendingIntent);//important
            pendingIntent.cancel();
        }
    }
    private boolean checkAlarm() {
        Intent intent = new Intent(this, AlarmReceiver.class);//the same as up
        intent.setAction("com.example.iosdev.pushit");//the same as up
        return (PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_NO_CREATE) != null);
    }
}
