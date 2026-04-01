package space.blockway.waybucks.shared.dto;

public class TransactionDto {
    private long id;
    private String type;          // ADD, TAKE, TRANSFER, SHOP, DAILY, CONVERT
    private String playerUuid;
    private String targetUuid;    // for transfers
    private long amount;
    private String note;
    private long createdAt;

    public TransactionDto() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getPlayerUuid() { return playerUuid; }
    public void setPlayerUuid(String playerUuid) { this.playerUuid = playerUuid; }
    public String getTargetUuid() { return targetUuid; }
    public void setTargetUuid(String targetUuid) { this.targetUuid = targetUuid; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
