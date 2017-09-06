package net.instant.hooks;

import java.nio.ByteBuffer;
import net.instant.api.ClientConnection;
import net.instant.api.RequestHook;

public abstract class HookAdapter implements RequestHook {

    public void onOpen(ClientConnection req) {}

    public void onInput(ClientConnection req, ByteBuffer data) {}

    public void onInput(ClientConnection req, String data) {}

    public void onClose(ClientConnection req, boolean normal) {}

    public void onError(ClientConnection req, Exception exc) {}

}
