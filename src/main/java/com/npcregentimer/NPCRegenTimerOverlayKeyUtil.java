package com.npcregentimer;

import net.runelite.api.NPC;
import net.runelite.api.coords.LocalPoint;

final class NPCRegenTimerOverlayKeyUtil
{
    private NPCRegenTimerOverlayKeyUtil() {}

    static long makeSpawnKey(NPCRegenTimerPlugin plugin, NPC npc)
    {
        LocalPoint lp = npc.getLocalLocation();
        if (lp == null)
        {
            return 0;
        }

        int id = npc.getId();
        int plane = plugin.getClient().getPlane();
        int x = lp.getX();
        int y = lp.getY();

        long key = 0;
        key |= ((long) (id & 0xFFFF)) << 48;
        key |= ((long) (plane & 0x3)) << 46;
        key |= ((long) (x & 0x7FFF)) << 23;
        key |= ((long) (y & 0x7FFF));
        return key;
    }
}