package com.kovuthehusky.simplyuhc;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Joiner;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.FireworkEffect;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Scoreboard;

public class SimplyUHC extends JavaPlugin implements Listener {
    private final ArrayList<PotionEffect> potionEffects = new ArrayList<>();
    private final PotionEffectType[] potionTypes = {
        PotionEffectType.SLOW,
        PotionEffectType.SLOW_DIGGING,
        PotionEffectType.HEAL,
        PotionEffectType.REGENERATION,
        PotionEffectType.DAMAGE_RESISTANCE,
        PotionEffectType.WEAKNESS,
        PotionEffectType.SATURATION
    };

    private FileConfiguration configuration;
    private Scoreboard scoreboard;
    private Server server;
    private World world;

    private boolean isPaused = false;
    private boolean isStarted = false;
    private List<Player> players = new ArrayList<>();
    private List<Player> waiting = new ArrayList<>();

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("ready")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                waiting.remove(player);
                player.setPlayerListName("[READY] " + player.getName());
                server.broadcastMessage(player.getName() + " is now ready.");
                if (waiting.isEmpty()) {
                    if (server.getOnlinePlayers().size() > 1)
                        this.start((int) (Math.sqrt(players.size()) * 400));
                    else
                        sender.sendMessage("Ultra Hardcore requires a minimum of two players.");
                }

            } else {
                sender.sendMessage("Only players can mark themselves as ready.");
            }
            return true;
        }
        return false;
    }

    @Override
    public void onEnable() {
        // Initialize the potion effects list
        for (PotionEffectType type : potionTypes)
            potionEffects.add(new PotionEffect(type, Integer.MAX_VALUE, Byte.MAX_VALUE));
        // Initialize the other class level fields
        this.configuration = this.getConfig();
        this.scoreboard = this.getServer().getScoreboardManager().getMainScoreboard();
        this.server = this.getServer();
        this.world = this.getServer().getWorld("world");
        // Deal with the configuration file
        this.saveDefaultConfig();
        configuration.options().copyDefaults(true);
        this.saveConfig();
        // Reset all of the scoreboard objectives
        if (scoreboard.getObjective("deaths") != null)
            scoreboard.getObjective("deaths").unregister();
        if (scoreboard.getObjective("health") != null)
            scoreboard.getObjective("health").unregister();
        if (scoreboard.getObjective("kills") != null)
            scoreboard.getObjective("kills").unregister();
        // Prepare the server and world for pregame
        server.setDefaultGameMode(GameMode.SURVIVAL);
        server.setSpawnRadius(32);
        world.setDifficulty(Difficulty.HARD);
        world.setGameRuleValue("naturalRegeneration", "true");
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doMobSpawning", "false");
        world.setGameRuleValue("spectatorsGenerateChunks", "false");
        world.setFullTime(0);
        world.getWorldBorder().setCenter(world.getSpawnLocation().getX(), world.getSpawnLocation().getZ());
        world.getWorldBorder().setSize(32);
        for (Entity entity : server.getWorld("world").getLivingEntities())
            if (entity instanceof Monster)
                entity.remove();
        // Set up the waiting list
        waiting.addAll(server.getOnlinePlayers());
        // Register for events
        server.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        this.stop();
    }

    @EventHandler
    public void onPlayerDeath(final PlayerDeathEvent event) {
        if (!this.isStarted)
            return;
        this.players.remove(event.getEntity());
        event.getEntity().setGameMode(GameMode.SPECTATOR);
        // Drop the skull of the killed player
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(event.getEntity());
        meta.setDisplayName(ChatColor.RESET + event.getEntity().getName());
        skull.setItemMeta(meta);
        event.getDrops().add(skull);
        // End the game if only one player is left
        if (players.size() == 1) {
            // Launch a firework about the last player
            Firework firework = (Firework) world.spawnEntity(players.get(0).getLocation(), EntityType.FIREWORK);
            FireworkMeta fireworkMetadata = firework.getFireworkMeta();
            FireworkEffect fireworkEffect = FireworkEffect.builder().with(Type.BALL_LARGE).withColor(Color.YELLOW).withTrail().flicker(true).build();
            fireworkMetadata.addEffect(fireworkEffect);
            fireworkMetadata.setPower(0);
            firework.setFireworkMeta(fireworkMetadata);
            // End the game on the next tick after the death
            new BukkitRunnable() {
                @Override
                public void run() {
                    server.broadcastMessage(players.get(0).getName() + " is the winner. Congratulations!");
                    SimplyUHC.this.stop();
                }
            }.runTaskLater(this, 1);
        }
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        if (!this.isStarted) {
            event.getPlayer().setGameMode(GameMode.SURVIVAL);
            new BukkitRunnable() {
                @Override
                public void run() {
                    waiting.add(event.getPlayer());
                    event.getPlayer().setPlayerListName("[NOT READY] " + event.getPlayer().getName());
                    event.getPlayer().sendMessage("You are not ready. Send " + ChatColor.BOLD + "/ready" + ChatColor.RESET + " in chat to ready up.");
                }
            }.runTaskLater(this, 1);
        } else {
            if (!this.players.contains(event.getPlayer()))
                event.getPlayer().setGameMode(GameMode.SPECTATOR);
            else if (this.isPaused)
                this.freezePlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerMove(final PlayerMoveEvent event) {
        if (this.isPaused)
            event.setCancelled(true);
    }

    private void clearInventory(Player player) {
        player.getInventory().clear();
        player.getEquipment().clear();
        player.getEquipment().setArmorContents(null);
    }

    private void feedPlayer(Player player) {
        player.setFoodLevel(20);
    }

    private void freezePlayer(Player player) {
        player.addPotionEffects(potionEffects);
    }

    private void healPlayer(Player player) {
        player.setHealth(player.getMaxHealth());
    }

    private void start(int size) {
        players = new ArrayList<>(server.getOnlinePlayers());
        // Set up the world border
        world.getWorldBorder().setCenter(world.getSpawnLocation().getX(), world.getSpawnLocation().getZ());
        world.getWorldBorder().setSize(size);
        // Unprotect the spawn
        server.setSpawnRadius(0);
        // Spread the players throughout the world
        String[] names = new String[players.size()];
        for (int i = 0; i < names.length; ++i)
            names[i] = players.get(i).getName();
        int min = size / (int) (Math.sqrt(players.size()) + 1) - 1;
        int x = world.getSpawnLocation().getBlockX();
        int z = world.getSpawnLocation().getBlockZ();
        server.dispatchCommand(server.getConsoleSender(), "spreadplayers " + x + " " + z + " " + min + " " + size / 2 + " false " + Joiner.on(' ').join(names));
        // Freeze all of the players
        for (Player p : players) {
            p.setGameMode(GameMode.SURVIVAL);
            p.setPlayerListName(p.getName());
            this.freezePlayer(p);
            server.dispatchCommand(server.getConsoleSender(), "title " + p.getName() + " title {\"text\":\"Get ready!\"}");
            server.dispatchCommand(server.getConsoleSender(), "title " + p.getName() + " subtitle {\"text\":\"Game begins in 15 seconds...\"}");
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                server.broadcastMessage("The game has started.");
                // Unfreeze and reset all of the players
                for (Player p : players) {
                    SimplyUHC.this.healPlayer(p);
                    SimplyUHC.this.feedPlayer(p);
                    SimplyUHC.this.clearInventory(p);
                    SimplyUHC.this.unfreezePlayer(p);
                    server.dispatchCommand(server.getConsoleSender(), "title " + p.getName() + " title {\"text\":\"Go go go!\"}");
                    server.dispatchCommand(server.getConsoleSender(), "title " + p.getName() + " subtitle {\"text\":\"Good luck and have fun...\"}");
                }
                // Prepare the server and world for the game
                world.setGameRuleValue("naturalRegeneration", "false");
                world.setGameRuleValue("doDaylightCycle", "true");
                world.setGameRuleValue("doMobSpawning", "true");
                // Set up all of the scoreboard objectives
                scoreboard.registerNewObjective("deaths", "deathCount");
                scoreboard.getObjective("deaths").setDisplayName("Deaths");
                scoreboard.registerNewObjective("health", "health");
                scoreboard.getObjective("health").setDisplayName("Health");
                scoreboard.registerNewObjective("kills", "totalPlayerKills");
                scoreboard.getObjective("kills").setDisplayName("Kills");
                if (configuration.getString("display.belowName") != null)
                    scoreboard.getObjective(configuration.getString("display.belowName")).setDisplaySlot(DisplaySlot.BELOW_NAME);
                if (configuration.getString("display.list") != null)
                    scoreboard.getObjective(configuration.getString("display.list")).setDisplaySlot(DisplaySlot.PLAYER_LIST);
                if (configuration.getString("display.sidebar") != null)
                    scoreboard.getObjective(configuration.getString("display.sidebar")).setDisplaySlot(DisplaySlot.SIDEBAR);
            }
        }.runTaskLater(this, 300);
    }

    private void stop() {
        server.broadcastMessage("The game has stopped.");
        this.isStarted = false;
        this.players.clear();
        // Prepare the server and world for postgame
        server.setDefaultGameMode(GameMode.SPECTATOR);
        for (Player p : server.getOnlinePlayers())
            p.setGameMode(GameMode.SPECTATOR);
    }

    private void unfreezePlayer(Player player) {
        for (PotionEffectType type : potionTypes)
            player.removePotionEffect(type);
    }

}
