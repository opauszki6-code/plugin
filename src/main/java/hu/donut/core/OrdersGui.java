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

public class OrdersGui extends Gui {
    private final DonutCore pl;
    private final int page;
    private final boolean hasNext;
    private final Map<Integer, Long> slotId = new HashMap<>();

    public OrdersGui(DonutCore pl, int page, Player viewer) {
        this.pl = pl;
        this.page = page;
        this.inv = Bukkit.createInventory(this, 54, Util.c("<gold>Megrendelések <gray>(" + (page + 1) + ". oldal)"));

        List<OrderBook.Order> all = new ArrayList<>();
        for (OrderBook.Order o : pl.orders.all()) if (o.remaining > 0) all.add(o);
        all.sort(Comparator.comparingLong((OrderBook.Order o) -> o.priceEach).reversed());

        int per = 45;
        int start = page * per;
        for (int i = 0; i < per; i++) {
            int idx = start + i;
            if (idx >= all.size()) break;
            OrderBook.Order o = all.get(idx);
            boolean mine = o.owner.equals(viewer.getUniqueId());
            ItemStack it = new ItemStack(o.mat);
            ItemMeta m = it.getItemMeta();
            m.displayName(Util.c("<!italic><white>" + Util.pretty(o.mat)));
            List<Component> lore = new ArrayList<>();
            lore.add(Util.c("<!italic><gray>Megrendelő: <white>" + o.ownerName));
            lore.add(Util.c("<!italic><gray>Ár / db: <gold>$" + Util.fmt(o.priceEach)));
            lore.add(Util.c("<!italic><gray>Hátra: <white>" + Util.full(o.remaining) + " db"));
            lore.add(Component.empty());
            lore.add(Util.c(mine ? "<!italic><red>Kattints a lemondáshoz"
                    : "<!italic><yellow>Kattints: beszállítod, amid van <gray>(" + Util.countItems(viewer, o.mat) + " db)"));
            m.lore(lore);
            it.setItemMeta(m);
            inv.setItem(i, it);
            slotId.put(i, o.id);
        }
        this.hasNext = all.size() > start + per;

        for (int i = 45; i < 54; i++) inv.setItem(i, Util.item(Material.BLACK_STAINED_GLASS_PANE, " "));
        if (page > 0) inv.setItem(45, Util.item(Material.ARROW, "<yellow>« Előző oldal"));
        inv.setItem(49, Util.item(Material.WRITABLE_BOOK, "<gold>Megrendelés leadása",
                "<gray>/orders create <tárgy> <db> <ár/db>", "<gray>/orders claim <dark_gray>- beszállított tárgyak átvétele"));
        if (hasNext) inv.setItem(53, Util.item(Material.ARROW, "<yellow>Következő oldal »"));
    }

    @Override
    public void click(InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getClickedInventory() != inv) return;
        Player p = (Player) e.getWhoClicked();
        int slot = e.getSlot();
        if (slot == 45 && page > 0) {
            Gui.later(pl, p, new OrdersGui(pl, page - 1, p));
        } else if (slot == 53 && hasNext) {
            Gui.later(pl, p, new OrdersGui(pl, page + 1, p));
        } else if (slotId.containsKey(slot)) {
            pl.orders.fill(p, slotId.get(slot));
            Gui.later(pl, p, new OrdersGui(pl, page, p));
        }
    }
}
