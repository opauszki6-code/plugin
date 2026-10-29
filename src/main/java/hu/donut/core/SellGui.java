package hu.donut.core;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public class SellGui extends Gui {
    private final DonutCore pl;

    public SellGui(DonutCore pl) {
        this.pl = pl;
        this.inv = Bukkit.createInventory(this, 54, Util.c("<gold>Eladás <gray>- tedd be a tárgyakat, zárd be"));
    }

    // a tárgyak behelyezése engedélyezett
    @Override
    public void click(InventoryClickEvent e) {
    }

    @Override
    public void drag(InventoryDragEvent e) {
    }

    @Override
    public void close(InventoryCloseEvent e) {
        Player p = (Player) e.getPlayer();
        long total = 0;
        long count = 0;
        for (ItemStack it : inv.getContents()) {
            if (it == null) continue;
            Long price = it.hasItemMeta() ? null : pl.sellPrices.get(it.getType());
            if (price == null) {
                Util.giveOrDrop(p, it);
                continue;
            }
            total += price * it.getAmount();
            count += it.getAmount();
        }
        inv.clear();
        if (total > 0) {
            pl.store.get(p).money += total;
            Util.send(p, "<green>Eladtál <white>" + count + " <green>tárgyat <gold>$" + Util.fmt(total) + " <green>értékben.");
        } else {
            Util.send(p, "<gray>Nem adtál el semmit.");
        }
    }
}
