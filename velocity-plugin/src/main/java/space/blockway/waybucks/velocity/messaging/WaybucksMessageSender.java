package space.blockway.waybucks.velocity.messaging;

import com.google.gson.Gson;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;
import space.blockway.waybucks.shared.WaybucksMessage;
import space.blockway.waybucks.shared.WaybucksMessageType;

import java.util.UUID;

public class WaybucksMessageSender {

    public static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.create("waybucks", "events");

    private final ProxyServer proxy;
    private final Logger logger;
    private final Gson gson;

    public WaybucksMessageSender(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
        this.gson = new Gson();
    }

    /**
     * Sends a WaybucksMessage to a player via their current server's plugin messaging channel.
     *
     * @param playerUuid the UUID of the target player
     * @param message    the message to send
     */
    public void sendToPlayer(UUID playerUuid, WaybucksMessage message) {
        proxy.getPlayer(playerUuid).ifPresentOrElse(player ->
                player.getCurrentServer().ifPresentOrElse(serverConnection -> {
                    byte[] data = gson.toJson(message).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    serverConnection.sendPluginMessage(CHANNEL, data);
                }, () -> logger.warn(
                        "[WaybucksMessageSender] Player {} is not connected to any server; cannot send message of type {}",
                        playerUuid, message.getType()
                )),
                () -> logger.warn(
                        "[WaybucksMessageSender] Player {} is not online; cannot send message of type {}",
                        playerUuid, message.getType()
                )
        );
    }

    /**
     * Convenience overload — constructs a WaybucksMessage and sends it.
     *
     * @param playerUuid the UUID of the target player
     * @param type       the message type
     * @param payload    the JSON payload string
     */
    public void sendToPlayer(UUID playerUuid, WaybucksMessageType type, String payload) {
        sendToPlayer(playerUuid, new WaybucksMessage(type, payload, playerUuid.toString()));
    }
}
