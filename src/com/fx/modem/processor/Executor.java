/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fx.modem.processor;

import com.fx.modem.Cmd;
import com.fx.modem.Modem;
import com.fx.modem.Modem.Output;
import com.fx.modem.db.Pulsa;
import com.fx.modem.balance.BalanceDB;
import com.fx.modem.db.TransHistory;
import com.fx.modem.db.User;
import com.fx.modem.properties.Prop;
import com.fx.modem.rx.IncomingSMS;
import com.fx.modem.rx.OutgoingSMS;
import java.sql.Connection;
import java.util.HashMap;
import lib.fx.db.DB;
import lib.fx.util.MdnUtil;
import lib.fx.logger.Log;

/**
 *
 * @author febri
 */
public class Executor {
    private static final String TAG = "Executor"; 
    
    public static void start() {
        new Thread() {
            @Override
            public void run() {
                while (true) {                    
                    String[] sms = IncomingSMS.get();
                    if (sms != null) {
                        exec(sms[0], sms[1]);
                    }
                }
            }
        }.start();
    }
    
    public static String exec(String p_user_id, String p_request) {
        String[] msg = p_request.split("\\.");
        switch (msg[0].toUpperCase()) {
            case "REG":
                //REG.0853432423.1111.Nada Cell.Ciledug.1111
                return register(p_user_id, msg[5], msg[1], msg[2], msg[3], msg[4]);
            case "I":
                //I.T10.0853432423.1111
                return topup(p_user_id, msg[3], msg[2], msg[1], p_request);
            case "PLN":
                break;
            case "":
                break;
            default:
                break;
        }
        return null;
    }

    private static boolean validPin(String p_user_id, String p_pin) {
        User user = User.get(p_user_id);
        if (user == null) {
            return false;
        }
        return user.pin.equals(p_pin);
    }
    /**
     * 
     * @param p_user_id
     * @param p_new_pin 
     * @param p_name
     * @param p_address 
     */
    private static String register(String p_user_id, String p_pin, String p_phone, String p_new_pin, String p_name, String p_address) {
        String error = null;
        if (!validPin(p_user_id, p_pin)) {
            OutgoingSMS.add(p_user_id, error = "Gagal mendaftarkan " + p_phone + "(" + p_name + "). PIN anda salah");
            return error;
        }
        Connection conn = DB.getConnection(TAG);
        try {
            User new_user    = new User();
            new_user.user_id = MdnUtil.format(p_phone);
            new_user.pin     = p_new_pin;
            new_user.name    = p_name;
            new_user.address = p_address;
            int insert = User.insert(conn, new_user);
            if (insert <= 0) {
                OutgoingSMS.add(p_user_id, error = "Gagal mendaftarkan " + p_phone + ". Nomor sudah terpakai");
                DB.rollback(conn);
                return error;
            }
            User.put(new_user);
            DB.commit(conn);
            OutgoingSMS.add(p_user_id, "Berhasil mendaftarkan " + p_phone + " sebagai "+ p_name);
            OutgoingSMS.add(p_phone, "Selamat datang sebagai Agen Pulsa " + Prop.get(Prop.ORG_NAME, "") + " PIN: " + p_new_pin);
        }
        catch (Exception e) {
            Log.e(TAG, e);
        }
        finally {
            DB.releaseConnection(conn);
        }
        return error;
    }
    
