package com.example.iosdev.pushit;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.iosdev.pushit.classes.ChatDetails;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;



public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatHolder> {

    ArrayList<ChatDetails> chats;
    private static final int VIEW_PURPLE=1;
    private static final int VIEW_ORANGE=2;

    public ChatAdapter(ArrayList<ChatDetails> chats) {
        this.chats = chats;
    }

    public void setChats(ArrayList<ChatDetails> chats) {
        this.chats = chats;
    }

    @Override
    public ChatAdapter.ChatHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        if(viewType==VIEW_ORANGE) {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recyler_row_message_received, parent, false);
        }else{
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.recyler_row_message_sent,parent,false);
        }
        return new ChatAdapter.ChatHolder(view);
    }

    @Override
    public int getItemViewType(int position) {
        if(chats.get(position).isSent()){
            return VIEW_PURPLE;
        }else {
            return  VIEW_ORANGE;
        }
    }

    @Override
    public void onBindViewHolder(ChatAdapter.ChatHolder holder, int position) {
        holder.setData(chats.get(position));
    }

    @Override
    public int getItemCount() {
        return chats.size();
    }

    public static class ChatHolder extends RecyclerView.ViewHolder{
        View mView;

        public ChatHolder(View itemView) {
            super(itemView);
            mView =itemView;
        }
        public void setData(ChatDetails chatDetails){
            TextView message = mView.findViewById(R.id.text_message_body);
            TextView time = mView.findViewById(R.id.text_message_time);
            TextView name = mView.findViewById(R.id.text_message_name);
            message.setText(chatDetails.getMessage());
            long timeData = Long.valueOf(chatDetails.getTime());
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd,yyyy HH:mm");
            Date resultdate = new Date(timeData);
            time.setText(sdf.format(resultdate));
            name.setText(chatDetails.getName());
        }
    }
}
