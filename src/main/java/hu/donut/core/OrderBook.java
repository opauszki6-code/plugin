package hu.donut.core;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Vételi megrendelések: a megrendelő előre kifizeti a pénzt, mások tárgyat szállítanak be. */
public class OrderBook {
    public static class Order {
        public long id;
        public UUID owner;
        public String ownerName;
        public Material mat;
        public long remaining;
        public long priceEach;
        public long claimable;
    }

    private final DonutCore pl;
    private final File file;
    private final Map<Long, Order> map = new LinkedHashMap<>();
    private long next = 1;

    public OrderBook(DonutCore pl) {
        this.pl = pl;
        this.file = new File(pl.getDataFolder(), "orders.yml");
    }

    public List<Order> all() {
        return new ArrayList<>(map.values());
    }

    public List<Order> of(UUID id) {
        List<Order> l = new ArrayList<>();
        for (Order o : map.values()) if (o.owner.equals(id)) l.add(o);
        return l;
    }

    public Order create(Player p, Material mat, long amount, long priceEach) {
        Order o = new Order();
        o.id = next++;
        o.owner = p.getUniqueId();
        o.ownerName = p.getName();
        o.mat = mat;
        o.remaining = amount;
        o.priceEach = priceEach;
        map.put(o.id, o);
        return o;
    }

    /** A játékos beszállít tárgyakat egy megrendelésbe (vagy a saját megrendelését lemondja). */
    public void fill(Player p, long id) {
        Order o = map.get(id);
        if (o == null) {
            Util.send(p, "<red>Ez a megrendelés már nem elérhető.");
            return;
        }
        if (o.owner.equals(p.getUniqueId())) {
            cancel(p, o);
            return;
        }
        if (o.remaining <= 0) {
            Util.send(p, "<red>Ez a megrendelés már teljesült.");
            return;
        }
        int have = Util.countItems(p, o.mat);
        if (have <= 0) {
            Util.send(p, "<red>Nincs nálad egy db <white>" + Util.pretty(o.mat) + "<red> sem (sima, meta nélküli).");
            return;
        }
        int n = (int) Math.min(have, o.remaining);
        int removed = Util.removeItems(p, o.mat, n);
        if (removed <= 0) return;
        long pay = removed * o.priceEach;
        pl.store.get(p).money += pay;
        o.remaining -= removed;
        o.claimable += removed;
        Util.send(p, "<green>Beszállítottál <white>" + removed + "x " + Util.pretty(o.mat) + "<green>: <gold>+$" + Util.fmt(pay));
        Player owner = Bukkit.getPlayer(o.owner);
        if (owner != null) Util.send(owner, "<white>" + p.getName() + " <green>beszállított <white>" + removed + "x "
                + Util.pretty(o.mat) + " <green>a megrendelésedbe. <gray>(/orders claim)");
    }

    private void cancel(Player p, Order o) {
        long refund = o.remaining * o.priceEach;
        pl.store.get(p).money += refund;
        if (o.claimable > 0) giveStacks(p, o.mat, o.claimable);
        map.remove(o.id);
        Util.send(p, "<yellow>Megrendelés lemondva. Visszakaptál <gold>$" + Util.fmt(refund)
                + (o.claimable > 0 ? " <yellow>és <white>" + o.claimable + " <yellow>tárgyat." : "<yellow>."));
    }

    /** Összes beszállított tárgy átvétele. */
    public long claim(Player p) {
        long total = 0;
        List<Long> done = new ArrayList<>();
        for (Order o : map.values()) {
            if (!o.owner.equals(p.getUniqueId()) || o.claimable <= 0) continue;
            giveStacks(p, o.mat, o.claimable);
            total += o.claimable;
            o.claimable = 0;
            if (o.remaining <= 0) done.add(o.id);
        }
        for (Long id : done) map.remove(id);
        return total;
    }

    private void giveStacks(Player p, Material m, long amount) {
        int max = m.getMaxStackSize();
        while (amount > 0) {
            int n = (int) Math.min(max, amount);
            Util.giveOrDrop(p, new ItemStack(m, n));
            amount -= n;
        }
    }

    public void load() {
        map.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        next = y.getLong("next", 1);
        ConfigurationSection s = y.getConfigurationSection("o");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(k);
            if (c == null) continue;
            Material m = Material.matchMaterial(c.getString("material", ""));
            if (m == null) continue;
            Order o = new Order();
            o.id = Long.parseLong(k);
            o.owner = UUID.fromString(c.getString("owner"));
            o.ownerName = c.getString("name", "?");
            o.mat = m;
            o.remaining = c.getLong("remaining");
            o.priceEach = c.getLong("price");
            o.claimable = c.getLong("claimable");
            map.put(o.id, o);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("next", next);
        for (Order o : map.values()) {
            String p = "o." + o.id;
            y.set(p + ".owner", o.owner.toString());
            y.set(p + ".name", o.ownerName);
            y.set(p + ".material", o.mat.name());
            y.set(p + ".remaining", o.remaining);
            y.set(p + ".price", o.priceEach);
            y.set(p + ".claimable", o.claimable);
        }
        try {
            y.save(file);
        } catch (IOException e) {
            pl.getLogger().severe("Megrendelés mentési hiba: " + e.getMessage());
        }
    }
}
