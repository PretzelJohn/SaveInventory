package com.pretzel.dev.saveinventory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.pretzel.dev.saveinventory.commands.*;
import com.pretzel.dev.saveinventory.data.*;
import com.pretzel.dev.saveinventory.db.*;
import com.pretzel.dev.saveinventory.lib.*;

import net.md_5.bungee.api.ChatColor;

public class SaveInventory extends JavaPlugin implements Listener {
	public static final String PLUGIN_NAME = "SaveInventory";
	public static final String PREFIX = ChatColor.GOLD+"["+PLUGIN_NAME+"] ";
	
	private static final Table BACKUP = new Table("backup", new Column[]{new Column("uuid","VARCHAR(36)",true,true), new Column("btime","VARCHAR(23)",true,true), new Column("inventory","LONGTEXT",true), new Column("enderchest","LONGTEXT",true)});
	private static final Table BUNGEE = new Table("bungee", new Column[]{new Column("uuid","VARCHAR(36)",true,true), new Column("inventory","LONGTEXT",true), new Column("enderchest","LONGTEXT",true), new Column("health","DOUBLE"), new Column("food","INT"), new Column("saturation","FLOAT"), new Column("exhaustion","FLOAT"), new Column("xp","FLOAT"), new Column("xp_level", "INT"), new Column("gamemode", "TEXT")});
	private static final Table COMPLETE = new Table("complete", new Column[]{new Column("uuid", "VARCHAR(36)", true, true), new Column("completed", "TEXT", true)});
	private static final Table[] TABLES = {BACKUP, BUNGEE, COMPLETE};
	private HashMap<UUID, PlayerData> data;
	private Database db;
	private String mainPath;
	
	private SaveInventory instance;
	
	private boolean bStats;
	private int saves;
	private boolean backupOnJoin;
	private boolean backupOnDeath;
	private boolean bungee;
	
