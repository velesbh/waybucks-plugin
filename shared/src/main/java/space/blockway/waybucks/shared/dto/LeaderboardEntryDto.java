package space.blockway.waybucks.shared.dto;

public class LeaderboardEntryDto {
    private int rank;
    private String uuid;
    private String username;
    private long balance;

    public LeaderboardEntryDto() {}
    public LeaderboardEntryDto(int rank, String uuid, String username, long balance) {
        this.rank = rank; this.uuid = uuid;
        this.username = username; this.balance = balance;
    }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public long getBalance() { return balance; }
    public void setBalance(long balance) { this.balance = balance; }
}
