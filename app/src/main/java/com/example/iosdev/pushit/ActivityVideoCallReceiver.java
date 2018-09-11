package com.example.iosdev.pushit;

import android.content.Context;
import android.graphics.Color;
import android.media.AudioManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
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
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;


public class ActivityVideoCallReceiver extends AppCompatActivity implements ValueEventListener {
    //Constants
    private static final String AUDIO_TRACK_ID = "audio1";
    private static final String VIDEO_TRACK_ID = "video1";
    //SDP constants
    private static final String SDP_MID = "sdpMid";
    private static final String SDP_M_LINE_INDEX = "sdpMLineIndex";
    private static final String SDP = "sdp";


    //Our Audio Stream
    private static final String LOCAL_STREAM_ID = "stream1";

    //Tags
    private static final String TAG = "PeerToPeerLog";

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

    Handler mHandler;
    Runnable timeoutRunner;
    //ProgressDialog progressDialog;

    //P2P Components
    private PeerConnection peerConnection;
    private PeerConnectionFactory peerConnectionFactory;
    private MediaStream localMediaStream;

    //Firebase database reference
    DatabaseReference databaseReference;
    boolean first=true;
    //To store all localCandidates and send at once
    JSONArray candidates;

    //dc
    DataChannel dataChannel;

    Button disconnetButton;
    TextView statusText;
    VideoCapturer vc;
    private VideoRenderer otherPeerRenderer;
    private GLSurfaceView videoView;

