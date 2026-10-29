package dev.sayitem.client;

import java.util.UUID;

/** Pairing is automatic and ephemeral: no Minecraft folders or shared config files. */
public final class ClientSettings {
    public final int port = 8765;
    public final String token = UUID.randomUUID().toString() + UUID.randomUUID();
    public final String microphoneName = "";
}
