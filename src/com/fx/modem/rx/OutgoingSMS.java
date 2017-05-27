/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fx.modem.rx;

import com.fx.modem.processor.Queue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 *
 * @author febri
 */
public class OutgoingSMS {

    private static final LinkedBlockingDeque<String[]> QUEUE = new LinkedBlockingDeque<>();
    public static void add(String p_target, String p_text) {
        try {
            Queue.outgoing(p_target, p_text, 5000);
        }
        catch (Exception e) {
        }
    }
    public static String[] get() {
        return QUEUE.poll();
    }
    
}
