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
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lib.fx.db.DB;
import lib.fx.logger.Log;

/**
 *
 * @author febri
 */
public class Modem extends Thread {
    
    private static final ConcurrentHashMap<String, Modem> PORT_USAGE_MAP = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ArrayList<Modem>> MODEM_MAP = new ConcurrentHashMap<>();
    public static Modem getAtPort(String p_port) {
        return PORT_USAGE_MAP.get(p_port);
    }
    public static ArrayList<Modem> getAtProvider(String p_provider_code) {
        return MODEM_MAP.get(p_provider_code);
    }

    static Modem get(String p_provider, int p_amount) {
        Modem m = null;
        try {
            ArrayList<Modem> list = getAtProvider(p_provider);
            if (list == null) {
                return m;
            }
            for (Modem modem : list) {
                if (!modem.isAvaliableBalance(p_amount)) {
                    continue;
                }
                if (modem.isLocked()) {
                    continue;
                }
                m = modem;
                break;
            }
        }
        catch (Exception e) {
            Log.e("", e);
        }
        return m;
    }

    static void unlock(Modem modem, String metalog) {
        if (modem != null) {
            modem.unlock(metalog);
        }
    }

    static void lock(Modem modem, String metalog) {
        if (modem != null) {
            modem.lock(metalog);
        }
    }

    public static void pending(String metalog, Modem modem, Cmd cmd, HashMap<String, String> param) {
        
        
    }

    public boolean attach() {
        ArrayList<Modem> list = MODEM_MAP.get(mProvider);
        if (list == null) {
            MODEM_MAP.put(mProvider, list = new ArrayList<>());
        }
        list.add(this);
        PORT_USAGE_MAP.put(mPort, this);
        return true;
    }
    public void dettach() {
        ArrayList<Modem> list = MODEM_MAP.get(mProvider);
        if (list != null) {
            list.remove(this);
        }
        PORT_USAGE_MAP.remove(mPort);
    }
    
    public static final int RATE_9600 = 9600;
    public static final int RATE_38400 = 38400;
    public static final int RATE_19200 = 19200;
    public static final int RATE_57600 = 57600;
    public static final int RATE_115200 = 115200;
    public static final int RATE_128000 = 128000;
        
    private boolean mBusy;
    protected String mMetalog;
    private final String mPort;
    private final int    mRate;
    public Modem(String pTag, String pPort, int pBaudRate) {
        mPort = pPort;
        mRate = pBaudRate;
        mMetalog = "";
        setName(pTag);
    }

