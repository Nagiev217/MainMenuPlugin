package com.yourname;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
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

    @Override
    public void onEnable() {
        getLogger().info("MainMenuPlugin включён!");
        pm = ProtocolLibrary.getProtocolManager();
        getServer().getPluginManager().registerEvents(this, this);
        registerPacketListeners();

        // Тик каждую секунду — замораживаем позицию
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (UUID uuid : states.keySet()) {
                Player player = getServer().getPlayer(uuid);
                if (player == null) continue;
                CursorState state = states.get(uuid);
                if (!state.inMenu) continue;

                // Телепортируем обратно, но сохраняем yaw/pitch
                Location frozen = state.frozenLocation.clone();
                frozen.setYaw(player.getLocation().getYaw());
                frozen.setPitch(player.getLocation().getPitch());
                player.teleport(frozen);
            }
        }, 1L, 1L);
    }

    @Override
    public void onDisable() {
        getLogger().info("MainMenuPlugin выключен.");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        enterMenu(player);
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
        getLogger().info(player.getName() + " вошёл в меню");
    }

    public void exitMenu(Player player) {
        states.remove(player.getUniqueId());
        player.setWalkSpeed(0.2f);
        player.setGameMode(GameMode.SURVIVAL);
        getLogger().info(player.getName() + " вышел из меню");
    }

    private void registerPacketListeners() {
        // Блокируем движение
        pm.addPacketListener(new PacketAdapter(this,
                PacketType.Play.Client.POSITION,
                PacketType.Play.Client.POSITION_LOOK) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                CursorState state = states.get(event.getPlayer().getUniqueId());
                if (state != null && state.inMenu) {
                    event.setCancelled(true);
                }
            }
        });

        // Считываем движение мыши через LOOK пакет
        pm.addPacketListener(new PacketAdapter(this,
                PacketType.Play.Client.LOOK,
                PacketType.Play.Client.POSITION_LOOK) {
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

        // Перехватываем левый клик
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

        // Нормализуем переход через ±180°
        if (dy >  180f) dy -= 360f;
        if (dy < -180f) dy += 360f;

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
