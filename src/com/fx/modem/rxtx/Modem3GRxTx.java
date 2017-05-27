/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fx.modem.rxtx;

import com.fx.modem.Cmd;
import com.fx.modem.Modem3G;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import lib.fx.logger.Log;

/**
 *
 * @author febri
 */
public class Modem3GRxTx extends Modem3G {
    
    public Modem3GRxTx(String pTag, String pPort, String pRxPort, int pBaudRate) {
        super(pTag, pPort, pRxPort, pBaudRate);
    }
    
    private final ConcurrentHashMap<String, ArrayList<Cmd>> WAITING_RESPONSE_MAP = new ConcurrentHashMap<>();
    private final LinkedBlockingDeque<Cmd>                  OUTGOING_LIST        = new LinkedBlockingDeque<>();

    @Override
    public synchronized void start() {
        if (mAsExecutor) {
            startSender();
            startReceiver();
        }
        if (mAsSender) {
            startSender();
            startOutgoingWaiter();
        }
        if (mAsReceiver) {
            startReceiver();
        }
    }
    
    public void setAsExecutor() {
        mAsExecutor = true;
    }
    public void setAsReceiver() {
        mAsReceiver = true;
    }
    public void setAsSender() {
        mAsSender = true;
    }
    
    private boolean mAsExecutor = false;
    private boolean mAsReceiver = false;
    private boolean mAsSender   = false;

    @Override
    public boolean exec(String p_metalog, Cmd cmd, HashMap<String, String> p_param) {
        try {
            for (String source : cmd.svc_response_source) {
                ArrayList<Cmd> list = WAITING_RESPONSE_MAP.get(source);
                if (list == null) {
                    WAITING_RESPONSE_MAP.put(source, list = new ArrayList<>());
                }
                list.add(cmd);
            }
            OUTGOING_LIST.add(cmd);
            synchronized (cmd) {
                cmd.wait(cmd.svc_request_timeout);
            }
        }
        catch (Exception e) {
            Log.e(getClass().getSimpleName(), p_metalog, e);
        }
        return true;
    }
    @Override
    public Output getOutput(String p_metalog, Cmd cmd, HashMap<String, String> p_param) {
        return cmd.output;
    }
    /*
     *
     */
    private boolean bReceiverStarted = false;
    private void startReceiver() {
        if (bReceiverStarted) return;
            bReceiverStarted = true;
        new Thread() {
            @Override
            public void run() {
                while (isConnected()) {   
                    try {
                        runReceiver();
                    }
                    catch (Exception e) {
                        Log.e(getClass().getSimpleName(), mMetalog, e);
                    }
                }
            }
        }.start();
    }
    private void runReceiver() throws InterruptedException {
        String sms_id = getIncomingSMS(1000);
        if (sms_id == null) {
            return;
        }
        String[] sms = readSMS(sms_id, 5000);
        if (sms == null) {
            return;
        }
        if (!deleteSMS(sms[0])) {
            return;
        }
        Output response = putAsResponse(sms[1], sms[2]);
        if (response != null) {
            Log.i(getClass().getSimpleName(), mMetalog, "[TR] " + sms[1] + "  " + sms[2] + " " + response.toString());
        }
        else {
            Log.i(getClass().getSimpleName(), mMetalog, "[T1] " + sms[1] + "  " + sms[2]);
            Queue.incoming(sms[1], sms[2], 10000L);
        }
    }
    private Output putAsResponse(String p_source, String p_message) {
        Output output = null;
        ArrayList<Cmd> list = WAITING_RESPONSE_MAP.getOrDefault(p_source, WAITING_RESPONSE_MAP.get(""));
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                Cmd cmd = list.get(i);
                if (match(p_message, format(cmd.svc_response_keys_success, cmd.param).split(","))){
                    output = new Output();
                    output.status(Output.SUCCESS);
                }
                else if (match(p_message, format(cmd.svc_response_keys_failed, cmd.param).split(","))){
                    output = new Output();
                    output.status(Output.FAILED);
                }
                else {
                    continue;
                }
                cmd.output = output;
                cmd.output.putAll(parse(cmd.svc_response_format, p_message));
                synchronized(cmd) {
                    try { cmd.notify();} catch (Exception e) {}
                }
                break;
            }
        }
        return output;
    }
    /*
     *
     */
    private boolean bSenderStarted = false;
    private void startSender() {
        if (bSenderStarted) return;
            bSenderStarted = true;
        new Thread() {
            @Override
            public void run() {
                while (isConnected()) {                    
                    try {
                        runSender();
                    }
                    catch (Exception e) {
                        Log.e(getClass().getSimpleName(), mMetalog, e);
                    }
                }
            }
        }.start();
    }
    private boolean bWaiterStarted = false;
    private void startOutgoingWaiter() {
        if (bWaiterStarted) return;
            bWaiterStarted = true;
        new Thread() {
            @Override
            public void run() {
                while (isConnected()) {                    
                    try {
                        Cmd cmd = Queue.OUTGOING.poll();
                        if (cmd == null) {
                            continue;
                        }
                        OUTGOING_LIST.add(cmd);
                    }
                    catch (Exception e) {
                        Log.e(getClass().getSimpleName(), mMetalog, e);
                    }
                }
            }
        }.start();
    }
    private void runSender() {
        Cmd cmd = OUTGOING_LIST.poll();
        if (cmd == null) {
            return;
        }
        long   timeout = cmd.svc_request_timeout;
        long   puttime = Long.valueOf(cmd.param.getOrDefault("puttime", "0"));
        String transid = cmd.param.getOrDefault("transid", "0"); 
        long   curtime = System.currentTimeMillis(); 
        if (curtime - puttime > timeout) {
            Log.w(getClass().getSimpleName(), mMetalog, "[R1 TIMEOUT] " + (curtime - puttime) + "ms  " + transid + "  " + cmd.toString(cmd.param));
            return;
        }
        Log.i(getClass().getSimpleName(), mMetalog, "[Te] " + cmd.toString(cmd.param));
        super.exec(mMetalog, cmd, cmd.param);
    }
}
