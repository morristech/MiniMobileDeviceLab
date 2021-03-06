/**
 * Copyright 2013 Google Inc. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package co.uk.gauntface.devicelab.appengine;

import com.google.android.gcm.server.Message;
import com.google.android.gcm.server.MulticastResult;
import com.google.android.gcm.server.Result;
import com.google.android.gcm.server.Sender;
import com.google.appengine.labs.repackaged.org.json.JSONArray;
import com.google.appengine.labs.repackaged.org.json.JSONException;
import com.google.appengine.labs.repackaged.org.json.JSONObject;

import co.uk.gauntface.devicelab.appengine.controller.DevicesController;
import co.uk.gauntface.devicelab.appengine.model.Device;
import co.uk.gauntface.devicelab.appengine.utils.C;
import co.uk.gauntface.devicelab.appengine.utils.GPlusTokenInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class GCMServlet extends HttpServlet {

    public GCMServlet() {
        
    }
    
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException  { 
        // pre-flight request processing
        resp.setHeader("Access-Control-Allow-Origin", "*");
        resp.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
    }
    
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String requestUri = req.getRequestURI();
        
        String[] uriParts = requestUri.split("/");
        
        if(uriParts.length < 4) {
            // TODO return relevant error message and http status code
            System.out.println("The URI parts length is less that 3 => "+uriParts.length);
            return;
        }
        
        String action = uriParts[3];
        
        if(action.equals("url")) {
            handlePushMessage(req, resp);
        }
    }
    
    private void handlePushMessage(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = null;
        try {
            String line;
            reader = req.getReader();
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            if(reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
        
        String jsonData = sb.toString();
        
        JSONObject jsonObj = null;
        try {
            jsonObj = new JSONObject(jsonData);
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        if(jsonObj == null) {
            // TODO: Return JSON error here
            return;
        }
        
        String idToken = jsonObj.optString("id_token");
        String userId = GPlusTokenInfo.getUserId(idToken);
        String url = jsonObj.optString("url");
        JSONArray browserOptsJsonArray = jsonObj.optJSONArray("browser_options");
        JSONArray deviceJsonArray = jsonObj.optJSONArray("devices");
        String androidPkg = null;
        
        for(int i = 0; i < browserOptsJsonArray.length(); i++) {
            JSONObject browserOptions = browserOptsJsonArray.optJSONObject(i);
            if(browserOptions != null && browserOptions.optInt("platform") == 0) {
                androidPkg = browserOptions.optString("pkg");
            }
        }
        
        Long deviceId;
        List<Long> deviceIds = new ArrayList<Long>();
        for(int i = 0; i < deviceJsonArray.length(); i++) {
            deviceId = deviceJsonArray.optLong(i);
            if(deviceId != null) {
                deviceIds.add(deviceId);
            }
        }
        
        DevicesController devicesCtrl = new DevicesController();
        List<Device> devices = devicesCtrl.getDevices(userId, deviceIds);
        
        if(devices.size() > 0 && androidPkg != null) {
            List<String> registrationIds = new ArrayList<String>();
            for(Device device : devices) {
                registrationIds.add(device.getGcmId());
            }
            
            Sender sender = new Sender(C.API_KEY);
            Message message = new Message.Builder().addData("data", "{url: \""+url+"\", pkg: \""+androidPkg+"\"}").build();;
            
            try {
                MulticastResult result = sender.send(message, registrationIds, 5);
                List<Result> results = result.getResults();
                System.out.println("GCMServlet: Number of Results = "+results.size());
                for(Result singleResult : results) {
                    String messageId = singleResult.getMessageId();
                    System.out.println("GCMServlet: regId = "+singleResult.getCanonicalRegistrationId());
                    System.out.println("GCMServlet: errorCodeName = "+singleResult.getErrorCodeName());
                    if(messageId != null) {
                        continue;
                    }
                    
                    // Message ID was null which means there was an error
                    String errorCodeName = singleResult.getErrorCodeName();
                    String regId = singleResult.getCanonicalRegistrationId();
                }
            } catch (IOException e) {
                System.out.println("GCMServlet: handlePushMessage() IOException e = "+e.getMessage());
            }
        }
        
        String jsonResponse = "{";
        
        jsonResponse += "}";
        
        resp.addHeader("Access-Control-Allow-Origin", "*");
        resp.setContentType("application/json");
        resp.getWriter().println(jsonResponse);
    }
}
