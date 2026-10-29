package hu.donut.core;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class ShopGui extends Gui {
    private final DonutCore pl;
    private final String root;      // "shop" vagy "shardshop"
    private final String category;  // null = főmenü
    private final boolean shards;
    private final Map<Integer, String> slotCat = new HashMap<>();
    private final Map<Integer, Material> slotItem = new HashMap<>();
    private final Map<Integer, Long> slotPrice = new HashMap<>();

    public ShopGui(DonutCore pl, String root, String category) {
        this.pl = pl;
        this.root = root;
        this.category = category;
        this.shards = root.equals("shardshop");
        String title = shards ? "<aqua>Shard bolt" : "<gold>Bolt";
        ConfigurationSection cats = pl.getConfig().getConfigurationSection(root + ".categories");

        if (category == null) {
            inv = Bukkit.createInventory(this, 27, Util.c(title));
            fill(Material.BLACK_STAINED_GLASS_PANE);
            int slot = 10;
            if (cats != null) {
                for (String key : cats.getKeys(false)) {
                    ConfigurationSection cs = cats.getConfigurationSection(key);
                    if (cs == null) continue;
                    Material icon = Material.matchMaterial(cs.getString("icon", "CHEST"));
                    if (icon == null) icon = Material.CHEST;
                    inv.setItem(slot, Util.item(icon, cs.getString("name", key), "<gray>Kattints a megnyitáshoz"));
                    slotCat.put(slot, key);
                    slot++;
                    if (slot % 9 == 8) slot += 2;
                    if (slot > 25) break;
                }
            }
        } else {
            inv = Bukkit.createInventory(this, 54, Util.c(title + " <dark_gray>» <white>" + category));
            ConfigurationSection items = cats == null ? null : cats.getConfigurationSection(category + ".items");
            int slot = 0;
            String cur = shards ? "<aqua>✦ shard" : "<gold>$";
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    if (slot >= 45) break;
                    Material m = Material.matchMaterial(key);
                    if (m == null || !m.isItem()) continue;
                    long price = items.getLong(key);
                    inv.setItem(slot, Util.item(m, "<white>" + Util.pretty(m),
                            "<gray>Ár: " + (shards ? "<aqua>" + Util.fmt(price) + " ✦" : "<gold>$" + Util.fmt(price)) + " <gray>/ db",
                            "",
                            "<yellow>Bal klikk: <white>1 db",
                            "<yellow>Jobb klikk: <white>16 db",
                            "<yellow>Shift+bal: <white>64 db"));
                    slotItem.put(slot, m);
                    slotPrice.put(slot, price);
                    slot++;
                }
            }
            for (int i = 45; i < 54; i++) inv.setItem(i, Util.item(Material.BLACK_STAINED_GLASS_PANE, " "));
            inv.setItem(49, Util.item(Material.ARROW, "<red>Vissza"));
        }
    }

    @Override
    public void click(InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getClickedInventory() != inv) return;
        Player p = (Player) e.getWhoClicked();
        int slot = e.getSlot();

        if (slotCat.containsKey(slot)) {
            Gui.later(pl, p, new ShopGui(pl, root, slotCat.get(slot)));
            return;
        }
        if (category != null && slot == 49) {
            Gui.later(pl, p, new ShopGui(pl, root, null));
            return;
        }
        Material m = slotItem.get(slot);
        if (m == null) return;
        int qty = switch (e.getClick()) {
            case LEFT -> 1;
            case RIGHT -> 16;
            case SHIFT_LEFT -> 64;
            default -> 0;
        };
        if (qty == 0) return;
        long cost = slotPrice.get(slot) * qty;
        PlayerData d = pl.store.get(p);
        if (shards) {
            if (d.shards < cost) {
                Util.send(p, "<red>Nincs elég shardod! Kell: <aqua>" + Util.fmt(cost) + " ✦");
                return;
            }
            d.shards -= cost;
        } else {
            if (d.money < cost) {
                Util.send(p, "<red>Nincs elég pénzed! Kell: <gold>$" + Util.fmt(cost));
                return;
            }
            d.money -= cost;
        }
        Util.giveOrDrop(p, new ItemStack(m, qty));
        Util.send(p, "<green>Megvettél <white>" + qty + "x " + Util.pretty(m) + " <green>- <gray>"
                + (shards ? Util.fmt(cost) + " ✦" : "$" + Util.fmt(cost)));
    }
}
