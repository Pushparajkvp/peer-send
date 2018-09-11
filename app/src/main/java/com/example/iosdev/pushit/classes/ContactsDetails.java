package com.example.iosdev.pushit.classes;

public class ContactsDetails {
    private String id,phoneNo,name,token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public ContactsDetails() {
    }


    public ContactsDetails(String id, String phoneNo, String name) {
        this.id = id;
        this.phoneNo = phoneNo;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPhoneNo() {
        return phoneNo;
    }

    public void setPhoneNo(String phoneNo) {
        this.phoneNo = phoneNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
