package hu.donut.core;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DataStore {
    private final DonutCore pl;
    private final File playersFile;
    private final File teamsFile;
    public final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();
    public final Map<String, Team> teams = new ConcurrentHashMap<>();

    public DataStore(DonutCore pl) {
        this.pl = pl;
        pl.getDataFolder().mkdirs();
        this.playersFile = new File(pl.getDataFolder(), "players.yml");
        this.teamsFile = new File(pl.getDataFolder(), "teams.yml");
    }

    public PlayerData get(UUID id) {
        return players.computeIfAbsent(id, PlayerData::new);
    }

    public PlayerData get(Player p) {
        return get(p.getUniqueId());
    }

    public PlayerData byName(String name) {
        for (PlayerData d : players.values()) {
            if (d.name.equalsIgnoreCase(name)) return d;
        }
        return null;
    }

    public boolean has(UUID id) {
        return players.containsKey(id);
    }

    public Team teamOf(UUID id) {
        PlayerData d = players.get(id);
        if (d == null || d.team == null) return null;
        return teams.get(d.team.toLowerCase());
    }

    public boolean sameTeam(UUID a, UUID b) {
        Team t = teamOf(a);
        return t != null && t.members.contains(b);
    }

    public synchronized void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(playersFile);
        ConfigurationSection s = y.getConfigurationSection("players");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                ConfigurationSection c = s.getConfigurationSection(k);
                if (c == null) continue;
                PlayerData d = new PlayerData(UUID.fromString(k));
                d.name = c.getString("name", "?");
                d.money = c.getLong("money");
                d.shards = c.getLong("shards");
                d.kills = c.getLong("kills");
                d.deaths = c.getLong("deaths");
                d.playtime = c.getLong("playtime");
                d.bounty = c.getLong("bounty");
                d.streak = c.getLong("streak");
                d.team = c.getString("team");
                ConfigurationSection h = c.getConfigurationSection("homes");
                if (h != null) {
                    for (String n : h.getKeys(false)) {
                        Location l = h.getLocation(n);
                        if (l != null) d.homes.put(n, l);
                    }
                }
                players.put(d.id, d);
            }
        }
        YamlConfiguration t = YamlConfiguration.loadConfiguration(teamsFile);
        ConfigurationSection ts = t.getConfigurationSection("teams");
        if (ts != null) {
            for (String k : ts.getKeys(false)) {
                ConfigurationSection c = ts.getConfigurationSection(k);
                if (c == null) continue;
                Team team = new Team(c.getString("name", k), UUID.fromString(c.getString("owner")));
                for (String m : c.getStringList("members")) team.members.add(UUID.fromString(m));
                teams.put(k.toLowerCase(), team);
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (PlayerData d : players.values()) {
            String p = "players." + d.id;
            y.set(p + ".name", d.name);
            y.set(p + ".money", d.money);
            y.set(p + ".shards", d.shards);
            y.set(p + ".kills", d.kills);
            y.set(p + ".deaths", d.deaths);
            y.set(p + ".playtime", d.playtime);
            y.set(p + ".bounty", d.bounty);
            y.set(p + ".streak", d.streak);
            y.set(p + ".team", d.team);
            for (Map.Entry<String, Location> e : d.homes.entrySet()) {
                y.set(p + ".homes." + e.getKey(), e.getValue());
            }
        }
        YamlConfiguration t = new YamlConfiguration();
        for (Team team : teams.values()) {
            String p = "teams." + team.name.toLowerCase();
            t.set(p + ".name", team.name);
            t.set(p + ".owner", team.owner.toString());
            t.set(p + ".members", team.members.stream().map(UUID::toString).toList());
        }
        try {
            y.save(playersFile);
            t.save(teamsFile);
        } catch (IOException e) {
            pl.getLogger().severe("Mentési hiba: " + e.getMessage());
        }
    }
}
