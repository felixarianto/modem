/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fx.modem.main;

import com.fx.modem.SynchTask;
import com.fx.modem.console.ModemConsole;
import com.fx.modem.db.Pulsa;
import com.fx.modem.db.User;
import java.io.IOException;
import java.net.Socket;
import java.sql.Connection;
import java.util.Date;
import java.util.Properties;
import lib.fx.console.Console;
import lib.fx.console.Console.BindCallback;
import lib.fx.db.DB;
import lib.fx.logger.Log;


/**
 *
 * @author febri
 */
public class Main {
    private static final String TAG = "Main";
    public static void main(String[] args) throws IOException {
        properties();
        log();
        database();
        new SynchTask().start();
        new Console()  .bind(9999, new BindCallback() {
            @Override
            public void onAccept(Socket p_socket) {
                new ModemConsole(p_socket).start();
            }
        });
    }
    private static void properties() {
    }
    private static void log() {
        Log.create(TAG);
    }
    private static boolean database() {
        DB.create(new Properties());
        Connection connection = DB.getConnection(TAG);
        try {
            if (!User.load(connection)) {
                return false;
            }
            sout(TAG, "User loaded size " + User.size());
            if (!Pulsa.load(connection)) {
                return false;
            }
            sout(TAG, "Pulsa loaded size " + User.size());
            return true;
        }
        catch (Exception e) {
            Log.e(TAG, e);
        }
        finally {
            DB.releaseConnection(connection);
        }
        return false;
    }
    private static void sout(String pTag, String pMessage) {
    	System.out.println(new Date(System.currentTimeMillis()).toString() + "    " + pTag + "    " + pMessage);
    }
    
    
}
