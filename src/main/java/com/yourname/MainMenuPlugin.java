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
    static final int GRID_Y = 9;
    static final char CURSOR_BASE = '\uE100';
    static final String SPACES = "                 "; // 17 пробелов

    @Override
    public void onEnable() {
        getLogger().info("MainMenuPlugin включён!");
        pm = ProtocolLibrary.getProtocolManager();
        getServer().getPluginManager().registerEvents(this, this);
        registerPacketListeners();

        getServer().getScheduler().runTaskTimer(this, () -> {
            for (UUID uuid : states.keySet()) {
                Player player = getServer().getPlayer(uuid);
                if (player == null) continue;
                CursorState state = states.get(uuid);
                if (!state.inMenu) continue;

                // Телепортируем только если реально сдвинулся
                Location current = player.getLocation();
                Location frozen = state.frozenLocation.clone();
                if (current.distanceSquared(frozen) > 0.01) {
                    frozen.setYaw(current.getYaw());
                    frozen.setPitch(current.getPitch());
                    player.teleport(frozen);
                }

                renderCursor(player, state);
            }
        }, 1L, 1L);
    }

    @Override
    public void onDisable() {
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
        state.prevYaw = player.getLocation().getYaw();
        state.prevPitch = player.getLocation().getPitch();
        states.put(player.getUniqueId(), state);

        player.setGameMode(GameMode.ADVENTURE);
        player.setWalkSpeed(0f);
    }

    public void exitMenu(Player player) {
        states.remove(player.getUniqueId());
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

        player.sendActionBar(msg);
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

        // ВРЕМЕННЫЙ ЛОГ
        getLogger().info("dy=" + dy + " dp=" + dp);

        state.cursorX = Math.max(0f, Math.min(1f, state.cursorX + dy * 0.008f));
        state.cursorY = Math.max(0f, Math.min(1f, state.cursorY + dp * 0.010f));

        state.prevYaw   = newYaw;
        state.prevPitch = newPitch;
    }

    private void handleClick(Player player) {
        CursorState state = states.get(player.getUniqueId());
        if (state == null) return;
        getLogger().info(player.getName() + " кликнул на X="
                + state.cursorX + " Y=" + state.cursorY);
    }
}
