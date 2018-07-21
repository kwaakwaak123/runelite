package net.runelite.client.game;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
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
		NpcLootReceived npcLootReceived = new NpcLootReceived(npc, allItems);
		eventBus.post(npcLootReceived);
	}

	@Subscribe
	public void onPlayerDespawn(PlayerDespawned playerDespawned) {
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
	public void onGameTick(GameTick gameTick)
	{
		itemSpawns.clear();
	}
}
