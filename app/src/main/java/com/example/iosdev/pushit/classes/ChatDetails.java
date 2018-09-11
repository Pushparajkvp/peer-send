package com.example.iosdev.pushit.classes;

//Stores chat details
public class ChatDetails {
    private String message,name,time,userId;
    private boolean sent;

    public ChatDetails(String message, String name, String time, boolean sent, String userId) {
        this.message = message;
        this.name = name;
        this.time = time;
        this.sent = sent;
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public boolean isSent() {
        return sent;
    }

}
