package com.pretzel.dev.saveinventory.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class CommandsTab implements TabCompleter {
    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        final List<String> cmdlist = new ArrayList<String>();
        if (args.length == 1) {
        	if (sender.hasPermission("saveinventory.reload")) {
                cmdlist.add("reload");
            }
            if (sender.hasPermission("saveinventory.backup")) {
                cmdlist.add("backup");
            }
            if (sender.hasPermission("saveinventory.restore")) {
                cmdlist.add("restore");
            }
            if (sender.hasPermission("saveinventory.clear")) {
            	cmdlist.add("clear");
            }
        } else {
            return null;
        }
        return cmdlist;
    }
}