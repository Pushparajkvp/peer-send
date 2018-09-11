package com.example.iosdev.pushit;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.example.iosdev.pushit.classes.Utils;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.iid.FirebaseInstanceId;

import java.util.HashMap;

public class SetupPage extends AppCompatActivity {
    EditText nameEditText;
    String phoneNumber;
    RelativeLayout loadingPanel;
    Button updateButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup_page);
        getWindow().setBackgroundDrawable(Utils.getGradient());
        nameEditText = (EditText)findViewById(R.id.setUpPageName);
        loadingPanel = (RelativeLayout)findViewById(R.id.loadingPanel);
        phoneNumber = Utils.getNumber();
        updateButton = (Button)findViewById(R.id.setUpUpdate);
    }

    public void updateClicked(View view) {
        if(nameEditText.getText().toString().equals("")){
            Toast.makeText(this,"Please dont leave the fields blank.",Toast.LENGTH_LONG).show();
            return;
        }
        setIsLoading(true);
        final String name = nameEditText.getText().toString();
        String token = FirebaseInstanceId.getInstance().getToken();
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference("users").child(FirebaseAuth.getInstance().getUid());
        HashMap<String,Object> update_map = new HashMap<>();
        update_map.put("name",name);
        update_map.put("token",token);
        update_map.put("number",phoneNumber);
        update_map.put("mess","nothing");
        reference.updateChildren(update_map).addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if(task.isSuccessful()){
                    setIsLoading(false);
                    Utils.setUserProfileSet(true);
                    Utils.setUsername(name);
                    Intent mainPage = new Intent(SetupPage.this,MainPage.class);
                    startActivity(mainPage);
                    finish();
                }else {
                    if(task.getException() != null){
                        FirebaseAuth.getInstance().getCurrentUser().delete();
                        Toast.makeText(SetupPage.this,task.getException().getMessage(),Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
    }
    //To hide and show loading panel
    public void setIsLoading(boolean isLoading){
        if(isLoading){
            loadingPanel.setVisibility(View.VISIBLE);
            updateButton.setVisibility(View.GONE);
        }else{
            loadingPanel.setVisibility(View.GONE);
            updateButton.setVisibility(View.VISIBLE);
        }
    }
}
