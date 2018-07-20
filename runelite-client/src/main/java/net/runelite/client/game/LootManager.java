package net.runelite.client.game;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.Collection;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.NPC;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.NpcDespawned;
import net.runelite.client.events.NpcLootReceived;

@Singleton
@Slf4j
public class LootManager
{
	private final EventBus eventBus;
	private final Provider<Client> client;
	private final Multimap<Integer, ItemStack> itemSpawns = HashMultimap.create();

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
			return;
		}

		Client client = this.client.get();
		LocalPoint location = LocalPoint.fromWorld(client, npc.getWorldLocation());
		int x = location.getSceneX();
		int y = location.getSceneY();
		int size = npc.getComposition().getSize();

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
					NpcLootReceived npcLootReceived = new NpcLootReceived(npc, items);
					eventBus.post(npcLootReceived);
					break;
				}
			}
		}
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
	public void onGameTick(GameTick gameTick)
	{
		itemSpawns.clear();
	}
}
