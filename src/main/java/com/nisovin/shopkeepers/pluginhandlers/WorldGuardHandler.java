package com.nisovin.shopkeepers.pluginhandlers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.nisovin.shopkeepers.Settings;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.DefaultFlag;

public class WorldGuardHandler {

	public static final String PLUGIN_NAME = "WorldGuard";

	public static Plugin getPlugin() {
		return Bukkit.getPluginManager().getPlugin(PLUGIN_NAME);
	}

	public static boolean isPluginEnabled() {
		return Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME);
	}

	public static boolean isShopAllowed(Player player, Location loc) {
		// note: This works even if WorldGuard is not present.
		// The class is only going to get resolved, when it is required (ex. when accessed).
		WorldGuardPlugin wgPlugin = (WorldGuardPlugin) getPlugin();
		if (wgPlugin == null || !wgPlugin.isEnabled()) return true;

		boolean allowShopFlag = wgPlugin.getRegionManager(loc.getWorld()).getApplicableRegions(loc).testState(null, DefaultFlag.ENABLE_SHOP);
		if (Settings.requireWorldGuardAllowShopFlag) {
			// allow shops ONLY in regions with the ENABLE_SHOP flag set:
			return allowShopFlag;
		} else {
			// allow shops in regions where the ENABLE_SHOP flag is set OR the player can build:
			return allowShopFlag || wgPlugin.canBuild(player, loc);
		}
	}
}
