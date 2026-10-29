package hu.donut.core;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DonutCore extends JavaPlugin {
    public DataStore store;
    public AuctionHouse ah;
    public OrderBook orders;
    public TeleportCommands tp;
    public ScoreboardManager sb;
    public final Map<UUID, Long> combat = new ConcurrentHashMap<>();
    public final Map<Material, Long> sellPrices = new HashMap<>();
    private int tick;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new DataStore(this);
        store.load();
        ah = new AuctionHouse(this);
        ah.load();
        orders = new OrderBook(this);
        orders.load();
        sb = new ScoreboardManager(this);
        loadPrices();

        tp = new TeleportCommands(this);
        EconomyCommands eco = new EconomyCommands(this);
        for (String n : List.of("balance", "pay", "baltop", "shards", "eco", "bounty", "stats", "top", "donutcore"))
            reg(n, eco);
        for (String n : List.of("spawn", "setspawn", "rtp", "tpa", "tpahere", "tpaccept", "tpdeny", "sethome", "home", "delhome", "afk"))
            reg(n, tp);
        reg("team", new TeamCommands(this));
        MarketCommands mk = new MarketCommands(this);
        for (String n : List.of("sell", "sellhand", "shop", "shardshop", "ah", "orders")) reg(n, mk);

        Bukkit.getPluginManager().registerEvents(new Listeners(this), this);
        Bukkit.getScheduler().runTaskTimer(this, this::secondTick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::saveAll, 6000L, 6000L);

        // újratöltés esetén a már bent lévő játékosok scoreboardja
        for (Player p : Bukkit.getOnlinePlayers()) store.get(p).name = p.getName();
        getLogger().info("DonutCore elindult.");
    }

    @Override
    public void onDisable() {
        saveAll();
        if (sb != null) sb.clearAll();
    }

    private void reg(String name, CommandExecutor ex) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Hiányzó parancs a plugin.yml-ből: " + name);
            return;
        }
        cmd.setExecutor(ex);
        if (ex instanceof TabCompleter tc) cmd.setTabCompleter(tc);
    }

    public void saveAll() {
        store.save();
        ah.save();
        orders.save();
    }

    public void loadPrices() {
        sellPrices.clear();
        ConfigurationSection s = getConfig().getConfigurationSection("sell-prices");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            Material m = Material.matchMaterial(k);
            if (m != null) sellPrices.put(m, s.getLong(k));
            else getLogger().warning("Ismeretlen anyag a sell-prices-ban: " + k);
        }
    }

    public boolean inCombat(Player p) {
        Long t = combat.get(p.getUniqueId());
        return t != null && t > System.currentTimeMillis();
    }

    public void tag(Player p) {
        long until = System.currentTimeMillis() + getConfig().getLong("combat-seconds", 20) * 1000L;
        boolean already = inCombat(p);
        combat.put(p.getUniqueId(), until);
        if (!already) p.sendActionBar(Util.c("<red><bold>Harcban vagy!</bold> <gray>Ne lépj ki!"));
    }

    public Location spawn() {
        Location l = getConfig().getLocation("spawn");
        if (l != null && l.getWorld() != null) return l;
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    private void secondTick() {
        tick++;
        long now = System.currentTimeMillis();
        boolean afkPay = tick % 60 == 0;
        Location afk = getConfig().getLocation("afk.location");
        double r = getConfig().getDouble("afk.radius", 15);
        long amt = getConfig().getLong("afk.shards-per-minute", 1);

        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = store.get(p);
            d.playtime++;
            Long c = combat.get(p.getUniqueId());
            if (c != null && c < now) {
                combat.remove(p.getUniqueId());
                p.sendActionBar(Util.c("<green>Vége a harcnak, kiléphetsz."));
            }
            if (afkPay && afk != null && afk.getWorld() != null && p.getWorld().equals(afk.getWorld())
                    && p.getLocation().distanceSquared(afk) <= r * r) {
                d.shards += amt;
                p.sendActionBar(Util.c("<aqua>+" + amt + " shard <gray>(AFK)"));
            }
            sb.update(p, d);
        }
    }
}
