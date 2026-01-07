package com.npcregentimer;

import com.google.inject.Provides;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
		name = "NPC Regen Timer",
		description = "Displays overhead timer for NPC health regeneration"
)
public class NPCRegenTimerPlugin extends Plugin
{
	private static final double SECONDS_PER_TICK = 0.6;
	private static final int GREEN_THRESHOLD_SECONDS = 18;

	@Getter
	@Inject
	private Client client;

	@Inject
	private NPCRegenTimerConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private NPCRegenTimerOverlay overlay;

	// Persistent timers keyed by "spawn slot"
	@Getter
	private final Map<Long, RegenTimer> timersBySpawnKey = new HashMap<>();

	// NPC index -> spawnKey (MUST be stable; do NOT recompute when moving)
	@Getter
	private final Map<Integer, Long> spawnKeyByNpcIndex = new HashMap<>();

	private int gameTickCounter = 0;

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		timersBySpawnKey.clear();
		spawnKeyByNpcIndex.clear();
		gameTickCounter = 0;
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		timersBySpawnKey.clear();
		spawnKeyByNpcIndex.clear();
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();

		// Compute ONCE at spawn (or first time we see the index)
		long spawnKey = makeSpawnKey(npc.getId(), client.getPlane(), npc.getWorldLocation());
		spawnKeyByNpcIndex.put(npc.getIndex(), spawnKey);

		RegenTimer t = timersBySpawnKey.get(spawnKey);
		if (t != null)
		{
			// Resume after death
			if (t.state == TimerState.PAUSED_DEAD)
			{
				t.state = TimerState.RUNNING;
			}

			t.lastHealthRatio = npc.getHealthRatio();
			t.lastSeenGameTick = gameTickCounter;
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!(event.getActor() instanceof NPC))
		{
			return;
		}
		NPC npc = (NPC) event.getActor();

		int dmg = event.getHitsplat().getAmount();
		if (dmg <= 0)
		{
			return;
		}

		long spawnKey = getOrCreateSpawnKeyForNpc(npc);

		RegenTimer t = timersBySpawnKey.computeIfAbsent(spawnKey, k -> new RegenTimer());

		// Arm on damage; START only on first heal tick
		if (t.state == TimerState.IDLE)
		{
			t.state = TimerState.WAITING_FOR_FIRST_HEAL;
		}

		t.lastHealthRatio = npc.getHealthRatio();
		t.lastSeenGameTick = gameTickCounter;
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (!(event.getActor() instanceof NPC))
		{
			return;
		}
		NPC npc = (NPC) event.getActor();

		long spawnKey = getOrCreateSpawnKeyForNpc(npc);

		RegenTimer t = timersBySpawnKey.get(spawnKey);
		if (t != null)
		{
			t.state = TimerState.PAUSED_DEAD; // freeze
			t.lastSeenGameTick = gameTickCounter;
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		gameTickCounter++;

		// Ensure each currently-loaded NPC has a stable spawnKey entry, but DO NOT overwrite existing ones.
		for (NPC npc : client.getNpcs())
		{
			spawnKeyByNpcIndex.computeIfAbsent(
					npc.getIndex(),
					idx -> makeSpawnKey(npc.getId(), client.getPlane(), npc.getWorldLocation()) // baseline
			);
		}

		// Heal detection + countdown tickdown
		for (NPC npc : client.getNpcs())
		{
			Long spawnKey = spawnKeyByNpcIndex.get(npc.getIndex());
			if (spawnKey == null)
			{
				continue;
			}

			RegenTimer t = timersBySpawnKey.get(spawnKey);
			if (t == null)
			{
				continue;
			}

			t.lastSeenGameTick = gameTickCounter;

			if (t.state == TimerState.PAUSED_DEAD)
			{
				continue;
			}

			int ratio = npc.getHealthRatio(); // -1 when unknown
			if (ratio >= 0)
			{
				if (t.lastHealthRatio >= 0 && ratio > t.lastHealthRatio)
				{
					// Start on first heal, reset on subsequent heals
					t.state = TimerState.RUNNING;
					t.remainingTicks = secondsToTicks(config.regenIntervalSeconds());
				}
				t.lastHealthRatio = ratio;
			}

			if (t.state == TimerState.RUNNING)
			{
				if (t.remainingTicks > 0)
				{
					t.remainingTicks--;
				}

				// If it hit 0, loop back to full interval so it keeps counting forever
				if (t.remainingTicks <= 0)
				{
					t.remainingTicks = secondsToTicks(config.regenIntervalSeconds());
				}
			}
		}

		// Cleanup: keep dead timers around longer so respawn can resume
		int staleAlive = Math.max(20, config.staleCleanupTicks());

		Iterator<Map.Entry<Long, RegenTimer>> it = timersBySpawnKey.entrySet().iterator();
		while (it.hasNext())
		{
			RegenTimer t = it.next().getValue();

			// Keep paused-dead timers so they can survive until respawn
			if (t.state == TimerState.PAUSED_DEAD)
			{
				continue;
			}

			if (gameTickCounter - t.lastSeenGameTick > staleAlive)
			{
				it.remove();
			}
		}

		// Optional: clear old npcIndex mappings occasionally so the map doesn't grow forever
		// (not required for correctness)
	}

	public int getRemainingSeconds(RegenTimer t)
	{
		return (int) Math.ceil(t.remainingTicks * SECONDS_PER_TICK);
	}

	public boolean isGreen(int remainingSeconds)
	{
		return remainingSeconds <= GREEN_THRESHOLD_SECONDS;
	}

	private static int secondsToTicks(int seconds)
	{
		return (int) Math.ceil(seconds / SECONDS_PER_TICK);
	}

	private long getOrCreateSpawnKeyForNpc(NPC npc)
	{
		Long existing = spawnKeyByNpcIndex.get(npc.getIndex());
		if (existing != null)
		{
			return existing;
		}

		long created = makeSpawnKey(npc.getId(), client.getPlane(), npc.getWorldLocation());
		spawnKeyByNpcIndex.put(npc.getIndex(), created);
		return created;
	}

	private static long makeSpawnKey(int npcId, int plane, WorldPoint wp)
	{
		if (wp == null)
		{
			return 0;
		}

		int x = wp.getX();
		int y = wp.getY();

		long key = 0;
		key |= ((long) (npcId & 0xFFFF)) << 48;
		key |= ((long) (plane & 0x3)) << 46;
		key |= ((long) (x & 0x3FFF)) << 32;
		key |= ((long) (y & 0x3FFF)) << 18;
		return key;
	}

	@Provides
	NPCRegenTimerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NPCRegenTimerConfig.class);
	}

	public enum TimerState
	{
		IDLE,
		WAITING_FOR_FIRST_HEAL,
		RUNNING,
		PAUSED_DEAD
	}

	public static class RegenTimer
	{
		public int remainingTicks = 0;
		public TimerState state = TimerState.IDLE;
		public int lastSeenGameTick = 0;
		public int lastHealthRatio = -1;
	}
}
