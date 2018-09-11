package com.example.iosdev.pushit;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.example.iosdev.pushit.classes.Utils;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class ForegroundServiceSender extends Service implements ValueEventListener {

    //Mess Tags
    private static final String ACKNOWLEDGE = "ACK";
    private static final String PICKED = "PICKED";
    private static final String STOP = "STOP";
    private static final String SENT = "SENT";
    private static final String REJECTED = "REJECTED";
    private static final String OFFER = "OFFER";
    private static final String ANSWER = "ANSWER";
    private static final String CANDIDATE = "CANDIDATE";
    private static final String BUSY="BUSY";
    private static final String TAG="ThisTagShouldBeUnique";
    //SDP constants
    private static final String SDP_MID = "sdpMid";
    private static final String SDP_M_LINE_INDEX = "sdpMLineIndex";
    private static final String SDP = "sdp";

    private Uri fileUri;
    JSONArray localCandidates;

    //P2P Components
    private PeerConnection peerConnection;
    private PeerConnectionFactory peerConnectionFactory;
    private DataChannel dataChannel;


    RemoteViews notificationViews;
    Notification notification;
    NotificationManager manager;
    String remoteUserId;
    DatabaseReference databaseReference;
    long sessionId;
    boolean firstRead = true;
    Handler mHandler;
    Runnable timeoutRunner;
    boolean CONNECTED = false;
    FileReader fileReader;
    WakeLock wakeLock;
    boolean back = false;
    @Override
    public void onCreate() {
        super.onCreate();
        manager =(NotificationManager) this
                .getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        notificationViews = new RemoteViews(getPackageName(),R.layout.custom_notification);
        if(intent==null)
            return START_STICKY;
        if(intent.getAction()==null)
            return START_STICKY;
        if (intent.getAction().equals(Utils.cancel)) {
            wakeLock.release();
            if(fileReader!=null)
            fileReader.setInterrupted(true);
            notificationViews.setBoolean(R.id.notificationCancelButton,"setEnabled",false);
            try {
                JSONObject obj = new JSONObject();
                obj.put("type",STOP);
                obj.put("session",sessionId);
                send(obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            mHandler.postDelayed(timeoutRunner,5000);

        }else if(intent.getAction().equals(Utils.main)) {
            PowerManager pwm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pwm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "com.example.iosdev.pushit.wakelock");
            wakeLock.acquire();
            mHandler = new Handler();
            timeoutRunner = new Runnable() {
                @Override
                public void run() {
                    removeCommunication();
                    dismissForeGrounf();
                    disconnectPeer();
                }
            };
            remoteUserId = intent.getExtras().getString("id");
            fileUri = Uri.parse(intent.getExtras().getString("uri"));
            fileReader = new FileReader(fileUri);
            databaseReference = FirebaseDatabase.getInstance().getReference();
            databaseReference.child("users").child(FirebaseAuth.getInstance().getUid()).child("mess").addValueEventListener(this);
            PeerConnectionFactory.initializeAndroidGlobals(
                    this,
                    true,
                    true,
                    true);
            localCandidates = new JSONArray();

            Utils.setFileUploading(true);
            notification = showNotification(0, "Sending Request");
            startForeground(1997,
                    notification);
            try {
                JSONObject object = new JSONObject();
                object.put("name", Utils.getUsername());
                sessionId = System.currentTimeMillis();
                object.put("session", sessionId);
                object.put("type", "file");
                sendCouldMessage(object);
            } catch (JSONException e) {
                Log.i(TAG, e.getMessage());
            }
        }
        return START_STICKY;
    }

    private Notification showNotification(int progress,String text) {
        Intent notificationIntent = new Intent(this, MainPage.class);
        notificationIntent.setAction(Utils.main);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        Intent nextIntent = new Intent(this, ForegroundServiceSender.class);
        nextIntent.setAction(Utils.cancel);
        nextIntent.putExtra("id",remoteUserId);
        PendingIntent cancelIntent = PendingIntent.getService(this, 0,
                nextIntent, 0);

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
        if(!back) {
            notification = showNotification(n, text);
            manager.notify(1997, notification);
        }
    }

    public void sendCouldMessage(JSONObject jsonObject){
        try {
            jsonObject.put("time",System.currentTimeMillis());
            Log.i(TAG,fileUri.toString());
            Cursor returnCursor = getContentResolver().query(fileUri, null, null, null, null);
            int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
            int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            returnCursor.moveToFirst();
            long size =  returnCursor.getLong(sizeIndex);
            String name = returnCursor.getString(nameIndex);
            returnCursor.close();

            jsonObject.put("fileName",name);
            jsonObject.put("fileSize",size);
            FirebaseDatabase.getInstance().getReference().child("mess")
                    .child(FirebaseAuth.getInstance().getUid()).child(remoteUserId).child("mess").setValue(jsonObject.toString()).addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    if(task.isSuccessful()){
                        updateProgress(0,"Waiting for aceptance");
                    }else{
                        updateProgress(0,"No internet access");
                    }
                }
            });
        } catch (JSONException e) {
            Log.i(TAG,e.getMessage());
        }catch (NullPointerException e){
            Log.i(TAG,e.getMessage());
        }

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

    @Override
    public void onDataChange(DataSnapshot dataSnapshot) {
        if(dataSnapshot == null)
            return;
        if(firstRead) {
            firstRead =false;
            return;
        }
        //To remove time stamp attached to end of message
        try {
            String mess = dataSnapshot.getValue().toString();
            JSONObject object = new JSONObject(mess);
            processMessage(object);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCancelled(DatabaseError databaseError) {

    }
    private void processMessage(JSONObject mess) {
        try {
            if(mess.getLong("session") != sessionId)
                return;
            //Do according to ICE requests
            Log.i(TAG,mess.getString("type"));
            switch (mess.getString("type")){
                case ANSWER:
                    if(peerConnection==null)
                        return;
                    updateProgress(0,"processing localCandidates");
                    //Setting remote sdp
                    SessionDescription sdp = new SessionDescription(SessionDescription.Type.ANSWER,
                            mess.getString(SDP));
                    peerConnection.setRemoteDescription(sdpObserver, sdp);
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
                    if(fileReader!=null)
                    fileReader.setInterrupted(true);
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
                case ACKNOWLEDGE:
                    //Toast.makeText(ActivityVideoCallSender.this,"Diconnected",Toast.LENGTH_LONG).show();
                    removeCommunication();
                    dismissForeGrounf();
                    disconnectPeer();
                    break;
                case PICKED:
                    updateProgress(0,"Connecting");
                    setUpFactory();
                    connectToPeer();
                    break;
                case SENT:
                    updateProgress(0,"Ringing");
                    break;
                case REJECTED:
                    Toast.makeText(ForegroundServiceSender .this,"Send Rejected",Toast.LENGTH_LONG).show();
                    removeCommunication();
                    dismissForeGrounf();
                    break;
            }
        } catch (Exception e) {
            Log.i(TAG,e.getMessage());
        }
    }

    //Peer Codes
    private void setUpFactory() {
        peerConnectionFactory = new PeerConnectionFactory();
    }
    public void connectToPeer() {
        //Add stun servers
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

        //Media constraints to establish connection
        MediaConstraints mc = new MediaConstraints();

        //Get a peerConnetion object from factory
        peerConnection = peerConnectionFactory.createPeerConnection(
                iceServers,
                mc,
                peerConnectionObserver);

        Log.i(TAG,"Peer Connection Create");


        //Dc,
        DataChannel.Init dcInit = new DataChannel.Init();
        dcInit.id=1;
        dcInit.ordered=true;
        dataChannel =peerConnection.createDataChannel("1",dcInit);
        dataChannel.registerObserver(dcObserver);
        peerConnection.createOffer(sdpObserver, new MediaConstraints());

    }

    DataChannel.Observer dcObserver = new DataChannel.Observer() {
        @Override
        public void onBufferedAmountChange(long l) {
            //Log.i(TAG,"change :"+String.valueOf(l));
        }

        //1638
        @Override
        public void onStateChange() {
            Log.i(TAG,"State Changed : "+dataChannel.state());
            if(dataChannel.state() == DataChannel.State.OPEN){
                fileReader.start();
            }
        }

        @Override
        public void onMessage(DataChannel.Buffer buffer) {
            ByteBuffer dataBuffer = buffer.data;
            byte[] data = new byte[dataBuffer.remaining()];
            dataBuffer.get(data,0,data.length);
            String dataString = new String(data);
            updateProgress(Integer.parseInt(dataString),dataString+"%");
        }
    };
    //Observer for sdp states
    SdpObserver sdpObserver = new SdpObserver() {

        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            Log.i(TAG, "onCreateSucces sdp:");

            //Use the sdp we get to set Local Description
            peerConnection.setLocalDescription(sdpObserver, sessionDescription);

            try {
                JSONObject obj = new JSONObject();
                obj.put(SDP, sessionDescription.description);
                obj.put("type",OFFER);
                obj.put("session",sessionId);
                send(obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        //Rest are just logged
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
    PeerConnection.Observer peerConnectionObserver = new PeerConnection.Observer() {
        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

            //Wait until we get all the localCandidates
            if(iceGatheringState.equals(PeerConnection.IceGatheringState.COMPLETE)) {
                updateProgress(0,"Gathering Complete");

                //Send it as json to other peer
                try {
                    JSONObject wrapper = new JSONObject();
                    wrapper.put("candidates", localCandidates);
                    wrapper.put("type", CANDIDATE);
                    wrapper.put("session",sessionId);
                    Log.i(TAG, "Gathered and sent");
                    send(wrapper);
                    localCandidates = new JSONArray();
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
                localCandidates.put(obj);
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
                CONNECTED=false;
            }
            if(iceConnectionState.equals(PeerConnection.IceConnectionState.CONNECTED)){
                try {
                    CONNECTED =true;
                    updateProgress(0,"Conneted");
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

    private void stopTheSend() {
        Intent nextIntent = new Intent(ForegroundServiceSender.this, ForegroundServiceSender.class);
        nextIntent.setAction(Utils.cancel);
        startService(nextIntent);
    }

    public void send(JSONObject mess){
        try {
            if(remoteUserId==null)
                return;
            Log.i(TAG,remoteUserId);
            mess.put("time",System.currentTimeMillis());
            HashMap<String, Object> update_map = new HashMap<>();
            //Append additional
            update_map.put("users/"+remoteUserId+"/mess", mess.toString());
            databaseReference.updateChildren(update_map);
        } catch (JSONException e) {
            Log.i(TAG,e.getMessage());
        }

    }
    public void disconnectPeer(){
        //Remove peer connection
        if (peerConnection != null) {
            peerConnection.dispose();
            peerConnection = null;
        }
        /*if(peerConnectionFactory!=null){
            peerConnectionFactory.dispose();
            peerConnection=null;
        }*/
        //statusText.setText("");
        localCandidates = new JSONArray();
    }
    //Remove the mesaging listener
    public void removeCommunication(){
        databaseReference.child("users").child(FirebaseAuth.getInstance().getUid()).child("mess").removeEventListener(this);
    }
    public void dismissForeGrounf(){
        mHandler.removeCallbacks(timeoutRunner);
        stopForeground(true);
        Utils.setFileUploading(false);
        stopSelf();
    }
    public void sendData(byte[] data) {
        if(CONNECTED) {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            dataChannel.send(new DataChannel.Buffer(buffer, false));
        }
    }

    public class FileReader extends Thread{
        BufferedInputStream is;
        int bufferSize = 8000;
        boolean interrupted = false;
        public FileReader(Uri uri) {
            try {
                AssetFileDescriptor assest = getContentResolver().openAssetFileDescriptor(uri,"r");
                FileInputStream fis = assest.createInputStream();
                is = new BufferedInputStream(fis);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try{
                byte[] buffer = new byte[bufferSize];
                final long start = System.currentTimeMillis();
                int len;
                while((len = is.read(buffer,0,buffer.length))!=-1 && !interrupted){
                    if(dataChannel.bufferedAmount()>=1000000){
                        while(dataChannel.bufferedAmount()>=500000) {
                            sleep(10);
                            //Log.i(TAG,"Is More "+String.valueOf(dataChannel.bufferedAmount()));
                        }
                    }
                    byte[] temp = Arrays.copyOf(buffer, len);
                    sendData(temp);
                    //Log.i(TAG,String.valueOf(len));
                }
                is.close();
                byte[] finalbuffer = new byte[bufferSize+1];
                sendData(finalbuffer);
                Log.i(TAG,"Elapsed " + (System.currentTimeMillis() - start) + " ms");
            } catch (FileNotFoundException e) {
                Log.i(TAG,e.getMessage());
            }catch (IOException e){
                Log.i(TAG,e.getMessage());
            } catch (InterruptedException e) {
                Log.i(TAG,e.getMessage());
            }
        }
        public void setInterrupted(boolean bo){
            this.interrupted =bo;
        }
    }

}
