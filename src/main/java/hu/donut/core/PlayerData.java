package hu.donut.core;

import org.bukkit.Location;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerData {
    public final UUID id;
    public String name = "?";
    public long money, shards, kills, deaths, playtime, bounty, streak;
    public String team;
    public final Map<String, Location> homes = new LinkedHashMap<>();

    public PlayerData(UUID id) {
        this.id = id;
    }
}
