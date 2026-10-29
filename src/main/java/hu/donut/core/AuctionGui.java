package hu.donut.core;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuctionGui extends Gui {
    private static final String[] SORTS = {"Legújabb", "Legolcsóbb", "Legdrágább"};
    private final DonutCore pl;
    private final int page;
    private final int sort;
    private final boolean hasNext;
    private final Map<Integer, Long> slotId = new HashMap<>();

    public AuctionGui(DonutCore pl, int page, int sort) {
        this.pl = pl;
        this.page = page;
        this.sort = sort;
        this.inv = Bukkit.createInventory(this, 54, Util.c("<gold>Aukciósház <gray>(" + (page + 1) + ". oldal)"));

        List<AuctionHouse.Listing> all = pl.ah.all();
        switch (sort) {
            case 1 -> all.sort(Comparator.comparingLong((AuctionHouse.Listing l) -> l.price));
            case 2 -> all.sort(Comparator.comparingLong((AuctionHouse.Listing l) -> l.price).reversed());
            default -> all.sort(Comparator.comparingLong((AuctionHouse.Listing l) -> l.id).reversed());
        }
        int per = 45;
        int start = page * per;
        long now = System.currentTimeMillis();
        for (int i = 0; i < per; i++) {
            int idx = start + i;
            if (idx >= all.size()) break;
            AuctionHouse.Listing l = all.get(idx);
            ItemStack it = l.item.clone();
            ItemMeta m = it.getItemMeta();
            List<Component> lore = m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();
            lore.add(Component.empty());
            lore.add(Util.c("<!italic><gray>Ár: <gold>$" + Util.fmt(l.price)));
            lore.add(Util.c("<!italic><gray>Eladó: <white>" + l.sellerName));
            long left = (l.expires - now) / 1000;
            lore.add(Util.c("<!italic><gray>Lejár: " + (left > 0 ? "<white>" + Util.time(left) : "<red>lejárt")));
            lore.add(Util.c("<!italic><yellow>Kattints a vásárláshoz <dark_gray>(saját: visszavonás)"));
            m.lore(lore);
            it.setItemMeta(m);
            inv.setItem(i, it);
            slotId.put(i, l.id);
        }
        this.hasNext = all.size() > start + per;

        for (int i = 45; i < 54; i++) inv.setItem(i, Util.item(Material.BLACK_STAINED_GLASS_PANE, " "));
        if (page > 0) inv.setItem(45, Util.item(Material.ARROW, "<yellow>« Előző oldal"));
        inv.setItem(49, Util.item(Material.HOPPER, "<gold>Rendezés: <white>" + SORTS[sort],
                "<gray>Kattints a váltáshoz", "", "<gray>Eladás: <white>/ah sell <ár>"));
        if (hasNext) inv.setItem(53, Util.item(Material.ARROW, "<yellow>Következő oldal »"));
    }

    @Override
    public void click(InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getClickedInventory() != inv) return;
        Player p = (Player) e.getWhoClicked();
        int slot = e.getSlot();
        if (slot == 45 && page > 0) {
            Gui.later(pl, p, new AuctionGui(pl, page - 1, sort));
        } else if (slot == 53 && hasNext) {
            Gui.later(pl, p, new AuctionGui(pl, page + 1, sort));
        } else if (slot == 49) {
            Gui.later(pl, p, new AuctionGui(pl, 0, (sort + 1) % SORTS.length));
        } else if (slotId.containsKey(slot)) {
            pl.ah.buy(p, slotId.get(slot));
            Gui.later(pl, p, new AuctionGui(pl, page, sort));
        }
    }
}
