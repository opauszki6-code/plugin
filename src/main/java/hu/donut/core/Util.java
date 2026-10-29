package hu.donut.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Util {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = "<gold><bold>Donut</bold> <dark_gray>» <gray>";

    private Util() {}

    public static Component c(String s) {
        return MM.deserialize(s);
    }

    public static void send(CommandSender to, String msg) {
        to.sendMessage(c(PREFIX + msg));
    }

    public static void raw(CommandSender to, String msg) {
        to.sendMessage(c(msg));
    }

    /** 1234567 -> 1.23M */
    public static String fmt(long v) {
        if (Math.abs(v) < 1000) return Long.toString(v);
        String[] suf = {"", "K", "M", "B", "T", "Q"};
        double d = v;
        int i = 0;
        while (Math.abs(d) >= 1000 && i < suf.length - 1) {
            d /= 1000;
            i++;
        }
        String s = String.format(Locale.US, "%.2f", d);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s + suf[i];
    }

    public static String full(long v) {
        return String.format(Locale.US, "%,d", v);
    }

    /** "1k", "2.5m", "1b" -> long. Hibánál NumberFormatException. */
    public static long parse(String in) {
        String s = in.trim().toLowerCase(Locale.ROOT).replace(",", "");
        if (s.isEmpty()) throw new NumberFormatException();
        long mult = 1;
        char last = s.charAt(s.length() - 1);
        switch (last) {
            case 'k' -> mult = 1_000L;
            case 'm' -> mult = 1_000_000L;
            case 'b' -> mult = 1_000_000_000L;
            case 't' -> mult = 1_000_000_000_000L;
            default -> { }
        }
        if (mult != 1) s = s.substring(0, s.length() - 1);
        double d = Double.parseDouble(s) * mult;
        if (Double.isNaN(d) || Double.isInfinite(d) || d > 9e15 || d < 0) throw new NumberFormatException();
        return Math.round(d);
    }

    public static String time(long sec) {
        long d = sec / 86400, h = (sec % 86400) / 3600, m = (sec % 3600) / 60, s = sec % 60;
        if (d > 0) return d + "n " + h + "ó";
        if (h > 0) return h + "ó " + m + "p";
        if (m > 0) return m + "p " + s + "mp";
        return s + "mp";
    }

    public static String pretty(Material m) {
        String[] parts = m.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    public static ItemStack item(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c("<!italic>" + name));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(c("<!italic>" + s));
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    public static void giveOrDrop(Player p, ItemStack it) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        for (ItemStack i : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), i);
    }

    /** Eltávolít max `amount` darab sima (meta nélküli) tárgyat. Visszaadja, mennyit vett el. */
    public static int removeItems(Player p, Material mat, int amount) {
        int removed = 0;
        ItemStack[] contents = p.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && removed < amount; i++) {
            ItemStack st = contents[i];
            if (st == null || st.getType() != mat || st.hasItemMeta()) continue;
            int take = Math.min(st.getAmount(), amount - removed);
            removed += take;
            if (take == st.getAmount()) contents[i] = null;
            else st.setAmount(st.getAmount() - take);
        }
        p.getInventory().setStorageContents(contents);
        return removed;
    }

    public static int countItems(Player p, Material mat) {
        int n = 0;
        for (ItemStack st : p.getInventory().getStorageContents()) {
            if (st != null && st.getType() == mat && !st.hasItemMeta()) n += st.getAmount();
        }
        return n;
    }
}
