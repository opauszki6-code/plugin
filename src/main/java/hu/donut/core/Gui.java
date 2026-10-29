package hu.donut.core;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public abstract class Gui implements InventoryHolder {
    protected Inventory inv;

    @Override
    public Inventory getInventory() {
        return inv;
    }

    /** Alapértelmezetten minden kattintás tiltott. */
    public void click(InventoryClickEvent e) {
        e.setCancelled(true);
    }

    public void drag(InventoryDragEvent e) {
        e.setCancelled(true);
    }

    public void close(InventoryCloseEvent e) {
    }

    public void open(Player p) {
        p.openInventory(inv);
    }

    /** Következő tickben nyit meg egy új GUI-t (biztonságos kattintás közben). */
    public static void later(DonutCore pl, Player p, Gui g) {
        Bukkit.getScheduler().runTask(pl, () -> g.open(p));
    }

    protected void fill(Material m) {
        ItemStack glass = Util.item(m, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, glass);
    }
}
