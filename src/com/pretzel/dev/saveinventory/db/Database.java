package com.pretzel.dev.saveinventory.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import com.pretzel.dev.saveinventory.lib.Util;

import net.md_5.bungee.api.ChatColor;

public class Database {
	private final Plugin plugin;
	private final HashMap<String, Table> tables;
	private final String username;
	private final String password;
	
	private boolean mysql;
	private Connection conn;
	private String connString;
	private String version;
	
	public Database(final Plugin plugin, final ConfigurationSection section, final Table[] tables) {
		this.plugin = plugin;
		this.tables = new HashMap<String, Table>();
		this.mysql = section.getBoolean("MySQL", false);
		this.connString = getConnectionString(section);
		this.username = (this.mysql ? section.getString("username", "root") : null);
		this.password = (this.mysql ? section.getString("password", "root") : null);
		
		this.conn = null;
		try {
			this.conn = this.connect();
			
			for(Table table : tables) {
				this.conn.prepareStatement(table.getCreateSQL()).execute();
				this.tables.put(table.getName(), table);
			}
			this.version = (mysql?"MySQL v"+executeQuery("SELECT VERSION();").replace("'",""):"SQLite v"+executeQuery("SELECT sqlite_version();").replace("'",""));
		} catch (Exception e) {
			Util.errorMsg(e);
			Util.consoleMsg("");
			Util.consoleMsg(ChatColor.DARK_RED+"Error connecting to the database! Is it configured correctly in config.yml?");
			this.conn = null;
		}
	}
	
	// ---------- Connection Handling -----------
	private String getConnectionString(final ConfigurationSection section) {
		if(this.mysql) {
			final String host = section.getString("host", "localhost");
			final String port = section.getString("port", "3306");
			final String name = section.getString("name", "saveinventory");
			final String encoding = section.getString("encoding", "utf8");
			final boolean useSSL = section.getBoolean("useSSL", false);
			return "jdbc:mysql://"+host+":"+port+"/"+name+"?characterEncoding="+encoding+"&useSSL="+useSSL;
		}
		final File file = new File(this.plugin.getDataFolder(), "database.db");
		return "jdbc:sqlite:"+file.getAbsolutePath();
	}
	
	private Connection connect() throws Exception {
		if(username == null || password == null) return DriverManager.getConnection(this.connString);
		return DriverManager.getConnection(this.connString, this.username, this.password);
	}
	public void reconnect(int timeout) throws Exception {
		if(!this.isValid(timeout)) this.conn = this.connect();
	}
	
	public void disconnect() {
		try {
			if(this.conn != null) this.conn.close();
		} catch(Exception e) {
			Util.errorMsg(e);
		}
	}
	
	// --------- Query Handling ---------
	private void execute(final String sql, boolean ignore) {
		try {
			this.reconnect(15);
			this.conn.prepareStatement(sql).execute();
		} catch (Exception e) {
			if(!ignore) Util.errorMsg(e);
		}
	}
	public void execute(final String sql, boolean ignore, final Callback callback) {
		if(callback == null) execute(sql, ignore);
		else Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
			@Override
			public void run() {
				execute(sql, ignore);
				Bukkit.getScheduler().runTask(plugin, new Runnable() {
					@Override public void run() { callback.onDone(""); }
				});
			}
		});
	}

	private String executeQuery(final String sql) {
		String out = "";
		try {
			this.reconnect(15);
			final ResultSet result = this.conn.createStatement().executeQuery(sql);
			final int n = result.getMetaData().getColumnCount();
			while(result.next()) {
				if(!out.isEmpty()) out += "\n";
				for(int i = 1 ; i <= n; i++)
					out += "'"+result.getString(i)+"'"+(i < n ? ",":"");
			}
		} catch(Exception e) {
			Util.errorMsg(e);
		}
		return out;
	}
	public String executeQuery(final String sql, final Callback callback) {
		if(callback == null) return executeQuery(sql);
		Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
			@Override
			public void run() {
				final String result = executeQuery(sql);
				Bukkit.getScheduler().runTask(plugin, new Runnable() { 
					@Override public void run() { callback.onDone(result); }
				});
			}
		});
		return "";
	}
	
	// ---------- SQL Handling ----------
	public String select(final String tableName, final String what, final String where, final String orderBy, final Callback callback) {
		return executeQuery("SELECT "+what+" FROM "+tableName+(where == null ? "":" WHERE "+where)+(orderBy == null ? "":" ORDER BY "+orderBy)+";", callback);
	}
	
	public void addColumns(final String tableName, final Column[] columns, final Callback callback) {
		execute(this.tables.get(tableName).getAddColumnsSQL(columns), true, callback);
	}
	
	public void truncate(final String tableName, final Callback callback) {
		execute((this.mysql ? "TRUNCATE TABLE ":"DELETE FROM ")+tableName+";", false, callback);
	}
	
	public void delete(final String tableName, final String where, final Callback callback) {
		execute("DELETE FROM "+tableName+" WHERE "+where+";", false, callback);
	}
	
	public void insert(final String tableName, final String values, final Callback callback) {
		execute("INSERT "+(this.mysql ? "":"OR ")+"IGNORE INTO "+tableName+tables.get(tableName).getColumnNames()+" VALUES"+values+";", false, callback);
	}
	
	public void replace(final String tableName, final String values, final Callback callback) {
		execute("REPLACE INTO "+tableName+tables.get(tableName).getColumnNames()+" VALUES"+values+";", false, callback);
	}
	
	// ------- Migration Handling -------
	public Database migrate(ConfigurationSection section) {
		final boolean mysql = section.getBoolean("MySQL", false);
		if(this.mysql != mysql) {
			this.mysql = mysql;
			
			int t = 0;
			Table[] tables = new Table[this.tables.size()];
			for(Table table : this.tables.values()) tables[t++] = table;
			final Database db2 = new Database(this.plugin, section, tables);
			if(db2.getConnection() == null) return null;
			
			for(int i = 0; i < tables.length; i++) {
				String result = this.select(tables[i].getName(), "*", null, null, null);
				if(result.isEmpty()) continue;
				
				String values = "";
				String[] rows = result.split("\n");
				for(int j = 0; j < rows.length; j++)
					values += "("+rows[j]+")"+(j < rows.length-1 ? ",":"");
				
				db2.insert(tables[i].getName(), values, null);
			}
			this.disconnect();
			return db2;
		}
		return this;
	}
	
	// ---------- Getters ----------
	public boolean isMySQL() {
		return this.mysql;
	}
	
	public boolean isValid(int timeout) {
		if(this.conn == null) return false;
		if(timeout < 0) timeout = 5;
		try { return this.conn.isValid(timeout); }
		catch(Exception ignored) { return false; }
	}
	
	public Connection getConnection() {
		return this.conn;
	}
	
	public String getVersion() {
		return this.version;
	}
}
