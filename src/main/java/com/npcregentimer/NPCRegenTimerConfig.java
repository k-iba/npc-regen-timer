package com.npcregentimer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("npcregentimer")
public interface NPCRegenTimerConfig extends Config
{
	@ConfigItem(
			keyName = "regenIntervalSeconds",
			name = "Regen interval (seconds)",
			description = "Countdown duration started/reset when the NPC takes damage."
	)
	default int regenIntervalSeconds()
	{
		return 60;
	}

	@ConfigItem(
			keyName = "deadPersistTicks",
			name = "Persist after death (ticks)",
			description = "How long to keep a paused timer around while the NPC is dead (so it can resume on respawn)."
	)
	default int deadPersistTicks()
	{
		return 2000; // ~20 minutes
	}
	default boolean showOnlyWhenActive()
	{
		return true;
	}

	@ConfigItem(
			keyName = "staleCleanupTicks",
			name = "Cleanup after (ticks)",
			description = "Remove timers not seen for this many game ticks (to prevent memory growth)."
	)
	default int staleCleanupTicks()
	{
		return 400; // ~4 minutes
	}
}