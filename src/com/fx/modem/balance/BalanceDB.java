/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fx.modem.balance;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import lib.fx.logger.Log;

/**
 *
 * @author febri
 */
public class BalanceDB {
    private static final String TAG = "BalanceDB";
    public static boolean balanceUpdate(Connection conn, String p_metalog, String p_user_id, double p_amount, String p_desc) {
        try { 
            String mutation_type = "";
            Statement statement = conn.createStatement();
            //@INCREASE
            if (p_amount > 0) {
                String query = "update BALANCE set AMOUNT=AMOUNT+" + p_amount + " WHERE USER_ID ='" + p_user_id + "'";
                Log.d(TAG, p_metalog, query);
                int exec  = statement.executeUpdate(query);
                if (exec == 0) {
                    query = "insert into BALANCE(USER_ID, AMOUNT) values('" + p_user_id + "'," + p_amount +")";
                    Log.d(TAG, p_metalog, query);
                    statement.executeUpdate(query);    
                }
            }
            //@DECREASE
            else {
                String query = "update BALANCE set AMOUNT=AMOUNT+" + p_amount + " WHERE USER_ID ='" + p_user_id + "' AND AMOUNT>=" + Math.abs(p_amount);
                Log.d(TAG, p_metalog, query);
                int exec  = statement.executeUpdate(query);
                if (exec == 0) {
                    return false;
                }
            }
            double balance_before = 0;
            double balance_after  = 0;
            ResultSet rs = statement.executeQuery("select AMOUNT from BALANCE where USER_ID='" + p_user_id + "'");
            if (rs.next()) {
                balance_after = rs.getDouble(1);
                if (p_amount > 0) {
                    mutation_type = "CR";
                    balance_before = balance_after - Math.abs(p_amount);
                }
                else {
                    mutation_type = "DB";
                    balance_before = balance_after + Math.abs(p_amount);
                }
            }
            String query = "insert into BALANCE_MUTATION(USER_ID, AMOUNT, TYPE, DESCRIPTION, CREATED_DATE, BALANCE_BEFORE, BALANCE_AFTER) values "
                     + "('" + p_user_id + "',ABS(" + p_amount + "),'" + mutation_type + "','" + p_desc + "',CURDATE()," + balance_before + "," + balance_after + ")";
            conn.createStatement().executeUpdate(query);
            return true;
        } catch (Exception e) {
            Log.e(TAG, p_metalog, e);
        }
        return false;
    }
}
