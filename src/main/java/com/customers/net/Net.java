package com.customers.net;

import com.customers.client.ClientNet;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class Net {
    private Net() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(OpenScreenPayload.TYPE, OpenScreenPayload.CODEC, ClientNet::handleOpen);
        r.playToServer(ActionPayload.TYPE, ActionPayload.CODEC, ServerActions::handleAction);
    }
}
