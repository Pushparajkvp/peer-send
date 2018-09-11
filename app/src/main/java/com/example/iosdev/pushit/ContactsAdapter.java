package com.example.iosdev.pushit;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.iosdev.pushit.classes.ContactsDetails;
import com.example.iosdev.pushit.classes.Utils;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

public class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.ContactAdapterHolder> {
    ArrayList<ContactsDetails> list;
    Context mContext;
    AlertDialog.Builder alertDialog;
    String selections[] = {"Video Call","File Share","Secret Chat"};

    public ContactsAdapter(ArrayList<ContactsDetails> list,Context context) {
        this.list = list;
        this.mContext = context;
        alertDialog = new AlertDialog.Builder(mContext);
    }

    @Override
    public ContactAdapterHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_contacts,parent,false);
        return new ContactAdapterHolder(view);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public void onBindViewHolder(ContactAdapterHolder holder, int position) {
        final String name = list.get(position).getName();
        final String phoneNo = list.get(position).getPhoneNo();
        final String id = list.get(position).getId();
        final String token = list.get(position).getToken();
        Button invite = (Button) holder.mView.findViewById(R.id.recylerButtInvite);
        invite.setText("Send");
        invite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alertDialog.setItems(selections, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        switch (i){
                            case 0:
                                Intent callSender = new Intent(mContext,ActivityVideoCallSender.class);
                                callSender.putExtra("userId",id);
                                callSender.putExtra("username",name);
                                callSender.putExtra("token",token);
                                mContext.startActivity(callSender);
                                break;
                            case 1:
                                MainPage activity = (MainPage)mContext;
                                activity.clickedSend(name,phoneNo,id);
                                break;
                            case 2:
                                Intent chatSender = new Intent(mContext,ActivityChatWindowSender.class);
                                chatSender.putExtra("userId",id);
                                chatSender.putExtra("username",name);
                                chatSender.putExtra("token",token);
                                chatSender.putExtra("name",name);
                                mContext.startActivity(chatSender);
                                break;
                        }
                    }
                });
                alertDialog.show();
            }
        });
        holder.set(name,phoneNo);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }
    public void setData(ArrayList<ContactsDetails> list){
        this.list = list;
    }

    public class ContactAdapterHolder extends RecyclerView.ViewHolder{
        View mView;

        public ContactAdapterHolder(View itemView) {
            super(itemView);
            mView = itemView;
        }

        public void set(String name, String phoneNo) {
            TextView nameTextView = (TextView) mView.findViewById(R.id.recylerTxtUsername);
            TextView numberTextView = (TextView) mView.findViewById(R.id.recylerTxtNumber);
            nameTextView.setText(name);
            numberTextView.setText(phoneNo);
        }
    }
}
