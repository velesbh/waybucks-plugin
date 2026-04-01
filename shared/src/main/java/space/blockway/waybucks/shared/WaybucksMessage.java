package space.blockway.waybucks.shared;

import com.google.gson.annotations.SerializedName;

/**
 * Plugin messaging envelope for the waybucks:events channel.
 * Serialized with Gson on both sides.
 */
public class WaybucksMessage {

    @SerializedName("type")
    private WaybucksMessageType type;

    @SerializedName("payload")
    private String payload;

    @SerializedName("targetUuid")
    private String targetUuid;

    @SerializedName("timestamp")
    private long timestamp;

    public WaybucksMessage() {}

    public WaybucksMessage(WaybucksMessageType type, String payload) {
        this.type = type;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }

    public WaybucksMessage(WaybucksMessageType type, String payload, String targetUuid) {
        this(type, payload);
        this.targetUuid = targetUuid;
    }

    public WaybucksMessageType getType() { return type; }
    public String getPayload() { return payload; }
    public String getTargetUuid() { return targetUuid; }
    public long getTimestamp() { return timestamp; }
    public void setType(WaybucksMessageType type) { this.type = type; }
    public void setPayload(String payload) { this.payload = payload; }
    public void setTargetUuid(String targetUuid) { this.targetUuid = targetUuid; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
