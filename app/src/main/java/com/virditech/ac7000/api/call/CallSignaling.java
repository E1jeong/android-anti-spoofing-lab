package com.virditech.ac7000.api.call;

import com.google.gson.JsonObject;

final class CallSignaling {
    private CallSignaling() {}

    static Invite parseInvite(JsonObject message) {
        if (!"call.invite".equals(stringValue(message, "type"))) return null;
        String from = stringValue(message, "from");
        String callId = stringValue(message, "callId");
        if (from.isEmpty() || callId.isEmpty()) return null;
        return new Invite(from, callId);
    }

    static SessionDescriptionMessage parseSessionDescription(JsonObject message) {
        String type = stringValue(message, "type");
        if (!"webrtc.offer".equals(type) && !"webrtc.answer".equals(type)) return null;
        String from = stringValue(message, "from");
        String callId = stringValue(message, "callId");
        String sdp = rawStringValue(message, "sdp");
        if (from.isEmpty() || callId.isEmpty() || sdp.trim().isEmpty()) return null;
        return new SessionDescriptionMessage(type, from, callId, sdp);
    }

    static IceMessage parseIce(JsonObject message) {
        if (!"webrtc.ice".equals(stringValue(message, "type"))) return null;
        String from = stringValue(message, "from");
        String callId = stringValue(message, "callId");
        String candidate = stringValue(message, "candidate");
        String sdpMid = stringValue(message, "sdpMid");
        if (from.isEmpty() || callId.isEmpty() || candidate.isEmpty() || sdpMid.isEmpty()
                || !message.has("sdpMLineIndex")) {
            return null;
        }
        try {
            return new IceMessage(
                    from,
                    callId,
                    candidate,
                    sdpMid,
                    message.get("sdpMLineIndex").getAsInt()
            );
        } catch (RuntimeException e) {
            return null;
        }
    }

    static Hangup parseHangup(JsonObject message) {
        if (!"call.hangup".equals(stringValue(message, "type"))) return null;
        String from = stringValue(message, "from");
        String callId = stringValue(message, "callId");
        if (from.isEmpty() || callId.isEmpty()) return null;
        return new Hangup(from, callId);
    }

    static JsonObject createRelay(String type, String to, String callId) {
        JsonObject message = new JsonObject();
        message.addProperty("type", type);
        message.addProperty("to", to);
        message.addProperty("callId", callId);
        return message;
    }

    static JsonObject createSessionDescription(
            String type,
            String to,
            String callId,
            String sdp
    ) {
        JsonObject message = createRelay(type, to, callId);
        message.addProperty("sdp", sdp);
        return message;
    }

    static JsonObject createIce(
            String to,
            String callId,
            String candidate,
            String sdpMid,
            int sdpMLineIndex
    ) {
        JsonObject message = createRelay("webrtc.ice", to, callId);
        message.addProperty("candidate", candidate);
        message.addProperty("sdpMid", sdpMid);
        message.addProperty("sdpMLineIndex", sdpMLineIndex);
        return message;
    }

    private static String stringValue(JsonObject message, String name) {
        return rawStringValue(message, name).trim();
    }

    private static String rawStringValue(JsonObject message, String name) {
        if (!message.has(name) || !message.get(name).isJsonPrimitive()) return "";
        return message.get(name).getAsString();
    }

    static final class Invite {
        final String from;
        final String callId;

        Invite(String from, String callId) {
            this.from = from;
            this.callId = callId;
        }
    }

    static final class SessionDescriptionMessage {
        final String type;
        final String from;
        final String callId;
        final String sdp;

        SessionDescriptionMessage(String type, String from, String callId, String sdp) {
            this.type = type;
            this.from = from;
            this.callId = callId;
            this.sdp = sdp;
        }
    }

    static final class IceMessage {
        final String from;
        final String callId;
        final String candidate;
        final String sdpMid;
        final int sdpMLineIndex;

        IceMessage(
                String from,
                String callId,
                String candidate,
                String sdpMid,
                int sdpMLineIndex
        ) {
            this.from = from;
            this.callId = callId;
            this.candidate = candidate;
            this.sdpMid = sdpMid;
            this.sdpMLineIndex = sdpMLineIndex;
        }
    }

    static final class Hangup {
        final String from;
        final String callId;

        Hangup(String from, String callId) {
            this.from = from;
            this.callId = callId;
        }
    }
}
