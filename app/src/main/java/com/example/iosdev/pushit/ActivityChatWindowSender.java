package com.example.iosdev.pushit;

import android.graphics.Color;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.iosdev.pushit.classes.ChatDetails;
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
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;


public class ActivityChatWindowSender extends AppCompatActivity implements ValueEventListener {

    RecyclerView mRecyclerView;
    ChatAdapter chatAdapter;

    //Has chat details (Both sent and received)
    ArrayList<ChatDetails> data;

    //The id and and name of the other user
    String name;


    EditText editTextChatBox;
    Button buttonChatBox;
    Toolbar mToolbar;

    //CONSTANTS
    private static final String AUDIO_TRACK_ID = "audio1";
    //tags for logging
    private static final String TAG = "RTCP2P";

    //sdp tag constants for ICE(must be same on both ends)
    private static final String SDP_MID = "sdpMid";
    private static final String SDP_M_LINE_INDEX = "sdpMLineIndex";
    private static final String SDP = "sdp";

    private static final String VIDEO_TRACK_ID = "video1";

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


    //tag id for local stream(sender)
    private static final String LOCAL_STREAM_ID = "stream1";

    private long sessionId;

    //P2P Components
    private PeerConnection peerConnection;
    private PeerConnectionFactory peerConnectionFactory;
    private DataChannel dataChannel;

    VideoCapturer vc;
    VideoRenderer remoteRenderer;
    GLSurfaceView videoView;

    //Ui elements
    Handler mHandler;
    Runnable timeoutRunner,ringerTimeoutRunner;

    TextView connectionStatusText;

    //Video Components
    private MediaStream localMediaStream;
    //Firebase database reference
    DatabaseReference databaseReference;
    boolean firstRead =true;

    //To store ICE localCandidates and send in a single message
    JSONArray localCandidates,remoteCandidates;

    //Initially no connection
    private boolean CONNECTED = false;

