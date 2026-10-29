package hu.donut.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class TeleportCommands implements CommandExecutor, TabCompleter {
    private final DonutCore pl;
    private final Map<UUID, BukkitTask> warm = new HashMap<>();

    private record Req(UUID from, boolean here, long expires) {}

    private final Map<UUID, Req> reqs = new HashMap<>(); // kulcs: a cél játékos

    public TeleportCommands(DonutCore pl) {
        this.pl = pl;
    }

    // ------------------------------------------------------------------ warmup
    public void teleport(Player p, Location dest, String what) {
        if (pl.inCombat(p)) {
            Util.send(p, "<red>Harc közben nem teleportálhatsz!");
            return;
        }
        UUID id = p.getUniqueId();
        cancelWarmup(id);
        int secs = p.hasPermission("donutcore.bypass.warmup") ? 0 : pl.getConfig().getInt("teleport-warmup", 3);
        if (secs <= 0) {
            p.teleportAsync(dest);
            Util.send(p, "<green>Teleportálva: <white>" + what);
            return;
        }
        Location start = p.getLocation().clone();
        Util.send(p, "<yellow>Teleportálás <white>" + secs + " mp <yellow>múlva... Ne mozogj!");
        int[] left = {secs};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(pl, () -> {
            if (!p.isOnline()) {
                cancelWarmup(id);
                return;
            }
            Location now = p.getLocation();
            if (now.getWorld() != start.getWorld() || now.distanceSquared(start) > 0.6) {
                cancelWarmup(id);
                Util.send(p, "<red>Teleportálás megszakítva, mert mozogtál.");
                return;
            }
            left[0]--;
            if (left[0] <= 0) {
                cancelWarmup(id);
                p.teleportAsync(dest);
                Util.send(p, "<green>Teleportálva: <white>" + what);
            } else {
                p.sendActionBar(Util.c("<yellow>Teleport: <white>" + left[0] + "..."));
            }
        }, 20L, 20L);
        warm.put(id, task);
    }

    public boolean cancelWarmup(UUID id) {
        BukkitTask t = warm.remove(id);
        if (t != null) t.cancel();
        return t != null;
    }

    // ------------------------------------------------------------------ commands
    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            Util.send(s, "<red>Ezt csak játékos használhatja.");
            return true;
        }
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "spawn" -> teleport(p, pl.spawn(), "spawn");
            case "setspawn" -> {
                if (!p.hasPermission("donutcore.admin")) return true;
                pl.getConfig().set("spawn", p.getLocation());
                pl.saveConfig();
                Util.send(p, "<green>Spawn beállítva.");
            }
            case "rtp" -> rtp(p, 0);
            case "tpa" -> request(p, a, false);
            case "tpahere" -> request(p, a, true);
            case "tpaccept" -> accept(p);
            case "tpdeny" -> deny(p);
            case "sethome" -> setHome(p, a);
            case "home" -> home(p, a);
            case "delhome" -> delHome(p, a);
            case "afk" -> afk(p);
            default -> { }
        }
        return true;
    }

    private void afk(Player p) {
        Location l = pl.getConfig().getLocation("afk.location");
        if (l == null || l.getWorld() == null) {
            Util.send(p, "<red>Az AFK terület még nincs beállítva (/donutcore setafk).");
            return;
        }
        teleport(p, l, "AFK terület <aqua>(+" + pl.getConfig().getLong("afk.shards-per-minute", 1) + " shard/perc)");
    }

    // ------------------------------------------------------------------ RTP
    private void rtp(Player p, int attempt) {
        if (attempt == 0) {
            if (pl.inCombat(p)) {
                Util.send(p, "<red>Harc közben nem használhatod az /rtp-t!");
                return;
            }
            Util.send(p, "<yellow>Biztonságos hely keresése...");
        }
        if (attempt > 12) {
            Util.send(p, "<red>Nem találtam biztonságos helyet, próbáld újra.");
            return;
        }
        World w = p.getWorld().getEnvironment() == World.Environment.NORMAL ? p.getWorld() : Bukkit.getWorlds().get(0);
        int min = pl.getConfig().getInt("rtp.min", 500);
        int max = pl.getConfig().getInt("rtp.max", 5000);
        int cx = pl.getConfig().getInt("rtp.center-x", 0);
        int cz = pl.getConfig().getInt("rtp.center-z", 0);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double ang = r.nextDouble() * Math.PI * 2;
        double dist = min + r.nextDouble() * Math.max(1, max - min);
        int x = cx + (int) (Math.cos(ang) * dist);
        int z = cz + (int) (Math.sin(ang) * dist);
        w.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            if (!p.isOnline()) return;
            int y = w.getHighestBlockYAt(x, z);
            Block ground = w.getBlockAt(x, y, z);
            Material m = ground.getType();
            Location dest = new Location(w, x + 0.5, y + 1, z + 0.5);
            boolean bad = ground.isLiquid() || m == Material.LAVA || m == Material.MAGMA_BLOCK || m == Material.CACTUS
                    || m == Material.FIRE || m == Material.SOUL_FIRE || m == Material.POWDER_SNOW || m == Material.CAMPFIRE
                    || !w.getWorldBorder().isInside(dest) || y <= w.getMinHeight();
            if (bad) {
                rtp(p, attempt + 1);
                return;
            }
            teleport(p, dest, "random hely <gray>(" + x + ", " + (y + 1) + ", " + z + ")");
        });
    }

    // ------------------------------------------------------------------ TPA
    private void request(Player p, String[] a, boolean here) {
        if (a.length < 1) {
            Util.send(p, "<red>Használat: /" + (here ? "tpahere" : "tpa") + " <játékos>");
            return;
        }
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null || t == p) {
            Util.send(p, "<red>A játékos nem elérhető.");
            return;
        }
        reqs.put(t.getUniqueId(), new Req(p.getUniqueId(), here, System.currentTimeMillis() + 60_000));
        Util.send(p, "<green>Kérés elküldve neki: <white>" + t.getName() + "<green>. (60 mp)");
        Util.send(t, "<white>" + p.getName() + " <yellow>" + (here ? "azt kéri, hogy teleportálj hozzá" : "hozzád szeretne teleportálni")
                + ". <green>/tpaccept <gray>vagy <red>/tpdeny");
    }

    private void accept(Player p) {
        Req r = reqs.remove(p.getUniqueId());
        if (r == null || r.expires() < System.currentTimeMillis()) {
            Util.send(p, "<red>Nincs függőben lévő kérésed.");
            return;
        }
        Player from = Bukkit.getPlayer(r.from());
        if (from == null) {
            Util.send(p, "<red>A kérő már nincs fent.");
            return;
        }
        Util.send(p, "<green>Elfogadtad a kérést.");
        if (r.here()) teleport(p, from.getLocation(), from.getName());
        else teleport(from, p.getLocation(), p.getName());
    }

    private void deny(Player p) {
        Req r = reqs.remove(p.getUniqueId());
        if (r == null) {
            Util.send(p, "<red>Nincs függőben lévő kérésed.");
            return;
        }
        Util.send(p, "<yellow>Kérés elutasítva.");
        Player from = Bukkit.getPlayer(r.from());
        if (from != null) Util.send(from, "<red>" + p.getName() + " elutasította a kérésed.");
    }

    // ------------------------------------------------------------------ Homes
    private void setHome(Player p, String[] a) {
        PlayerData d = pl.store.get(p);
        String name = (a.length > 0 ? a[0] : "home").toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_]{1,16}")) {
            Util.send(p, "<red>A név csak betű/szám/_ lehet, max 16 karakter.");
            return;
        }
        int max = pl.getConfig().getInt("homes-max", 3);
        if (!d.homes.containsKey(name) && d.homes.size() >= max && !p.hasPermission("donutcore.homes.unlimited")) {
            Util.send(p, "<red>Legfeljebb " + max + " otthonod lehet.");
            return;
        }
        d.homes.put(name, p.getLocation());
        Util.send(p, "<green>Otthon beállítva: <white>" + name);
    }

    private void home(Player p, String[] a) {
        PlayerData d = pl.store.get(p);
        if (d.homes.isEmpty()) {
            Util.send(p, "<red>Nincs otthonod. Használd: /sethome");
            return;
        }
        String name;
        if (a.length > 0) name = a[0].toLowerCase(Locale.ROOT);
        else if (d.homes.size() == 1) name = d.homes.keySet().iterator().next();
        else {
            Util.send(p, "<yellow>Otthonaid: <white>" + String.join(", ", d.homes.keySet()));
            return;
        }
        Location l = d.homes.get(name);
        if (l == null || l.getWorld() == null) {
            Util.send(p, "<red>Nincs ilyen otthon: " + name);
            return;
        }
        teleport(p, l, "otthon <white>" + name);
    }

    private void delHome(Player p, String[] a) {
        PlayerData d = pl.store.get(p);
        String name = (a.length > 0 ? a[0] : "home").toLowerCase(Locale.ROOT);
        if (d.homes.remove(name) != null) Util.send(p, "<green>Otthon törölve: <white>" + name);
        else Util.send(p, "<red>Nincs ilyen otthon.");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length != 1) return out;
        String last = a[0].toLowerCase(Locale.ROOT);
        String n = cmd.getName().toLowerCase(Locale.ROOT);
        if (n.equals("tpa") || n.equals("tpahere")) {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(p.getName());
        } else if ((n.equals("home") || n.equals("delhome")) && s instanceof Player p) {
            for (String h : pl.store.get(p).homes.keySet()) if (h.startsWith(last)) out.add(h);
        }
        return out;
    }
}
