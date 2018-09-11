package com.example.iosdev.pushit;


import android.support.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

//Gets called when the token is refreshed or app re-installed
public class MyFirebaseInstanceIDService extends FirebaseInstanceIdService {
    FirebaseUser user;
    DatabaseReference databaseReference;
    @Override
    public void onTokenRefresh() {
        //FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        databaseReference = FirebaseDatabase.getInstance().getReference();
        user = FirebaseAuth.getInstance().getCurrentUser();
        updateToken();

    }
    public void updateToken(){
        if(user!=null) {
            databaseReference.child("users").child(user.getUid()).child("token").setValue(FirebaseInstanceId.getInstance().getId()).addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    if(!task.isSuccessful()){
                        updateToken();
                    }
                }

            });
        }
    }

}
