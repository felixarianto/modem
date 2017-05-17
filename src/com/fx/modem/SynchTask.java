/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fx.modem;

import gnu.io.CommPortIdentifier;
import java.util.Enumeration;
import lib.fx.logger.Log;
import lib.fx.thread.Task;

/**
 *
 * @author febri
 */
public class SynchTask extends Task {

    public SynchTask() {
        interval = 60000;
    }
    

    @Override
    protected String getTag() {
        return "SynchTask";
    }

    @Override
    public void onRunning() {
        String metalog = Long.toHexString(System.currentTimeMillis());
        try {
            Enumeration<CommPortIdentifier> ports = CommPortIdentifier.getPortIdentifiers();
            int size = 0;
            while (ports.hasMoreElements()) {
                checkPort(metalog, ports.nextElement());
                size++;
                sleep(3000);
            }
            d(metalog, "Avaliable " + size + " PORT, 0 SIM, 0 PROVIDE, 0 DEVICE");
        }
        catch (Exception e) {
            e(metalog, e);
        }
    }
    
    protected void checkPort(String metalog, CommPortIdentifier pPort) {
        metalog = metalog + ":" + pPort.getName();
    	if (pPort.getPortType() != CommPortIdentifier.PORT_SERIAL) {
    		d(metalog, "Not serial port " + pPort.getName());
    		return;
    	}
        if (pPort.isCurrentlyOwned()) {
        	d(metalog, "Curently owned port " + pPort.getName());
            return;
        }
        Modem modem = Modem.getAtPort(pPort.getName());
        if (modem != null) {
        	d(metalog, "Skip port " + pPort.getName());
            return;
        }
        modem = new Modem(pPort.getName(), pPort.getName(), Modem.RATE_115200);
        try {
            if (!modem.portOpen()) {
                e(metalog, "Failed open port " + pPort.getName());
                return;
            }
            if (!modem.setICCID()) {
            	e(metalog, "Failed iccid" + pPort.getName());
                return;
            }
            if (!modem.setIMSI()) {
            	e(metalog, "Failed imsi" + pPort.getName());
                return;
            }
            if (!modem.setProvider()) {
            	e(metalog, "Failed set provider");
                return;
            }
            if (!modem.setCommand()) {
            	e(metalog, "Failed set Setting, check SERVICE provider=" + modem.getMNC());
                return;
            }
            if (!modem.inquiryBalance()) {
            	e(metalog, "Failed set Balance");
                return;
            }
            if (modem.attach()) {
                d(metalog, "New attach modem to port " + modem.toString());
            }
        }
        catch (Exception e) {
            e(metalog, e);
        }
    }

    @Override
    protected void e(String pMetalog, Throwable e) {
        Log.e(getTag(), pMetalog, e);
    }

    @Override
    protected void e(String pMetalog, String pMessage) {
        Log.e(getTag(), pMetalog, pMessage);
    }
    
    @Override
    protected void d(String pMetalog, String pMessage) {
        Log.d(getTag(), pMetalog, pMessage);
    }

    @Override
    protected void i(String pMetalog, String pMessage) {
        Log.i(getTag(), pMetalog, pMessage);
    }

    
}