    /**
     * 
     * @param p_user_id
     * @param p_pin
     * @param p_b_number
     * @param p_nominal_code 
     */
    private static String topup(String p_user_id, String p_pin, String p_b_number, String p_nominal_code, String p_command) {
        String result = null;
        if (!validPin(p_user_id, p_pin)) {
            OutgoingSMS.add(p_user_id, result = "Gagal isi " + p_b_number + ". PIN anda salah");
            return result;
        }
        TransHistory th = new TransHistory();
        th.user_id      = p_user_id;
        th.trans_id     = Long.toHexString(System.currentTimeMillis()).toUpperCase();
        String metalog  = p_user_id + ":" + th.trans_id + ":" + p_nominal_code + ":"+ p_b_number;
        Modem  modem    = null;
        Connection conn = null;
        try {
            Pulsa pulsa = Pulsa.get(p_nominal_code);
            if (pulsa == null) {
                th.status = "Kode " + p_nominal_code + " tidak diketahui";
                OutgoingSMS.add(p_user_id, result = "Gagal isi ke " + p_b_number + ". " + p_nominal_code + " tidak diketahui");
                return result;
            }
            th.bnumber = p_b_number;
            th.amount  = pulsa.amount;
            th.price   = pulsa.price;
            th.command = p_command;
            modem = Modem.get(pulsa.provider, pulsa.amount);
            if (modem == null) {
                th.status = "Modem " + pulsa.provider + " tidak tersedia";
                Log.e(TAG, metalog, "Modem not alvaliable provider: " + pulsa.provider + " amount: " + pulsa.amount);
                OutgoingSMS.add(p_user_id, result = "Gagal isi ke " + p_b_number + " sistem sedang sibuk");
                return result;
            }
            Modem.lock(modem, metalog);
            conn = DB.getConnection(TAG);
            if (conn == null) {
                th.status = "DB Connection NULL";
                Log.e(TAG, metalog, "DB Connection Not Found");
                OutgoingSMS.add(p_user_id, result = "Gagal isi ke " + p_b_number + " sistem sedang sibuk");
                return result;
            }
            th.anumber     = modem.getICCID();
            th.description = modem.getPort();
            boolean balance_dec = BalanceDB.balanceUpdate(conn, metalog, p_user_id, -pulsa.price, th.trans_id);
            if (!balance_dec) {
                th.status = "Saldo tidak cukup";
                OutgoingSMS.add(p_user_id, result = "Gagal isi " + pulsa.amount + " ke " + p_b_number + " Saldo tidak cukup. ID." + th.trans_id);
                return result;
            }
            boolean balance_add = BalanceDB.balanceUpdate(conn, metalog, "ADMIN", pulsa.price, th.trans_id);
            if (!balance_add) {
                Log.w(TAG, metalog, "Failed add to ADMIN wallet " + pulsa.price);
            }
            HashMap<String, String> 
            param = new HashMap<>();
            param.put("nomor", p_b_number);
            Cmd cmd = modem.COMMAND.get("ISI_" + pulsa.amount);
            if (cmd == null) {
                th.status = "Service ISI_" + pulsa.amount + " belum tersedia";
                Log.w(TAG, metalog, result = "Unknown command at " + modem.getName() + " " + pulsa.amount);
                return result;
            }
            if (!modem.exec(metalog, cmd, param)) {
                th.status = "Modem " + modem.getICCID() + " gagal eksekusi " + cmd.toString(param);
                Log.w(TAG, metalog, result = "Failed execution command " + cmd.toString(param));
                return result;
            }
            Output output = modem.getOutput(metalog, cmd, param);
            if (output.isEmpty()) {
                double balance_before = modem.BALANCE.get(pulsa.amount + "");
                if (!modem.inquiryBalance()) {
                    output.status(Output.PENDING);
                }
                else {
                    double balance_after = modem.BALANCE.get(pulsa.amount + "");
                    double selisih       = balance_before - balance_after;
                    if (selisih == 0) {
                        output.status(Output.FAILED);
                    }
                    else if (selisih == 1) {
                        output.status(Output.SUCCESS);
                    }
                    else {
                        output.status(Output.PENDING);
                    }
                }
            }
            
            if (output.isSuccess()) {
                DB.commit(conn);
                result    = output.getStatus();
                th.status = result;
                Log.w(TAG, metalog, "Success " + output.getStatus() + " >> " + p_b_number + " : " + p_nominal_code);
            }
            else if (output.isFailed()) {
                DB.rollback(conn);
                result    = "Gagal isi " + pulsa.amount + " ke " + p_b_number + " silahkan coba beberapa saat lagi. ID." + th.trans_id;
                th.status = output.getStatus();
                th.description = output.getOrDefault("DESC", "");
                OutgoingSMS.add(p_user_id, result);
                Log.w(TAG, metalog, "Failed output " + modem.getICCID() + " >> " + p_b_number + " : " + p_nominal_code);
            }
            else if (output.isPending()) {
                DB.commit(conn);
                result    = "Transaksi isi " + pulsa.amount + " ke " + p_b_number + " sedang di proses. ID." + th.trans_id;
                th.status = output.getStatus();
                th.description = output.getOrDefault("DESC", "");
                OutgoingSMS.add(p_user_id, result);
                modem.portClose();
                Log.w(TAG, metalog, "Pending output " + modem.getICCID() + " >> " + p_b_number + " : " + p_nominal_code);
            }
            else {
                DB.commit(conn);
                result    = "Unknown status " + output.toString() + " >> " + p_b_number + " : " + p_nominal_code;
                th.status = "Tidak diketahui";
                th.description = output.getOrDefault("DESC", "");
                Log.w(TAG, metalog, result);
            }
            
        }
        catch (Exception e) {
            DB.rollback(conn);
            Log.e(TAG, e);
        }
        finally {
            Modem.unlock(modem, metalog);
            DB.rollback(conn);
            if (conn != null) {
                if (TransHistory.insert(conn, th) > 0) {
                    DB.commit(conn);
                }
            }
            DB.releaseConnection(conn);
        }
        return result;
    }
    private static void topupPending(String metalog, Modem modem, Cmd cmd, HashMap<String, String> param) {
        
    }
    
}
