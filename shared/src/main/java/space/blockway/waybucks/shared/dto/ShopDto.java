package space.blockway.waybucks.shared.dto;

import java.util.List;

public class ShopDto {
    private String id;
    private String name;
    private String description;
    private long price;
    private List<String> commands;       // placeholders: {player}, {uuid}
    private boolean enabled;
    private int stock;                   // -1 = unlimited
    private int maxPerPlayer;            // -1 = unlimited
    private long createdAt;
    private String createdBy;
    private String iconMaterial;         // e.g. DIAMOND

    public ShopDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }
    public List<String> getCommands() { return commands; }
    public void setCommands(List<String> commands) { this.commands = commands; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
    public int getMaxPerPlayer() { return maxPerPlayer; }
    public void setMaxPerPlayer(int maxPerPlayer) { this.maxPerPlayer = maxPerPlayer; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getIconMaterial() { return iconMaterial; }
    public void setIconMaterial(String iconMaterial) { this.iconMaterial = iconMaterial; }
}
