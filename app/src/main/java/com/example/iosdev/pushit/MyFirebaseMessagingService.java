package com.example.iosdev.pushit;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.widget.Toast;

import com.example.iosdev.pushit.classes.Utils;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;

//Happens When User Gets a Message
public class MyFirebaseMessagingService extends FirebaseMessagingService {
    JSONObject object;
    DatabaseReference databaseReference;
    String sender;
    long sessionId;
    private static final String TAG="ThisTagShouldBeUnique";
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        try {
            Log.i(TAG,"Received");
            sender = remoteMessage.getData().get("sender");

            String mess = remoteMessage.getData().get("mess");
            object = new JSONObject(mess);
            switch (object.getString("type")){
                case "file":
                    String name = object.getString("name");
                    sessionId = object.getLong("session");
                    String fileName = object.getString("fileName");
                    long fileSize = object.getLong("fileSize");

                    Intent i = new Intent(this,ForegroundServiceReceiver.class);
                    i.setAction(Utils.main);
                    i.putExtra("name",name);
                    i.putExtra("session",sessionId);
                    i.putExtra("id",sender);
                    i.putExtra("fileName",fileName);
                    i.putExtra("fileSize",fileSize);
                    startService(i);
                    break;
                case "video":
                    String username = object.getString("name");
                    String type = object.get("type").toString();
                    sessionId = object.getLong("session");

                    Intent callDecistion = new Intent(MyFirebaseMessagingService.this, ActivityCallDecision.class);
                    callDecistion.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    callDecistion.putExtra("sender",sender);
                    callDecistion.putExtra("type",type);
                    callDecistion.putExtra("name",username);
                    callDecistion.putExtra("session",sessionId);

                    if(Utils.isIsUserBusy()){
                        sendRejectedMessage("Busy");
                        buildNotification("You have a missed call");
                        return;
                    }
                    startActivity(callDecistion);
                    break;
                case "chat":
                    String usernameChat = object.getString("name");
                    String typeChat = object.get("type").toString();
                    sessionId = object.getLong("session");

                    Intent callDecistionChat = new Intent(MyFirebaseMessagingService.this, ActivityCallDecision.class);
                    callDecistionChat.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    callDecistionChat.putExtra("sender",sender);
                    callDecistionChat.putExtra("type",typeChat);
                    callDecistionChat.putExtra("name",usernameChat);
                    callDecistionChat.putExtra("session",sessionId);

                    if(Utils.isIsUserBusy()){
                        sendRejectedMessage("Busy");
                        buildNotification("You have a missed call");
                        return;
                    }
                    startActivity(callDecistionChat);
                    break;
            }


            /*Intent i = new Intent(MyFirebaseMessagingService.this, ActivityCallDecision.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.putExtra("sender",sender);
            i.putExtra("type",type);
            i.putExtra("name",name);
            i.putExtra("session",sessionId);

            if(Utils.isIsUserBusy()){
                sendRejectedMessage("Busy");
                buildNotification("You have a missed call");
                return;
            }
            startActivity(i);*/

        }catch (Exception ignored){
            Log.i(TAG,ignored.getMessage());
        }
    }
    public void sendRejectedMessage(String type){
        try {
            databaseReference = FirebaseDatabase.getInstance().getReference().child("users").child(sender).child("mess");
            JSONObject objectToSend = new JSONObject();
            objectToSend.put("type", type);
            objectToSend.put("session",sessionId);
            databaseReference.setValue(objectToSend.toString() + System.currentTimeMillis());
        }catch (Exception ignored){}
    }
    public void buildNotification(String message){
        NotificationCompat.Builder builder= new NotificationCompat.Builder(this);

        builder.setSmallIcon(R.mipmap.ic_launcher_round);
        builder.setContentTitle("CALL");
        builder.setContentText(message);
        builder.setSmallIcon(R.mipmap.ic_launcher_round);
        builder.setWhen(0);
        builder.setAutoCancel(true);
        builder.setPriority(Notification.PRIORITY_MAX);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        Intent intent = new Intent(this,MainPage.class);
        stackBuilder.addNextIntent(intent);
        PendingIntent pendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent);
        //Notification Manager
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(0, builder.build());
    }

}
