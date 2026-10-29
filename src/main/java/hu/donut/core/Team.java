package hu.donut.core;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Team {
    public String name;
    public UUID owner;
    public final Set<UUID> members = new LinkedHashSet<>();
    public final Set<UUID> invites = ConcurrentHashMap.newKeySet();

    public Team(String name, UUID owner) {
        this.name = name;
        this.owner = owner;
        this.members.add(owner);
    }
}
