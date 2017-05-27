/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fx.modem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

/**
 *
 * @author febri
 */
public class Cmd {
    public final String service_code;
    public Cmd(String pName) {
        service_code = pName;
    }
    
    public long   svc_request_timeout = 10000;
    public String svc_request_mode = "";
    public String svc_request_format = "";
    public String svc_request_source = "";
    
    public String svc_response_mode = "";
    public String svc_response_keys_failed = ""; 
    public String svc_response_keys_success = "";
    public String svc_response_format = "";
    public ArrayList<String> svc_response_source = new ArrayList<>();
    /*
     *
     */
    public HashMap<String, String> param;
    public Modem.Output output;

    @Override
    public String toString() {
        return new StringBuilder()
        .append("[").append(service_code)
        .append(", request_mode:").append(svc_request_mode)
        .append(", format:").append(svc_request_format)
        .append(", number:").append(svc_request_source)
        .append(", response_mode:").append(svc_response_mode)
        .append(", format:").append(svc_response_format)
        .append(", number:").append(svc_response_source)
        .append(", success:").append(svc_response_keys_success)
        .append(", failed:") .append(svc_response_keys_failed)
        .append("]").toString();
    }

    public String toString(HashMap<String, String> param) {
        return format(svc_request_format, param);
    }
    
    private String format(String pFormat, HashMap<String, String> pParams) {
        Set<String> keys = pParams.keySet();
        for (String key : keys) {
            pFormat = pFormat.replaceAll(key.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]"), pParams.getOrDefault(key, ""));
        }
        return pFormat;
    }
}
