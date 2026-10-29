package hu.donut.core;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AuctionHouse {
    public static class Listing {
        public long id;
        public UUID seller;
        public String sellerName;
        public ItemStack item;
        public long price;
        public long expires;
    }

    private final DonutCore pl;
    private final File file;
    private final Map<Long, Listing> map = new LinkedHashMap<>();
    private long next = 1;

    public AuctionHouse(DonutCore pl) {
        this.pl = pl;
        this.file = new File(pl.getDataFolder(), "auction.yml");
    }

    public List<Listing> all() {
        return new ArrayList<>(map.values());
    }

    public Listing get(long id) {
        return map.get(id);
    }

    public long countBy(UUID id) {
        return map.values().stream().filter(l -> l.seller.equals(id)).count();
    }

    public void add(Player p, ItemStack item, long price) {
        Listing l = new Listing();
        l.id = next++;
        l.seller = p.getUniqueId();
        l.sellerName = p.getName();
        l.item = item.clone();
        l.price = price;
        l.expires = System.currentTimeMillis() + pl.getConfig().getLong("ah.hours", 48) * 3_600_000L;
        map.put(l.id, l);
    }

    /** Vásárlás / saját hirdetés visszavonása. Az üzeneteket maga küldi. */
    public void buy(Player buyer, long id) {
        Listing l = map.get(id);
        if (l == null) {
            Util.send(buyer, "<red>Ez a tárgy már nem elérhető.");
            return;
        }
        if (l.seller.equals(buyer.getUniqueId())) {
            map.remove(id);
            Util.giveOrDrop(buyer, l.item.clone());
            Util.send(buyer, "<yellow>Visszavontad a hirdetést, a tárgy visszakerült hozzád.");
            return;
        }
        if (System.currentTimeMillis() > l.expires) {
            Util.send(buyer, "<red>Ez a hirdetés lejárt.");
            return;
        }
        PlayerData bd = pl.store.get(buyer);
        if (bd.money < l.price) {
            Util.send(buyer, "<red>Nincs elég pénzed! Kell: <gold>$" + Util.fmt(l.price));
            return;
        }
        map.remove(id);
        long tax = Math.round(l.price * pl.getConfig().getDouble("ah.tax", 0.05));
        long gain = l.price - tax;
        bd.money -= l.price;
        pl.store.get(l.seller).money += gain;
        Util.giveOrDrop(buyer, l.item.clone());
        Util.send(buyer, "<green>Megvetted: <white>" + l.item.getAmount() + "x " + Util.pretty(l.item.getType())
                + " <green>- <gold>$" + Util.fmt(l.price));
        Player sp = Bukkit.getPlayer(l.seller);
        if (sp != null) {
            Util.send(sp, "<white>" + buyer.getName() + " <green>megvette a tárgyad (" + Util.pretty(l.item.getType())
                    + "): <gold>+$" + Util.fmt(gain) + " <dark_gray>(adó: $" + Util.fmt(tax) + ")");
        }
    }

    public void load() {
        map.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        next = y.getLong("next", 1);
        ConfigurationSection s = y.getConfigurationSection("l");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(k);
            if (c == null) continue;
            try {
                Listing l = new Listing();
                l.id = Long.parseLong(k);
                l.seller = UUID.fromString(c.getString("seller"));
                l.sellerName = c.getString("name", "?");
                l.price = c.getLong("price");
                l.expires = c.getLong("expires");
                l.item = ItemStack.deserializeBytes(Base64.getDecoder().decode(c.getString("item")));
                map.put(l.id, l);
            } catch (Exception e) {
                pl.getLogger().warning("Hibás aukciós bejegyzés: " + k);
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next", next);
        for (Listing l : map.values()) {
            String p = "l." + l.id;
            y.set(p + ".seller", l.seller.toString());
            y.set(p + ".name", l.sellerName);
            y.set(p + ".price", l.price);
            y.set(p + ".expires", l.expires);
            y.set(p + ".item", Base64.getEncoder().encodeToString(l.item.serializeAsBytes()));
        }
        try {
            y.save(file);
        } catch (IOException e) {
            pl.getLogger().severe("Aukció mentési hiba: " + e.getMessage());
        }
    }
}
