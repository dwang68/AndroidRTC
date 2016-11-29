package fr.pchab.webrtcclient;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedList;

import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.github.nkzawa.emitter.Emitter;
import org.json.JSONException;
import org.json.JSONObject;

import android.opengl.EGLContext;
import android.util.Log;
import org.webrtc.*;

public class WebRtcClient {
    private final static String TAG = WebRtcClient.class.getCanonicalName();
    private final static int MAX_PEER = 2;
    private boolean[] endPoints = new boolean[MAX_PEER];
    private PeerConnectionFactory factory;
    private HashMap<String, Peer> peers = new HashMap<>();
    private LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<>();
    private PeerConnectionParameters pcParams;
    private MediaConstraints pcConstraints = new MediaConstraints();
    private MediaStream localMS;
    private VideoSource videoSource;
    private RtcListener mListener;
    private Socket client;


    private class CreateOfferCommand implements Command{
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Log.d(TAG,"CreateOfferCommand");
            Peer peer = peers.get(peerId);
            peer.getPc().createOffer(peer, pcConstraints);
        }
    }

    private class CreateAnswerCommand implements Command{
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Log.d(TAG,"CreateAnswerCommand");
            Peer peer = peers.get(peerId);
            SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(payload.getString("type")),
                    payload.getString("sdp")
            );
            peer.getPc().setRemoteDescription(peer, sdp);
            peer.getPc().createAnswer(peer, pcConstraints);
        }
    }

    private class SetRemoteSDPCommand implements Command{
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Log.d(TAG,"SetRemoteSDPCommand");
            Peer peer = peers.get(peerId);
            SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(payload.getString("type")),
                    payload.getString("sdp")
            );
            peer.getPc().setRemoteDescription(peer, sdp);
        }
    }

    private class AddIceCandidateCommand implements Command{
        public void execute(String peerId, JSONObject payload) throws JSONException {
            Log.d(TAG,"AddIceCandidateCommand");
            PeerConnection pc = peers.get(peerId).getPc();
            if (pc.getRemoteDescription() != null) {
                IceCandidate candidate = new IceCandidate(
                        payload.getString("id"),
                        payload.getInt("label"),
                        payload.getString("candidate")
                );
                pc.addIceCandidate(candidate);
            }
        }
    }

    /**
     * Send a message through the signaling server
     *
     * @param to id of recipient
     * @param type type of message
     * @param payload payload of message
     * @throws JSONException
     */
    public void sendMessage(String to, String type, JSONObject payload) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("to", to);
        message.put("type", type);
        message.put("payload", payload);
        client.emit("message", message);
    }

    private class MessageHandler {
        private HashMap<String, Command> commandMap;

        private MessageHandler() {
            this.commandMap = new HashMap<>();
            commandMap.put("init", new CreateOfferCommand());
            commandMap.put("offer", new CreateAnswerCommand());
            commandMap.put("answer", new SetRemoteSDPCommand());
            commandMap.put("candidate", new AddIceCandidateCommand());
        }

        private Emitter.Listener onMessage = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject data = (JSONObject) args[0];
                try {
                    String from = data.getString("from");
                    String type = data.getString("type");
                    JSONObject payload = null;
                    if(!type.equals("init")) {
                        payload = data.getJSONObject("payload");
                    }
                    // if peer is unknown, try to add him
                    if(!peers.containsKey(from)) {
                        // if MAX_PEER is reach, ignore the call
                        int endPoint = findEndPoint();
                        if(endPoint != MAX_PEER) {
                            Peer peer = addPeer(from, endPoint);
                            peer.getPc().addStream(localMS);
                            commandMap.get(type).execute(from, payload);
                        }
                    } else {
                        commandMap.get(type).execute(from, payload);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        };

        private Emitter.Listener onId = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String id = (String) args[0];
                mListener.onCallReady(id);
            }
        };
    }

    private Peer addPeer(String id, int endPoint) {
        Peer peer = new Peer(this, id, endPoint);
        peers.put(id, peer);

        endPoints[endPoint] = true;
        return peer;
    }

    protected void removePeer(String id) {
        Peer peer = peers.get(id);
        mListener.onRemoveRemoteStream(peer.getEndPoint());
        peer.getPc().close();
        peers.remove(peer.getId());
        endPoints[peer.getEndPoint()] = false;
    }

    public WebRtcClient(RtcListener listener, String host, PeerConnectionParameters params, EGLContext mEGLcontext) {
        mListener = listener;
        pcParams = params;
        PeerConnectionFactory.initializeAndroidGlobals(listener, true, true,
                params.videoCodecHwAcceleration, mEGLcontext);
        factory = new PeerConnectionFactory();
        MessageHandler messageHandler = new MessageHandler();

        try {
            client = IO.socket(host);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        client.on("id", messageHandler.onId);
        client.on("message", messageHandler.onMessage);
        client.connect();

        iceServers.add(new PeerConnection.IceServer("stun:23.21.150.121"));
        iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));

        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        pcConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        pcConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
    }

    /**
     * Call this method in Activity.onPause()
     */
    public void onPause() {
        if(videoSource != null) videoSource.stop();
    }

    /**
     * Call this method in Activity.onResume()
     */
    public void onResume() {
        if(videoSource != null) videoSource.restart();
    }

    /**
     * Call this method in Activity.onDestroy()
     */
    public void onDestroy() {
        for (Peer peer : peers.values()) {
            peer.getPc().dispose();
        }
        videoSource.dispose();
        factory.dispose();
        client.disconnect();
        client.close();
    }

    private int findEndPoint() {
        for(int i = 0; i < MAX_PEER; i++) if (!endPoints[i]) return i;
        return MAX_PEER;
    }

    /**
     * Start the client.
     *
     * Set up the local stream and notify the signaling server.
     * Call this method after onCallReady.
     *
     * @param name client name
     */
    public void start(String name){
        setCamera();
        try {
            JSONObject message = new JSONObject();
            message.put("name", name);
            client.emit("readyToStream", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void setCamera(){
        localMS = factory.createLocalMediaStream("ARDAMS");
        if(pcParams.videoCallEnabled){
            MediaConstraints videoConstraints = new MediaConstraints();
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxHeight", Integer.toString(pcParams.videoHeight)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxWidth", Integer.toString(pcParams.videoWidth)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("maxFrameRate", Integer.toString(pcParams.videoFps)));
            videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair("minFrameRate", Integer.toString(pcParams.videoFps)));

            videoSource = factory.createVideoSource(getVideoCapturer(), videoConstraints);
            localMS.addTrack(factory.createVideoTrack("ARDAMSv0", videoSource));
        }

        AudioSource audioSource = factory.createAudioSource(new MediaConstraints());
        localMS.addTrack(factory.createAudioTrack("ARDAMSa0", audioSource));

        mListener.onLocalStream(localMS);
    }

    private VideoCapturer getVideoCapturer() {
        String frontCameraDeviceName = VideoCapturerAndroid.getNameOfFrontFacingDevice();
        return VideoCapturerAndroid.create(frontCameraDeviceName);
    }

    public PeerConnectionFactory getFactory() {
        return factory;
    }

    public RtcListener getmListener() {
        return mListener;
    }

    public PeerConnectionParameters getPcParams() {
        return pcParams;
    }

    public MediaConstraints getPcConstraints() {
        return pcConstraints;
    }

    public LinkedList<PeerConnection.IceServer> getIceServers() {
        return iceServers;
    }

    public Socket getClient() {
        return client;
    }

    public MediaStream getLocalMS() {
        return localMS;
    }
}
