package com.codeski.simplyuhc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class SimplyUHC extends JavaPlugin implements Listener {
	private FileConfiguration configuration;
	private CommandSender console;
	private int counter = 0;
	private final PotionEffectType[] effects = { PotionEffectType.SLOW, PotionEffectType.SLOW_DIGGING,
			PotionEffectType.DAMAGE_RESISTANCE, PotionEffectType.INVISIBILITY, PotionEffectType.BLINDNESS };
	private Player[] players;
	private PluginManager pm;
	private Server server;
	private boolean started = false;
	private Timer timer;
	private World world;

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase("uhc")) {
			if (args.length < 1) {
				sender.sendMessage("Usage: /uhc <command>");
				sender.sendMessage("Commands: start stop");
				return true;
			}
			if (args[0].equalsIgnoreCase("start")) {
				if (args.length < 5) {
					sender.sendMessage("Usage: /uhc start <x> <z> <radius> <countdown>");
					return true;
				}
				int x = Integer.parseInt(args[1]);
				int z = Integer.parseInt(args[2]);
				int radius = Integer.parseInt(args[3]);
				int countdown = Integer.parseInt(args[4]) * 20;
				if (radius < 1)
					sender.sendMessage("Radius must be positive integer.");
				else if (countdown < 1)
					sender.sendMessage("Countdown must be a positive integer.");
				else {
					server.dispatchCommand(console, "gamerule naturalRegeneration false");
					server.dispatchCommand(console, "scoreboard objectives add deaths deathCount Deaths");
					server.dispatchCommand(console, "scoreboard objectives add health health Health");
					server.dispatchCommand(console, "scoreboard objectives add kills health totalPlayerKills");
					if (configuration.getString("display.name") != null)
						if (configuration.getString("display.name").equals("deaths"))
							server.dispatchCommand(console, "scoreboard objectives setdisplay name deaths");
						else if (configuration.getString("display.name").equals("health"))
							server.dispatchCommand(console, "scoreboard objectives setdisplay name health");
						else if (configuration.getString("display.name").equals("kills"))
							server.dispatchCommand(console, "scoreboard objectives setdisplay name kills");
					if (configuration.getString("display.list") != null)
						if (configuration.getString("display.list").equals("deaths"))
							server.dispatchCommand(console, "scoreboard objectives setdisplay list deaths");
						else if (configuration.getString("display.list").equals("health"))
							server.dispatchCommand(console, "scoreboard objectives setdisplay list health");
						else if (configuration.getString("display.list").equals("kills"))
							server.dispatchCommand(console, "scoreboard objectives setdisplay list kills");
					if (configuration.getString("display.sidebar") != null)
						if (configuration.getString("display.sidebar").equals("deaths"))
							server.dispatchCommand(console, "scoreboard objectives setdisplay sidebar deaths");
						else if (configuration.getString("display.sidebar").equals("health"))
							server.dispatchCommand(console, "scoreboard objectives setdisplay sidebar health");
						else if (configuration.getString("display.sidebar").equals("kills"))
							server.dispatchCommand(console, "scoreboard objectives setdisplay sidebar kills");
					players = server.getOnlinePlayers();
					for (Player p : players) {
						this.healPlayer(p);
						this.feedPlayer(p);
						this.clearInventory(p);
						this.freezePlayer(p, countdown);
					}
					int spawnY = world.getMaxHeight() - 1;
					for (; world.getBlockAt(x, spawnY, z).getType() == Material.AIR; --spawnY)
						;
					world.setSpawnLocation(x, spawnY, z);
					this.generateBorder(x, z, radius, Material.BEDROCK);
					if (pm.isPluginEnabled("WorldBorder"))
						server.dispatchCommand(console, "wb world set " + radius + " " + x + " " + z);
					int playerRadius = (int) (radius * 0.75);
					int playerCount = players.length;
					for (int i = 0; i < playerCount; ++i) {
						int playerX = (int) (Math.cos(2 * Math.PI * i / playerCount) * playerRadius) + x;
						int playerZ = (int) (Math.sin(2 * Math.PI * i / playerCount) * playerRadius) + z;
						int playerY = world.getMaxHeight() - 1;
						for (; world.getBlockAt(playerX, playerY, playerZ).getType() == Material.AIR; --playerY)
							;
						Block b = world.getBlockAt(playerX, playerY, playerZ);
						Material m = b.getType();
						if (m == Material.STATIONARY_WATER) {
							b.getRelative(BlockFace.UP).setType(Material.WATER_LILY);
							playerY += 2;
						} else if (m == Material.STATIONARY_LAVA || m == Material.WATER || m == Material.LAVA) {
							b.getRelative(BlockFace.UP).setType(Material.GLASS);
							playerY += 3;
						} else
							playerY += 1;
						players[i].teleport(new Location(world, playerX + 0.5, playerY + 0.5, playerZ + 0.5));
					}
					server.broadcastMessage("Game begins in " + countdown / 20 + " seconds... Get ready!");
					timer = new Timer();
					timer.scheduleAtFixedRate(new TimerTask() {
						@Override
						public void run() {
							if (counter < 1) {
								Player[] players = server.getOnlinePlayers();
								for (Player p : players) {
									p.setGameMode(GameMode.SURVIVAL);
									SimplyUHC.this.healPlayer(p);
									SimplyUHC.this.feedPlayer(p);
									SimplyUHC.this.clearInventory(p);
									SimplyUHC.this.unfreezePlayer(p);
								}
								server.dispatchCommand(console, "time set 0");
								server.broadcastMessage("The game has begun. Good luck!");
							}
							else
								server.broadcastMessage(10 * counter + " minutes passed.");
							++counter;
						}
					}, countdown / 20 * 1000, 600000);
					started = true;
				}
				return true;
			} else if (args[0].equalsIgnoreCase("stop")) {
				if (players.length == 1)
					server.broadcastMessage("Game over! " + players[0].getName() + " is the winner!");
				else
					server.broadcastMessage("Game over!");
				players = null;
				server.dispatchCommand(console, "gamerule naturalRegeneration true");
				server.dispatchCommand(console, "scoreboard objectives remove deaths");
				server.dispatchCommand(console, "scoreboard objectives remove health");
				server.dispatchCommand(console, "wb world clear");
				timer.cancel();
				counter = 0;
				started = false;
				return true;
			} else if (args[0].equalsIgnoreCase("freeze"))
				this.freezePlayer(null);
		}
		return false;
	}

	@Override
	public void onEnable() {
		server = this.getServer();
		this.saveDefaultConfig();
		configuration = this.getConfig();
		configuration.options().copyDefaults(true);
		this.saveConfig();
		console = server.getConsoleSender();
		pm = server.getPluginManager();
		world = server.getWorld("world");
		pm.registerEvents(this, this);
	}

	@EventHandler
	public void onPlayerDeath(final PlayerDeathEvent event) {
		ItemStack skull = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
		SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
		skullMeta.setOwner(event.getEntity().getName());
		skullMeta.setDisplayName(ChatColor.RESET + event.getEntity().getName());
		skull.setItemMeta(skullMeta);
		event.getDrops().add(skull);
		ArrayList<Player> pl = new ArrayList<Player>(Arrays.asList(players));
		pl.remove(event.getEntity());
		players = pl.toArray(new Player[pl.size()]);
		if (players.length == 1)
			server.dispatchCommand(console, "uhc stop");
		else
			event.getEntity().kickPlayer(event.getDeathMessage().replaceFirst(event.getEntity().getName(), "You") + ".");
	}

	@EventHandler
	public void onPlayerLogin(final PlayerLoginEvent event) {
		if (started) {
			for (Player p : players)
				if (event.getPlayer().getName().equalsIgnoreCase(p.getName()))
					return;
			event.disallow(Result.KICK_OTHER, "You cannot rejoin until the game is over. Sorry!");
		}
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
		this.freezePlayer(player, Integer.MAX_VALUE);
	}

	private void freezePlayer(Player player, int ticks) {
		ArrayList<PotionEffect> potions = new ArrayList<PotionEffect>();
		for (PotionEffectType type : effects)
			potions.add(new PotionEffect(type, ticks, Byte.MAX_VALUE));
		player.addPotionEffects(potions);
	}

	private void generateBorder(int x0, int z0, int radius, Material type) {
		int x = radius, z = 0;
		int radiusError = 1 - x;
		while (x >= z) {
			for (int y = 0; y < world.getMaxHeight(); ++y) {
				world.getBlockAt(x + x0, y, z + z0).setType(type);
				world.getBlockAt(x + x0, y, z + z0).setType(type);
				world.getBlockAt(z + x0, y, x + z0).setType(type);
				world.getBlockAt(-x + x0, y, z + z0).setType(type);
				world.getBlockAt(-z + x0, y, x + z0).setType(type);
				world.getBlockAt(-x + x0, y, -z + z0).setType(type);
				world.getBlockAt(-z + x0, y, -x + z0).setType(type);
				world.getBlockAt(x + x0, y, -z + z0).setType(type);
				world.getBlockAt(z + x0, y, -x + z0).setType(type);
			}
			++z;
			if (radiusError < 0)
				radiusError += 2 * z + 1;
			else
				radiusError += 2 * (z - --x + 1);
		}
	}

	private void healPlayer(Player player) {
		player.setHealth(player.getMaxHealth());
	}

	private void unfreezePlayer(Player player) {
		for (PotionEffectType type : effects)
			player.removePotionEffect(type);
	}
}
