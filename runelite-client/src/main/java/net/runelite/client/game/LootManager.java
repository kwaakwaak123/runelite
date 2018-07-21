package net.runelite.client.game;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.AnimationID;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.NpcID;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.PlayerLootReceived;

@Singleton
@Slf4j
public class LootManager
{
	private static final Map<Integer, Integer> NPC_DEATH_ANIMATIONS = ImmutableMap.of(
		NpcID.CAVE_KRAKEN, AnimationID.CAVE_KRAKEN_DEATH,
		NpcID.AIR_WIZARD, AnimationID.WIZARD_DEATH,
		NpcID.WATER_WIZARD, AnimationID.WIZARD_DEATH,
		NpcID.EARTH_WIZARD, AnimationID.WIZARD_DEATH,
		NpcID.FIRE_WIZARD, AnimationID.WIZARD_DEATH
	);

	private final EventBus eventBus;
	private final Provider<Client> client;
	private final Multimap<Integer, ItemStack> itemSpawns = HashMultimap.create();
	private WorldPoint playerLocationLastTick;
	private WorldPoint krakenPlayerLocation;

	@Inject
	private LootManager(EventBus eventBus, Provider<Client> client)
	{
		this.eventBus = eventBus;
		this.client = client;
	}

	@Subscribe
	public void onNpcDespawn(NpcDespawned npcDespawned)
	{
		NPC npc = npcDespawned.getNpc();
		if (!npc.isDead())
		{
			int id = npc.getId();
			switch (id)
			{
				case NpcID.GARGOYLE:
				case NpcID.GARGOYLE_413:
				case NpcID.GARGOYLE_1543:
				case NpcID.MARBLE_GARGOYLE:
				case NpcID.MARBLE_GARGOYLE_7408:

				case NpcID.ROCKSLUG:
				case NpcID.ROCKSLUG_422:
				case NpcID.GIANT_ROCKSLUG:

				case NpcID.SMALL_LIZARD:
				case NpcID.SMALL_LIZARD_463:
				case NpcID.DESERT_LIZARD:
				case NpcID.DESERT_LIZARD_460:
				case NpcID.DESERT_LIZARD_461:
				case NpcID.LIZARD:

				case NpcID.ZYGOMITE:
				case NpcID.ZYGOMITE_474:
				case NpcID.ANCIENT_ZYGOMITE:

					// these monsters die with >0 hp, so we just look for coincident
					// item spawn with despawn
					break;
				default:
					return;
			}
		}

		processNpcLoop(npc);
	}

	private void processNpcLoop(NPC npc)
	{
		Client client = this.client.get();
		LocalPoint location = LocalPoint.fromWorld(client, getDropLocation(npc, npc.getWorldLocation()));
		int x = location.getSceneX();
		int y = location.getSceneY();
		int size = npc.getComposition().getSize();

		// some npcs drop items onto multiple tiles
		List<ItemStack> allItems = new ArrayList<>();
		for (int i = 0; i < size; ++i)
		{
			for (int j = 0; j < size; ++j)
			{
				int packed = (x + i) << 8 | (y + j);
				Collection<ItemStack> items = itemSpawns.get(packed);
				if (!items.isEmpty())
				{
					for (ItemStack item : items)
					{
						log.debug("Drop from {}: {}", npc.getName(), item.getId());
					}
					allItems.addAll(items);
				}
			}
		}
		if (allItems.isEmpty())
		{
			return;
		}
		NpcLootReceived npcLootReceived = new NpcLootReceived(npc, allItems);
		eventBus.post(npcLootReceived);
	}

	@Subscribe
	public void onPlayerDespawn(PlayerDespawned playerDespawned)
	{
		Player player = playerDespawned.getPlayer();
		final Client client = this.client.get();
		final LocalPoint location = LocalPoint.fromWorld(client, player.getWorldLocation());
		if (location == null)
		{
			return;
		}

		final int x = location.getSceneX();
		final int y = location.getSceneY();
		final int packed = x << 8 | y;
		final Collection<ItemStack> items = itemSpawns.get(packed);

		if (items.isEmpty())
		{
			return;
		}

		for (ItemStack item : items)
		{
			log.debug("Drop from {}: {}", player.getName(), item.getId());
		}

		eventBus.post(new PlayerLootReceived(player, items));
	}

