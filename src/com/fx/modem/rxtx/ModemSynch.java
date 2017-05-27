/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fx.modem.rxtx;

import com.fx.modem.Modem;
import com.fx.modem.processor.ReceiverAndSender;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import lib.fx.db.DB;
import lib.fx.thread.Task;

/**
 *
 * @author febri
 */
public class ModemSynch extends Task {

    public ModemSynch() {
        setName("ModemSynch");
        mInterval = 60000;
    }

    @Override
    public void onRunning() {
        String metalog = Long.toHexString(System.currentTimeMillis());
        try {
            String txPort;
            String rxPort;
            HashMap<String, ArrayList<String>> map = getUsbUsagesApp();
            Set<String> keys = map.keySet();
            int size = 0;
            for (String key : keys) {
                ArrayList<String> list = map.get(key);
                if (list.size() > 2) {
                    list.remove(0);
                    txPort = list.remove(0);
                    rxPort = list.remove(0);
                }
                else {
                    txPort = list.remove(0);
                    rxPort = null;
                }
                checkPort(metalog, txPort, rxPort);
                size++;
                sleep(3000);
            }
            d(metalog, "Avaliable " + size + " PORT, 0 SIM, 0 PROVIDE, 0 DEVICE");
        }
        catch (Exception e) {
            e(metalog, e);
        }
    }
    
    private HashMap<String, ArrayList<String>> getUsbUsagesApp() {
        HashMap<String, ArrayList<String>> result = new HashMap<>();
        BufferedReader reader = null;
        Process        proces = null;
        try {
            String command = "/bin/ls /sys/bus/usb-serial/devices/ -lt";
            proces = Runtime.getRuntime().exec(command);
            reader = new BufferedReader(new InputStreamReader(proces.getInputStream()));
            String[] data;
            String line, key;
            while((line = reader.readLine()) != null) {
                try {
                    data = line.split("/");
                    key  = data[data.length - 3];
                    ArrayList<String> list = result.get(key);
                    if (list == null) {
                        result.put(key, list = new ArrayList<>());
                    }
                    list.add("/dev/" + line.split("\\ ")[8]);
                }
                catch (Exception e) {
                }
            }
            d("", result.toString());
            proces.waitFor();  
            proces.destroy();
        }
        catch (Exception | Error e) {
            e("", e);
        }
        finally {
            if (reader != null) try {reader.close();} catch (Exception e){}
            if (proces != null) try {proces.destroy();} catch (Exception e){}
        }
        return result;
    }
    
    protected void checkPort(String metalog, String pPort, String pRxPort) {
        Modem modem = Modem.getAtPort(pPort);
        if (modem != null) {
            return;
        }
        if (pRxPort == null) {
            modem = new Modem(pPort, pPort, Modem.RATE_115200);
        }
        else {
            modem = new Modem3GRxTx(pPort + ":" + pRxPort, pPort, pRxPort, Modem.RATE_460800);
        }
        try {
            if (!modem.portOpen()) {
                e(metalog, "Failed open port " + pPort);
                return;
            }
            if (!modem.setICCID()) {
            	e(metalog, "Failed iccid" + pPort);
                return;
            }
            if (!modem.setIMSI()) {
            	e(metalog, "Failed imsi" + pPort);
                return;
            }
            if (!modem.setProvider()) {
            	e(metalog, "Failed set provider " + modem.getProvider());
                return;
            }
            Object[] simcard = DB.getRecord("select RECEIVER, SENDER, TRANSMITER from SIMCARD where ICCID ='" + modem.getICCID() + "'");
            if (simcard == null) {
                d(metalog, "SIMCARD NOT REGISTERED " + modem.getICCID() + " " + modem.getProvider());
                return;
            }
            if (simcard[0].equals("1") && simcard[1].equals("1")) {
                modem.setRunnable(new ReceiverAndSender());
                d(metalog, "Attach ReceiverAndSender modem to port " + modem.toString());
                return;
            }
            if (!modem.setCommand()) {
            	e(metalog, "Failed set Setting, check SERVICE provider=" + modem.getMNC());
                return;
            }
            if (!modem.inquiryBalance()) {
            	e(metalog, "Failed set Balance, command " + modem.COMMAND.toString());
                return;
            }
            ArrayList<String[]> deleted = modem.deleteAllSMS();
            for (String[] sms : deleted) {
                i(metalog, "CLEANUP SMS at " + modem.getICCID() + " > " +  sms[0] + " " + sms[1] + " " + sms[2]);
            }
            if (simcard[2].equals("1")) {
                modem.attach();
                d(metalog, "Attach transmitter to port " + modem.toString());
            }
        } 
        catch (Exception e) {
            e(metalog, e);
        }
    }
    
}