    String remoteUserId,remoteUserToken,remoteUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_window_sender);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        Utils.setIsUserBusy(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Bundle extras = getIntent().getExtras();
        this.remoteUserId = extras.getString("userId");
        this.remoteUsername = extras.getString("username");
        this.remoteUserToken = extras.getString("token");
        this.name = extras.getString("name");

        //Toolbar setup
        mToolbar = (Toolbar)findViewById(R.id.toolbar_id);
        setSupportActionBar(mToolbar);
        if(getSupportActionBar()!=null){
            getSupportActionBar().setTitle(name);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }


        mRecyclerView = (RecyclerView)findViewById(R.id.reyclerview_message_list);
        editTextChatBox = (EditText)findViewById(R.id.edittext_chatbox);
        buttonChatBox = (Button)findViewById(R.id.button_chatbox_send);
        connectionStatusText = (TextView)findViewById(R.id.connectionProgressText);

        mHandler = new Handler();
        timeoutRunner = new Runnable() {
            @Override
            public void run() {
                removeCommunication();
                finish();
            }
        };
        ringerTimeoutRunner = new Runnable(){
            @Override
            public void run() {
                Toast.makeText(ActivityChatWindowSender.this,"Receiver missed the call",Toast.LENGTH_LONG).show();
                mHandler.removeCallbacks(ringerTimeoutRunner);
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("type",STOP);
                    obj.put("session",sessionId);
                    send(obj.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                removeCommunication();
                finish();
            }
        };
        localCandidates = new JSONArray();
        remoteCandidates = new JSONArray();

        databaseReference = FirebaseDatabase.getInstance().getReference();
        //Listener to check messages
        databaseReference.child("users").child(FirebaseAuth.getInstance().getUid()).child("mess").addValueEventListener(this);

        PeerConnectionFactory.initializeAndroidGlobals(
                this,
                true,
                true,
                true);

        //Recylerview setup
        LinearLayoutManager linearLayout = new LinearLayoutManager(this);
        linearLayout.setReverseLayout(true);
        mRecyclerView.setLayoutManager(linearLayout);
        mRecyclerView.setItemAnimator(null);

        buttonChatBox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(editTextChatBox.getText().toString().equals(""))
                    return;
                sendData(editTextChatBox.getText().toString().getBytes());
                ChatDetails chatDetails = new ChatDetails(editTextChatBox.getText().toString(),"You",String.valueOf(System.currentTimeMillis()),false,"");
                data.add(0,chatDetails);
                refresh();
            }
        });


        data = new ArrayList<>();
        connectionStatusText.setText("Sending");
        //Re-establish Connection
        sendCloudMessage(Utils.getUsername());

    }
    public void refresh(){
        //Refresh recyler adapter
        if(chatAdapter==null){
            chatAdapter = new ChatAdapter(data);
            chatAdapter.setHasStableIds(true);
            mRecyclerView.setAdapter(chatAdapter);
        }else{
            chatAdapter.setChats(data);
            chatAdapter.notifyDataSetChanged();
        }
    }
    private void setUpFactoryAndLocalVideo() {
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
        dataChannel =peerConnection.createDataChannel("1",dcInit);
        dataChannel.registerObserver(dcObserver);


        peerConnection.createOffer(sdpObserver, new MediaConstraints());

    }
    DataChannel.Observer dcObserver = new DataChannel.Observer() {
        @Override
        public void onBufferedAmountChange(long l){}

        @Override
        public void onStateChange() {

            Log.i(TAG,"State Changed");
        }

        @Override
        public void onMessage(DataChannel.Buffer buffer) {
            ByteBuffer dataBuffer = buffer.data;
            final byte[] datas = new byte[dataBuffer.remaining()];
            dataBuffer.get(datas,0,datas.length);
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    ChatDetails chatDetails = new ChatDetails(new String(datas),name,String.valueOf(System.currentTimeMillis()),true,"");
                    data.add(0,chatDetails);
                    refresh();
                }
            });
        }
    };
    //Observes various events in the peer connection
    PeerConnection.Observer peerConnectionObserver = new PeerConnection.Observer() {

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

            if(iceConnectionState.equals(PeerConnection.IceConnectionState.CONNECTED)){
                CONNECTED = true;
                //We are not in UI thread so use handler
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        connectionStatusText.setText("Connected");
                    }
                });

            }

            /*if(iceConnectionState.equals(PeerConnection.IceConnectionState.CLOSED)){
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        disconnectPeer();
                    }
                });
            }*/

            if(iceConnectionState.equals(PeerConnection.IceConnectionState.FAILED)){

                //Show connect in button so user can connect again
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        disconnectPeer();
                    }
                });

            }
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            //We want a stage where all the localCandidates are collected and ready to be sent
            if(iceGatheringState.equals(PeerConnection.IceGatheringState.COMPLETE)) {
                try {
                    JSONObject wrapper = new JSONObject();
                    wrapper.put("candidates", localCandidates);
                    wrapper.put("type", CANDIDATE);
                    wrapper.put("session",sessionId);
                    Log.i(TAG,"Sent all localCandidates");
                    send(wrapper.toString());
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
           // mediaStream.videoTracks.getFirst().addRenderer(remoteRenderer);
        }

        //Rest are just logged for tracking status
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.i(TAG, "onSignalingChange:" + signalingState.toString());
        }
        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            Log.i(TAG,"Stream removed :"+mediaStream.label());
        }
        @Override
        public void onDataChannel(DataChannel dataChannel) {
            Log.i(TAG,"Data Channel : "+dataChannel.label());
        }
        @Override
        public void onRenegotiationNeeded() {
            Log.i(TAG, "onRenegotiationNeeded:");
        }
        @Override
        public void onIceConnectionReceivingChange(boolean b) {
            Log.i(TAG, "onIceConnectionReceivingChange : "+String.valueOf(b));

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
                send(obj.toString());
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
    public void send(final String mess){
        HashMap<String, Object> update_map = new HashMap<>();
        //Append additional
        update_map.put("users/"+remoteUserId+"/mess", mess+System.currentTimeMillis());
        databaseReference.updateChildren(update_map);
    }
    public void sendData(byte[] data) {
        if(CONNECTED) {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            dataChannel.send(new DataChannel.Buffer(buffer, false));
        }
    }
    public void disconnectPeer(){
        //Remove peer connection
        if (peerConnection != null) {
            peerConnection.dispose();
            peerConnection = null;
        }
        if(peerConnectionFactory!=null){
            peerConnectionFactory.dispose();
            peerConnection=null;
        }

        //statusText.setText("");
        localCandidates = new JSONArray();
    }
    private void processMessage(JSONObject mess) {
        try {
            Log.i(TAG,"mess : "+mess.getString("type"));
            if(mess.getLong("session") != sessionId)
                return;
            //Do according to ICE requests
            switch (mess.getString("type")){
                case ANSWER:
                    if(peerConnection==null)
                        return;
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            connectionStatusText.setText("Processing Candidates");
                        }
                    });
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
                    disconnectPeer();
                    //Toast.makeText(ActivityVideoCallSender.this,"Disconnected",Toast.LENGTH_LONG).show();
                    try{
                        JSONObject object = new JSONObject();
                        object.put("type",ACKNOWLEDGE);
                        object.put("session",sessionId);
                        send(object.toString());
                    }catch (Exception e){}
                case ACKNOWLEDGE:
                    Toast.makeText(ActivityChatWindowSender.this,"Diconnected",Toast.LENGTH_LONG).show();
                    connectionStatusText.setText("Diconnected");
                    mHandler.removeCallbacks(timeoutRunner);
                    removeCommunication();
                    finish();
                    break;
                case PICKED:
                    mHandler.removeCallbacks(ringerTimeoutRunner);
                    connectionStatusText.setText("Connecting");
                    setUpFactoryAndLocalVideo();
                    Log.i(TAG,"Inside Picked");
                    connectToPeer();
                    break;
                case SENT:
                    connectionStatusText.setText("Ringing");
                    break;
                case REJECTED:
                    Toast.makeText(ActivityChatWindowSender .this,"Call Rejected",Toast.LENGTH_LONG).show();
                    connectionStatusText.setText("Call Rejected");
                    disconnectPeer();
                    removeCommunication();
                    finish();
                    break;
                case BUSY:
                    Toast.makeText(ActivityChatWindowSender.this,"Other User is busy",Toast.LENGTH_LONG).show();
                    connectionStatusText.setText("Other User is busy");
                    //progressDialog.dismiss();
                    disconnectPeer();
                    removeCommunication();
                    finish();
                    break;
            }
        } catch (Exception e) {
            Log.i(TAG,e.getMessage());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                this.onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        disConnectButtonClicked();
        removeCommunication();
        //super.onBackPressed();
    }
    //Remove the mesaging listener
    public void removeCommunication(){
        databaseReference.child("users").child(FirebaseAuth.getInstance().getUid()).child("mess").removeEventListener(this);
        Utils.setIsUserBusy(false);
    }
    //To send a cloud message to notify call
    public void sendCloudMessage(String mess){
        try {
            JSONObject object = new JSONObject();

            object.put("name",mess);
            object.put("type","chat");
            sessionId = System.currentTimeMillis();
            object.put("session",sessionId);

            //To send a cloud message to mobile devices
            HashMap<String,Object> update_map = new HashMap<>();
            update_map.put("mess/"+FirebaseAuth.getInstance().getUid()+"/"+remoteUserId+"/mess",object.toString());

            databaseReference.updateChildren(update_map).addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    if(task.isSuccessful()){
                        mHandler.postDelayed(ringerTimeoutRunner,30000);
                        connectionStatusText.setText("Checking if he is reachable");
                    }
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }

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
            int i;
            for(i=mess.length()-1;;i--){
                if(!Character.isDigit(mess.charAt(i)))
                    break;
            }
            mess=mess.substring(0,i+1);
            JSONObject object = new JSONObject(mess);
            processMessage(object);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onCancelled(DatabaseError databaseError) {
        Log.i(TAG,databaseError.getMessage());
        Toast.makeText(ActivityChatWindowSender.this,databaseError.getMessage(),Toast.LENGTH_LONG).show();
    }
    public void disConnectButtonClicked() {
        connectionStatusText.setText("Waiting for connection close");
        connectionStatusText.setBackgroundColor(Color.parseColor("#ae0001"));
        mHandler.postDelayed(timeoutRunner,5000);
        mHandler.removeCallbacks(ringerTimeoutRunner);
        try {
            JSONObject obj = new JSONObject();
            obj.put("type",STOP);
            obj.put("session",sessionId);
            send(obj.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        disconnectPeer();//Disconnect if already connected
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

}