	@Subscribe
	public void onItemSpawn(ItemSpawned itemSpawned)
	{
		Item item = itemSpawned.getItem();
		Tile tile = itemSpawned.getTile();
		LocalPoint location = tile.getLocalLocation();
		int packed = location.getSceneX() << 8 | location.getSceneY();
		itemSpawns.put(packed, new ItemStack(item.getId(), item.getQuantity()));
		log.debug("Item spawn {} loc {},{}", item.getId(), location.getSceneX(), location.getSceneY());
	}

	@Subscribe
	public void onItemQuantityChanged(ItemQuantityChanged itemQuantityChanged)
	{
		Item item = itemQuantityChanged.getItem();
		Tile tile = itemQuantityChanged.getTile();
		LocalPoint location = tile.getLocalLocation();
		int packed = location.getSceneX() << 8 | location.getSceneY();
		int diff = itemQuantityChanged.getNewQuantity() - itemQuantityChanged.getOldQuantity();

		if (diff <= 0)
		{
			return;
		}

		itemSpawns.put(packed, new ItemStack(item.getId(), diff));
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged e)
	{
		if (!(e.getActor() instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) e.getActor();
		int id = npc.getId();

		// We only care about certain NPCs
		Integer deathAnim = NPC_DEATH_ANIMATIONS.get(id);

		// Current animation is death animation?
		if (deathAnim != null && deathAnim == npc.getAnimation())
		{
			if (id == NpcID.CAVE_KRAKEN)
			{
				// Big Kraken drops loot wherever player is standing when animation starts.
				krakenPlayerLocation = this.client.get().getLocalPlayer().getWorldLocation();
			}
			else
			{
				// These NPCs drop loot on death animation, which is right now.
				processNpcLoop(npc);
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		playerLocationLastTick = client.get().getLocalPlayer().getWorldLocation();
		itemSpawns.clear();
	}

	private WorldPoint getDropLocation(NPC npc, WorldPoint worldLocation)
	{
		switch (npc.getId())
		{
			case NpcID.KRAKEN:
			case NpcID.KRAKEN_6640:
			case NpcID.KRAKEN_6656:
				worldLocation = playerLocationLastTick;
				break;
			case NpcID.CAVE_KRAKEN:
				worldLocation = krakenPlayerLocation;
				break;
			case NpcID.ZULRAH:        // Green
			case NpcID.ZULRAH_2043: // Red
			case NpcID.ZULRAH_2044: // Blue
				for (Map.Entry<Integer, ItemStack> entry : itemSpawns.entries())
				{
					if (entry.getValue().getId() == ItemID.ZULRAHS_SCALES)
					{
						int packed = entry.getKey();
						int unpackedX = packed >> 8;
						int unpackedY = packed & 0xFF;
						worldLocation = new WorldPoint(unpackedX, unpackedY, worldLocation.getPlane());
						break;
					}
				}
				break;
			case NpcID.VORKATH:
			case NpcID.VORKATH_8058:
			case NpcID.VORKATH_8059:
			case NpcID.VORKATH_8060:
			case NpcID.VORKATH_8061:
				int x = worldLocation.getX() + 3;
				int y = worldLocation.getY() + 3;
				if (playerLocationLastTick.getX() < x)
				{
					x -= 4;
				}
				else if (playerLocationLastTick.getX() > x)
				{
					x += 4;
				}
				if (playerLocationLastTick.getY() < y)
				{
					y -= 4;
				}
				else if (playerLocationLastTick.getY() > y)
				{
					y += 4;
				}
				worldLocation = new WorldPoint(x, y, worldLocation.getPlane());
				break;
		}

		return worldLocation;
	}
}
