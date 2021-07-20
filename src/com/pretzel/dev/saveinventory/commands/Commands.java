package com.pretzel.dev.saveinventory.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.pretzel.dev.saveinventory.SaveInventory;
import com.pretzel.dev.saveinventory.lib.Util;

import net.md_5.bungee.api.ChatColor;

//Plugin commands handler
public class Commands implements CommandExecutor {
	private final SaveInventory instance;
	
	public Commands(final SaveInventory instance) {
		this.instance = instance;
	}
	
    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
    	if (command.getName().equalsIgnoreCase("saveinventory") || command.getName().equalsIgnoreCase("si")) {
           	if (sender instanceof Player) {
           		final Player p = (Player)sender;
           		if (p.hasPermission("saveinventory.use")) {
           			if(args.length == 0 || args.length > 2) {
           				this.instance.help(p);
           			} else if(args.length == 1) {
           				if(args[0].equals("reload")) {
           					if(p.hasPermission("saveinventory.reload")) {
           						this.instance.loadSettings();
           	        			p.sendMessage(ChatColor.YELLOW+"SaveInventory "+ChatColor.GREEN+"has been reloaded!");
           					} else {
           						p.sendMessage(ChatColor.RED+"No permission!");
           					}
           				} else {
           					this.instance.help(p);
           				}
           			} else if(args.length == 2) {
           				if(args[0].equals("backup")) {
           					if(p.hasPermission("saveinventory.backup")) {
          						this.instance.backup(args[1], p);
           					} else {
           						p.sendMessage(ChatColor.RED+"No permission!");
           					}
           				} else if(args[0].equals("restore")) {
           					if(p.hasPermission("saveinventory.restore")) {
           						this.instance.restore(args[1], p);
           					} else {
           						p.sendMessage(ChatColor.RED+"No permission!");
           					}
           				} else if(args[0].equals("clear")) {
           					if(p.hasPermission("saveinventory.clear")) {
           						this.instance.clear(args[1], p);
           					} else {
           						p.sendMessage(ChatColor.RED+"No permission!");
           					}
           				} else {
           					this.instance.help(p);
           				}
           			}
           		} else {
           			p.sendMessage(ChatColor.RED+"No permission!");
           		}
           	} else {
           		if(args.length == 0 || args.length > 2) {
           			this.instance.help(null);
           		} else if(args.length == 1) {
           			if(args[0].equals("reload")) {
           				this.instance.loadSettings();
           				Util.consoleMsg(ChatColor.YELLOW+"SaveInventory "+ChatColor.GREEN+"has been reloaded!");
           			} else {
           				this.instance.help(null);
           			}
           		} else if(args.length == 2) {
           			if(args[0].equals("backup")) {
           				this.instance.backup(args[1], null);
           			} else if(args[0].equals("restore")) {
           				this.instance.restore(args[1], null);
           			} else if(args[0].equals("clear")) {
           				this.instance.clear(args[1], null);
           			} else {
           				this.instance.help(null);
           			}
           		}
           	}
           	return true;
    	}
    	return false;
    }
}