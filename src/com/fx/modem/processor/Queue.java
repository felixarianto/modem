/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fx.modem.processor;

import java.util.concurrent.LinkedBlockingQueue;

/**
 *
 * @author febri
 */
public class Queue {
    
    public static final LinkedBlockingQueue<Object[]> INCOMING = new LinkedBlockingQueue<>();
    /**
     * Queue Request from User
     */
    public static boolean incoming(String p_source, String p_message, long p_timeout) throws InterruptedException {
        return INCOMING.add(new Object[]{p_source, p_message, p_timeout, System.currentTimeMillis()});
    }
    
    
    public static final LinkedBlockingQueue<Object[]> OUTGOING = new LinkedBlockingQueue<>();
    /**
     * Queue Sender to User
     */
    public static boolean outgoing(String p_target, String p_message, long p_transid) throws InterruptedException {
        return OUTGOING.add(new Object[]{p_target, p_message, 5000l, System.currentTimeMillis(), Long.toHexString(p_transid)});
    }
    /**
     * Queue Response to User
     */
}
