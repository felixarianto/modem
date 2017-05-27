/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fx.modem;

import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import java.io.IOException;
import java.io.InputStream;
import lib.fx.encode.HexaBitEncoder;

/**
 *
 * @author febri
 */
public class Modem3G extends Modem {
    
    private final String      mRxport;
    public Modem3G(String pTag, String pPort, String pRxPort, int pBaudRate) {
        super(pTag, pPort, pBaudRate);
        mRxport = pRxPort;
    }

    private SerialPort  mRxSerialPort;
    private InputStream mRxInputStream;
    @Override
    public boolean portOpen() {
        if (!super.portOpen()) {
            return false;
        }
        mConnected = false;
        try {
            i(mMetalog, "Connecting to " + mRxport + " as receiver");
            CommPortIdentifier port = gnu.io.CommPortIdentifier.getPortIdentifier(mRxport);
            if (port == null) {
                return false;
            }
            if (port.isCurrentlyOwned()) {
                return false;
            }
            mRxSerialPort = (gnu.io.SerialPort) port.open(getName(), 100);
            mRxSerialPort.setSerialPortParams(mRate, gnu.io.SerialPort.DATABITS_8, gnu.io.SerialPort.STOPBITS_1, gnu.io.SerialPort.PARITY_NONE);
            mRxSerialPort.setFlowControlMode (gnu.io.SerialPort.FLOWCONTROL_NONE);
            mRxSerialPort.disableReceiveThreshold();
            mRxSerialPort.disableReceiveTimeout();
            mRxSerialPort.setOutputBufferSize(256);
            mRxSerialPort.setInputBufferSize(256);
            mRxSerialPort.notifyOnDataAvailable(true);
            mRxInputStream  = mRxSerialPort.getInputStream();
            i(mMetalog, "Connected receiver at " + mRxport);
            mConnected = true;
        }
        catch (Exception | Error e) {
            e(mMetalog, e);
            portClose();
        }
        return mConnected;
    }

    @Override
    public boolean setICCID() {
        boolean r = false;
        if (send("AT+CRSM = 176,12258,0,0,10")){
            String iccid = onReadEnds("+CRSM", "\"", 3000);
            if (iccid != null) {
                int adx = iccid.indexOf("\"");
                int edx = iccid.indexOf("\"", adx + 1);
                iccid = iccid.substring(adx + 1, edx);
                mICCID = "";
                int size = iccid.length();
                for (int i = 0; i < size; i+=2) {
                    mICCID += iccid.substring(i + 1, i + 2);
                    mICCID += iccid.substring(i, i + 1);
                }
                mICCID = mICCID.replaceAll("F", "");
                if (mICCID.matches("[0-9]+")) {
                    r = true;
                    mMetalog = mICCID;
                    d(mMetalog, "OK ICCID " + mICCID);
                }
            }
        }
        return r;
    }

    @Override
    public void portClose() {
        super.portClose();
        try {mRxInputStream.close();} catch (Exception e) {}
        try {mRxSerialPort.close();} catch (Exception e) {}
    }

    @Override
    public boolean sendUMB(String pCommand, long pTimeout) {
        if (!pCommand.startsWith("*") || !pCommand.endsWith("#")) {
            e(mMetalog, "Invalid format UMB " + pCommand);
            return false;
        }
        if (!sendOK("AT+CUSD=1,\"" + pCommand + "\",15")) {
            return false;
        }
        return true;
    }

    @Override
    public String readUMB(long pTimeout) {
        String umb = null; 
        try {
            umb = readEndsRx("+CUSD:", new String[]{",15", ",0", "+CUSD: 4"}, pTimeout);
            if (umb == null || umb.startsWith("+CUSD: 4")) {
                return null;
            }
            int adx = umb.indexOf("\"");
            int edx = umb.indexOf("\"", adx + 1);
            umb = umb.substring(adx + 1, edx);
            d(mMetalog, "Decode : " + umb);
            umb = HexaBitEncoder.decode(umb);
            d(mMetalog, "Decoded: " + umb);
        } catch (Exception e) {
            e(mMetalog, e);
        }
        return umb;
    }
    
    protected String readEndsRx(String pPrefix, String[] pEnds, long pTimeout) {
        String read = null;
        long t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t0 < pTimeout) {            
            String r = readRx(pTimeout);
            if (r != null) {
                if (r.startsWith("+CME ERROR:") || r.startsWith("+CMS ERROR:") || r.equals("ERROR")) {
                    break;
                }
                if (read == null) {
                    if (r.startsWith(pPrefix)) {
                        read = r;
                    }
                }
                else {
                    read += r;
                }
                if (read != null){
                    boolean end = false;
                    for (String pEnd : pEnds) {
                        if (end = read.endsWith(pEnd)) {
                            break;
                        }
                    }
                    if (end) break;
                }
            }
            else {
                break;
            }
        }
        return read;
    }
    
    private String readRx(long pTimeout) {
        String data   = null;
        int    offset = 0;
        byte[] buffer = new byte[4096];
        int    n;
        try {
            char[] ls = System.getProperty("line.separator").toCharArray();
            p : {
            long t0 = System.currentTimeMillis();
            while (System.currentTimeMillis() - t0 <= pTimeout) {
                int l = 0;
                int x = mRxInputStream.available();
                while (l++ < x) {
                    n = 1;
                    n = mRxInputStream.read(buffer, offset, n);
                    if (n > 0) {
                        offset += n;
                    }
                    boolean eol = false;
                    if (ls.length == 1) {
                        eol = buffer[offset - 1] == ls[0];
                    }
                    else if (ls.length == 2) { //\r\n    
                        eol = buffer[offset - 1] == ls[1] && buffer[offset - 2] == ls[0];
                    }
                    if (eol) {
                        data = new String(buffer, 0, offset - 1).trim();
                        break p;
                    }
                }
            }
            if (data == null && offset > 0) {
                data = new String(buffer, 0, offset - 1).trim();
            }
            }
        }
        catch (IOException e) {
            e(mMetalog, e);
        }
        catch (Exception e) {
            e(mMetalog, e);
        }
        try {d(mMetalog, "readRx: " + data);} catch (Exception e) {e.printStackTrace();}
        return data;
    }

    @Override
    public String getIncomingSMS(long p_timeout) {
        String r = null;
        try {
            String read = readEndsRx("+CMTI:", new String[]{""}, p_timeout);
            if (read == null) {
                return r;
            }
            String[] data = read.split(",");
            if (data.length != 2) {
                w(mMetalog, "Bad incoming: " + read);
            }
            else {
                r = data[1];
            }
        }
        catch (Exception e) {
            e(mMetalog, e);
        }
        return r;
    }
    
    
    
}
