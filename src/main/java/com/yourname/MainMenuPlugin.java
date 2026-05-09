package com.yourname;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
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
    static final String SPACES = "                 ";

    // Фиксированный угол камеры — смотрим прямо
    static final float FIXED_YAW = 0f;
    static final float FIXED_PITCH = 0f;

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
        state.prevYaw = FIXED_YAW;
        state.prevPitch = FIXED_PITCH;
        states.put(player.getUniqueId(), state);

        player.setGameMode(GameMode.ADVENTURE);
        player.setWalkSpeed(0f);

        // Телепортируем на фиксированный угол сразу
        Location loc = player.getLocation().clone();
        loc.setYaw(FIXED_YAW);
        loc.setPitch(FIXED_PITCH);
        player.teleport(loc);
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
        // Блокируем чистую позицию
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

        // POSITION_LOOK — читаем delta, блокируем
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

                // Блокируем пакет — позиция и поворот не меняются
                event.setCancelled(true);

                // Отправляем серверный пакет чтобы зафиксировать камеру
                sendFixedRotation(event.getPlayer(), state);
            }
        });

        // LOOK — читаем delta, блокируем поворот
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

                // Блокируем поворот
                event.setCancelled(true);

                // Возвращаем камеру на фиксированный угол
                sendFixedRotation(event.getPlayer(), state);
            }
        });

        // Блокируем прыжок
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

        // Левый клик
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

    // Отправляем клиенту пакет который фиксирует камеру на FIXED_YAW/FIXED_PITCH
    private void sendFixedRotation(Player player, CursorState state) {
        try {
            PacketContainer packet = pm.createPacket(
                    PacketType.Play.Server.POSITION);

            packet.getDoubles().write(0, state.frozenLocation.getX());
            packet.getDoubles().write(1, state.frozenLocation.getY());
            packet.getDoubles().write(2, state.frozenLocation.getZ());
            packet.getFloat().write(0, FIXED_YAW);
            packet.getFloat().write(1, FIXED_PITCH);
            // Flags = 0 означает абсолютные координаты
            packet.getIntegers().write(0, 0);

            pm.sendServerPacket(player, packet);
        } catch (Exception e) {
            getLogger().warning("Ошибка отправки пакета: " + e.getMessage());
        }
    }

    private void updateCursor(CursorState state, float newYaw, float newPitch) {
        // prevYaw/prevPitch теперь всегда FIXED угол
        // потому что мы постоянно сбрасываем камеру туда
        float dy = newYaw   - state.prevYaw;
        float dp = newPitch - state.prevPitch;

        if (dy >  180f) dy -= 360f;
        if (dy < -180f) dy += 360f;

        state.cursorX = Math.max(0f, Math.min(1f, state.cursorX + dy   * 0.002f));
        state.cursorY = Math.max(0f, Math.min(1f, state.cursorY + dp   * 0.0008f));

        // Сбрасываем prev на фиксированный угол
        // чтобы следующая delta считалась от него
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
