package com.example.iosdev.pushit;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AlarmReceiver extends BroadcastReceiver {

    AlarmManager alarms;

    @Override
    public void onReceive(Context context, Intent intent) {
        //Restart the alarm on booting the phone
        if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)){
            alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            removeAlarm(context);
            if(!checkAlarm(context)){
                Intent i = new Intent(context, AlarmReceiver.class);
                i.setAction("com.example.iosdev.pushit");
                PendingIntent recurringLl24 = PendingIntent.getBroadcast(context, 0, i, PendingIntent.FLAG_CANCEL_CURRENT);
                alarms.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(),1000*120, recurringLl24);
            }
        }
        //Hear beat messages to keep firebase messaging socket alive
        context.sendBroadcast(new Intent("com.google.android.intent.action.GTALK_HEARTBEAT"));
        context.sendBroadcast(new Intent("com.google.android.intent.action.MCS_HEARTBEAT"));
        Log.i("SHIT","Sent Heartbeat");
    }

    private void removeAlarm(Context context) {
        if(checkAlarm(context)) {
            Intent intent = new Intent(context, AlarmReceiver.class);//the same as up
            intent.setAction("ccom.example.iosdev.pushit");//the same as up
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);//the same as up
            alarms.cancel(pendingIntent);//important
            pendingIntent.cancel();
        }
    }
    private boolean checkAlarm(Context context) {
        Intent intent = new Intent(context, AlarmReceiver.class);//the same as up
        intent.setAction("com.example.iosdev.pushit");//the same as up
        return (PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_NO_CREATE) != null);
    }
}
