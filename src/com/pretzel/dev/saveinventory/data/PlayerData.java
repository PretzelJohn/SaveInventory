package com.pretzel.dev.saveinventory.data;

import java.util.Stack;

import org.bukkit.OfflinePlayer;

import com.pretzel.dev.saveinventory.lib.Util;

public class PlayerData {
	private Stack<Backup> backups;
	private final OfflinePlayer player;
	private int maxSaves;
	
	public PlayerData(final OfflinePlayer player, int maxSaves) {
		this.backups = new Stack<Backup>();
		this.player = player;
		this.maxSaves = maxSaves;
	}
	
	public void addBackup(Backup backup) {
		this.backups.push(backup);
		if(this.numBackups() > this.maxSaves) this.backups.setSize(this.maxSaves > 0 ? this.maxSaves : 1);
	}
	
	public Backup getLastBackup() {
		if(this.backups.isEmpty()) return null;
		return this.backups.pop();
	}
	
	public int numBackups() {
		return this.backups.size();
	}
	
	public String getSQLInsert() {
		String out = "";
		
		Stack<Backup> temp = new Stack<Backup>();
		while(!this.backups.isEmpty()) {
			Backup last = this.getLastBackup();
			if(last == null) continue;
			temp.push(last);
			final String uuid = this.player.getUniqueId().toString();
			final String time = last.getTime();
			final String inventory = Util.stacksToBase64(last.getInventoryContents());
			final String enderchest = Util.stacksToBase64(last.getEnderchestContents());
			out += "('"+uuid+"','"+(time.isEmpty()?"":time+"','")+inventory+"','"+enderchest+"')";
			if(!this.backups.isEmpty()) out += ",";
		}
		this.backups = temp;
		return out;
	}
	
	public OfflinePlayer getPlayer() {
		return this.player;
	}
}
