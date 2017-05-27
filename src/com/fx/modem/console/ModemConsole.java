/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fx.modem.console;

import com.fx.modem.processor.Executor;
import com.fx.modem.db.Pulsa;
import com.fx.modem.db.User;
import java.net.Socket;
import java.sql.Connection;
import lib.fx.console.ConsoleHandler;
import lib.fx.db.DB;
import lib.fx.logger.Log;
import lib.fx.util.SpaceToken;


/**
 *
 * @author febri
 */
public class ModemConsole extends ConsoleHandler {

    private final String TAG = "ModemConsole";
    private final String VERSION = "1.0 First Release 11 May 2017";
    public ModemConsole(Socket p_socket) {
        super(p_socket);
    }

    @Override
    public void onReceive(String p_data) {
        Log.i(TAG, "", "CONSOLE >> " + p_data);
        SpaceToken token = new SpaceToken(p_data);
        switch(token.getToken(0).toUpperCase()) {
            case "EXEC"  : exec(token.getToken(1));
            break;
            case "LOAD"  : load(token.getToken(1));
            break;
            case "HELP"  : help();
            break;
        }
    }
    
    private void exec (String p_data) {
        String  error = Executor.exec("superuser", p_data);
        writeLn(error == null ? "OK" : error);
    }
    
    private void load (String p_data) {
        String resp = null;
        Connection connection = DB.getConnection(TAG);
        try {
            if (p_data.equals("pulsa") && !Pulsa.load(connection)) {
                resp = "Failed Load Pulsa";
            }
            else if (p_data.equals("user") && !User.load(connection)) {
                resp = "Failed Load User";
            }
        }
        catch (Exception e) {
            resp = e.toString(); 
        }
        finally {
            DB.releaseConnection(connection);
        }
        writeLn(resp == null ? "OK" : resp);
    }
    
    private void help () {
        writeLn("Version " + VERSION);
    }
    
}