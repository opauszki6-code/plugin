package hu.donut.core;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MarketCommands implements CommandExecutor, TabCompleter {
    private final DonutCore pl;

    public MarketCommands(DonutCore pl) {
        this.pl = pl;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            Util.send(s, "<red>Ezt csak játékos használhatja.");
            return true;
        }
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "sell" -> new SellGui(pl).open(p);
            case "sellhand" -> sellHand(p);
            case "shop" -> new ShopGui(pl, "shop", null).open(p);
            case "shardshop" -> new ShopGui(pl, "shardshop", null).open(p);
            case "ah" -> ah(p, a);
            case "orders" -> orders(p, a);
            default -> { }
        }
        return true;
    }

    private void sellHand(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();
        if (it.getType() == Material.AIR) {
            Util.send(p, "<red>Nincs nálad semmi a kezedben.");
            return;
        }
        Long price = it.hasItemMeta() ? null : pl.sellPrices.get(it.getType());
        if (price == null) {
            Util.send(p, "<red>Ez a tárgy nem eladható.");
            return;
        }
        long total = price * it.getAmount();
        p.getInventory().setItemInMainHand(null);
        pl.store.get(p).money += total;
        Util.send(p, "<green>Eladtál <white>" + it.getAmount() + "x " + Util.pretty(it.getType()) + " <green>- <gold>$" + Util.fmt(total));
    }

    private void ah(Player p, String[] a) {
        if (a.length == 0) {
            new AuctionGui(pl, 0, 0).open(p);
            return;
        }
        if (!a[0].equalsIgnoreCase("sell") || a.length < 2) {
            Util.send(p, "<red>Használat: /ah | /ah sell <ár>");
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
            Util.send(p, "<red>Tartsd a kezedben az eladni kívánt tárgyat.");
            return;
        }
        long price;
        try {
            price = Util.parse(a[1]);
        } catch (NumberFormatException e) {
            Util.send(p, "<red>Érvénytelen ár. Példa: 5000, 10k, 2.5m");
            return;
        }
        if (price <= 0) {
            Util.send(p, "<red>Az árnak pozitívnak kell lennie.");
            return;
        }
        int max = pl.getConfig().getInt("ah.max-listings", 10);
        if (pl.ah.countBy(p.getUniqueId()) >= max) {
            Util.send(p, "<red>Legfeljebb " + max + " hirdetésed lehet.");
            return;
        }
        pl.ah.add(p, hand, price);
        p.getInventory().setItemInMainHand(null);
        Util.send(p, "<green>Meghirdetted: <white>" + hand.getAmount() + "x " + Util.pretty(hand.getType())
                + " <green>- <gold>$" + Util.fmt(price) + " <dark_gray>(" + pl.getConfig().getInt("ah.hours", 48) + " óráig)");
    }

    private void orders(Player p, String[] a) {
        if (a.length == 0) {
            new OrdersGui(pl, 0, p).open(p);
            return;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "claim" -> {
                long n = pl.orders.claim(p);
                if (n > 0) Util.send(p, "<green>Átvettél <white>" + n + " <green>beszállított tárgyat.");
                else Util.send(p, "<gray>Nincs átvehető tárgyad.");
            }
            case "my" -> {
                List<OrderBook.Order> mine = pl.orders.of(p.getUniqueId());
                if (mine.isEmpty()) Util.send(p, "<gray>Nincs megrendelésed.");
                for (OrderBook.Order o : mine) {
                    Util.raw(p, "<gray>#" + o.id + " <white>" + Util.pretty(o.mat) + " <gray>hátra: <white>" + o.remaining
                            + " <gray>ár: <gold>$" + Util.fmt(o.priceEach) + " <gray>átvehető: <green>" + o.claimable);
                }
            }
            case "create" -> {
                if (a.length < 4) {
                    Util.send(p, "<red>Használat: /orders create <tárgy> <darab> <ár/db>");
                    return;
                }
                Material m = Material.matchMaterial(a[1]);
                if (m == null || !m.isItem() || m == Material.AIR) {
                    Util.send(p, "<red>Ismeretlen tárgy: " + a[1]);
                    return;
                }
                long amount, each;
                try {
                    amount = Util.parse(a[2]);
                    each = Util.parse(a[3]);
                } catch (NumberFormatException e) {
                    Util.send(p, "<red>Érvénytelen szám.");
                    return;
                }
                if (amount <= 0 || amount > 100_000 || each <= 0) {
                    Util.send(p, "<red>A darabszám 1 és 100000 között legyen, az ár pozitív.");
                    return;
                }
                if (pl.orders.of(p.getUniqueId()).size() >= pl.getConfig().getInt("orders.max-orders", 5)) {
                    Util.send(p, "<red>Elérted a megrendelés limitet.");
                    return;
                }
                long total = amount * each;
                PlayerData d = pl.store.get(p);
                if (d.money < total) {
                    Util.send(p, "<red>Nincs elég pénzed! Kell: <gold>$" + Util.fmt(total));
                    return;
                }
                d.money -= total;
                pl.orders.create(p, m, amount, each);
                Util.send(p, "<green>Megrendelés leadva: <white>" + amount + "x " + Util.pretty(m) + " <green>- <gold>$"
                        + Util.fmt(each) + "/db <dark_gray>(letétbe helyezve: $" + Util.fmt(total) + ")");
            }
            default -> Util.send(p, "<red>/orders | create <tárgy> <db> <ár/db> | claim | my");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        List<String> opts = new ArrayList<>();
        if (name.equals("ah") && a.length == 1) opts.add("sell");
        if (name.equals("orders")) {
            if (a.length == 1) opts.addAll(List.of("create", "claim", "my"));
            else if (a.length == 2 && a[0].equalsIgnoreCase("create")) {
                for (Material m : Material.values()) if (m.isItem() && !m.isLegacy()) opts.add(m.name().toLowerCase(Locale.ROOT));
            }
        }
        for (String o : opts) if (o.startsWith(last)) out.add(o);
        if (out.size() > 50) return out.subList(0, 50);
        return out;
    }
}
