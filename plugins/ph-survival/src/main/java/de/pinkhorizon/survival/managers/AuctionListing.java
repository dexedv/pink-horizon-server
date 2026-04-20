package de.pinkhorizon.survival.managers;

import org.bukkit.inventory.ItemStack;
import java.util.UUID;

public class AuctionListing {

    private final UUID id;
    private final UUID sellerUuid;
    private final String sellerName;
    private final ItemStack item;
    private final long price;
    private final long listedAt;

    public AuctionListing(UUID id, UUID sellerUuid, String sellerName,
                          ItemStack item, long price, long listedAt) {
        this.id         = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.item       = item.clone();
        this.price      = price;
        this.listedAt   = listedAt;
    }

    public UUID     getId()         { return id; }
    public UUID     getSellerUuid() { return sellerUuid; }
    public String   getSellerName() { return sellerName; }
    public ItemStack getItem()      { return item.clone(); }
    public long     getPrice()      { return price; }
    public long     getListedAt()   { return listedAt; }
}
