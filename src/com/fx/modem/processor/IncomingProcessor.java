/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fx.modem.processor;

import java.util.concurrent.TimeUnit;
import lib.fx.thread.Task;

/**
 *
 * @author febri
 */
public class IncomingProcessor extends Task {

    private long mProcessId = System.currentTimeMillis();
    @Override
    protected void onRunning() {
        String metalog = (mProcessId++) + ""; 
        try {
            Object[] incoming = Queue.INCOMING.poll(1, TimeUnit.MINUTES);
            String source  = (String) incoming[0]; 
            String message = (String) incoming[1]; 
            long   timeout = (long)   incoming[2];
            long   puttime = (long)   incoming[3];
            long   curtime = System.currentTimeMillis(); 
                   metalog += ":" + source;
            if (curtime - puttime > timeout) {
                w(metalog, "[T1 TIMEOUT] " + message);
                return;
            }
            i(metalog, "[T1] " + message);
            String exec = Executor.exec(source, message);
            i(metalog, "[T4] " + (curtime - System.currentTimeMillis()) + "ms " + message + "  " + exec);
        }
        catch (InterruptedException ex) {
            e(metalog, "Interupted");
        }
    }
    
}
