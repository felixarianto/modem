/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fx.modem.rx;

import com.fx.modem.Modem;
import java.util.ArrayList;

/**
 *
 * @author febri
 */
public class Receiver extends Modem {
    
    public Receiver(String pTag, String pPort) {
        super(pTag, pPort, RATE_128000);
    }

    @Override
    public void run() {
        while (isConnected()) {
            p : {
                int size = getIncomingSMS();
                if (size == 0) {
                    break p;
                }
                String msg = readSMS(30000);
                if (msg == null) {
                    break p;
                }
                ArrayList<String[]> sms = smsparse(msg);
                if (sms.isEmpty()) {
                    d(mMetalog, "Failed parse SMS, is any undefined character? >> " + msg);
                    break p;
                }
                for (int i = 0; i < sms.size(); i++) {
                    String[] m = sms.get(i);
                    if (deleteSMS(m[0])) {
                        i(mMetalog, "Read SMS " + m[1] + "  " + m[2]);
                        IncomingSMS.add(m[1], m[2]);
                    }
                }
            }
            try {sleep(10000);} catch (Exception e) {}
        }
    }

    private int getIncomingSMS() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    
}