    //Initial plugin load/unload
	public void onEnable() {
		this.instance = this;
		this.data = new HashMap<UUID, PlayerData>();
        this.getConfig().options().copyDefaults();
        this.saveDefaultConfig();
        this.loadSettings();
        
        if(this.bStats) new Metrics(this, 9925);
        
        this.getCommand("saveinventory").setExecutor(new Commands(this));
        this.getCommand("saveinventory").setTabCompleter(new CommandsTab());
        this.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)this);
        
        Util.consoleMsg(PREFIX+PLUGIN_NAME+" is running!");
    }
	public void onDisable() {
		this.disableDatabase();
	}
	
	//Loads the active config.yml
	public void loadSettings() {
        this.mainPath = this.getDataFolder().getPath()+"/";
        final File file = new File(this.mainPath, "config.yml");
        try {
			file.createNewFile();
		} catch (IOException e) {
			e.printStackTrace();
		}
        ConfigUpdater updater = new ConfigUpdater(this.getTextResource("config.yml"), file);
        FileConfiguration cfg = updater.updateConfig(file, PREFIX);
        
        this.bStats = cfg.getBoolean("bStats", true);
        this.saves = cfg.getInt("SavesPerPlayer", 10);
        this.backupOnJoin = cfg.getBoolean("BackupOnJoin", false);
        this.backupOnDeath = cfg.getBoolean("BackupOnDeath", false);
        this.bungee = cfg.getBoolean("BungeeAutoSync", false);
        
        this.enableDatabase(cfg);
        this.updateTables(cfg);
        this.deleteSaveFile();
    }
	
	//Enable the database based on the config settings (MySQL or SQLite)
	private void enableDatabase(FileConfiguration cfg) {
		final File file = new File(this.mainPath, "state");
		String lastType = (file.exists() ? Util.readFile(file)[1] : null);
		
        final ConfigurationSection section = cfg.getConfigurationSection("database");
        boolean currentType = section.getBoolean("MySQL", false);
        if(this.db == null) {
        	Util.consoleMsg(PREFIX+"Enabling database...");
        	if(lastType != null && lastType.equals("true") != currentType) {
        		section.set("MySQL", !currentType);
        		this.db = new Database(this, section, TABLES);
        		section.set("MySQL", currentType);
        		this.migrateDatabase(section);
        	} else this.db = new Database(this, section, TABLES);
        	Util.consoleMsg(PREFIX+"... "+this.db.getVersion()+" enabled!");
        } else if(lastType != null && lastType.equals("true") != currentType) {
        	this.migrateDatabase(section);
        } else if(!this.db.isValid(30)) {
        	try { this.db.reconnect(30); }
        	catch(Exception ignored) {}
        }
        if(this.db != null) Util.writeFile(file, "# DO NOT TOUCH OR DELETE THIS FILE\n"+this.db.isMySQL());
	}
	private void disableDatabase() {
		if(this.db != null) {
			Util.consoleMsg(PREFIX+"Disabling database...");
			for(Player player : Bukkit.getOnlinePlayers()) {
				this.saveBungee(player, false);
				this.saveBackups(this.data.get(player.getUniqueId()), null);
			}
			
			int count = 0;
			while(Bukkit.getScheduler().getActiveWorkers().size() > 0) {
				try { 
					this.wait(1000);
					count++;
					if(count > 30) break;
				}
				catch(IllegalMonitorStateException ignored) {}
				catch(Exception e) { Util.errorMsg(e); }
			}
			
			this.db.disconnect();
			Util.consoleMsg(PREFIX+"... database disabled!");
		}
	}
	private void migrateDatabase(ConfigurationSection section) {
		Util.consoleMsg(PREFIX+"Migrating database...");
    	Database db2 = this.db.migrate(section);
    	this.db = db2;
    	if(this.db != null) Util.consoleMsg(PREFIX+"... database migrated!");
	}
	
	//Updates the BungeeAutoSync feature's table to include new features
	private void updateTables(FileConfiguration cfg) {
		if(this.db == null) return;
		final Column[] columns = new Column[]{new Column("health","DOUBLE"), new Column("food","INT"), new Column("saturation","FLOAT"), new Column("exhaustion","FLOAT"), new Column("xp","FLOAT"), new Column("xp_level","INT"), new Column("gamemode","TEXT")};
		this.db.addColumns("bungee", columns, null);
	}
	
	//Migrate to the new database
	private void deleteSaveFile() {
		final File file = new File(this.mainPath, "saves.yml");
		if(!file.exists()) return;
		Util.consoleMsg(PREFIX+"Copying saves.yml to the database...");
		FileConfiguration saves = (FileConfiguration)YamlConfiguration.loadConfiguration(file);
		for(String uuid : saves.getKeys(false)) {
			ConfigurationSection section = saves.getConfigurationSection(uuid);
			try {
				OfflinePlayer player = Bukkit.getPlayer(UUID.fromString(uuid));
				if(player == null) player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
				if(player == null) continue;
				PlayerData pd = new PlayerData(player, this.saves);
				for(String k : section.getKeys(false)) {
					ConfigurationSection bsection = section.getConfigurationSection(k);
					Backup backup = new Backup(Util.stacksFromBase64(bsection.getString("main")), Util.stacksFromBase64(bsection.getString("ender")), k);
					pd.addBackup(backup);
				}
				this.data.put(UUID.fromString(uuid), pd);
			} catch(Exception e) {
				Util.errorMsg(e);
			}
		}
		this.saveAllBackups(null);
		file.delete();
		this.data = new HashMap<UUID, PlayerData>();
		Util.consoleMsg(PREFIX+"... saves.yml copy complete!");
	}
	
	//Updates the database with all cached backups
	private void saveAllBackups(final Callback callback) {
		String values = "";
		int i = 0;
		for(UUID uuid : this.data.keySet()) {
			PlayerData pd = this.data.get(uuid);
			if(pd.numBackups() == 0) continue;
			values += pd.getSQLInsert();
			if(i < this.data.keySet().size()-1) values += ",";
			i++;
		}
		if(!values.isEmpty()) this.db.insert("backup", values, callback);
	}
	//Updates the database with this player's cached backups
	private void saveBackups(final PlayerData pd, final Callback callback) {
		if(pd == null) return;
		if(callback == null) {
			this.db.delete("backup", "uuid = '"+pd.getPlayer().getUniqueId().toString()+"'", null);
			if(pd.numBackups() > 0) this.db.insert("backup", pd.getSQLInsert(), null);
		} else this.db.delete("backup", "uuid = '"+pd.getPlayer().getUniqueId().toString()+"'", new Callback() {
			@Override
			public void onDone(String result) {
				if(pd.numBackups() > 0)	db.insert("backup", pd.getSQLInsert(), callback);
			}
		});
	}
	private void saveBungee(final Player player, final boolean async) {
		if(this.bungee) {
			final String uuid = "'"+player.getUniqueId().toString()+"'";
			this.db.insert("complete", "("+uuid+",'false')", null);
			final String inv = "'"+Util.stacksToBase64(player.getInventory().getContents())+"'";
			final String ender = "'"+Util.stacksToBase64(player.getEnderChest().getContents())+"'";
			final String health = player.getHealth()+"";
			final String food = player.getFoodLevel()+"";
			final String sat = player.getSaturation()+"";
			final String exh = player.getExhaustion()+"";
			final String xp = player.getExp()+"";
			final String xpLevel = player.getLevel()+"";
			final String gamemode = "'"+player.getGameMode().name()+"'";
			if(async) {
				this.db.replace("bungee", "("+uuid+","+inv+","+ender+","+health+","+food+","+sat+","+exh+","+xp+","+xpLevel+","+gamemode+")", new Callback() {
					public void onDone(String result) {
						db.replace("complete", "("+uuid+",'true')", new Callback() {
							public void onDone(String result) {}
						});
					}
				});
			} else {
				this.db.replace("bungee", "("+uuid+","+inv+","+ender+","+health+","+food+","+sat+","+exh+","+xp+","+xpLevel+","+gamemode+")", null);
				this.db.replace("complete", "("+uuid+",'true')", null);
			}
		}
	}
	
	private boolean isValid(String[] values, int i) {
		return (values.length > i && !values[i].isEmpty() && !values[i].equals("null"));
	}
	
	//Checks if the player's data has been completely saved before loading it 
	private void loadBungee(final Player player) {
		final String uuid = player.getUniqueId().toString();
		
		this.db.select("complete", "uuid,completed", "uuid = '"+uuid+"'", null, new Callback() {
			public void onDone(String result) {
				if(result.contains("false")) {
					Bukkit.getScheduler().runTask(instance, new Runnable() {
						@Override
						public void run() {
							try {
								Thread.sleep(1000);
								loadBungee(player);
							} catch(InterruptedException e) {}
						}
					});
				} else {
					db.select("bungee", "inventory,enderchest,health,food,saturation,exhaustion,xp,xp_level,gamemode", "uuid = '"+uuid+"'", null, new Callback() {
						@Override
						public void onDone(String result) {
							final String[] values = result.replace("'", "").split(",");
							player.getInventory().setContents(isValid(values, 0) ? Util.stacksFromBase64(values[0]) : player.getInventory().getContents());
							player.getEnderChest().setContents(isValid(values, 1) ? Util.stacksFromBase64(values[1]) : player.getEnderChest().getContents());
							player.setHealth(isValid(values, 2) ? Double.parseDouble(values[2]) : player.getHealth());
							player.setFoodLevel(isValid(values, 3) ? Integer.parseInt(values[3]) : player.getFoodLevel());
							player.setSaturation(isValid(values, 4) ? Float.parseFloat(values[4]) : player.getSaturation());
							player.setExhaustion(isValid(values, 5) ? Float.parseFloat(values[5]) : player.getExhaustion());
							player.setExp(isValid(values, 6) ? Float.parseFloat(values[6]) : player.getExp());
							player.setLevel(isValid(values, 7) ? Integer.parseInt(values[7]) : player.getLevel());
							player.setGameMode(isValid(values, 8) ? GameMode.valueOf(values[8]) : player.getGameMode());
							
							db.delete("complete", "uuid = '"+uuid+"'", new Callback() {
								public void onDone(String result) {}
							});
						}
					});
				}
			}
		});
	}
	
	//Event listener handlers
	@EventHandler
    public void playerJoin(final PlayerJoinEvent event) {
		final Player player = event.getPlayer();
		final UUID uuid = player.getUniqueId();
		
		//query database for player, load into cache
		if(this.bungee) {
			this.clear(uuid);
			this.loadBungee(player);
		}
		
		this.db.select("backup", "btime,inventory,enderchest", "uuid = '"+uuid.toString()+"'", "btime", new Callback() {
			@Override
			public void onDone(String result) {
				String[] backups = result.replace("'", "").split("\n");
				PlayerData pd = new PlayerData(player, saves);
				for(String backup : backups) {
					String[] values = backup.split(",");
					if(values.length < 3) return;
					pd.addBackup(new Backup(Util.stacksFromBase64(values[1]), Util.stacksFromBase64(values[2]), values[0]));
				}
				data.put(uuid, pd);
				
				if(backupOnJoin) backup(uuid);
			}
		});
	}
	
	@EventHandler
    public void playerQuit(final PlayerQuitEvent event) {
		final Player player = event.getPlayer();
		final UUID uuid = player.getUniqueId();
		this.saveBungee(player, true);
		
		//update database for player, remove from cache
		saveBackups(this.data.get(uuid), null);
		data.remove(uuid);
	}
	@EventHandler
	public void playerDeath(final PlayerDeathEvent event) {
		if(this.backupOnDeath) backup(event.getEntity().getUniqueId());
	}
	
	
	//Commands and messages
	public void help(Player p) {
		if(p != null) {
			if(!p.hasPermission("saveinventory.use") && !p.hasPermission("saveinventory.*")) return;
			p.sendMessage(ChatColor.GREEN+"SaveInventory commands:");
			p.sendMessage(ChatColor.AQUA+"/si "+ChatColor.WHITE+"- shows this help message");
			Util.sendIfPermitted("saveinventory.reload", ChatColor.AQUA+"/si reload "+ChatColor.WHITE+"- reloads config.yml", p);
			Util.sendIfPermitted("saveinventory.backup", ChatColor.AQUA+"/si backup {player} "+ChatColor.WHITE+"- saves the player's inventory and enderchest", p);
			Util.sendIfPermitted("saveinventory.clear", ChatColor.AQUA+"/si clear {player} "+ChatColor.WHITE+"- clears the player's inventory and enderchest", p);
			Util.sendIfPermitted("saveinventory.restore", ChatColor.AQUA+"/si restore {player} "+ChatColor.WHITE+"- loads the player's inventory and enderchest from the last save", p);
		} else {
			Util.consoleMsg(ChatColor.GREEN+"SaveInventory commands:");
			Util.consoleMsg(ChatColor.AQUA+"/si "+ChatColor.WHITE+"- shows this help message");
			Util.consoleMsg(ChatColor.AQUA+"/si reload "+ChatColor.WHITE+"- reloads config.yml");
			Util.consoleMsg(ChatColor.AQUA+"/si backup {player} "+ChatColor.WHITE+"- saves the player's inventory and enderchest");
			Util.consoleMsg(ChatColor.AQUA+"/si clear {player} "+ChatColor.WHITE+"- clears the player's inventory and enderchest");
			Util.consoleMsg(ChatColor.AQUA+"/si restore {player} "+ChatColor.WHITE+"- loads the player's inventory and enderchest from the last save");
		}
	}
	
	public void backup(String playerName, Player p) {
		try {
			this.backup(Bukkit.getPlayer(playerName).getUniqueId());
			Util.sendMsg(ChatColor.GREEN+"Inventory backup complete for "+ChatColor.AQUA+playerName, p);
		} catch(Exception e) {
			Util.sendMsg(ChatColor.RED+"No backup found for player: "+playerName, p);
			Util.errorMsg(e);
		}
	}
	public void backup(UUID uuid) {
		Player player = Bukkit.getPlayer(uuid);
		if(player == null) return;
		if(!this.data.containsKey(uuid)) this.data.put(uuid, new PlayerData(player, saves));
		this.data.get(uuid).addBackup(new Backup(player.getInventory().getContents(), player.getEnderChest().getContents(), ""));
	}
	
	public void restore(String playerName, Player p) {
		try {
			boolean success = this.restore(Bukkit.getPlayer(playerName).getUniqueId(), p);
			if(success) Util.sendMsg(ChatColor.GREEN+"Inventory restore complete for "+ChatColor.AQUA+playerName, p);
		} catch(Exception e) {
			Util.sendMsg(ChatColor.RED+"No backup found for player: "+playerName, p);
			Util.errorMsg(e);
		}
	}
	public boolean restore(UUID uuid, Player p) {
		Player player = Bukkit.getPlayer(uuid);
		String name = player.getName();
		if(this.data.containsKey(uuid)) {
			PlayerData pd = data.get(uuid);
			Backup last = pd.getLastBackup();
			if(last == null) {
				Util.sendMsg(ChatColor.RED+"No backup found for player: "+name, p);
				return false;
			}
			player.getInventory().setContents(last.getInventoryContents());
			player.getEnderChest().setContents(last.getEnderchestContents());
			player.updateInventory();
			return true;
		}
		Util.sendMsg(ChatColor.RED+"No backup found for player: "+name, p);
		return false;
	}
	
	public void clear(String playerName, Player p) {
		try {
			this.clear(Bukkit.getPlayer(playerName).getUniqueId());
			Util.sendMsg(ChatColor.GREEN+"Inventory clear complete for "+ChatColor.AQUA+playerName, p);
		} catch(Exception e) {
			Util.sendMsg(ChatColor.RED+"No backup found for player: "+playerName, p);
			Util.errorMsg(e);
		}
	}
	public void clear(UUID uuid) {
		Player player = Bukkit.getPlayer(uuid);
		player.getInventory().clear();
		player.getEnderChest().clear();
		player.updateInventory();
	}
}
