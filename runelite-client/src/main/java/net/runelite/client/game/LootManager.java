/*
 * Copyright (c) 2018, Adam Adam <Adam@sigterm.info>
 * Copyright (c) 2018, TheStonedTurtle <https://github.com/TheStonedTurtle>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
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
import net.runelite.api.NpcID;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
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
	private WorldPoint playerLocationLastTick;

	@Inject
	private LootManager(EventBus eventBus, Provider<Client> client)
	{
		this.eventBus = eventBus;
		this.client = client;
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned playerDespawned)
	{
		final Player player = playerDespawned.getPlayer();
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
	public void onNpcDespawn(NpcDespawned npcDespawned)
	{
		final NPC npc = npcDespawned.getNpc();
		if (!npc.isDead())
		{
			return;
		}

		Client client = this.client.get();
		WorldPoint worldLocation = npc.getWorldLocation();

		switch (npc.getId())
		{
			case NpcID.KRAKEN:
			case NpcID.KRAKEN_6640:
			case NpcID.KRAKEN_6656:
			case NpcID.CAVE_KRAKEN:
				worldLocation = playerLocationLastTick;
				break;
		}

		final LocalPoint location = LocalPoint.fromWorld(client, worldLocation);
		if (location == null)
		{
			return;
		}

		final int x = location.getSceneX();
		final int y = location.getSceneY();
		final int size = npc.getComposition().getSize();

		for (int i = 0; i < size; ++i)
		{
			for (int j = 0; j < size; ++j)
			{
				final int packed = (x + i) << 8 | (y + j);
				final Collection<ItemStack> items = itemSpawns.get(packed);
				if (!items.isEmpty())
				{
					for (ItemStack item : items)
					{
						log.debug("Drop from {}: {}", npc.getName(), item.getId());
					}

					final NpcLootReceived npcLootReceived = new NpcLootReceived(npc, items);
					eventBus.post(npcLootReceived);
					break;
				}
			}
		}
	}

	@Subscribe
	public void onItemSpawn(ItemSpawned itemSpawned)
	{
		final Item item = itemSpawned.getItem();
		final Tile tile = itemSpawned.getTile();
		final LocalPoint location = tile.getLocalLocation();
		final int packed = location.getSceneX() << 8 | location.getSceneY();
		itemSpawns.put(packed, new ItemStack(item.getId(), item.getQuantity()));
		log.debug("Item spawn {} loc {},{}", item.getId(), location.getSceneX(), location.getSceneY());
	}

	@Subscribe
	public void onItemQuantityChanged(ItemQuantityChanged itemQuantityChanged)
	{
		final Item item = itemQuantityChanged.getItem();
		final Tile tile = itemQuantityChanged.getTile();
		final LocalPoint location = tile.getLocalLocation();
		final int packed = location.getSceneX() << 8 | location.getSceneY();
		final int diff = itemQuantityChanged.getNewQuantity() - itemQuantityChanged.getOldQuantity();

		if (diff <= 0)
		{
			return;
		}

		itemSpawns.put(packed, new ItemStack(item.getId(), diff));
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		playerLocationLastTick = client.get().getLocalPlayer().getWorldLocation();
		itemSpawns.clear();
	}
}
