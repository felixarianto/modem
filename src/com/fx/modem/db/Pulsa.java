/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fx.modem.db;

import static com.fx.modem.db.User.TAG;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.concurrent.ConcurrentHashMap;
import lib.fx.logger.Log;

/**
 *
 * @author febri
 */
public class Pulsa {

    public String code;
    public String provider;
    public int    amount;
    public double price;
    
    private static final ConcurrentHashMap<String, Pulsa> DATA = new ConcurrentHashMap<>();
    public static Pulsa get(String p_code) {
        return DATA.get(p_code);
    }
     public static int size() {
        return DATA.size();
    }
    public static boolean load(Connection p_connection) {
        try {
            ResultSet rs = p_connection.createStatement().executeQuery("select CODE, PROVIDER, AMOUNT, PRICE from PULSA where EC_DATE>NOW()");
            while (rs.next()) {
                Pulsa pulsa = new Pulsa();
                pulsa.code     = rs.getString(1);
                pulsa.provider = rs.getString(2);
                pulsa.amount   = rs.getInt(3);
                pulsa.price    = rs.getDouble(4);
                DATA.put(pulsa.code, pulsa);
            }
            return true;
        }
        catch (Exception e) {
            Log.e(TAG, e);
        }
        return false;
    }

    
    
}
