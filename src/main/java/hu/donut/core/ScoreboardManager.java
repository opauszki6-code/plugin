package hu.donut.core;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ScoreboardManager {
    private static final int LINES = 10;
    private final DonutCore pl;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public ScoreboardManager(DonutCore pl) {
        this.pl = pl;
    }

    private Scoreboard create(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective o = sb.registerNewObjective("donut", "dummy", Util.c("<gold><bold>DONUT SMP"));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int i = 0; i < LINES; i++) {
            Team t = sb.registerNewTeam("l" + i);
            String entry = "§" + Integer.toHexString(i) + "§r";
            t.addEntry(entry);
            o.getScore(entry).setScore(LINES - i);
        }
        p.setScoreboard(sb);
        boards.put(p.getUniqueId(), sb);
        return sb;
    }

    public void update(Player p, PlayerData d) {
        Scoreboard sb = boards.get(p.getUniqueId());
        if (sb == null) sb = create(p);
        pl.store.teamOf(p.getUniqueId());
        var team = pl.store.teamOf(p.getUniqueId());
        String[] lines = {
                "<dark_gray>――――――――――――",
                "<gold>$ <white>Pénz: <green>" + Util.fmt(d.money),
                "<aqua>✦ <white>Shard: <aqua>" + Util.fmt(d.shards),
                "<red>⚔ <white>Ölés: <red>" + d.kills,
                "<gray>☠ <white>Halál: <gray>" + d.deaths,
                "<yellow>⌚ <white>Játékidő: <yellow>" + Util.time(d.playtime),
                "<light_purple>⚑ <white>Csapat: <light_purple>" + (team == null ? "-" : team.name),
                "<dark_gray>――――――――――――",
                "<white>Online: <green>" + Bukkit.getOnlinePlayers().size(),
                "<gold>play.donut.hu"
        };
        for (int i = 0; i < LINES; i++) {
            Team t = sb.getTeam("l" + i);
            if (t == null) continue;
            Component next = Util.c(lines[i]);
            if (!next.equals(t.prefix())) t.prefix(next);
        }
    }

    public void remove(Player p) {
        boards.remove(p.getUniqueId());
        p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void clearAll() {
        for (Player p : Bukkit.getOnlinePlayers()) remove(p);
    }
}
