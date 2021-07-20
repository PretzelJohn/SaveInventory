package com.pretzel.dev.saveinventory.data;

import java.time.Instant;

import org.bukkit.inventory.ItemStack;

public class Backup {
	private final ItemStack[] inventory;
	private final ItemStack[] enderchest;
	private String time;
	
	public Backup(final ItemStack[] inventory, final ItemStack[] enderchest, String time) {
		this.inventory = inventory;
		this.enderchest = enderchest;
		if(time.isEmpty()) this.time = Instant.now().toString();
		else this.time = time;
	}
	
	public ItemStack[] getInventoryContents() {
		if(this.inventory == null) return new ItemStack[0];
		return this.inventory;
	}
	
	public ItemStack[] getEnderchestContents() {
		if(this.enderchest == null) return new ItemStack[0];
		return this.enderchest;
	}
	
	public String getTime() {
		return this.time;
	}
}
