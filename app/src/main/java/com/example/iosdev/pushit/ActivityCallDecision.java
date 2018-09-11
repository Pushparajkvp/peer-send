package com.example.iosdev.pushit;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.example.iosdev.pushit.classes.Utils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;


public class ActivityCallDecision extends AppCompatActivity implements ValueEventListener {

    DatabaseReference databaseReference,valueReference;

    Button callAcceptedButton,callRejectedButton;
    TextView nameTextView,callTypeTextView;

    long sessionId;
    RelativeLayout relativeLayout;

    //For call vibration and ringtone
    Vibrator vibrator;
    Ringtone ringtone;

    //Caller details
    String sender,type,name;

    private static final String REJECTED = "REJECTED";
    private static final String SENT = "SENT";

    boolean firstRead=true;
    private static final String ACKNOWLEDGE = "ACK";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call_decision);
        getWindow().addFlags(LayoutParams.FLAG_DISMISS_KEYGUARD |
                LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                LayoutParams.FLAG_TURN_SCREEN_ON |
                LayoutParams.FLAG_KEEP_SCREEN_ON);

        //Make user busy
        Utils.setIsUserBusy(true);
        Bundle extras = getIntent().getExtras();
        sender = extras.getString("sender");
        type = extras.getString("type");
        name = extras.getString("name");
        sessionId = extras.getLong("session");

        callAcceptedButton = (Button)findViewById(R.id.callAcceptButton);
        callRejectedButton = (Button)findViewById(R.id.callRejectButton);
        nameTextView = (TextView)findViewById(R.id.txtCallerName);
        callTypeTextView = (TextView)findViewById(R.id.txtCallType);
        relativeLayout = (RelativeLayout)findViewById(R.id.relativeLayoutCallDecision);

        nameTextView.setText(name);
        callTypeTextView.setText("is "+type+" calling");

        callAcceptedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopRinging();
                answerCall();
            }
        });
        callRejectedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopRinging();
                sendRejectedMessage();
                //Make user free
                Utils.setIsUserBusy(false);
                finishActivity();
            }
        });

        //Start ringing and send message
        startRinging();
        sendReceivedMessage();
        //FirebaseDatabase.getInstance().getReference().child("users").child(FirebaseAuth.getInstance().getUid()).child("mess").keepSynced(true);
        FirebaseDatabase.getInstance().getReference().child("users").child(FirebaseAuth.getInstance().getUid()).child("mess").addValueEventListener(this);
    }

    private void answerCall() {
        if(type.equals("video")){
            Intent intent;
            intent = new Intent(this, ActivityVideoCallReceiver.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra("cloud",sender);
            intent.putExtra("session",sessionId);
            startActivity(intent);
            FirebaseDatabase.getInstance().getReference().child("users").child(FirebaseAuth.getInstance().getUid()).child("mess").removeEventListener(this);
            stopRinging();
            finish();
        }else{
            Intent intent;
            intent = new Intent(this, ActivityChatWindowReceiver.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra("cloud",sender);
            intent.putExtra("session",sessionId);
            intent.putExtra("name",name);
            startActivity(intent);
            FirebaseDatabase.getInstance().getReference().child("users").child(FirebaseAuth.getInstance().getUid()).child("mess").removeEventListener(this);
            stopRinging();
            finish();
        }

    }

    //Starts the ringing and vibrating
    private void startRinging() {
        vibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);
        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        ringtone = RingtoneManager.getRingtone(getApplicationContext(), notification);
        ringtone.play();
        if(vibrator!=null)
            vibrator.vibrate(new long[]{1000,1000},0);
    }

    //Stops vibration and ringing
    public void stopRinging(){
        vibrator.cancel();
        ringtone.stop();
    }


    //Sends a message that the call is received
    public void sendReceivedMessage(){
        try {
            databaseReference = FirebaseDatabase.getInstance().getReference().child("users").child(sender).child("mess");
            JSONObject objectToSend = new JSONObject();
            objectToSend.put("type", SENT);
            objectToSend.put("session",sessionId);
            databaseReference.setValue(objectToSend.toString() + System.currentTimeMillis());
        }catch (Exception ignored){}
    }

    //Sends a message that the call is rejected
    public void sendRejectedMessage(){
        try {
            databaseReference = FirebaseDatabase.getInstance().getReference().child("users").child(sender).child("mess");
            JSONObject objectToSend = new JSONObject();
            objectToSend.put("type", REJECTED);
            objectToSend.put("session",sessionId);
            databaseReference.setValue(objectToSend.toString() + System.currentTimeMillis());
        }catch (Exception ignored){}
    }

    @Override
    public void onDataChange(DataSnapshot dataSnapshot) {
        //Log.i("CallDecisionChecking","yes");
        if(dataSnapshot==null)
                return;
        if(firstRead){
            firstRead =false;
            try {
                String mess = dataSnapshot.getValue().toString();
                int i;
                for(i=mess.length()-1;;i--){
                    if(!Character.isDigit(mess.charAt(i)))
                        break;
                }
                mess=mess.substring(0,i+1);
                JSONObject object = new JSONObject(mess);
                if(object.getLong("session") == sessionId && object.getString("type").equals("STOP")) {
                    buildNotification("You have a missed call from "+name);
                    //Log.i("CallDecisionChecking","STOP");
                    finishActivity();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return;
        }
        try {
            String mess = dataSnapshot.getValue().toString();
            int i;
            for(i=mess.length()-1;;i--){
                if(!Character.isDigit(mess.charAt(i)))
                    break;
            }
            mess=mess.substring(0,i+1);
            JSONObject object = new JSONObject(mess);
            processMessage(object);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void processMessage(JSONObject object) {
        try {
            if (object.getLong("session") != sessionId){
                buildNotification("You have a missed call from "+name);
                finishActivity();
                //Log.i("CallDecisionChecking","session");
            }else if(object.getLong("session") == sessionId && object.getString("type").equals("STOP")) {
                buildNotification("You have a missed call from "+name);
                //Log.i("CallDecisionChecking","STOP");
                finishActivity();
            }else if(object.getLong("session") == sessionId && object.getString("type").equals("WEB")){
                FirebaseDatabase.getInstance().getReference().child("users").child(FirebaseAuth.getInstance().getUid()).child("mess").removeEventListener(this);
                stopRinging();
                finish();
                Utils.setIsUserBusy(false);
            }
        }catch (Exception ignored){}
    }

    public void finishActivity(){
        try{
            JSONObject object = new JSONObject();
            object.put("type",ACKNOWLEDGE);
            object.put("session",sessionId);
            send(object.toString());
        }catch (Exception ignored){}
        FirebaseDatabase.getInstance().getReference().child("users").child(FirebaseAuth.getInstance().getUid()).child("mess").removeEventListener(this);
        stopRinging();
        finish();
        Utils.setIsUserBusy(false);
    }
    public void send(final String mess){
        HashMap<String, Object> update_map = new HashMap<>();
        update_map.put("users/"+sender+"/mess", mess+System.currentTimeMillis());
        FirebaseDatabase.getInstance().getReference().updateChildren(update_map);
    }
    @Override
    public void onCancelled(DatabaseError databaseError) {

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
