package org.drdeceased.dSleep;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;

public class DSleep extends JavaPlugin {

    public int maxSpeedupTicks;
    public int forceSpeedupTicks;
    public String bossbarTitleTemplate;
    public String forceBossbarTitleTemplate;
    public BossBar.Color barColor;
    public BossBar.Overlay barOverlay;

    private SleepTaskEngine engine;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPluginConfig();

        this.engine = new SleepTaskEngine(this);
        getServer().getPluginManager().registerEvents(this.engine, this);

        Bukkit.getScheduler().runTaskTimer(this, this.engine::processDynamicTime, 0L, 1L);

        if (getCommand("dynamicsleep") != null) {
            getCommand("dynamicsleep").setExecutor(this::onCommand);
            getCommand("dynamicsleep").setTabCompleter(this::onTabComplete);
        }
    }

    @Override
    public void onDisable() {
        if (this.engine != null) {
            this.engine.cleanupActiveElements();
        }
    }

    public void loadPluginConfig() {
        reloadConfig();
        FileConfiguration config = getConfig();
        this.maxSpeedupTicks = config.getInt("max-speedup-ticks", 60);
        this.forceSpeedupTicks = config.getInt("force-speedup-ticks", 50);
        this.bossbarTitleTemplate = config.getString("bossbar-title", "Sleeping: %sleeping%/%total% | Progress: %progress%%");
        this.forceBossbarTitleTemplate = config.getString("force-bossbar-title", "Forced Speedup | Progress: %progress%%");

        try {
            this.barColor = BossBar.Color.valueOf(config.getString("bossbar-color", "GOLD").toUpperCase());
        } catch (IllegalArgumentException e) {
            this.barColor = BossBar.Color.YELLOW;
        }

        try {
            this.barOverlay = BossBar.Overlay.valueOf(config.getString("bossbar-overlay", "PROGRESS").toUpperCase());
        } catch (IllegalArgumentException e) {
            this.barOverlay = BossBar.Overlay.PROGRESS;
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("dynamicsleeptime.admin")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>You do not have permission.</red>"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<gold>Usage: /dsleep [reload | force]</gold>"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("reload")) {
            loadPluginConfig();
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>Config reloaded!</green>"));
            return true;
        }

        if (subCommand.equals("force")) {
            if (this.engine != null) {
                boolean activeState = this.engine.toggleForcedSpeedup();
                if (activeState) {
                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>Forced night speedup enabled!</green>"));
                } else {
                    sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Forced night speedup disabled.</red>"));
                }
            }
            return true;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1 && sender.hasPermission("dynamicsleeptime.admin")) {
            completions.add("reload");
            completions.add("force");
        }
        return completions;
    }
}