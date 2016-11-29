/*
 * Copyright 2014 Pierre Chabardes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.pchab.webrtcclient;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

/**
 * Created by dalin.wang on 11/29/16.
 */

class Peer implements SdpObserver, PeerConnection.Observer{
    private final static String TAG = Peer.class.getCanonicalName();
    private PeerConnection pc;
    private String id;
    private int endPoint;

    public PeerConnection getPc() {
        return pc;
    }

    public String getId() {
        return id;
    }

    public int getEndPoint() {
        return endPoint;
    }

    private WebRtcClient webRtcClient;

    @Override
    public void onCreateSuccess(final SessionDescription sdp) {
        // TODO: modify sdp to use pcParams prefered codecs
        try {
            JSONObject payload = new JSONObject();
            payload.put("type", sdp.type.canonicalForm());
            payload.put("sdp", sdp.description);
            webRtcClient.sendMessage(id, sdp.type.canonicalForm(), payload);
            pc.setLocalDescription(Peer.this, sdp);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSetSuccess() {}

    @Override
    public void onCreateFailure(String s) {}

    @Override
    public void onSetFailure(String s) {}

    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {}

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
        if(iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
            webRtcClient.removePeer(id);
            webRtcClient.getmListener().onStatusChanged("DISCONNECTED");
        }
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}

    @Override
    public void onIceCandidate(final IceCandidate candidate) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("label", candidate.sdpMLineIndex);
            payload.put("id", candidate.sdpMid);
            payload.put("candidate", candidate.sdp);
            webRtcClient.sendMessage(id, "candidate", payload);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onAddStream(MediaStream mediaStream) {
        Log.d(TAG,"onAddStream "+mediaStream.label());
        // remote streams are displayed from 1 to MAX_PEER (0 is localStream)
        webRtcClient.getmListener().onAddRemoteStream(mediaStream, endPoint+1);
    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) {
        Log.d(TAG,"onRemoveStream "+mediaStream.label());
        webRtcClient.removePeer(id);
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {}

    @Override
    public void onRenegotiationNeeded() {

    }

    public Peer(WebRtcClient webRtcClient, String id, int endPoint) {
        Log.d(TAG,"new Peer: "+id + " " + endPoint);
        this.pc = webRtcClient.getFactory().createPeerConnection(webRtcClient.getIceServers()
                , webRtcClient.getPcConstraints(), this);
        this.id = id;
        this.endPoint = endPoint;
        this.webRtcClient = webRtcClient;

        pc.addStream(webRtcClient.getLocalMS()); //, new MediaConstraints()

        this.webRtcClient.getmListener().onStatusChanged("CONNECTING");
    }
}