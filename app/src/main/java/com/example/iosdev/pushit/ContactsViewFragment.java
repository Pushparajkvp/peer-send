package com.example.iosdev.pushit;


import android.content.ContentResolver;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.example.iosdev.pushit.classes.ContactsDetails;

import java.util.ArrayList;


/**
 * A simple {@link Fragment} subclass.
 */
public class ContactsViewFragment extends Fragment {


    public ContactsViewFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view= inflater.inflate(R.layout.fragment_contacts_view, container, false);

        return view;
    }


}
