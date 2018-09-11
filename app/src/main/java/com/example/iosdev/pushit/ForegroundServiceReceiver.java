package com.example.iosdev.pushit;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.opengl.Visibility;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.example.iosdev.pushit.classes.Utils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

public class ForegroundServiceReceiver extends Service implements ValueEventListener {
    public ForegroundServiceReceiver(){}
    //SDP constants
    private static final String SDP_MID = "sdpMid";
    private static final String SDP_M_LINE_INDEX = "sdpMLineIndex";
    private static final String SDP = "sdp";
    private static final String TAG="ThisTagShouldBeUnique";

    private long sessionId;

    private static final String ACKNOWLEDGE = "ACK";
    private static final String PICKED = "PICKED";
    private static final String STOP = "STOP";
    private static final String SENT = "SENT";
    private static final String REJECTED = "REJECTED";
    private static final String OFFER = "OFFER";
    private static final String ANSWER = "ANSWER";
    private static final String CANDIDATE = "CANDIDATE";
    private static final String BUSY="BUSY";
    boolean CONNECTED = false;
    //P2P Components
    private PeerConnection peerConnection;
    private PeerConnectionFactory peerConnectionFactory;

    //Firebase database reference
    DatabaseReference databaseReference;
    boolean first=true;

    //To store all localCandidates and send at once
    JSONArray candidates;
    Queue<byte[]> dataQue;
    //dc
    DataChannel dataChannel;

    RemoteViews notificationViews;
    Notification notification;
    NotificationManager manager;

    String remoteUserId,remoteUserName;
    Handler mHandler;
    Runnable timeoutRunner;

