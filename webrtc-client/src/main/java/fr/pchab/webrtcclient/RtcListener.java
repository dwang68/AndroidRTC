package fr.pchab.webrtcclient;

/**
 * Created by dalinwang on 11/27/16.
 */

import org.webrtc.MediaStream;

/**
 * Implement this interface to be notified of events.
 */
public interface RtcListener{
    void onCallReady(String callId);

    void onStatusChanged(String newStatus);

    void onLocalStream(MediaStream localStream);

    void onAddRemoteStream(MediaStream remoteStream, int endPoint);

    void onRemoveRemoteStream(int endPoint);
}
