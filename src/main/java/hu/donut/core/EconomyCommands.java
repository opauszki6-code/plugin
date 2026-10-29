package hu.donut.core;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.ToLongFunction;

public class EconomyCommands implements CommandExecutor, TabCompleter {
    private final DonutCore pl;
    private final DataStore st;

    public EconomyCommands(DonutCore pl) {
        this.pl = pl;
        this.st = pl.store;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "balance" -> balance(s, a, false);
            case "shards" -> balance(s, a, true);
            case "pay" -> pay(s, a);
            case "baltop" -> top(s, "money");
            case "top" -> top(s, a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "money");
            case "bounty" -> bounty(s, a);
            case "stats" -> stats(s, a);
            case "eco" -> eco(s, a);
            case "donutcore" -> admin(s, a);
            default -> { }
        }
        return true;
    }

    private void balance(CommandSender s, String[] a, boolean shardsOnly) {
        PlayerData d;
        if (a.length > 0) d = st.byName(a[0]);
        else if (s instanceof Player p) d = st.get(p);
        else {
            Util.send(s, "<red>Használat: /balance <játékos>");
            return;
        }
        if (d == null) {
            Util.send(s, "<red>Ismeretlen játékos.");
            return;
        }
        if (shardsOnly) Util.send(s, "<white>" + d.name + " <gray>shardjai: <aqua>" + Util.full(d.shards) + " ✦");
        else Util.send(s, "<white>" + d.name + " <gray>egyenlege: <green>$" + Util.full(d.money)
                + " <dark_gray>(" + Util.fmt(d.money) + ") <gray>| <aqua>" + Util.full(d.shards) + " ✦");
    }

    private void pay(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) return;
        if (a.length < 2) {
            Util.send(p, "<red>Használat: /pay <játékos> <összeg>");
            return;
        }
        PlayerData target = st.byName(a[0]);
        if (target == null) {
            Util.send(p, "<red>Ismeretlen játékos.");
            return;
        }
        if (target.id.equals(p.getUniqueId())) {
            Util.send(p, "<red>Magadnak nem küldhetsz pénzt.");
            return;
        }
        long amount;
        try {
            amount = Util.parse(a[1]);
        } catch (NumberFormatException e) {
            Util.send(p, "<red>Érvénytelen összeg. Példa: 1000, 5k, 2.5m, 1b");
            return;
        }
        if (amount <= 0) {
            Util.send(p, "<red>Az összegnek pozitívnak kell lennie.");
            return;
        }
        PlayerData me = st.get(p);
        if (me.money < amount) {
            Util.send(p, "<red>Nincs ennyi pénzed!");
            return;
        }
        me.money -= amount;
        target.money += amount;
        Util.send(p, "<green>Elküldtél <white>$" + Util.fmt(amount) + " <green>összeget <white>" + target.name + "<green> részére.");
        Player tp = Bukkit.getPlayer(target.id);
        if (tp != null) Util.send(tp, "<green>Kaptál <white>$" + Util.fmt(amount) + " <green>összeget tőle: <white>" + p.getName());
    }

    private void top(CommandSender s, String type) {
        ToLongFunction<PlayerData> f;
        String title;
        switch (type) {
            case "kills", "ol", "ölés" -> { f = d -> d.kills; title = "Ölések"; }
            case "deaths", "halal", "halál" -> { f = d -> d.deaths; title = "Halálok"; }
            case "playtime", "time", "jatekido" -> { f = d -> d.playtime; title = "Játékidő"; }
            case "shards", "shard" -> { f = d -> d.shards; title = "Shardok"; }
            case "bounty" -> { f = d -> d.bounty; title = "Fejpénzek"; }
            default -> { f = d -> d.money; title = "Pénz"; }
        }
        List<PlayerData> list = new ArrayList<>(st.players.values());
        list.sort(Comparator.comparingLong(f).reversed());
        Util.raw(s, "<gold><bold>━━ Top 10: " + title + " ━━");
        int i = 1;
        for (PlayerData d : list) {
            if (i > 10) break;
            long v = f.applyAsLong(d);
            if (v <= 0) break;
            String val = type.equals("playtime") || type.equals("time") || type.equals("jatekido") ? Util.time(v) : Util.fmt(v);
            Util.raw(s, "<yellow>#" + i + " <white>" + d.name + " <dark_gray>- <green>" + val);
            i++;
        }
        if (i == 1) Util.raw(s, "<gray>Még nincs adat.");
    }

    private void bounty(CommandSender s, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("list")) {
            List<PlayerData> list = new ArrayList<>();
            for (PlayerData d : st.players.values()) if (d.bounty > 0) list.add(d);
            list.sort(Comparator.comparingLong((PlayerData d) -> d.bounty).reversed());
            Util.raw(s, "<red><bold>━━ Fejpénzek ━━");
            if (list.isEmpty()) Util.raw(s, "<gray>Nincs aktív fejpénz. Használd: /bounty set <játékos> <összeg>");
            for (int i = 0; i < Math.min(10, list.size()); i++)
                Util.raw(s, "<red>" + list.get(i).name + " <dark_gray>- <gold>$" + Util.fmt(list.get(i).bounty));
            return;
        }
        if (!(s instanceof Player p)) return;
        if (!a[0].equalsIgnoreCase("set") || a.length < 3) {
            Util.send(p, "<red>Használat: /bounty set <játékos> <összeg> | /bounty list");
            return;
        }
        PlayerData t = st.byName(a[1]);
        if (t == null) {
            Util.send(p, "<red>Ismeretlen játékos.");
            return;
        }
        if (t.id.equals(p.getUniqueId())) {
            Util.send(p, "<red>Magadra nem tehetsz fejpénzt.");
            return;
        }
        long amt;
        try {
            amt = Util.parse(a[2]);
        } catch (NumberFormatException e) {
            Util.send(p, "<red>Érvénytelen összeg.");
            return;
        }
        long min = pl.getConfig().getLong("bounty-min", 1000);
        if (amt < min) {
            Util.send(p, "<red>A minimum fejpénz: $" + Util.fmt(min));
            return;
        }
        PlayerData me = st.get(p);
        if (me.money < amt) {
            Util.send(p, "<red>Nincs ennyi pénzed!");
            return;
        }
        me.money -= amt;
        t.bounty += amt;
        Bukkit.broadcast(Util.c(Util.PREFIX + "<white>" + p.getName() + " <gray>fejpénzt tett <red>" + t.name
                + " <gray>fejére: <gold>$" + Util.fmt(amt) + " <dark_gray>(összesen: $" + Util.fmt(t.bounty) + ")"));
    }

    private void stats(CommandSender s, String[] a) {
        PlayerData d;
        if (a.length > 0) d = st.byName(a[0]);
        else if (s instanceof Player p) d = st.get(p);
        else return;
        if (d == null) {
            Util.send(s, "<red>Ismeretlen játékos.");
            return;
        }
        double kdr = d.deaths == 0 ? d.kills : (double) d.kills / d.deaths;
        Util.raw(s, "<gold><bold>━━ " + d.name + " statisztikái ━━");
        Util.raw(s, "<gray>Pénz: <green>$" + Util.fmt(d.money) + " <gray>| Shard: <aqua>" + Util.fmt(d.shards));
        Util.raw(s, "<gray>Ölés: <red>" + d.kills + " <gray>| Halál: <white>" + d.deaths
                + " <gray>| K/D: <yellow>" + String.format(Locale.US, "%.2f", kdr) + " <gray>| Sorozat: <yellow>" + d.streak);
        Util.raw(s, "<gray>Játékidő: <yellow>" + Util.time(d.playtime) + " <gray>| Fejpénz: <gold>$" + Util.fmt(d.bounty));
        Util.raw(s, "<gray>Csapat: <light_purple>" + (d.team == null ? "-" : d.team));
    }

    private void eco(CommandSender s, String[] a) {
        if (!s.hasPermission("donutcore.admin")) return;
        if (a.length < 4) {
            Util.send(s, "<red>Használat: /eco <give|take|set> <játékos> <money|shards> <összeg>");
            return;
        }
        PlayerData d = st.byName(a[1]);
        if (d == null) {
            Util.send(s, "<red>Ismeretlen játékos.");
            return;
        }
        long amt;
        try {
            amt = Util.parse(a[3]);
        } catch (NumberFormatException e) {
            Util.send(s, "<red>Érvénytelen összeg.");
            return;
        }
        boolean shards = a[2].equalsIgnoreCase("shards");
        long cur = shards ? d.shards : d.money;
        long nv = switch (a[0].toLowerCase(Locale.ROOT)) {
            case "give" -> cur + amt;
            case "take" -> Math.max(0, cur - amt);
            case "set" -> amt;
            default -> -1;
        };
        if (nv < 0) {
            Util.send(s, "<red>Ismeretlen művelet.");
            return;
        }
        if (shards) d.shards = nv;
        else d.money = nv;
        Util.send(s, "<green>" + d.name + " új " + (shards ? "shard" : "pénz") + " egyenlege: <white>" + Util.full(nv));
    }

    private void admin(CommandSender s, String[] a) {
        if (a.length == 0) {
            Util.send(s, "<yellow>/donutcore <reload|setafk|save>");
            return;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                pl.reloadConfig();
                pl.loadPrices();
                Util.send(s, "<green>Config újratöltve.");
            }
            case "save" -> {
                pl.saveAll();
                Util.send(s, "<green>Adatok elmentve.");
            }
            case "setafk" -> {
                if (!(s instanceof Player p)) return;
                pl.getConfig().set("afk.location", p.getLocation());
                pl.saveConfig();
                Util.send(p, "<green>AFK terület beállítva erre a pontra.");
            }
            default -> Util.send(s, "<red>Ismeretlen alparancs.");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        if (a.length == 1) {
            switch (name) {
                case "bounty" -> options.addAll(List.of("set", "list"));
                case "eco" -> options.addAll(List.of("give", "take", "set"));
                case "top" -> options.addAll(List.of("money", "kills", "deaths", "playtime", "shards", "bounty"));
                case "donutcore" -> options.addAll(List.of("reload", "setafk", "save"));
                default -> Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            }
        } else if (a.length == 2 && (name.equals("bounty") || name.equals("eco"))) {
            Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
        } else if (a.length == 3 && name.equals("eco")) {
            options.addAll(List.of("money", "shards"));
        }
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(last)) out.add(o);
        return out;
    }
}
