/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fx.modem.db;

import com.fx.modem.Modem;
import java.sql.Connection;
import java.sql.Statement;
import lib.fx.logger.Log;

/**
 *
 * @author febri
 */
public class TransHistory {
    
    public static final String TAG = "TransHistory";

    public String trans_id = "";
    public String user_id  = "";
    public String bnumber  = "";
    public double amount = 0;
    public double price  = 0;
    public String anumber     = "";
    public String description = "";
    public String command = "";
    public String status  = "";
    
    public static int insert(Connection p_connection, TransHistory p_trans) {
        int insert = 0;
        String query  = ""; 
        try {
            query  = "insert into TRANS_HISTORY(CREATED_DATE, TRANS_ID, USER_ID, BNUMBER, AMOUNT, PRICE, ANUMBER, DESCRIPTION, COMMAND, STATUS)"
                   + " values (CURDATE(), '" + p_trans.trans_id + "','" + p_trans.user_id + "','" + p_trans.bnumber + "'"
                   + "," + p_trans.amount + "," + p_trans.price + ",'" + p_trans.anumber + "','" + p_trans.description + "'"
                   + ",'"+ p_trans.command+ "','" + p_trans.status + "')";
            insert = p_connection.createStatement().executeUpdate(query);
            
            if (p_trans.status.equals(Modem.Output.PENDING)) {
                query  = "insert into TRANS_PENDING(TRANS_ID)"
                   + " values ('" + p_trans.trans_id + "')";
                p_connection.createStatement().executeUpdate(query);
            }
        }
        catch (Exception e) {
            Log.w(TAG, p_trans.trans_id, "Failed " +  query);
            Log.e(TAG, p_trans.trans_id, e);
        }
        return insert;
    }
}
