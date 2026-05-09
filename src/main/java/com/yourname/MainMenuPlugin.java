package com.yourname;

import org.bukkit.plugin.java.JavaPlugin;

public class MainMenuPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("MainMenuPlugin включён!");
    }

    @Override
    public void onDisable() {
        getLogger().info("MainMenuPlugin выключен.");
    }
}
