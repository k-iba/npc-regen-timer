package com.npcregentimer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.OverlayUtil;

public class NPCRegenTimerOverlay extends Overlay
{
    private final NPCRegenTimerPlugin plugin;

    @Inject
    public NPCRegenTimerOverlay(NPCRegenTimerPlugin plugin)
    {
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(OverlayPriority.MED);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        for (NPC npc : plugin.getClient().getNpcs())
        {
            Long spawnKey = plugin.getSpawnKeyByNpcIndex().get(npc.getIndex());
            if (spawnKey == null)
            {
                continue;
            }

            NPCRegenTimerPlugin.RegenTimer t = plugin.getTimersBySpawnKey().get(spawnKey);
            if (t == null)
            {
                continue;
            }

            if (t.state == NPCRegenTimerPlugin.TimerState.IDLE ||
                    t.state == NPCRegenTimerPlugin.TimerState.WAITING_FOR_FIRST_HEAL)
            {
                continue;
            }

            int seconds = plugin.getRemainingSeconds(t);
            if (seconds < 0) seconds = 0;

            Color color = plugin.isGreen(seconds) ? Color.GREEN : Color.RED;
            String text = Integer.toString(seconds);

            int zOffset = npc.getLogicalHeight() + 40;
            Point loc = npc.getCanvasTextLocation(graphics, text, zOffset);
            if (loc != null)
            {
                OverlayUtil.renderTextLocation(graphics, loc, text, color);
            }
        }
        return null;
    }
}
