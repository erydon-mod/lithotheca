package com.oliver.lithotheca.rack;

import net.minecraft.util.Identifier;

public final class RackProtocol {
    public static final int HOTBAR_SIZE = 9;
    public static final int MAX_EXTRA_SLOTS = 18;
    public static final int DEFAULT_EXTRA_SLOTS = 9;
    public static final int BRIDGE_SLOT = HOTBAR_SIZE - 1;

    public static final Identifier REQUEST_STATE = channel("request_state");
    public static final Identifier SELECT = channel("select");
    public static final Identifier PLACE = channel("place");
    public static final Identifier QUICK_ADD = channel("quick_add");
    public static final Identifier PICK = channel("pick");
    public static final Identifier CREATIVE_EDIT = channel("creative_edit");
    public static final Identifier CURSOR_ACTION = channel("cursor_action");
    public static final int CURSOR_TAKE = 0;
    public static final int CURSOR_EXCHANGE = 1;
    public static final int CURSOR_DISCARD = 2;
    public static final Identifier SWAP = channel("swap");
    public static final Identifier REMOVE = channel("remove");
    public static final Identifier CAPACITY = channel("capacity");
    public static final Identifier STATE = channel("state");

    private RackProtocol() {
    }

    private static Identifier channel(String name) {
        return new Identifier("lithotheca", "rack/" + name);
    }
}
