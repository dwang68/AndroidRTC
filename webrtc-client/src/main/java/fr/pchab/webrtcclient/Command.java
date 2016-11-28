package fr.pchab.webrtcclient;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by dalinwang on 11/27/16.
 */

interface Command{
    void execute(String peerId, JSONObject payload) throws JSONException;
}