    String remoteUserId;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_call_receiver);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Bundle extras = getIntent().getExtras();
        this.remoteUserId = extras.getString("cloud");
        this.sessionId = extras.getLong("session");

        disconnetButton = (Button)findViewById(R.id.activityReceiverDisonnect);
        mHandler = new Handler();
        videoView = (GLSurfaceView) findViewById(R.id.glview_call1);
        statusText = (TextView)findViewById(R.id.connectionProgressText);

        VideoRendererGui.setView(videoView, null);

        timeoutRunner = new Runnable(){
            @Override
            public void run() {
                removeCommunication();
                finish();
            }
        };
        //Set the database reference to root
        databaseReference = FirebaseDatabase.getInstance().getReference();
        databaseReference.child("users").child(FirebaseAuth.getInstance().getUid()).child("mess").addValueEventListener(this);

        //Initialize the candidate array
        candidates = new JSONArray();
        //To get the audio service
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if(audioManager!=null) {
            audioManager.setMode(AudioManager.STREAM_VOICE_CALL);
            setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
            if(audioManager.isWiredHeadsetOn()||audioManager.isBluetoothA2dpOn() ||audioManager.isBluetoothScoOn())
                audioManager.setSpeakerphoneOn(false);
            else
                audioManager.setSpeakerphoneOn(true);
            //audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL,(int) (audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)*0.8),AudioManager.ADJUST_RAISE);
        }else{
            Toast.makeText(this,"There is a problem with audio",Toast.LENGTH_LONG).show();
        }
        //Peer Connection
        PeerConnectionFactory.initializeAndroidGlobals(
                this,  // Context
                true,  // Audio Enabled
                true,  // Video Enabled
                true); // Render EGL Context

        //logs for debugging
        Log.i(TAG,"Peer Connection Create");

        setUpFactoryAndCreatePeer();

        statusText.setText("Waiting for offer");
    }

    private void setUpFactoryAndCreatePeer() {
        //We should not created if already exists
        if(peerConnection!=null)
            return;

        //Createing factory
        peerConnectionFactory = new PeerConnectionFactory();
        //Ceating Video Track (Local)
        vc = getVideoCapturer();//Gets local best available camera function is below
        //Create video source from video capturer and constraints
        VideoSource localVideoSource = peerConnectionFactory.createVideoSource(vc, new MediaConstraints());
        //Create video track from source
        VideoTrack localVideoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, localVideoSource);
        localVideoTrack.setEnabled(true);

        //Get the audio track
        MediaConstraints audioConstarints = new MediaConstraints();
        audioConstarints.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
        audioConstarints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
        AudioSource audioSource = peerConnectionFactory.createAudioSource(audioConstarints);
        AudioTrack localAudioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
        localAudioTrack.setEnabled(true);

        //Just add the audio to our media stream
        localMediaStream = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID);
        localMediaStream.addTrack(localVideoTrack);
        localMediaStream.addTrack(localAudioTrack);
        try {
            //Setting up renderer
            VideoRenderer renderer = VideoRendererGui.createGui(0,0,100,50, RendererCommon.ScalingType.SCALE_ASPECT_FILL, false);
            localMediaStream.videoTracks.getFirst().addRenderer(renderer);
            otherPeerRenderer = VideoRendererGui.createGui(0, 50, 100, 50, RendererCommon.ScalingType.SCALE_ASPECT_FILL, false);
        }catch (Exception e){
            Log.i(TAG,e.getMessage());
            Toast.makeText(ActivityVideoCallReceiver.this,e.getMessage(),Toast.LENGTH_LONG).show();
        }


        MediaConstraints mc = new MediaConstraints();
        //Stun and Turn servers list setup
        ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<>();
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

        //Add the media stream from sender(VideoCallSender)
        peerConnection.addStream(localMediaStream);

        //dc
        DataChannel.Init dcInit = new DataChannel.Init();
        dcInit.id=1;
        dataChannel =peerConnection.createDataChannel("1",dcInit);
        dataChannel.registerObserver(dcObserver);
    }
    //To get the best camera
    private VideoCapturer getVideoCapturer() {
        String[] cameraFacing = {"front" };
        int[] cameraIndex = { 0, 1 };
        int[] cameraOrientation = { 0, 90, 180, 270 };
        for (String facing : cameraFacing) {
            for (int index : cameraIndex) {
                for (int orientation : cameraOrientation) {
                    String name = "Camera " + index + ", Facing " + facing +
                            ", Orientation " + orientation;
                    VideoCapturer capturer = VideoCapturer.create(name);
                    if (capturer != null) {
                        return capturer;
                    }
                }
            }
        }
        throw new RuntimeException("Failed to open capturer");
    }

    DataChannel.Observer dcObserver = new DataChannel.Observer() {
        @Override
        public void onBufferedAmountChange(long l) {}

        @Override
        public void onStateChange() {
            Log.i(TAG,"State Changed");
        }

        @Override
        public void onMessage(DataChannel.Buffer buffer) {
            ByteBuffer dataBuffer = buffer.data;
            byte[] data = new byte[dataBuffer.remaining()];
            dataBuffer.get(data,0,data.length);
        }
    };
    PeerConnection.Observer peerConnectionObserver = new PeerConnection.Observer() {

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

            //Wait until we get all the localCandidates
            if(iceGatheringState.equals(PeerConnection.IceGatheringState.COMPLETE)) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        statusText.setText("Processing Candidates");
                    }
                });

                //Send it as json to other peer
                try {
                    JSONObject wrapper = new JSONObject();
                    wrapper.put("candidates", candidates);
                    wrapper.put("type", CANDIDATE);
                    wrapper.put("session",sessionId);
                    Log.i(TAG, "Gathered and sent");
                    send(wrapper.toString());
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
            mediaStream.videoTracks.getFirst().addRenderer(otherPeerRenderer);
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
                /*mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        disconnectPeer();
                    }
                });*/
            }
            if(iceConnectionState.equals(PeerConnection.IceConnectionState.CONNECTED)){
                try {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText("Connected");
                            disconnetButton.setEnabled(true);
                            disconnetButton.setVisibility(View.VISIBLE);
                        }
                    });
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
                send(obj.toString());
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

    //This triggers a function in firebase that sends the message to othr peer
    public void send(final String mess){
        HashMap<String, Object> update_map = new HashMap<>();
        update_map.put("users/"+remoteUserId+"/mess", mess+System.currentTimeMillis());
        databaseReference.updateChildren(update_map);
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

        //remove video capturer
        if (vc != null) {
            vc.dispose();
            vc = null;
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
                    statusText.setText("Got Offer to Connect");
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
                    disconnectPeer();
                    Toast.makeText(ActivityVideoCallReceiver.this,"Disconnected",Toast.LENGTH_LONG).show();
                    try{
                        JSONObject object = new JSONObject();
                        object.put("type",ACKNOWLEDGE);
                        object.put("session",sessionId);
                        send(object.toString());
                    }catch (Exception ignored){}
                    removeCommunication();
                    break;
                case ACKNOWLEDGE:
                    mHandler.removeCallbacks(timeoutRunner);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            disconnetButton.setEnabled(false);
                            disconnetButton.setVisibility(View.GONE);
                        }
                    });
                    removeCommunication();
                    Toast.makeText(ActivityVideoCallReceiver.this,"Disconnected",Toast.LENGTH_LONG).show();
                    break;
            }
        } catch (JSONException e) {
            Log.i(TAG,e.getMessage());
        }
    }
    @Override
    public void onBackPressed() {
        disconnetButton.callOnClick();
        removeCommunication();
        super.onBackPressed();
    }
    public void removeCommunication(){
        databaseReference.child("users").child(FirebaseAuth.getInstance().getUid()).child("mess").removeEventListener(this);
        Utils.setIsUserBusy(false);
        finish();

    }
    @Override
    public void onDataChange(DataSnapshot dataSnapshot) {
        if(dataSnapshot == null)
            return;
        if(first) {
            first=false;
            try {
                DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference();
                JSONObject object = new JSONObject();
                object.put("type","MOB");
                object.put("session",sessionId);
                databaseReference.child("users").child(FirebaseAuth.getInstance().getUid()).child("mess").setValue(object.toString());

                JSONObject objectToSend = new JSONObject();
                objectToSend.put("type", PICKED);
                objectToSend.put("session",sessionId);
                databaseReference.child("users").child(remoteUserId).child("mess").setValue(objectToSend.toString());

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
            processTheMessage(object);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCancelled(DatabaseError databaseError) {

    }

    public void disConnectButtonClicked(View view) {
        disconnetButton.setEnabled(false);
        statusText.setText("Waiting for connection close");
        statusText.setBackgroundColor(Color.parseColor("#ae0001"));
        mHandler.postDelayed(timeoutRunner,5000);
        try {
            JSONObject obj = new JSONObject();
            obj.put("type",STOP);
            obj.put("session",sessionId);
            send(obj.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        disconnectPeer();
    }
}
