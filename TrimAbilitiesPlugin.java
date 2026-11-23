package de.trimabilities;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class TrimAbilitiesPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(new AbilityListener(this), this);
        getLogger().info("TrimAbilities enabled. Press F (swap hands) to use a trim ability.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage("TrimAbilities: config reloaded.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            for (Ability a : Ability.values()) {
                sender.sendMessage(a.key() + " - " + a.description());
            }
            return true;
        }
        sender.sendMessage("/trimabilities <reload|list>");
        return true;
    }
}
