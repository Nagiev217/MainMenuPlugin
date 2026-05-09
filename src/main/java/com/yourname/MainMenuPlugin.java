package com.yourname;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MainMenuPlugin extends JavaPlugin implements Listener {

    private ProtocolManager pm;
    private final Map<UUID, CursorState> states = new HashMap<>();

    static final int GRID_X = 17;
    static final int GRID_Y = 100;
    static final char CURSOR_BASE = '\uE100';
    static final String SPACES = "                 ";

    static final float FIXED_YAW = 0f;
    static final float FIXED_PITCH = 0f;

    @Override
    public void onEnable() {
        getLogger().info("MainMenuPlugin включён!");
        pm = ProtocolLibrary.getProtocolManager();
        getServer().getPluginManager().registerEvents(this, this);
        registerPacketListeners();

        getServer().getPluginCommand("cursordebug").setExecutor(
            (sender, cmd, label, args) -> {
                if (sender instanceof Player player) {
                    CursorState state = states.get(player.getUniqueId());
                    if (state != null) {
                        player.sendMessage("cursorX=" + state.cursorX
                            + " cursorY=" + state.cursorY);
                    } else {
                        player.sendMessage("Не в меню");
                    }
                }
                return true;
            }
        );

        getServer().getScheduler().runTaskTimer(this, () -> {
            for (UUID uuid : states.keySet()) {
                Player player = getServer().getPlayer(uuid);
                if (player == null) continue;
                CursorState state = states.get(uuid);
                if (!state.inMenu) continue;
                renderCursor(player, state);
            }
        }, 1L, 1L);
    }

    @Override
    public void onDisable() {
        // Убираем все boss bar при выключении
        for (CursorState state : states.values()) {
            if (state.bossBar != null) {
                state.bossBar.removeAll();
            }
        }
        getLogger().info("MainMenuPlugin выключен.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        enterMenu(event.getPlayer());
    }

    public void enterMenu(Player player) {
        CursorState state = new CursorState();
        state.inMenu = true;
        state.frozenLocation = player.getLocation().clone();
        state.cursorX = 0.5f;
        state.cursorY = 0.5f;
        state.prevYaw = FIXED_YAW;
        state.prevPitch = FIXED_PITCH;
        state.tickCounter = 0;

        BossBar bar = getServer().createBossBar("", BarColor.WHITE, BarStyle.SOLID);
        bar.addPlayer(player);
        bar.setVisible(true);
        bar.setProgress(0.0);
        state.bossBar = bar;

        states.put(player.getUniqueId(), state);

        player.setGameMode(GameMode.ADVENTURE);
        player.setWalkSpeed(0f);

        Location loc = player.getLocation().clone();
        loc.setYaw(FIXED_YAW);
        loc.setPitch(FIXED_PITCH);
        player.teleport(loc);
    }

    public void exitMenu(Player player) {
        CursorState state = states.remove(player.getUniqueId());
        if (state != null && state.bossBar != null) {
            state.bossBar.removeAll();
        }
        player.setWalkSpeed(0.2f);
        player.setGameMode(GameMode.SURVIVAL);
    }

    private void renderCursor(Player player, CursorState state) {
        int gridX = (int)(state.cursorX * (GRID_X - 1));
        int gridY = (int)(state.cursorY * (GRID_Y - 1));

        char cursorChar = (char)(CURSOR_BASE + gridY);
        String spaces = SPACES.substring(0, gridX);

        Component msg = Component.text(spaces + cursorChar)
                .font(Key.key("minecraft", "default"))
                .color(NamedTextColor.WHITE);

        if (state.bossBar != null) {
            state.bossBar.title(msg);
        }
    }

    private void registerPacketListeners() {
        pm.addPacketListener(new PacketAdapter(this,
                PacketType.Play.Client.POSITION) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                CursorState state = states.get(event.getPlayer().getUniqueId());
                if (state != null && state.inMenu) {
                    event.setCancelled(true);
                }
            }
        });

        pm.addPacketListener(new PacketAdapter(this,
                PacketType.Play.Client.FLYING) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                CursorState state = states.get(event.getPlayer().getUniqueId());
                if (state != null && state.inMenu) {
                    event.setCancelled(true);
                }
            }
        });

        pm.addPacketListener(new PacketAdapter(this,
                PacketType.Play.Client.POSITION_LOOK) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                CursorState state = states.get(event.getPlayer().getUniqueId());
                if (state == null || !state.inMenu) return;

                PacketContainer pkt = event.getPacket();
                float newYaw   = pkt.getFloat().read(0);
                float newPitch = pkt.getFloat().read(1);
                updateCursor(state, newYaw, newPitch);

                pkt.getDoubles().write(0, state.frozenLocation.getX());
                pkt.getDoubles().write(1, state.frozenLocation.getY());
                pkt.getDoubles().write(2, state.frozenLocation.getZ());
                pkt.getFloat().write(0, FIXED_YAW);
                pkt.getFloat().write(1, FIXED_PITCH);
            }
        });

        pm.addPacketListener(new PacketAdapter(this,
                PacketType.Play.Client.LOOK) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                CursorState state = states.get(event.getPlayer().getUniqueId());
                if (state == null || !state.inMenu) return;

                PacketContainer pkt = event.getPacket();
                float newYaw   = pkt.getFloat().read(0);
                float newPitch = pkt.getFloat().read(1);
                updateCursor(state, newYaw, newPitch);

                pkt.getFloat().write(0, FIXED_YAW);
                pkt.getFloat().write(1, FIXED_PITCH);
            }
        });

        pm.addPacketListener(new PacketAdapter(this,
                PacketType.Play.Client.ENTITY_ACTION) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                CursorState state = states.get(event.getPlayer().getUniqueId());
                if (state != null && state.inMenu) {
                    event.setCancelled(true);
                }
            }
        });

        pm.addPacketListener(new PacketAdapter(this,
                PacketType.Play.Client.ARM_ANIMATION) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                CursorState state = states.get(event.getPlayer().getUniqueId());
                if (state != null && state.inMenu) {
                    event.setCancelled(true);
                    handleClick(event.getPlayer());
                }
            }
        });
    }

    private void updateCursor(CursorState state, float newYaw, float newPitch) {
        float dy = newYaw   - state.prevYaw;
        float dp = newPitch - state.prevPitch;

        if (dy >  180f) dy -= 360f;
        if (dy < -180f) dy += 360f;

        state.cursorX = Math.max(0f, Math.min(1f, state.cursorX + dy * 0.002f));
        state.cursorY = Math.max(0f, Math.min(1f, state.cursorY + dp * 0.02f));

        state.prevYaw   = FIXED_YAW;
        state.prevPitch = FIXED_PITCH;
    }

    private void handleClick(Player player) {
        CursorState state = states.get(player.getUniqueId());
        if (state == null) return;
        getLogger().info(player.getName() + " кликнул на X="
                + state.cursorX + " Y=" + state.cursorY);
    }
}
