package space.blockway.waybucks.shared.dto;

public class BalanceDto {
    private String uuid;
    private String username;
    private long balance;
    private long updatedAt;

    public BalanceDto() {}
    public BalanceDto(String uuid, String username, long balance, long updatedAt) {
        this.uuid = uuid; this.username = username;
        this.balance = balance; this.updatedAt = updatedAt;
    }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public long getBalance() { return balance; }
    public void setBalance(long balance) { this.balance = balance; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
