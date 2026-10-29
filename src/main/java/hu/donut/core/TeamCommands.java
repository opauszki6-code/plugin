package hu.donut.core;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class TeamCommands implements CommandExecutor, TabCompleter {
    private final DonutCore pl;
    private final DataStore st;

    public TeamCommands(DonutCore pl) {
        this.pl = pl;
        this.st = pl.store;
    }

    private String nameOf(UUID id) {
        return st.get(id).name;
    }

    private void broadcast(Team t, String msg) {
        for (UUID m : t.members) {
            Player p = Bukkit.getPlayer(m);
            if (p != null) Util.send(p, msg);
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) return true;
        if (a.length == 0) {
            help(p);
            return true;
        }
        PlayerData me = st.get(p);
        Team mine = st.teamOf(p.getUniqueId());
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (a.length < 2) { Util.send(p, "<red>/team create <név>"); return true; }
                if (mine != null) { Util.send(p, "<red>Már van csapatod."); return true; }
                if (!a[1].matches("[A-Za-z0-9]{3,12}")) { Util.send(p, "<red>A név 3-12 karakter, csak betű és szám."); return true; }
                if (st.teams.containsKey(a[1].toLowerCase(Locale.ROOT))) { Util.send(p, "<red>Ez a név már foglalt."); return true; }
                Team t = new Team(a[1], p.getUniqueId());
                st.teams.put(a[1].toLowerCase(Locale.ROOT), t);
                me.team = t.name;
                Util.send(p, "<green>Csapat létrehozva: <light_purple>" + t.name);
            }
            case "invite" -> {
                if (mine == null) { Util.send(p, "<red>Nincs csapatod."); return true; }
                if (!mine.owner.equals(p.getUniqueId())) { Util.send(p, "<red>Csak a vezető hívhat meg."); return true; }
                if (a.length < 2) { Util.send(p, "<red>/team invite <játékos>"); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { Util.send(p, "<red>A játékos nincs fent."); return true; }
                if (mine.members.size() >= pl.getConfig().getInt("team-max-members", 8)) { Util.send(p, "<red>A csapat tele van."); return true; }
                mine.invites.add(t.getUniqueId());
                Util.send(p, "<green>Meghívó elküldve: <white>" + t.getName());
                Util.send(t, "<white>" + p.getName() + " <yellow>meghívott a(z) <light_purple>" + mine.name + " <yellow>csapatba. <green>/team accept " + mine.name);
            }
            case "accept" -> {
                if (a.length < 2) { Util.send(p, "<red>/team accept <csapat>"); return true; }
                if (mine != null) { Util.send(p, "<red>Már van csapatod."); return true; }
                Team t = st.teams.get(a[1].toLowerCase(Locale.ROOT));
                if (t == null || !t.invites.remove(p.getUniqueId())) { Util.send(p, "<red>Nincs meghívód ebbe a csapatba."); return true; }
                if (t.members.size() >= pl.getConfig().getInt("team-max-members", 8)) { Util.send(p, "<red>A csapat tele van."); return true; }
                t.members.add(p.getUniqueId());
                me.team = t.name;
                broadcast(t, "<white>" + p.getName() + " <green>csatlakozott a csapathoz!");
            }
            case "leave" -> {
                if (mine == null) { Util.send(p, "<red>Nincs csapatod."); return true; }
                if (mine.owner.equals(p.getUniqueId())) { Util.send(p, "<red>A vezető nem léphet ki. Használd: /team disband"); return true; }
                mine.members.remove(p.getUniqueId());
                me.team = null;
                Util.send(p, "<yellow>Kiléptél a csapatból.");
                broadcast(mine, "<white>" + p.getName() + " <yellow>kilépett.");
            }
            case "kick" -> {
                if (mine == null || !mine.owner.equals(p.getUniqueId())) { Util.send(p, "<red>Csak a csapatvezető tud kirúgni."); return true; }
                if (a.length < 2) { Util.send(p, "<red>/team kick <játékos>"); return true; }
                PlayerData t = st.byName(a[1]);
                if (t == null || !mine.members.contains(t.id) || t.id.equals(p.getUniqueId())) { Util.send(p, "<red>Nem tudod kirúgni."); return true; }
                mine.members.remove(t.id);
                t.team = null;
                broadcast(mine, "<white>" + t.name + " <red>ki lett rúgva.");
                Player tp = Bukkit.getPlayer(t.id);
                if (tp != null) Util.send(tp, "<red>Kirúgtak a csapatból.");
            }
            case "disband" -> {
                if (mine == null || !mine.owner.equals(p.getUniqueId())) { Util.send(p, "<red>Csak a csapatvezető oszlathatja fel."); return true; }
                broadcast(mine, "<red>A csapat feloszlatva.");
                for (UUID m : mine.members) st.get(m).team = null;
                st.teams.remove(mine.name.toLowerCase(Locale.ROOT));
            }
            case "info" -> {
                Team t = a.length > 1 ? st.teams.get(a[1].toLowerCase(Locale.ROOT)) : mine;
                if (t == null) { Util.send(p, "<red>Nincs ilyen csapat."); return true; }
                long kills = 0;
                StringBuilder sb = new StringBuilder();
                for (UUID m : t.members) {
                    PlayerData d = st.get(m);
                    kills += d.kills;
                    boolean on = Bukkit.getPlayer(m) != null;
                    sb.append(on ? "<green>" : "<gray>").append(d.name).append(m.equals(t.owner) ? "<gold>★" : "").append("<dark_gray>, ");
                }
                Util.raw(p, "<light_purple><bold>━━ " + t.name + " ━━");
                Util.raw(p, "<gray>Vezető: <white>" + nameOf(t.owner) + " <gray>| Tagok: <white>" + t.members.size()
                        + " <gray>| Összes ölés: <red>" + kills);
                Util.raw(p, sb.toString());
            }
            case "list" -> {
                Util.raw(p, "<light_purple><bold>━━ Csapatok ━━");
                List<Team> ts = new ArrayList<>(st.teams.values());
                ts.sort((x, y) -> Integer.compare(y.members.size(), x.members.size()));
                for (int i = 0; i < Math.min(10, ts.size()); i++)
                    Util.raw(p, "<white>" + ts.get(i).name + " <gray>- " + ts.get(i).members.size() + " tag");
                if (ts.isEmpty()) Util.raw(p, "<gray>Még nincs csapat.");
            }
            default -> help(p);
        }
        return true;
    }

    private void help(Player p) {
        Util.raw(p, "<light_purple><bold>━━ Csapat parancsok ━━");
        Util.raw(p, "<yellow>/team create <név> <gray>- létrehozás");
        Util.raw(p, "<yellow>/team invite <játékos> <gray>- meghívás");
        Util.raw(p, "<yellow>/team accept <csapat> <gray>- meghívó elfogadása");
        Util.raw(p, "<yellow>/team leave | kick <játékos> | disband");
        Util.raw(p, "<yellow>/team info [csapat] | list");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        List<String> opts = new ArrayList<>();
        if (a.length == 1) opts.addAll(List.of("create", "invite", "accept", "leave", "kick", "disband", "info", "list"));
        else if (a.length == 2) {
            String sub = a[0].toLowerCase(Locale.ROOT);
            if (sub.equals("invite") || sub.equals("kick")) Bukkit.getOnlinePlayers().forEach(p -> opts.add(p.getName()));
            else if (sub.equals("accept") || sub.equals("info")) st.teams.values().forEach(t -> opts.add(t.name));
        }
        for (String o : opts) if (o.toLowerCase(Locale.ROOT).startsWith(last)) out.add(o);
        return out;
    }
}
