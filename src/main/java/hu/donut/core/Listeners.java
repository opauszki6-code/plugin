package hu.donut.core;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Locale;

public class Listeners implements Listener {
    private final DonutCore pl;

    public Listeners(DonutCore pl) {
        this.pl = pl;
    }

    // ------------------------------------------------------------ join / quit
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        boolean first = !pl.store.has(p.getUniqueId());
        PlayerData d = pl.store.get(p);
        d.name = p.getName();
        if (first) {
            d.money = pl.getConfig().getLong("start-money", 1000);
            Bukkit.broadcast(Util.c(Util.PREFIX + "<white>" + p.getName() + " <green>először csatlakozott a szerverre! Üdv!"));
        }
        e.joinMessage(Util.c("<dark_gray>[<green>+<dark_gray>] <gray>" + p.getName()));
        pl.sb.update(p, d);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        pl.tp.cancelWarmup(p.getUniqueId());
        if (pl.inCombat(p)) {
            pl.combat.remove(p.getUniqueId());
            p.setHealth(0); // harc közbeni kilépés = halál
            Bukkit.broadcast(Util.c(Util.PREFIX + "<red>" + p.getName() + " <gray>harc közben kilépett!"));
        }
        pl.combat.remove(p.getUniqueId());
        pl.sb.remove(p);
        e.quitMessage(Util.c("<dark_gray>[<red>-<dark_gray>] <gray>" + p.getName()));
    }

    // ------------------------------------------------------------ halál / ölés / fejpénz
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player v = e.getEntity();
        PlayerData vd = pl.store.get(v);
        vd.deaths++;
        vd.streak = 0;
        pl.combat.remove(v.getUniqueId());

        Player k = v.getKiller();
        if (k == null || k.equals(v)) return;
        PlayerData kd = pl.store.get(k);
        kd.kills++;
        kd.streak++;
        long shards = pl.getConfig().getLong("kill-shards", 1);
        kd.shards += shards;
        Util.send(k, "<green>Ölés! <aqua>+" + shards + " ✦ <gray>(sorozat: " + kd.streak + ")");
        if (vd.bounty > 0) {
            long b = vd.bounty;
            vd.bounty = 0;
            kd.money += b;
            Bukkit.broadcast(Util.c(Util.PREFIX + "<white>" + k.getName() + " <gray>beváltotta <red>" + v.getName()
                    + " <gray>fejpénzét: <gold>$" + Util.fmt(b)));
        }
    }

    // ------------------------------------------------------------ harc
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Entity damager = e.getDamager();
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) damager = shooter;
        if (!(damager instanceof Player attacker) || attacker.equals(victim)) return;

        if (pl.store.sameTeam(attacker.getUniqueId(), victim.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        pl.tag(attacker);
        pl.tag(victim);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && pl.tp.cancelWarmup(p.getUniqueId())) {
            Util.send(p, "<red>Teleportálás megszakítva, mert sebzést kaptál.");
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!pl.inCombat(p)) return;
        String msg = e.getMessage().substring(1).toLowerCase(Locale.ROOT);
        String first = msg.split(" ")[0];
        int colon = first.indexOf(':');
        if (colon >= 0) first = first.substring(colon + 1);
        List<String> blocked = pl.getConfig().getStringList("combat-blocked-commands");
        if (blocked.contains(first)) {
            e.setCancelled(true);
            Util.send(p, "<red>Harc közben nem használhatod ezt a parancsot!");
        }
    }

    // ------------------------------------------------------------ chat
    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Team t = pl.store.teamOf(p.getUniqueId());
        String tag = t == null ? "" : "<dark_gray>[<light_purple>" + t.name + "<dark_gray>] ";
        e.renderer(ChatRenderer.viewerUnaware((source, name, msg) ->
                Util.c(tag + "<gray>" + source.getName() + " <dark_gray>» ").append(msg.color(NamedTextColor.WHITE))));
    }

    // ------------------------------------------------------------ GUI
    @EventHandler
    public void onGuiClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof Gui g) g.click(e);
    }

    @EventHandler
    public void onGuiDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Gui g) g.drag(e);
    }

    @EventHandler
    public void onGuiClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Gui g) g.close(e);
    }
}