    String fileName;
    long fileSize,receivedSize =0;
    long globalCounter=0;
    ReceiverUiThread receiverThread ;
    PowerManager.WakeLock wakeLock;
    @Override
    public void onCreate() {
        manager =(NotificationManager) this
                .getSystemService(Context.NOTIFICATION_SERVICE);
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        notificationViews = new RemoteViews(getPackageName(),R.layout.custom_notification_receiver);
        if(intent==null)
            return START_STICKY;
        if(intent.getAction()==null)
            return START_STICKY;
        if (intent.getAction().equals(Utils.cancel)) {
            wakeLock.release();
            if(receiverThread!=null)
                receiverThread.interrupted=true;
            notificationViews.setBoolean(R.id.notificationCancelButton,"setEnabled",false);
            try {
                JSONObject obj = new JSONObject();
                obj.put("type",STOP);
                obj.put("session",sessionId);
                send(obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if(mHandler!=null)
                mHandler.postDelayed(timeoutRunner,5000);
        }else if(intent.getAction().equals(Utils.accept)){
            //Peer Connection
            PeerConnectionFactory.initializeAndroidGlobals(
                    this,  // Context
                    true,  // Audio Enabled
                    true,  // Video Enabled
                    true); // Render EGL Context
            dataQue = new LinkedList<>();
            setUpFactoryAndCreatePeer();
            updateAfterAccept(0,"Connecting");
            Log.i(TAG,"in accept");
            sendPicked();
        }else if(intent.getAction().equals(Utils.main)){
            PowerManager pwm = (PowerManager)getSystemService(Context.POWER_SERVICE);
            wakeLock = pwm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"com.example.iosdev.pushit.wakelock");
            wakeLock.acquire();
            mHandler = new Handler();
            timeoutRunner = new Runnable() {
                @Override
                public void run() {
                    dismissForeGrounf();
                }
            };
            databaseReference = FirebaseDatabase.getInstance().getReference();
            databaseReference.child("users").child(FirebaseAuth.getInstance().getUid()).child("mess").addValueEventListener(this);
            candidates = new JSONArray();
            Bundle extras = intent.getExtras();

            this.remoteUserId = extras.getString("id");
            this.sessionId = extras.getLong("session");
            this.remoteUserName = extras.getString("name");
            this.fileName = extras.getString("fileName");
            this.fileSize = extras.getLong("fileSize");
            receiverThread = new ReceiverUiThread();

            Utils.setFileUploading(true);
            notification = showNotification(0,remoteUserName+ " is Sharing");
            startForeground(1997,
                    notification);
            fileSetup();

        }
        return START_STICKY;
    }

    private Notification showNotification(int progress,String text) {
        Intent notificationIntent = new Intent(this, MainPage.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        Intent nextIntent = new Intent(this, ForegroundServiceReceiver.class);
        nextIntent.setAction(Utils.cancel);
        PendingIntent cancelIntent = PendingIntent.getService(this, 0,
                nextIntent, 0);

        Intent i = new Intent(this, ForegroundServiceReceiver.class);
        i.setAction(Utils.accept);
        PendingIntent acceptIntent = PendingIntent.getService(this, 0,
                i, 0);

        //notificationViews.setProgressBar(R.id.notificationProgress,100,progress,false);
        notificationViews.setOnClickPendingIntent(R.id.notificationCancelButton,cancelIntent);
        notificationViews.setOnClickPendingIntent(R.id.notificationAcceptButton,acceptIntent);
        notificationViews.setTextViewText(R.id.textView,text);

        notification = new NotificationCompat.Builder(this)
                .setTicker(getResources().getString(R.string.app_name))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setContent(notificationViews)
                .setOngoing(true)
                .build();

        return notification;
    }

    private Notification showNotificationAfterAccept(int progress,String text) {
        Intent notificationIntent = new Intent(this, MainPage.class);
        notificationIntent.setAction(Utils.main);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        Intent nextIntent = new Intent(this, ForegroundServiceReceiver.class);
        nextIntent.setAction(Utils.cancel);
        PendingIntent cancelIntent = PendingIntent.getService(this, 0,
                nextIntent, 0);

        notificationViews.setViewVisibility(R.id.notificationAcceptButton, View.GONE);
        notificationViews.setViewVisibility(R.id.notificationProgress,View.VISIBLE);
        notificationViews.setProgressBar(R.id.notificationProgress,100,progress,false);
        notificationViews.setOnClickPendingIntent(R.id.notificationCancelButton,cancelIntent);
        notificationViews.setTextViewText(R.id.textView,text);

        notification = new NotificationCompat.Builder(this)
                .setTicker(getResources().getString(R.string.app_name))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setContent(notificationViews)
                .setOngoing(true)
                .build();

        return notification;
    }

    public void updateProgress(int n,String text){
        notification = showNotification(n,text);
        manager.notify(1997,notification);
    }

    public void updateAfterAccept(int n,String text){
        notification = showNotificationAfterAccept(n,text);
        manager.notify(1997,notification);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Used only in case if services are bound (Bound Services).
        return null;
    }

    private void setUpFactoryAndCreatePeer() {
        //We should not created if already exists
        if(peerConnection!=null)
            return;

        //Createing factory
        peerConnectionFactory = new PeerConnectionFactory();
        MediaConstraints mc = new MediaConstraints();
        //Stun and Turn servers list setup
        ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));

        iceServers.add(new PeerConnection.IceServer("stun:s3.xirsys.com"
                , "2a8a1e58-1efe-11e8-be63-5f9d2a90df12"
                ,"2a8a1fa2-1efe-11e8-8cd9-6b6cf5567674"));
        iceServers.add(new PeerConnection.IceServer("turn:s3.xirsys.com:80?transport=udp"
                , "2a8a1e58-1efe-11e8-be63-5f9d2a90df12"
                ,"2a8a1fa2-1efe-11e8-8cd9-6b6cf5567674"));
        iceServers.add(new PeerConnection.IceServer("turn:s3.xirsys.com:3478?transport=udp"
                , "2a8a1e58-1efe-11e8-be63-5f9d2a90df12"
                ,"2a8a1fa2-1efe-11e8-8cd9-6b6cf5567674"));
        iceServers.add(new PeerConnection.IceServer("turn:s3.xirsys.com:80?transport=tcp"
                , "2a8a1e58-1efe-11e8-be63-5f9d2a90df12"
                ,"2a8a1fa2-1efe-11e8-8cd9-6b6cf5567674"));


        //Create the peer!
        peerConnection = peerConnectionFactory.createPeerConnection(
                iceServers,
                mc,
                peerConnectionObserver);

        //dc
        DataChannel.Init dcInit = new DataChannel.Init();
        dcInit.id=1;
        dcInit.ordered=true;
        dataChannel =peerConnection.createDataChannel("1",dcInit);
        dataChannel.registerObserver(dcObserver);
    }
    DataChannel.Observer dcObserver = new DataChannel.Observer() {
        @Override
        public void onBufferedAmountChange(long l) {}

        @Override
        public void onStateChange() {
            Log.i(TAG,"State Changed " + dataChannel.state());
            if(dataChannel.state().equals(DataChannel.State.OPEN)) {
                receiverThread.start();

            }
        }

        @Override
        public void onMessage(DataChannel.Buffer buffer) {
            ByteBuffer dataBuffer = buffer.data;
            byte[] data = new byte[dataBuffer.remaining()];
            dataBuffer.get(data,0,data.length);
            if(data.length<=8000) {
                Log.i(TAG,String.valueOf(data.length));
                try {
                    receivedSize+=data.length;
                    stream.write(data,0,data.length);
                } catch (IOException e) {
                    Log.i(TAG,e.getMessage());
                }
                //Log.i(TAG,String.valueOf((++counter) * 8000));
            }
            else if(data.length==8001){
                try {
                    stream.flush();
                    stream.close();
                    Log.i(TAG, "Closed");
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ForegroundServiceReceiver.this,"File Stored In Downloads",Toast.LENGTH_LONG).show();
                            receiverThread.interrupted = true;
                            buildNotification("Done");
                            Intent i = new Intent(ForegroundServiceReceiver.this,ForegroundServiceReceiver.class);
                            i.setAction(Utils.cancel);
                            ForegroundServiceReceiver.this.onStartCommand(i,0,0);
                        }
                    });
                } catch (IOException e) {
                    Log.i(TAG,e.getMessage());
                }

            }else{
                Log.i(TAG,"Another size");
            }
            //Log.i(TAG,String.valueOf(++counter));
            //Log.i(TAG,"Got data");
        }
    };
    int counter=0;
    PeerConnection.Observer peerConnectionObserver = new PeerConnection.Observer() {
        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

            //Wait until we get all the localCandidates
            if(iceGatheringState.equals(PeerConnection.IceGatheringState.COMPLETE)) {
                updateAfterAccept(0,"Gathering Complete");

                //Send it as json to other peer
                try {
                    JSONObject wrapper = new JSONObject();
                    wrapper.put("candidates", candidates);
                    wrapper.put("type", CANDIDATE);
                    wrapper.put("session",sessionId);
                    Log.i(TAG, "Gathered and sent");
                    send(wrapper);
                    candidates = new JSONArray();
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            //Add each candidate to the JSONArray
            try {
                JSONObject obj = new JSONObject();
                obj.put(SDP_MID, iceCandidate.sdpMid);
                obj.put(SDP_M_LINE_INDEX, iceCandidate.sdpMLineIndex);
                obj.put(SDP, iceCandidate.sdp);
                candidates.put(obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            Log.i(TAG, "onAddStream");
            //mediaStream.videoTracks.getFirst().addRenderer(otherPeerRenderer);
        }
        //All other changes are logged to debugg
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.i(TAG, "onSignalingChange:" + signalingState.toString());
        }

        @Override
        public void onIceConnectionChange(final PeerConnection.IceConnectionState iceConnectionState) {
            Log.i(TAG, "onIceConnectionChange:" + iceConnectionState.toString());
            if(iceConnectionState.equals(PeerConnection.IceConnectionState.CLOSED)){
                //stopTheSend();
            }
            if(iceConnectionState.equals(PeerConnection.IceConnectionState.CONNECTED)){
                CONNECTED =true;
                try {
                    updateAfterAccept(0,"Connected");
                }catch (Exception ex){
                    Log.i(TAG,ex.getMessage());
                }
            }
        }
        @Override
        public void onIceConnectionReceivingChange(boolean b) {
            Log.i(TAG, "onIceConnectionReceivingChange : "+String.valueOf(b));
        }
        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            Log.i(TAG,"Stream removed :"+mediaStream.label());
        }
        @Override
        public void onDataChannel(DataChannel dataChannel) {
            Log.i(TAG,"Data Channel "+dataChannel.label());
        }
        @Override
        public void onRenegotiationNeeded() {
            Log.i(TAG,"Negotiation needed");
        }
    };

    SdpObserver sdpObserver = new SdpObserver() {
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            Log.i(TAG, "onCreateSucces sdp:");
            //set our local sdp
            peerConnection.setLocalDescription(sdpObserver, sessionDescription);
            //send to other peer
            try {
                JSONObject obj = new JSONObject();
                obj.put(SDP, sessionDescription.description);
                obj.put("type",ANSWER);
                obj.put("session",sessionId);
                send(obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        //Rest of the states are logged
        @Override
        public void onSetSuccess() {
            Log.i(TAG, "onSetSuccess");
        }
        @Override
        public void onCreateFailure(String s) {
            Log.i(TAG, "Creation Failed"+s);
        }
        @Override
        public void onSetFailure(String s) {
            Log.i(TAG, "set Failed"+s);
        }
    };

    public void send(JSONObject mess){
        try {
            mess.put("time",System.currentTimeMillis());
            HashMap<String, Object> update_map = new HashMap<>();
            //Append additional
            update_map.put("users/"+remoteUserId+"/mess", mess.toString());
            databaseReference.updateChildren(update_map);
        } catch (JSONException e) {
            Log.i(TAG,e.getMessage());
        }

    }
    //To disconnect from peer
    public void disconnectPeer(){

        //Remove peer connection
        if (peerConnection != null) {
            peerConnection.dispose();
            peerConnection = null;
        }

        if(peerConnectionFactory !=null){
            peerConnectionFactory.dispose();
            peerConnectionFactory = null;
        }

        candidates = new JSONArray();
        //reset the text view for new updates
    }
    private void processTheMessage(JSONObject mess) {
        try {
            Log.i(TAG,"mess :"+mess.getString("type"));
            if(mess.getLong("session")!=sessionId)
                return;
            switch (mess.getString("type")){
                case OFFER:
                    if(peerConnection == null){
                        disconnectPeer();
                        return;
                    }
                    updateAfterAccept(0,"Got Offer");
                    //Get the remote SDP and set it
                    SessionDescription sdp = new SessionDescription(SessionDescription.Type.OFFER,
                            mess.getString(SDP));

                    peerConnection.setRemoteDescription(sdpObserver, sdp);
                    //To respond to the offer through answer
                    peerConnection.createAnswer(sdpObserver, new MediaConstraints());
                    break;
                case CANDIDATE:
                    //Get the JSON array of localCandidates from sender(VideoCallSender)
                    String array = mess.getString("candidates");
                    JSONArray jsonArray = new JSONArray(array);

                    //Set up all the SDPs received
                    for(int i=0;i<jsonArray.length();i++){
                        JSONObject eachObj= new JSONObject(String.valueOf(jsonArray.getJSONObject(i)));
                        peerConnection.addIceCandidate(new IceCandidate(eachObj.getString(SDP_MID),
                                eachObj.getInt(SDP_M_LINE_INDEX),
                                eachObj.getString(SDP)));
                    }
                    break;
                case STOP:
                    if(receiverThread!=null)
                        receiverThread.interrupted=true;
                    removeCommunication();
                    disconnectPeer();
                    dismissForeGrounf();
                    //Toast.makeText(ActivityVideoCallSender.this,"Disconnected",Toast.LENGTH_LONG).show();
                    try{
                        JSONObject object = new JSONObject();
                        object.put("type",ACKNOWLEDGE);
                        object.put("session",sessionId);
                        send(object);
                    }catch (Exception e){}
                    break;
                case ACKNOWLEDGE:
                    removeCommunication();
                    //stopTheSend();
                    disconnectPeer();
                    dismissForeGrounf();
                    break;
            }
        } catch (JSONException e) {
            Log.i(TAG,e.getMessage());
        }
    }

    public void removeCommunication(){
        databaseReference.child("users").child(FirebaseAuth.getInstance().getUid()).child("mess").removeEventListener(this);

    }

    @Override
    public void onDataChange(DataSnapshot dataSnapshot) {
        if(dataSnapshot == null)
            return;
        if(first) {
            first=false;
            return;
        }
        try {

            String mess = dataSnapshot.getValue().toString();
            JSONObject object = new JSONObject(mess);
            processTheMessage(object);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCancelled(DatabaseError databaseError) {

    }


    public void dismissForeGrounf(){
        mHandler.removeCallbacks(timeoutRunner);
        stopForeground(true);
        Utils.setFileUploading(false);
        stopSelf();
    }
    public void sendPicked(){
        try {
            JSONObject objectToSend = new JSONObject();
            objectToSend.put("type", PICKED);
            objectToSend.put("session",sessionId);
            objectToSend.put("time",System.currentTimeMillis());
            databaseReference.child("users").child(remoteUserId).child("mess").setValue(objectToSend.toString());
            //Log.i(TAG,remoteUserId);
        } catch (JSONException e) {
            Log.i(TAG,e.getMessage());
        }
    }

    File target = null;
    BufferedOutputStream stream;

    public void fileSetup(){
        try {
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File dir = new File(downloads, getResources().getString(R.string.app_name));
            if (!dir.exists()) {
                dir.mkdirs();
            }
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd,yyyy HH:mm");
            String title = sdf.format(new Date());
            title = title.replace("\\s","");
            target = new File(dir,title+fileName);
            FileOutputStream fos = new FileOutputStream(target);
            stream = new BufferedOutputStream(fos);
        } catch (FileNotFoundException e) {
            Log.i(TAG,e.getMessage());
        }
    }
    public class ReceiverUiThread extends Thread{

        public boolean interrupted = false;

        public ReceiverUiThread() {
        }

        @Override
        public void run() {
            while (!interrupted){
                try {
                    int percent = (int)(((float)receivedSize/(float)fileSize)*100);
                    updateAfterAccept(percent,String.valueOf(percent)+"%");
                    //Log.i(TAG,String.valueOf(percent));
                    sendData(String.valueOf(percent).getBytes());
                    sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    public void buildNotification(String message){
        NotificationCompat.Builder builder= new NotificationCompat.Builder(this);

        builder.setSmallIcon(R.mipmap.ic_launcher_round);
        builder.setContentTitle(getResources().getString(R.string.app_name));
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
        notificationManager.notify(1993, builder.build());
    }
    public void sendData(byte[] data) {
        if(CONNECTED) {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            dataChannel.send(new DataChannel.Buffer(buffer, false));
        }
    }
}
