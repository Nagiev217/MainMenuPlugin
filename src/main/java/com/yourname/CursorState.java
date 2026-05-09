package com.yourname;

import org.bukkit.Location;

public class CursorState {
    public boolean inMenu = false;
    public Location frozenLocation;

    public float cursorX = 0.5f;
    public float cursorY = 0.5f;

    public float prevYaw = 0f;
    public float prevPitch = 0f;

    public String hoveredButton = null;
}
