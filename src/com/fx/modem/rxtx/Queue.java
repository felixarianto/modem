/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fx.modem.rxtx;

import com.fx.modem.Cmd;
import java.util.concurrent.LinkedBlockingQueue;

/**
 *
 * @author febri
 */
public class Queue {
    
    /**
     * Queue Request from User
     */
    public static final LinkedBlockingQueue<Object[]> INCOMING = new LinkedBlockingQueue<>();
    public static boolean incoming(String p_source, String p_message, long p_timeout) throws InterruptedException {
        return INCOMING.add(new Object[]{p_source, p_message, p_timeout, System.currentTimeMillis()});
    }
    /**
     * Queue Sender to User
     */
    public static final LinkedBlockingQueue<Cmd> OUTGOING = new LinkedBlockingQueue<>();
    public static boolean outgoing(Cmd cmd) throws InterruptedException {
        return OUTGOING.add(cmd);
    }
}
