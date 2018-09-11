package com.example.iosdev.pushit;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.provider.ContactsContract;
import android.support.v4.widget.NestedScrollView;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v7.widget.Toolbar;

import com.example.iosdev.pushit.classes.ContactsDetails;
import com.example.iosdev.pushit.classes.DatabaseHandler;
import com.example.iosdev.pushit.classes.Utils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

public class MainPage extends AppCompatActivity implements ValueEventListener {



    FirebaseAuth mAuth;
    RecyclerView  mRecyclerViewKnown;
    ContactsAdapter adapterKnown;
    Handler mHandler;
    ArrayList<ContactsDetails> databaseList;
    HashSet<String> hashSet;
    DatabaseHandler databaseHandler;
    RelativeLayout loadingPanel;
    Toolbar mToolbar;

    String clickedName,clickedId,clickedNumber;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Utils.setFileUploading(false);
        mAuth = FirebaseAuth.getInstance();
        if(mAuth.getCurrentUser()==null){
            Intent startPage = new Intent(MainPage.this,StartPage.class);
            startActivity(startPage);
            finish();
            return;
        }
        if(!Utils.isUserProfileSet()){
            Intent setupPage = new Intent(MainPage.this,SetupPage.class);
            startActivity(setupPage);
            finish();
            return;
        }
        setContentView(R.layout.activity_main_page);

        mToolbar = (Toolbar) findViewById(R.id.toolbar_id);

        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setTitle(getResources().getString(R.string.app_name));

        databaseHandler = new DatabaseHandler(this);
        hashSet = new HashSet<>();
        databaseList = new ArrayList<>();

        loadingPanel = (RelativeLayout)findViewById(R.id.loadingPanel);

        mRecyclerViewKnown = (RecyclerView)findViewById(R.id.recycleViewKnow);
        mRecyclerViewKnown.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerViewKnown.setItemAnimator(null);
        mRecyclerViewKnown.setNestedScrollingEnabled(false);
        mHandler = new Handler();
        if(!Utils.isContactsSynced()) {
            BackGroundContactsGather contactsGather = new BackGroundContactsGather();
            contactsGather.execute();
            Utils.setIsContactSynced(true);
        }else {
            FirebaseDatabase.getInstance().getReference().child("users").keepSynced(true);
            FirebaseDatabase.getInstance().getReference().child("users").addListenerForSingleValueEvent(MainPage.this);
        }
        setIsLoading(true);
    }

    @Override
    public void onDataChange(DataSnapshot dataSnapshot) {
        if(dataSnapshot==null)
            return;
        databaseList.clear();
        //Toast.makeText(this,"TRIGGERED",Toast.LENGTH_LONG).show();
        for(DataSnapshot childs : dataSnapshot.getChildren()){
            Object name = childs.child("name").getValue();
            Object number = childs.child("number").getValue();
            Object id = childs.getKey();
            Object token = childs.child("token").getValue();
            if(name==null || number==null || token == null)
                continue;
            if(number.equals(Utils.getNumber()))
                continue;
            ContactsDetails cd = new ContactsDetails(id.toString(), number.toString(), name.toString());
            cd.setToken(token.toString());
            if(databaseHandler.getContactsCount(cd.getPhoneNo())>0)
                databaseList.add(cd);
        }
       // Toast.makeText(this,"yes",Toast.LENGTH_LONG).show();
        AddKnown();
    }

    @Override
    public void onCancelled(DatabaseError databaseError) {

    }

    public void invitePeople(View view) {
        String shareBody = "Hey! " + Utils.getUsername() + " invited you to " + getResources().getString(R.string.app_name) + "" +
                ". Use the following link \n " + getResources().getString(R.string.link);
        Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Subject Here");
        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareBody);
        startActivity(Intent.createChooser(sharingIntent, getResources().getString(R.string.share_using)));
    }

    public void clickedSend(String name, String phoneNo, String id) {
        Intent i = new Intent(Intent.ACTION_PICK);
        i.setType("*/*");
        startActivityForResult(i, 1997);
        this.clickedId = id;
        this.clickedName = name;
        this.clickedNumber = phoneNo;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(resultCode == RESULT_OK){
            if(requestCode==1997){
                Uri replyUri = data.getData();
                if(replyUri==null)
                    return;
                Intent i = new Intent(this,ForegroundServiceSender.class);
                i.setAction(Utils.main);
                i.putExtra("id",clickedId);
                i.putExtra("uri",replyUri.toString());
                startService(i);
            }
        }
    }

    public class BackGroundContactsGather extends AsyncTask<Void,Void,Void> {

        public BackGroundContactsGather() {

        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            FirebaseDatabase.getInstance().getReference().child("users").keepSynced(true);
            FirebaseDatabase.getInstance().getReference().child("users").addListenerForSingleValueEvent(MainPage.this);
            //Toast.makeText(MainPage.this,String.valueOf(list.size()),Toast.LENGTH_LONG).show();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            ContentResolver cr = getContentResolver();
            Cursor cur = cr.query(ContactsContract.Contacts.CONTENT_URI,
                    null, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME+" ASC");
            if (cur.getCount() > 0) {
                while (cur.moveToNext()) {
                    String id = cur.getString(cur.getColumnIndex(ContactsContract.Contacts._ID));
                    String name = cur.getString(cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
                    if (Integer.parseInt(cur.getString(cur.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER))) > 0) {
                        Cursor pCur = cr.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                null,
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID +" = ?",
                                new String[]{id}, null);
                        while (pCur.moveToNext()) {
                            String phoneNo = pCur.getString(pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                            phoneNo = phoneNo.replaceAll("[^\\d]","");
                            if(phoneNo.length()<=10)
                                phoneNo = "91"+phoneNo;
                            final ContactsDetails details = new ContactsDetails(id,phoneNo,name);
                            if(!hashSet.contains(details.getPhoneNo())) {
                                hashSet.add(details.getPhoneNo());
                                ContactsDetails cd = new ContactsDetails(id,phoneNo,name);
                                databaseHandler.addContact(cd);
                            }
                        }
                        pCur.close();
                    }
                }
            }
            return null;
        }
    }

    public void AddKnown(){
        if(adapterKnown==null){
            adapterKnown = new ContactsAdapter(databaseList,this);
            adapterKnown.setHasStableIds(true);
            mRecyclerViewKnown.setAdapter(adapterKnown);
        }else{
            adapterKnown.notifyDataSetChanged();
        }
        setIsLoading(false);
    }
    //To hide and show loading panel
    public void setIsLoading(boolean isLoading){
        if(isLoading){
            loadingPanel.setVisibility(View.VISIBLE);
        }else{
            loadingPanel.setVisibility(View.GONE);
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_page_menu,menu);
        return super.onCreateOptionsMenu(menu);
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.refresh:
                BackGroundContactsGather contactsGather = new BackGroundContactsGather();
                contactsGather.execute();
                setIsLoading(true);
                break;
        }
        return true;
    }

}
