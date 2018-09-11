package com.example.iosdev.pushit;

import android.*;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.iosdev.pushit.classes.Utils;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.iid.FirebaseInstanceId;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

public class StartPage extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private static final int MY_PERMISSIONS_REQUEST = 100;

    Button getSMS;
    PhoneAuthProvider.OnVerificationStateChangedCallbacks SMSCallback;
    FirebaseAuth mAuth;
    Spinner spinnerCountryCode;
    ArrayAdapter<CharSequence> CountryNames;
    EditText edtPhoneNumber,edtCode;
    String[] CountryCodes;
    RelativeLayout startPAgeRelativeLayout;
    boolean verifyState = false;
    String verificationId;
    TextView lblSmsSent;
    RelativeLayout loadingLayout;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start_page);
        getWindow().setBackgroundDrawable(Utils.getGradient());
        Utils.setUserProfileSet(false);
        //Initial Setup
        CountryCodes = this.getResources().getStringArray(R.array.CountryCodes);
        mAuth = FirebaseAuth.getInstance();
        //XML components
        edtPhoneNumber = (EditText)findViewById(R.id.edtPhoneNumber);
        getSMS = (Button) findViewById(R.id.buttonGetSMS);
        edtCode = (EditText)findViewById(R.id.edtConfirmationCode);
        loadingLayout = (RelativeLayout)findViewById(R.id.loadingPanel);
        getSMS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getSMSCicked(view);
            }
        });
        lblSmsSent = (TextView)findViewById(R.id.lblSmsSent);
        if ( ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                ){

            ActivityCompat.requestPermissions(this,
                    new String[]{
                            android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            android.Manifest.permission.READ_CONTACTS,
                            android.Manifest.permission.RECORD_AUDIO,
                            android.Manifest.permission.CAMERA},
                    MY_PERMISSIONS_REQUEST);
        }
        //SMS on getting
        SMSCallback = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            @Override
            public void onVerificationCompleted(PhoneAuthCredential phoneAuthCredential) {
                doFirebaseAuth(phoneAuthCredential);
            }

            @Override
            public void onVerificationFailed(FirebaseException e) {
                if (e instanceof FirebaseAuthInvalidCredentialsException) {
                    Toast.makeText(StartPage.this,e.getMessage(),Toast.LENGTH_LONG).show();
                } else if (e instanceof FirebaseTooManyRequestsException) {
                    Toast.makeText(StartPage.this,"SMS service exhausted",Toast.LENGTH_LONG).show();
                }else{
                    Toast.makeText(StartPage.this,e.getMessage(),Toast.LENGTH_LONG).show();
                }

            }

            @Override
            public void onCodeAutoRetrievalTimeOut(String s) {
                Toast.makeText(StartPage.this,"SMS detection timeout",Toast.LENGTH_LONG).show();
                super.onCodeAutoRetrievalTimeOut(s);
            }

            @Override
            public void onCodeSent(String s, PhoneAuthProvider.ForceResendingToken forceResendingToken) {
                getSMS.setVisibility(View.VISIBLE);
                loadingLayout.setVisibility(View.GONE);
                edtCode.setVisibility(View.VISIBLE);
                edtPhoneNumber.setVisibility(View.INVISIBLE);
                spinnerCountryCode.setVisibility(View.INVISIBLE);
                getSMS.setText("Verify");
                verifyState = true;
                verificationId= s;
                lblSmsSent.setVisibility(View.VISIBLE);
                super.onCodeSent(s, forceResendingToken);
            }
        };

        int userCountryPosition = getUserCountryPosition();
        spinnerCountryCode = (Spinner)findViewById(R.id.spiCountryCode);
        CountryNames = ArrayAdapter.createFromResource(StartPage.this,R.array.Country,android.R.layout.simple_spinner_item);
        CountryNames.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCountryCode.setAdapter(CountryNames);
        spinnerCountryCode.setOnItemSelectedListener(this);
        spinnerCountryCode.setSelection(userCountryPosition);
        edtPhoneNumber.setText(CountryCodes[userCountryPosition].split(",")[0]);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST: {
                if (!(grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED
                        && grantResults[1] == PackageManager.PERMISSION_GRANTED
                        && grantResults[2] == PackageManager.PERMISSION_GRANTED
                        && grantResults[3] == PackageManager.PERMISSION_GRANTED
                        && grantResults[4] == PackageManager.PERMISSION_GRANTED)){
                    Toast.makeText(StartPage.this,"We need permission for app functionality",Toast.LENGTH_LONG).show();
                    finish();
                }

            }
        }
    }
    //Signing in the user
    private void doFirebaseAuth(PhoneAuthCredential phoneAuthCredential) {
        mAuth.signInWithCredential(phoneAuthCredential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Intent setupPage = new Intent(StartPage.this,SetupPage.class);
                            Utils.setNumber(edtPhoneNumber.getText().toString());
                            startActivity(setupPage);
                            finish();

                        } else {
                            if (task.getException() instanceof FirebaseAuthInvalidCredentialsException) {
                                Toast.makeText(StartPage.this,task.getException().getMessage(),Toast.LENGTH_LONG).show();
                            }else if(task.getException() != null){
                                Toast.makeText(StartPage.this,task.getException().getMessage(),Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                });
    }
    //User clicked sms verification
    public void getSMSCicked(View view){
        if(!verifyState) {
            if (edtPhoneNumber.getText().toString().length() < 8) {
                Toast.makeText(StartPage.this, "Your Phone Number Is Invalid", Toast.LENGTH_LONG).show();
                return;
            }
            if (!Utils.isNetworkAvailable(StartPage.this)) {
                Toast.makeText(StartPage.this, "Please Check Your Network Connectivity", Toast.LENGTH_LONG).show();
                return;
            }
            PhoneAuthProvider.getInstance().verifyPhoneNumber(
                    "+" + edtPhoneNumber.getText().toString()
                    , 60
                    , TimeUnit.SECONDS
                    , this
                    , SMSCallback);
            getSMS.setVisibility(View.GONE);
            loadingLayout.setVisibility(View.VISIBLE);
        }else{
            if(edtCode.getText().toString().equals("")){
                Toast.makeText(StartPage.this, "Enter The Code Sent", Toast.LENGTH_LONG).show();
                return;
            }
            if (!Utils.isNetworkAvailable(StartPage.this)) {
                Toast.makeText(StartPage.this, "Please Check Your Network Connectivity", Toast.LENGTH_LONG).show();
                return;
            }
            PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, edtCode.getText().toString());
            doFirebaseAuth(credential);
            getSMS.setVisibility(View.GONE);
            loadingLayout.setVisibility(View.VISIBLE);
        }
    }
    //Look in resource string for country code position of user
    public int getUserCountryPosition(){
        String CountryID;
        int position=0;
        TelephonyManager manager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        if(manager!=null) {
            CountryID = manager.getNetworkCountryIso().toUpperCase();
        }else{
            CountryID = "IN";
        }
        for (int i = 0; i < CountryCodes.length; i++) {
            String[] g = CountryCodes[i].split(",");
            if (g[1].trim().equals(CountryID.trim())) {
                position = i;
                break;
            }
        }
        return position;
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        edtPhoneNumber.setText(CountryCodes[i].split(",")[0]);
        TextView selectedText = (TextView) adapterView.getChildAt(0);
        if (selectedText != null) {
            selectedText.setTextColor(Color.WHITE);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
        edtPhoneNumber.setText(CountryCodes[getUserCountryPosition()].split(",")[0]);
    }

}
