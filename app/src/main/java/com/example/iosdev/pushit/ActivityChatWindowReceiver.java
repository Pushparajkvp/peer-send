package com.example.iosdev.pushit;

import android.graphics.Color;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;


public class ActivityChatWindowReceiver extends AppCompatActivity implements ValueEventListener {

    RecyclerView mRecyclerView;
    ChatAdapter chatAdapter;

    //Has chat details (Both sent and received)
    ArrayList<ChatDetails> data;

    //The id and and name of the other user
    String name;


    EditText editTextChatBox;
    Button buttonChatBox;
    Toolbar mToolbar;

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
    private boolean CONNECTED =false;

    //P2P Components
    private PeerConnection peerConnection;
    private PeerConnectionFactory peerConnectionFactory;

    //Firebase database reference
    DatabaseReference databaseReference;
    boolean first=true;
    //To store all localCandidates and send at once
    JSONArray candidates;

    //dc
    DataChannel dataChannel;

    TextView statusText;
    private VideoRenderer otherPeerRenderer;
    private GLSurfaceView videoView;

    String remoteUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_window_receiver);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Bundle extras = getIntent().getExtras();
        this.remoteUserId = extras.getString("cloud");
        this.sessionId = extras.getLong("session");
        this.name = extras.getString("name");

        mHandler = new Handler();
        statusText = (TextView)findViewById(R.id.connectionProgressText);
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


        //dc
        DataChannel.Init dcInit = new DataChannel.Init();
        dcInit.id=1;
        dataChannel =peerConnection.createDataChannel("1",dcInit);
        dataChannel.registerObserver(dcObserver);
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
        editTextChatBox.setText("");
    }
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
                /*mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        disconnectPeer();
                    }
                });*/
            }
            if(iceConnectionState.equals(PeerConnection.IceConnectionState.CONNECTED)){
                try {
                    CONNECTED = true;
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            statusText.setText("Connected");
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
    public void sendData(byte[] data) {
        if(CONNECTED) {
            ByteBuffer buffer = ByteBuffer.wrap(data);
            dataChannel.send(new DataChannel.Buffer(buffer, false));
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
                    Toast.makeText(ActivityChatWindowReceiver.this,"Disconnected",Toast.LENGTH_LONG).show();
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
                    removeCommunication();
                    Toast.makeText(ActivityChatWindowReceiver.this,"Disconnected",Toast.LENGTH_LONG).show();
                    break;
            }
        } catch (JSONException e) {
            Log.i(TAG,e.getMessage());
        }
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // this takes the user 'back', as if they pressed the left-facing triangle icon on the main android toolbar.
                // if this doesn't work as desired, another possibility is to call `finish()` here.
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
    public void disConnectButtonClicked() {
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

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

}
