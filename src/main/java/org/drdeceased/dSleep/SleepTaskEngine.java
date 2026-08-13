package org.drdeceased.dSleep;

import io.papermc.paper.event.player.PlayerDeepSleepEvent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.TimeSkipEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class SleepTaskEngine implements Listener {

    private final DSleep plugin;
    private BossBar activeBossBar;
    private boolean forceSpeedupActive = false;
    private final Set<UUID> hasSleptThisNight = new HashSet<>();

    public SleepTaskEngine(DSleep plugin) {
        this.plugin = plugin;
    }

    public boolean toggleForcedSpeedup() {
        this.forceSpeedupActive = !this.forceSpeedupActive;
        return this.forceSpeedupActive;
    }

    public void cleanupActiveElements() {
        if (activeBossBar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.hideBossBar(activeBossBar);
            }
            activeBossBar = null;
        }
    }

    @EventHandler
    public void onDeepSleep(PlayerDeepSleepEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onVanillaTimeSkip(TimeSkipEvent event) {
        if (event.getSkipReason() == TimeSkipEvent.SkipReason.NIGHT_SKIP) {
            event.setCancelled(true);
        }
    }

    public void processDynamicTime() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) continue;

            List<? extends Player> players = world.getPlayers();
            if (players.isEmpty()) continue;

            double totalPlayers = players.size();
            long sleepingCount = 0;

            for (Player p : players) {
                if (p.isSleeping()) {
                    sleepingCount++;
                    hasSleptThisNight.add(p.getUniqueId());
                }
            }

            if (sleepingCount > 0 || forceSpeedupActive) {
                int extraTicks = forceSpeedupActive ? plugin.forceSpeedupTicks : (int) Math.round((sleepingCount / totalPlayers) * plugin.maxSpeedupTicks);
                long newTime = world.getTime() + extraTicks;
                int multiplier = 1 + extraTicks;

                // --- Progression Calculations ---
                double currentNightTicks = Math.max(0, world.getTime() - 13000);
                float progressPercent = (float) (currentNightTicks / 11000.0);
                if (progressPercent > 1.0f) progressPercent = 1.0f;
                if (progressPercent < 0.0f) progressPercent = 0.0f;

                int displayPercent = Math.round(progressPercent * 100);

                // --- Parsing BossBar Title ---
                String template = forceSpeedupActive ? plugin.forceBossbarTitleTemplate : plugin.bossbarTitleTemplate;
                String parsedTitle = template
                        .replace("%sleeping%", String.valueOf(sleepingCount))
                        .replace("%total%", String.valueOf((int) totalPlayers))
                        .replace("%progress%", String.valueOf(displayPercent))
                        .replace("%multiplier%", String.valueOf(multiplier));

                // --- BossBar Render Engine ---
                if (activeBossBar == null) {
                    activeBossBar = BossBar.bossBar(
                            MiniMessage.miniMessage().deserialize(parsedTitle),
                            progressPercent,
                            plugin.barColor,
                            plugin.barOverlay
                    );
                } else {
                    activeBossBar.name(MiniMessage.miniMessage().deserialize(parsedTitle));
                    activeBossBar.progress(progressPercent);
                }

                for (Player p : players) {
                    p.showBossBar(activeBossBar);
                }

                if (newTime >= 23999 || newTime < 0) {
                    world.setTime(0);
                    world.setStorm(false);
                    world.setThundering(false);
                    forceSpeedupActive = false;

                    cleanupActiveElements();

                    for (Player p : players) {
                        if (hasSleptThisNight.contains(p.getUniqueId())) {
                            p.setStatistic(Statistic.TIME_SINCE_REST, 0);
                        }
                        if (p.isSleeping()) {
                            p.wakeup(true);
                        }
                    }
                    hasSleptThisNight.clear();
                } else {
                    world.setTime(newTime);
                }
            } else {
                cleanupActiveElements();
                if (world.getTime() < 100) {
                    hasSleptThisNight.clear();
                }
            }
        }
    }
}
