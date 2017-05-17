/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fx.modem.rx;

import java.util.concurrent.LinkedBlockingDeque;


/**
 *
 * @author febri
 */
public class IncomingSMS {
    
    private static final LinkedBlockingDeque<String[]> QUEUE = new LinkedBlockingDeque<>();
    public static void add(String p_source, String p_text) {
        QUEUE.add(new String[]{p_source, p_text});
    }
    
    public static String[] get() {
        return QUEUE.poll();
    }
    
}
