/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fx.modem.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;
import lib.fx.logger.Log;

/**
 *
 * @author febri
 */
public class User {
    
    public static final String TAG = "User";

    public String user_id;
    public String pin;
    public String name;
    public String address;
    
    private static final ConcurrentHashMap<String, User> DATA = new ConcurrentHashMap<>();
    public static User get(String p_user_id) {
        return DATA.get(p_user_id);
    }
    public static User put(User p_user) {
        return DATA.put(p_user.user_id, p_user);
    }
    public static int size() {
        return DATA.size();
    }
    public static boolean load(Connection p_connection) {
        try {
            ResultSet rs = p_connection.createStatement().executeQuery("select USER_ID, PIN, NAME, ADDRESS from USER where EC_DATE>NOW()");
            while (rs.next()) {
                User user = new User();
                user.user_id = rs.getString(1);
                user.pin     = rs.getString(2);
                user.name    = rs.getString(3);
                user.address = rs.getString(4);
                DATA.put(user.user_id, user);
            }
            return true;
        }
        catch (Exception e) {
            Log.e(TAG, e);
        }
        return false;
    }
    
    public static int insert(Connection p_connection, User p_user) {
        int insert = 0;
        try {
            String    
            query  = "insert into USER(USER_ID, PIN, NAME, ADDRESS) values ('" + p_user.user_id + "','" + p_user.pin + "','" + p_user.name + "','" + p_user.address + "')";
            insert = p_connection.createStatement().executeUpdate(query, Statement.RETURN_GENERATED_KEYS);
        }
        catch (Exception e) {
            Log.e(TAG, e);
        }
        return insert;
    }
    
}