    public String getPort() {
        return mPort;
    }
    /*
     * LOG
     */
    protected void i(String pMetalog, String pMessage) {
        Log.i(getName(), pMetalog, pMessage.replaceAll("\r", "/r").replaceAll("\n", "/n"));
    }
    protected void d(String pMetalog, String pMessage) {
        Log.d(getName(), pMetalog, pMessage.replaceAll("\r", "/r").replaceAll("\n", "/n"));
    }
    protected void e(String pMetalog, String pMessage) {
        Log.e(getName(), pMetalog, pMessage.replaceAll("\r", "/r").replaceAll("\n", "/n"));
    }
    protected void e(String pMetalog, Throwable e) {
        Log.e(getName(), pMetalog, e);
    }
    /*
     * RUN
     */
    @Override
    public void run() {
    }
    /*
     * CONNECTOR
     */
    private boolean mConnected = false;
    private SerialPort mSerialPort;
    private OutputStream osOutputStream;
    private InputStream isInputStream;
    public boolean portOpen() {
        mBusy = true;
        try {
            if (!mConnected) {
                i(mMetalog, "Connecting to " + mPort);
                CommPortIdentifier port = gnu.io.CommPortIdentifier.getPortIdentifier(mPort);
                if (port == null) {
                    return false;
                }
                    if (port.isCurrentlyOwned()) {
                        return false;
                    }
                    mSerialPort = (gnu.io.SerialPort) port.open(getName(), 100);
                    mSerialPort.setSerialPortParams(mRate, gnu.io.SerialPort.DATABITS_8, gnu.io.SerialPort.STOPBITS_1, gnu.io.SerialPort.PARITY_NONE);
                    mSerialPort.setFlowControlMode(gnu.io.SerialPort.FLOWCONTROL_NONE);
                    mSerialPort.disableReceiveThreshold();
                    mSerialPort.disableReceiveTimeout();
                    mSerialPort.setOutputBufferSize(256);
                    mSerialPort.setInputBufferSize(256);
                    mSerialPort.notifyOnDataAvailable(true);
                    isInputStream  = mSerialPort.getInputStream();
                    osOutputStream = mSerialPort.getOutputStream();
                if      (!sendOK("ATZ", 1000))       {} // Reset Modem
                else if (!sendOK("AT+CMGF=1", 1000)) {} // Set Format
                else {
                    mConnected = true;
                    i(mMetalog, "Connected");
                }
            }
        }
        catch (Exception | Error e) {
            e (mMetalog, e);
            portClose();
        }
        if (!mConnected) e(mMetalog, "Cant Connect to port " + mPort);
        return mConnected;
    }
    public boolean sendOK(String pCommand) {
        return sendOK(pCommand, 1000);
    }
    public boolean sendOK(String pCommand, long pTimeout) {
        String read = null;
        if (send(pCommand)) {
            delay(200);
            read = onReadEnds("OK", "OK", pTimeout);
        }
        return read != null && read.contains("OK");
    }
    private void delay(long p_sleep) {
        try {Thread.sleep(p_sleep);} catch (Exception e) {}
    }
    public void portClose() {
        mConnected = false;
        try {osOutputStream.close();} catch (Exception e) {}
        try {isInputStream.close();} catch (Exception e) {}
        try {mSerialPort.close();} catch (Exception e) {}
        mBusy = false;
        dettach();
    }
    /*
     *
     */
    protected String onReadSMS(long pTimeout) {
        String read = null;
        long t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t0 < pTimeout) {            
            String r = onRead(pTimeout);
            if (r != null) {
                if (read == null) {
                    if (r.startsWith("+CMGL: ") || r.startsWith("+CMGR: ")) {
                        read = r;
                    }
                }
                else {
                    read += "\n" + r;
                }
                if (r.equals("OK") || r.equals("ERROR")) {
                    if (read == null) {
                        read = r;
                    }
                    break;
                }
            }
        }
        return read;
    }
    
    private String onReadSTIN(long pTimeout) {
        String read = null;
        long t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t0 < pTimeout) {            
            String r = onRead(pTimeout);
            if (r != null) {
                if (read == null) {
                    if (r.startsWith("+STIN: ")) {
                        read = r;
                        break;
                    }
                }
            }
        }
        return read;
    }
    protected boolean deleteSMS(String p_id) {
        return sendOK("AT+CMGD=" + p_id, 3000);
    }
    protected ArrayList<String[]> deleteAllSMS() {
        ArrayList<String[]> r = new ArrayList<>();
        onSkipReader();
        long timeout = 30000;
        long t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t0 < timeout) {      
            if (!send("AT+CMGL=\"ALL\"")) {
                continue;
            }
            String sms = onReadSMS(timeout);
            ArrayList<String[]> list = smsparse(sms);
            for (String[] m : list) {
                sendOK("AT+CMGD=" + m[0], 5000);
            }
            r.addAll(list);
            if (sms != null && sms.contains("OK") && list.isEmpty()) {
                break;
            }
            else {
                try {sleep(100);} catch (Exception e){}
            }
        }
        return r;
    }
    private String onReadEnds(String pPrefix, String pEnds, long pTimeout) {
        return onReadEnds(pPrefix, new String[]{pEnds}, pTimeout);
    }
    private String onReadEnds(String pPrefix, String[] pEnds, long pTimeout) {
        String read = null;
        long t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t0 < pTimeout) {            
            String r = onRead(pTimeout);
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
    private void onSkipReader() {
        try {
            byte[] buffer = new byte[4096];
            int len;
            while ( (len = isInputStream.available()) > 0) {
                isInputStream.read(buffer, 0, len);
                d(mMetalog, "onSkip: " + new String(buffer, 0, len));
            }
        }
        catch (Exception e) {
            e(mMetalog, e);
        }
    }
    private String onRead(long pTimeout) {
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
                int x = isInputStream.available();
                while (l++ < x) {
                    n = 1;
                    n = isInputStream.read(buffer, offset, n);
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
        onReceive(data);
        try {d(mMetalog, "onRead: " + data);} catch (Exception e) {e.printStackTrace();}
        return data;
    }
    public boolean sendUMB(String pCommand, long pTimeout) {
        if (!pCommand.startsWith("*") || !pCommand.endsWith("#")) {
            e(mMetalog, "Invalid format UMB " + pCommand);
            return false;
        }
        onSkipReader();
        delay(100);
        if (!sendOK("AT+CUSD=1,\"" + pCommand + "\"")) {
            return false;
        }
        return true;
    }
    public String readUMB(long pTimeout) {
        String umb = null; 
        try {
            umb = onReadEnds("+CUSD:", new String[]{",15", ",0", "+CUSD: 4"}, pTimeout);
            if (umb != null && umb.startsWith("+CUSD: 4")) {
                umb = null;
            }
        } catch (Exception e) {
            e(mMetalog, e);
        }
        return umb;
    }
    public boolean sendSMS(String pNumber, String pText, long pTimeout) {
        boolean r = false;
        onSkipReader();
        if (send("AT+CMGS=\"" + pNumber + "\"" + '\r' + pText + "\u001A")) {
            String read = onReadEnds(">", "OK", pTimeout);
            r = read.contains("CMGS:");
        }
        return r;
    }
    public boolean sendSTK(String pCommand, long pTimeout) {
        boolean r = false;
        if (!pCommand.startsWith("^")) {
            e(mMetalog, "Invalid format STK " + pCommand);
            return r;
        }
        onSkipReader();
        try {Thread.sleep(1000); } catch (InterruptedException e) {}
        String[] arrCommand = pCommand.split("\\^");
        //^1^[No_HP]^6^[10]000^[PIN]^YES
        //^3^1^1^[PIN]
        String sCommand;
        //iFlagResponse, 6=Optional, 3=Input Type, 1=Confirmation
        int iSTIN = 0;
        String read;
        for (int i = 0; i < arrCommand.length; i++) {
            sCommand = arrCommand[i].trim();
            if (i==0){
                if (!sendOK("AT+STGI=0",3000)){ return r;}
                continue;
            }
            switch (iSTIN) {
                case 0: //6=Selection Type First Menu
                case 6: //6=Selection Type
                    if (!sendOK("AT+STGR="+iSTIN+",1,"+sCommand,3000)){ return r;}
                    break;
                case 3: //3=Input Type
                    if (!sendOK("AT+STGR="+iSTIN+",1"+ '\r' + sCommand + "\u001A",3000)){ return r;}
                    break;
                case 1: //1=Confirmation Type
                    if (!sendOK("AT+STGR="+iSTIN+",1,"+(sCommand.equalsIgnoreCase("YES")?1:(Boolean.parseBoolean(sCommand)?1:0)),3000)){ return r;}
                    break;
                default:
                    return false;
            }
            if (i+1!=arrCommand.length){
                read = onReadSTIN(pTimeout);
                if (null==read){return false;}
                try {iSTIN = Integer.parseInt(read.split(":")[1].trim());} catch (Exception exception) {iSTIN=-1;}   
                if (-1==iSTIN){return false;}
                if (!sendOK("AT+STGI="+iSTIN,3000)){ return false;}
            }
        }
        return true;
    }
    public boolean send(String pCommand) {
        boolean send = false;
        try {
            d(mMetalog, "onSend: " + pCommand);
            osOutputStream.write((pCommand + "\r").getBytes());
            osOutputStream.flush();
            send = true;
        }
        catch (Exception e) {
            e(mMetalog, e);
        }
        return send;
    }

    public String umb(String pData) {
        try {
            String[] d = pData.substring("+CUSD: ".length()).split(",", 2);
            if (d.length == 2) {
                int sc = d[1].indexOf("\"") + 1;
                int ec = d[1].length() -1;
                return d[1].substring(sc, ec);
            }
        }
        catch (Exception e) {
        }
        return null;
    }
    /**
     * 
     * @param pData
     * @return array 0: id, 1:source, 2:text 
     */
    public ArrayList<String[]> smsparse(String pData) {
        ArrayList<String[]> list = new ArrayList<>();
        try {
            String[] data = pData.split("\\+CMGL: ");
            for (String sms : data) {
                if (sms.length() > 0) {
                    String[] a = sms.split("\n", 2);
                    String[] s = a[0].split(",", 4);
                    String[] m = new String[5];
                    m[0] = s[0];
                    m[1] = s[2].replaceAll("\"", "");
                    m[2] = s[3].replaceAll("\"", "");
                    m[2] = a[1];
                    list.add(m);
                }
            }
        }
        catch (Exception e) {
        }
        if (!list.isEmpty()) {
            d(mMetalog, "SMS List size " + list.size());
        } 
        return list;
    } 
    private String mIMSI = null;
    public String getIMSI() {
        return mIMSI;
    }
    public String getMNC() {
        return mIMSI.substring(3, 5);
    }
    public boolean setIMSI() {
        onSkipReader();
        send("AT+CIMI");
        long t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t0 <= 3000) {
            mIMSI = onRead(3000);
            try {
                if (mIMSI != null && mIMSI.startsWith("510")) {//Indonesia
                    d(mMetalog, "OK IMSI " + mIMSI);
                    return true;
                }
            }
            catch (Exception e) {
                e(mMetalog, e);
            }
        }
        return false;
    }
    private String mProvider;
    public String getProvider() {
        return mProvider;
    }
    
    /**
     * This is actually a MNC number
     * @return  
     */
    public boolean setProvider() {
        try {
            mProvider = mIMSI.substring(3, 5);
            mProvider = DB.getRecord("select CODE from PROVIDER where PROVIDER_ID='" + mProvider + "'")[0] + "";
            return true;
        }
        catch (Exception e) {
        }
        return false;
    }
    private String mICCID = null;
    public String getICCID() {
        return mICCID;
    }
    public boolean setICCID() {
        boolean r = false;
        if (send("AT+CCID")){
            mICCID = onReadEnds("+CCID: \"", "\"", 3000);
            if (mICCID != null) {
                mICCID = mICCID.replaceAll("\\+CCID: ", "").replaceAll("\"", "");
                if (mICCID.matches("[0-9]+")) {
                    r = true;
                    mMetalog = mICCID;
                    d(mMetalog, "OK ICCID " + mICCID);
                }
            }
        }
        return r;
    }
    public final boolean isConnected() {
        return mConnected;
    }
    public static class Output extends HashMap<String, String> {
        public static final String SUCCESS = "Success";
        public static final String FAILED  = "Failed";
        public static final String PENDING = "Pending";
        protected void status(String p_status) {
            put("STATUS", SUCCESS);
        }
        public String getStatus() {
            return getOrDefault("STATUS", "");
        }
        public boolean isPending() {
            return getOrDefault("STATUS", "").equals(PENDING);
        }
        public boolean isSuccess() {
            return getOrDefault("STATUS", "").equals(SUCCESS);
        }
        public boolean isFailed() {
            return getOrDefault("STATUS", "").equals(FAILED);
        }
    }

    public boolean exec(String p_metalog, Cmd cmd, HashMap<String, String> p_param) {
        boolean result = false;
        try {
            p :{
                if (cmd.svc_request_timeout < 60000) {
                    cmd.svc_request_timeout = 60000;
                }
                if (cmd.svc_request_mode.equalsIgnoreCase("umb")) {
                    boolean send = sendUMB(format(cmd.svc_request_format, p_param), cmd.svc_request_timeout);
                    if (!send) {
                        e(p_metalog, "Umb Not Sent");
                        break p;
                    }
                }
                else if (cmd.svc_request_mode.equalsIgnoreCase("sms")) {
                    boolean send = sendSMS(cmd.svc_request_source, format(cmd.svc_request_format, p_param), cmd.svc_request_timeout);
                    if (!send) {
                        e(p_metalog, "SMS Not Sent");
                        break p;
                    }
                }
                else if (cmd.svc_request_mode.equalsIgnoreCase("stk")) {
                    boolean send = sendSTK(format(cmd.svc_request_format, p_param), 60000 * 2);
                    if (!send) {
                        e(p_metalog, "STK Not Sent");
                        if (!sendOK("AT+CFUN=1", 3000)) {
                            e(p_metalog, "Reset Failed");
                        }
                        break p;
                    }
                }
                result = true;
            }
        }
        catch (Exception e) {
            e(p_metalog, e);
        }
        return result;
    }
    public Output getOutput(String p_metalog, Cmd cmd, HashMap<String, String> p_param) {
        Output output = new Output();
        try {
            String desc = "";
            p :{
                if (cmd.svc_response_mode.equalsIgnoreCase("umb")) {
                    String response = umb(readUMB(cmd.svc_request_timeout));
                    HashMap<String, String> m = parse(cmd.svc_response_format, response);
                    if (m != null) {
                        output.putAll(m);
                    }
                    if (match(response, format(cmd.svc_response_keys_success, p_param).split(","))){
                        output.status(Output.SUCCESS);
                        break p;
                    }
                    if (match(response, format(cmd.svc_response_keys_failed, p_param).split(","))){
                        output.status(Output.FAILED);
                        break p;
                    }
                }
                else if (cmd.svc_response_mode.equalsIgnoreCase("sms")) {
                    boolean rec = true;
                    long t0 = System.currentTimeMillis();
                    while (System.currentTimeMillis() - t0 <= cmd.svc_request_timeout) {
                        if (rec) {
                            rec = false;
                            if (!send("AT+CMGL=\"REC UNREAD\"")) {
                                e(p_metalog, "Cant Read SMS");
                                break p;
                            }
                        }
                        String sms   = onReadSMS(cmd.svc_request_timeout);
                               desc += sms;
                        ArrayList<String[]> list = smsparse(sms);
                        for (String[] m : list) {
                            if      (m == null) {}
                            else if (!cmd.svc_response_source.contains(mdn(m[1]).toUpperCase())) {}
                            else {
                                String txt = m[2];
                                HashMap<String, String> map = parse(cmd.svc_response_format, txt);
                                if (map != null) {
                                    output.putAll(map);
                                }
                                if (match(txt, format(cmd.svc_response_keys_success, p_param).split(","))){
                                    output.status(Output.SUCCESS);
                                    if     (sendOK("AT+CMGD=" + m[0], 5000)) {}
                                    else if(sendOK("AT+CMGD=" + m[0], 5000)) {}
                                    break p;
                                }
                                if (match(txt, format(cmd.svc_response_keys_failed, p_param).split(","))){
                                    output.status(Output.FAILED);
                                    if     (sendOK("AT+CMGD=" + m[0], 5000)) {}
                                    else if(sendOK("AT+CMGD=" + m[0], 5000)) {}
                                    break p;
                                }
                            }
                            if (m != null) {
                                if     (sendOK("AT+CMGD=" + m[0], 1000)) {}
                                else if(sendOK("AT+CMGD=" + m[0], 1000)) {}
                            }
                        }
                        rec = sms == null || sms.contains("OK");
                    }
                }
            }
            if (!output.isSuccess() && !output.isFailed()) {
                output.put("DESC", desc);
            }
        }
        catch (Exception e) {
            e(p_metalog, e);
        }
        return output;
    }

    private final HashMap<String, Long> LOCK = new HashMap<>();
    public void lock(String metalog) {
        LOCK.put(metalog, System.currentTimeMillis());
    }
    public void unlock(String metalog) {
        LOCK.remove(metalog);
    }
    public boolean isLocked() {
        return !LOCK.isEmpty();
    }
    
    public boolean isAvaliableBalance(int p_amount) {
        return BALANCE.getOrDefault(p_amount + "", 0D) >= 1D;
    }
    public static class Balance extends ConcurrentHashMap<String, Double> {
        public static final String K1 = "1000";
        public static final String K2 = "2000";
        public static final String K5 = "5000";
        public static final String K10  = "10000";
        public static final String K20  = "20000";
        public static final String K30  = "30000";
        public static final String K50  = "50000";
        public static final String K100 = "100000";
    }
    public final Balance BALANCE = new Balance();
    public static class Command extends ConcurrentHashMap<String, Cmd>{
        public static final String GET_K1 = "BALANCE_1";
        public static final String GET_K2 = "BALANCE_2";
        public static final String GET_K5 = "BALANCE_5";
        public static final String GET_K10 = "BALANCE_10";
        public static final String GET_K20 = "BALANCE_20";
        public static final String GET_AMOUNT = "BALANCE_AMT";
    }
    public final Command COMMAND = new Command();
    public boolean setCommand() {
        ArrayList<Object[]> records = DB.getRecords("SELECT SERVICE_CODE, REQ_MODE, REQ_FORMAT, REQ_SOURCE, RSP_MODE"
            + ", RSP_KEY_FAILED, RSP_KEY_SUCCESS, RSP_FORMAT, RSP_SOURCE, TIMEOUT " 
            + " FROM SERVICE where EC_DATE > NOW() AND PROVIDER_ID='" + mProvider + "'");
        for (Object[] record : records) {
            Cmd cmd = new Cmd(record[0] + "");
            cmd.svc_request_mode = record[1] + "";
            cmd.svc_request_format = record[2] + "";
            cmd.svc_request_source = record[3] + "";
            cmd.svc_response_mode  = record[4] + "";
            cmd.svc_response_keys_failed  = record[5] + "";
            cmd.svc_response_keys_success = record[6] + "";
            cmd.svc_response_format       = record[7] + "";
            cmd.svc_response_source.addAll(Arrays.asList((record[8] + "").split(",")));
            COMMAND.put(cmd.service_code, cmd);
        }
        return !COMMAND.isEmpty();
    }
    /**
     * Take Inquiry balance from the AIR
     * based on setting provider command
     * @return 
     */
    public boolean inquiryBalance() {
        BALANCE.clear();
        HashMap<String, String> param = new HashMap<>();
        HashMap<String, String> output;
        Set<String> keys = COMMAND.keySet();
        for (String key : keys) {
            if (!key.startsWith("SALDO")){
                continue;
            }
            if (BALANCE.get(key.replaceAll("SALDO_","")) != null){
                continue;
            }
            Cmd cmd = COMMAND.get(key);
            if (cmd == null) {
                d(mMetalog, "Command Not Found : " + key);
                continue;
            }
            if (cmd.svc_request_mode == null || cmd.svc_request_mode.equals("")) {
                continue;
            }
            for (int i = 1; i < 5; i++) {
                if (exec(mMetalog, cmd, param)) {
                    break;
                }
                try {Thread.sleep(3000 * i);} catch (Exception e){}
            }
            output = getOutput(mMetalog, cmd, param );
            if (output.isEmpty()) {
                d(mMetalog, "Execution Empty Result " + cmd.toString(param));
                continue;
            }
            Set<String> output_keys = output.keySet();
            for (String sKey : output_keys) {
                try {
                    if(sKey.toUpperCase().startsWith("U")) {
                        BALANCE.put(sKey.toUpperCase().replace("U", ""), Double.valueOf(output.get(sKey)));
                    }
                    else if(sKey.toUpperCase().startsWith("A")) {
                        double amount    = Double.valueOf(sKey.toUpperCase().replace("A", ""));
                        Double avaliable = Double.valueOf(output.get(sKey)) / amount;
                        BALANCE.put(amount + "", avaliable);
                    }
                }
                catch (Exception e) {
                    e(mMetalog, e);
                }
            }
        }
        return !BALANCE.isEmpty();
    }
   /*
    *
    */
    public String format(String pFormat, HashMap<String, String> pParams) {
        Set<String> keys = pParams.keySet();
        for (String key : keys) {
            pFormat = pFormat.replaceAll(key.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]"), pParams.getOrDefault(key, ""));
        }
        return pFormat;
    }
    private ArrayList<String[]> formats(String pFormat) {
        ArrayList<String[]> l = new ArrayList<>();
        String[] formats = pFormat.split("\\*");
        for (String format : formats) {
            String[] f = split(format);
            if (f != null) {
                l.add(f);
            }
        }
        return l;
    }
    private String[] split(String pFormat) {
        try {
            int sc = pFormat.indexOf("[");
            int ec = pFormat.indexOf("]");
            String[] f = new String[3];
            f[0] = pFormat.substring(0, sc);
            f[1] = pFormat.substring(sc + 1, ec);
            f[2] = pFormat.substring(ec + 1);
            return f;
        }
        catch (Exception e) {
        }
        return null;
    }
    public HashMap<String, String> parse(String pFormat, String pData) {
        HashMap<String, String> m = new HashMap<>();
        ArrayList<String[]> formats = formats(pFormat);
        for (String[] format : formats) {
            try {
                int sc = pData.indexOf(format[0]);
                if (sc != -1) {
                    sc = sc + format[0].length();
                    int ec = format[2].equals("") ? pData.length() : pData.indexOf(format[2], sc);
                    m.put(format[1], pData.substring(sc, ec).replaceAll("OK", "").trim());
                }
            }
            catch (Exception e) {
            }
        }
        if (!m.isEmpty()) d(mMetalog, m.toString());
        return m;
    }
    
    public boolean match(String pData, String[] pPattern) {
        boolean match = false;
        for (String pattern : pPattern) {
            try {
                boolean  ok = false;
                String[] or = pattern.split("\\|");
                for (String o : or) {
                    if (pData.toUpperCase().contains(o.toUpperCase())) {
                        ok = true;
                    }
                }
                match = ok;
                if (!match) {
                    break;
                }
            }
            catch (Exception e) {}
        }
        return match;
    }
    /*
     * Pending Handler
     */
    private void onReceive(String pData) {
        try {
            if (pData == null) {}
            else if (pData.startsWith("+CMGL:")) {
//                onReceiveSMS(pData);
            }
            else if (pData.startsWith("+CMS ERROR: ")) {
                onReceiveERROR(pData);
            }
            else if (pData.startsWith("+CME ERROR: ")) {
                onReceiveERROR(pData);
            }
        }
        catch (Exception e) {
            e(mMetalog, e);
        }
    }
    public void onReceiveERROR(String pData) {
        if (pData.trim().endsWith("310") || pData.trim().endsWith("10")) {//Sim Card Not Found
        }
    }
    private String mdn(String pValue) {
        try {
            if (pValue.startsWith("+62")) {
                pValue = pValue.substring(3);
            }
            else if (pValue.startsWith("62")) {
                pValue = pValue.substring(2);
            }
            else if (pValue.startsWith("0")) {
                pValue = pValue.substring(1);
            }
        }
        catch (Exception e) {
        }
        return pValue;
    }

    @Override
    public String toString() {
        return "provider: ".concat(mProvider).concat(" balance: ").concat(BALANCE.toString()).concat(" command: ").concat(COMMAND.keySet().toString()); //To change body of generated methods, choose Tools | Templates.
    }
    
    
}
