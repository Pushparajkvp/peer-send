package com.example.iosdev.pushit.classes;


import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.example.iosdev.pushit.AlarmReceiver;
import com.example.iosdev.pushit.AppClass;

import java.lang.reflect.AccessibleObject;

public class Utils {
    public static String cancel = "com.example.iosdev.pushit.classes.foreground.cancel";
    public static String accept = "com.example.iosdev.pushit.classes.foreground.accept";
    public static String main = "com.example.iosdev.pushit.classes.foreground.main";

    public static String getUsername() {
        return AppClass.preferences.getString("name","");
    }

    public static void setUsername(String username) {
        SharedPreferences.Editor preferences = AppClass.preferences.edit();
        preferences.putString("name",username);
        preferences.commit();
    }
    public  static boolean isFileUploading(){
        return AppClass.preferences.getBoolean("uploading",false);
    }
    public static void setFileUploading(boolean uploading){
        SharedPreferences.Editor preferences = AppClass.preferences.edit();
        preferences.putBoolean("uploading",uploading);
        preferences.commit();
    }
    public static String getNumber() {
        return AppClass.preferences.getString("number","");
    }

    public static void setNumber(String number) {
        SharedPreferences.Editor preferences = AppClass.preferences.edit();
        preferences.putString("number",number);
        preferences.commit();
    }

    public static boolean isUserProfileSet() {
        return AppClass.preferences.getBoolean("profileSet",false);
    }

    public static void setUserProfileSet(boolean userProfileSet) {
        SharedPreferences.Editor preferences = AppClass.preferences.edit();
        preferences.putBoolean("profileSet",userProfileSet);
        preferences.commit();
    }

    public static GradientDrawable getGradient(){
        return new GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                new int[] {
                        0xFF0c1c3e,
                        0xFF19365e,
                        0xFF1f3f68,
                        0xFF193653,
                        0xFF0c1c3e});
    }
    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = null;
        if (connectivityManager != null) {
            activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        }
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
    public static boolean isContactsSynced(){
        return AppClass.preferences.getBoolean("contactSync",false);
    }
    public static void setIsContactSynced(boolean b){
        SharedPreferences.Editor preferences = AppClass.preferences.edit();
        preferences.putBoolean("contactSync",b);
        preferences.commit();
    }
    public static boolean isIsUserBusy() {
        return AppClass.preferences.getBoolean("busy",false);
    }
    public static void setIsUserBusy(boolean isUserBusy) {
        SharedPreferences.Editor preferences = AppClass.preferences.edit();
        preferences.putBoolean("busy",isUserBusy);
        preferences.commit();
    }
}
