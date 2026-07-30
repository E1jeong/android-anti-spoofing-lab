package com.virditech.ac7000.api.call;

import com.google.gson.JsonObject;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public final class CallSignalingTest {
    @Test public void parsesValidCallInvite() {
        JsonObject message = new JsonObject();
        message.addProperty("type", "call.invite");
        message.addProperty("from", "operator-test-01");
        message.addProperty("callId", "call-1");

        CallSignaling.Invite invite = CallSignaling.parseInvite(message);

        assertNotNull(invite);
        assertEquals("operator-test-01", invite.from);
        assertEquals("call-1", invite.callId);
    }

    @Test public void rejectsInviteWithoutSenderOrCallId() {
        JsonObject message = new JsonObject();
        message.addProperty("type", "call.invite");

        assertNull(CallSignaling.parseInvite(message));
    }

    @Test public void createsTargetedCallMessage() {
        JsonObject message = CallSignaling.createRelay(
                "call.accept",
                "operator-test-01",
                "call-1"
        );

        assertEquals("call.accept", message.get("type").getAsString());
        assertEquals("operator-test-01", message.get("to").getAsString());
        assertEquals("call-1", message.get("callId").getAsString());
    }

    @Test public void parsesOfferAndIce() {
        String offerSdp = "v=0\r\n";
        JsonObject offer = CallSignaling.createSessionDescription(
                "webrtc.offer",
                "device-test-01",
                "call-1",
                offerSdp
        );
        offer.addProperty("from", "operator-test-01");
        CallSignaling.SessionDescriptionMessage parsedOffer =
                CallSignaling.parseSessionDescription(offer);

        assertNotNull(parsedOffer);
        assertEquals(offerSdp, parsedOffer.sdp);

        JsonObject ice = CallSignaling.createIce(
                "device-test-01",
                "call-1",
                "candidate:1",
                "0",
                0
        );
        ice.addProperty("from", "operator-test-01");
        CallSignaling.IceMessage parsedIce = CallSignaling.parseIce(ice);

        assertNotNull(parsedIce);
        assertEquals("candidate:1", parsedIce.candidate);
        assertEquals(0, parsedIce.sdpMLineIndex);
    }
}
