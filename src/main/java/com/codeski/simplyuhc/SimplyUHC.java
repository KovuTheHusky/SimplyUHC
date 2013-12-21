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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class SimplyUHC extends JavaPlugin implements Listener {
	private class SimplyTimer extends TimerTask {
		private int count = 0;

		@Override
		public void run() {
			if (count > 0)
				server.broadcastMessage(10 * count + " minutes passed.");
			else {
				Player[] players = server.getOnlinePlayers();
				for (Player p : players) {
					p.setGameMode(GameMode.SURVIVAL);
					SimplyUHC.this.healPlayer(p);
					SimplyUHC.this.feedPlayer(p);
					SimplyUHC.this.clearInventory(p);
					SimplyUHC.this.unfreezePlayer(p);
				}
				server.dispatchCommand(console, "gamerule naturalRegeneration false");
				server.dispatchCommand(console, "gamerule doDaylightCycle true");
				server.dispatchCommand(console, "gamerule doMobSpawning true");
				server.dispatchCommand(console, "scoreboard objectives add deaths deathCount Deaths");
				server.dispatchCommand(console, "scoreboard objectives add health health Health");
				server.dispatchCommand(console, "scoreboard objectives add kills health totalPlayerKills");
				if (configuration.getString("display.name") != null)
					server.dispatchCommand(console, "scoreboard objectives setdisplay name " + configuration.getString("display.name"));
				if (configuration.getString("display.list") != null)
					server.dispatchCommand(console, "scoreboard objectives setdisplay list " + configuration.getString("display.list"));
				if (configuration.getString("display.sidebar") != null)
					server.dispatchCommand(console, "scoreboard objectives setdisplay sidebar " + configuration.getString("display.sidebar"));
				server.broadcastMessage("The game has been started. Good luck!");
			}
			++count;
		}
	}

	private FileConfiguration configuration;
	private CommandSender console;
	private final PotionEffectType[] effects = { PotionEffectType.SLOW, PotionEffectType.SLOW_DIGGING, PotionEffectType.DAMAGE_RESISTANCE, PotionEffectType.INVISIBILITY, PotionEffectType.BLINDNESS };
	private ArrayList<Player> players;
	private Server server;
	private boolean started = false;
	private Timer timer;
	private World world;

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase("uhc"))
			if (args.length < 1) {
				sender.sendMessage(ChatColor.RED + "Usage: /uhc <command>");
				sender.sendMessage(ChatColor.RED + "Commands: start stop");
				return true;
			}
			else if (args[0].equalsIgnoreCase("start")) {
				if (started)
					sender.sendMessage("There is already a game in progress.");
				else {
					int countdown = 30;
					switch (args.length) {
						case 3:
							countdown = Math.abs(Integer.parseInt(args[2]));
						case 2:
							this.start(Math.abs(Integer.parseInt(args[1])), countdown * 20);
							break;
						default:
							sender.sendMessage(ChatColor.RED + "Usage: /uhc start <radius> [countdown]");
					}
				}
				return true;
			} else if (args[0].equalsIgnoreCase("stop")) {
				if (!started)
					sender.sendMessage("There is no game in progress.");
				else
					this.stop();
				return true;
			}
		return false;
	}

	@Override
	public void onEnable() {
		this.saveDefaultConfig();
		configuration = this.getConfig();
		configuration.options().copyDefaults(true);
		this.saveConfig();
		server = this.getServer();
		console = server.getConsoleSender();
		world = server.getWorld("world");
		server.getPluginManager().registerEvents(this, this);
		server.dispatchCommand(console, "defaultgamemode survival");
		server.dispatchCommand(console, "difficulty hard");
		server.dispatchCommand(console, "gamerule doDaylightCycle false");
		server.dispatchCommand(console, "gamerule doMobSpawning false");
		server.dispatchCommand(console, "time set 0");
		for (Entity entity : server.getWorld("world").getLivingEntities())
			if (entity instanceof Monster)
				entity.remove();
	}

	@EventHandler
	public void onPlayerDeath(final PlayerDeathEvent event) {
		if (started) {
			ItemStack skull = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
			SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
			skullMeta.setOwner(event.getEntity().getName());
			skullMeta.setDisplayName(ChatColor.RESET + event.getEntity().getName());
			skull.setItemMeta(skullMeta);
			event.getDrops().add(skull);
			players.remove(event.getEntity());
			if (players.size() == 1) {
				server.broadcastMessage(players.get(0).getName() + " is the winner!");
				this.stop();
			}
			else
				event.getEntity().kickPlayer(event.getDeathMessage().replaceFirst(event.getEntity().getName(), "You") + ".");
		}
	}

	@EventHandler
	public void onPlayerLogin(final PlayerLoginEvent event) {
		if (started && !players.contains(event.getPlayer()))
			event.disallow(Result.KICK_OTHER, "You cannot rejoin until the game is over. Sorry!");
	}

	private void buildBorder(int x0, int z0, int radius, Material type) {
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

	private Location[] buildSpawns(int x0, int z0, int radius) {
		Location[] locations = new Location[players.size()];
		for (int i = 0; i < players.size(); ++i) {
			int x = (int) (Math.cos(2 * Math.PI * i / players.size()) * radius) + x0;
			int z = (int) (Math.sin(2 * Math.PI * i / players.size()) * radius) + z0;
			int y = world.getMaxHeight() - 1;
			for (; world.getBlockAt(x, y, z).getType() == Material.AIR; --y)
				;
			Block b = world.getBlockAt(x, y, z);
			Material m = b.getType();
			if (m == Material.STATIONARY_LAVA || m == Material.WATER || m == Material.LAVA) {
				b.getRelative(BlockFace.UP).setType(Material.GLASS);
				y += 3;
			} else if (m == Material.STATIONARY_WATER) {
				b.getRelative(BlockFace.UP).setType(Material.WATER_LILY);
				y += 2;
			} else
				y += 1;
			locations[i] = new Location(world, x + 0.5, y + 0.5, z + 0.5);
		}
		return locations;
	}

	private void clearInventory(Player player) {
		player.getInventory().clear();
		player.getEquipment().clear();
		player.getEquipment().setArmorContents(null);
	}

	private void feedPlayer(Player player) {
		player.setFoodLevel(20);
	}

	private void freezePlayer(Player player, int ticks) {
		ArrayList<PotionEffect> potions = new ArrayList<PotionEffect>();
		for (PotionEffectType type : effects)
			potions.add(new PotionEffect(type, ticks, Byte.MAX_VALUE));
		player.addPotionEffects(potions);
	}

	private void healPlayer(Player player) {
		player.setHealth(player.getMaxHealth());
	}

	private void start(int radius, int countdown) {
		this.buildBorder(world.getSpawnLocation().getBlockX(), world.getSpawnLocation().getBlockZ(), radius, Material.BEDROCK);
		if (server.getPluginManager().isPluginEnabled("WorldBorder"))
			server.dispatchCommand(console, "wb world set " + radius + " " + world.getSpawnLocation().getBlockX() + " " + world.getSpawnLocation().getBlockZ());
		players = new ArrayList<Player>(Arrays.asList(server.getOnlinePlayers()));
		Location[] locations = this.buildSpawns(world.getSpawnLocation().getBlockX(), world.getSpawnLocation().getBlockZ(), (int) (radius * 0.75));
		for (int i = 0; i < players.size(); ++i) {
			this.teleportPlayer(players.get(i), locations[i]);
			this.freezePlayer(players.get(i), countdown);
		}
		server.broadcastMessage("Game begins in " + countdown / 20 + " seconds... Get ready!");
		timer = new Timer();
		timer.scheduleAtFixedRate(new SimplyTimer(), countdown / 20 * 1000, 600000);
		started = true;
	}

	private void stop() {
		server.broadcastMessage("The game has been stopped.");
		started = false;
		timer.cancel();
		if (server.getPluginManager().isPluginEnabled("WorldBorder"))
			server.dispatchCommand(console, "wb world clear");
		server.dispatchCommand(console, "gamerule naturalRegeneration true");
		server.dispatchCommand(console, "gamerule doDaylightCycle false");
		server.dispatchCommand(console, "gamerule doMobSpawning false");
		server.dispatchCommand(console, "time set 0");
		server.dispatchCommand(console, "scoreboard objectives remove deaths");
		server.dispatchCommand(console, "scoreboard objectives remove health");
		server.dispatchCommand(console, "scoreboard objectives remove kills");
	}

	private void teleportPlayer(Player player, Location location) {
		player.teleport(location);
	}

	private void unfreezePlayer(Player player) {
		for (PotionEffectType type : effects)
			player.removePotionEffect(type);
	}
}
