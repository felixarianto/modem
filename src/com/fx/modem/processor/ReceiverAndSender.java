/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fx.modem.processor;

import com.fx.modem.Modem;
import java.util.concurrent.TimeUnit;
import lib.fx.logger.Log;
import lib.fx.util.MdnUtil;

/**
 *
 * @author febri
 */
public class ReceiverAndSender implements Modem.Runnable{

    @Override
    public void run(Modem modem) {
        while (modem.isConnected()) {
            try { Thread.sleep(1000);} catch (Exception e) {}
            runReceiver(modem);
            runSender  (modem);
        }
    }
    
    private void runReceiver(Modem modem) {
        String metalog = "";
        try {
            String sms_id = modem.getIncomingSMS(5000);
            if (sms_id == null) {
                return;
            }
            String[] incoming = modem.readSMS(sms_id, 5000);
            try {
                incoming[1] = MdnUtil.format(incoming[1]);
                Queue.incoming(incoming[1], incoming[2], 900000);
                Log.d(modem.getName(), metalog, "Put incoming SMS: " + incoming[1] + "  " + incoming[2]);
            }
            catch (Exception e) {
                String s = "";
                if (incoming == null) {
                    s = "null";
                }
                else {
                    for (String string : incoming) {
                        s += string + " ";
                    }
                }
                Log.e(modem.getName(), metalog, "Bad incoming SMS: " + s);
            }
        }
        catch (Exception e) {
            Log.e(modem.getName(), metalog, e);
        }
    }
    private void runSender(Modem modem) {
        String metalog = "";
        try {
            Object[] incoming = Queue.OUTGOING.poll(1, TimeUnit.SECONDS);
            if (incoming == null) {
                return;
            }
            String target  = (String) incoming[0]; 
            String message = (String) incoming[1]; 
            long   timeout = (long)   incoming[2];
            long   puttime = (long)   incoming[3];
            String transid = (String) incoming[4]; 
            long   curtime = System.currentTimeMillis(); 
            metalog = transid + ":" + target;
            if (curtime - puttime > timeout) {
                Log.w(modem.getName(), metalog, "[R1 TIMEOUT] " + (curtime - puttime) + "ms " + message);
                return;
            }
            boolean send = false;
            Log.i(modem.getName(), metalog, "[R1] " + message);
            for (int i = 0; i < 3; i++) {
                send = modem.sendSMS(target, message, timeout);
                if (send) {
                    break;
                }
            }
            Log.i(modem.getName(), metalog, "[R4] " + (curtime - System.currentTimeMillis()) + "ms " + message + "  send: " + send);
            if (!send) {
                Queue.outgoing(target, message, timeout);
            }
        }
        catch (Exception e) {
            Log.e(modem.getName(), metalog, e);
        }
    }

    

}
