/*
 * Copyright (c) 2018, Woox <https://github.com/wooxsolo>
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
package net.runelite.client.game.loot;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.AnimationID;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.NpcID;
import net.runelite.api.Player;
import net.runelite.api.Tile;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDespawned;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemLayerChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.WidgetID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.loot.data.MemorizedActor;
import net.runelite.client.game.loot.data.MemorizedNpc;
import net.runelite.client.game.loot.data.MemorizedNpcAndLocation;
import net.runelite.client.game.loot.data.MemorizedPlayer;
import net.runelite.client.game.loot.events.EventLootReceived;
import net.runelite.client.game.loot.events.NpcLootReceived;
import net.runelite.client.game.loot.events.PlayerLootReceived;

@Slf4j
@Singleton
public class LootLogger
{
	/**
	 * Some NPCs decide where to drop the loot at the same time they start performing
	 * their death animation, so their death animation has to be known.
	 * This list may be incomplete, I didn't test every NPC in the game.
	 */
	private static final Map<Integer, Integer> NPC_DEATH_ANIMATIONS = ImmutableMap.of(
		NpcID.CAVE_KRAKEN, AnimationID.CAVE_KRAKEN_DEATH,
		NpcID.AIR_WIZARD, AnimationID.WIZARD_DEATH,
		NpcID.WATER_WIZARD, AnimationID.WIZARD_DEATH,
		NpcID.EARTH_WIZARD, AnimationID.WIZARD_DEATH,
		NpcID.FIRE_WIZARD, AnimationID.WIZARD_DEATH
	);

	@Inject
	private Client client;

	@Inject
	private ItemManager itemManager;

	// posting new events
	private final EventBus eventBus;

	private final Map<Actor, MemorizedActor> interactedActors = new HashMap<>();
	private final List<MemorizedActor> deadActorsThisTick = new ArrayList<>();

	private final Map<WorldPoint, List<Item>> groundItemsLastTick = new HashMap<>();
	private final Set<Tile> changedItemLayerTiles = new HashSet<>();

	private WorldPoint playerLocationLastTick = null;

	private boolean insideChambersOfXeric = false;
	private boolean hasOpenedRaidsRewardChest = false;
	private boolean hasOpenedTheatreOfBloodRewardChest = false;

	@Inject
	private LootLogger(EventBus eventBus)
	{
		this.eventBus = eventBus;
	}

	/*
	 * Wrappers for posting new events
	 */

	/**
	 * Called when loot was received by killing an NPC. Triggers the NpcLootReceived event.
	 *
	 * @param npc      Killed NpcID
	 * @param comp     Killed NPC's NPCComposition
	 * @param location WorldPoint the NPC died at
	 * @param drops    A List of Items dropped
	 */
	private void onNewNpcLogCreated(int npc, NPCComposition comp, WorldPoint location, List<Item> drops)
	{
		eventBus.post(new NpcLootReceived(npc, comp, location, drops));
	}

	/**
	 * Called when loot was received by killing another Player. Triggers the PlayerLootReceived event.
	 *
	 * @param player   Player that was killed
	 * @param location WorldPoint the Player died at
	 * @param drops    A List of Items dropped
	 */
	private void onNewPlayerLogCreated(Player player, WorldPoint location, List<Item> drops)
	{
		eventBus.post(new PlayerLootReceived(player, location, drops));
	}

	/**
	 * Called when loot was received by completing an activity. Triggers the EventLootReceived event.
	 * The types of events are static and available on the LootEventType class
	 *
	 * @param event LootEventType event name
	 * @param drops    A List of Items received
	 */
	private void onNewEventLogCreated(LootEventType event, List<Item> drops)
	{
		eventBus.post(new EventLootReceived(event, drops));
	}


	/**
	 * Compare the two lists and return any new items.
	 * @param prevItems Previous Ground Items
	 * @param currItems Current Ground Items
	 * @return List of new items
	 */
	private List<Item> getNewGroundItems(Iterable<Item> prevItems, Iterable<Item> currItems)
	{
		Map<Integer, Integer> diffMap = new HashMap<>();

		if (prevItems != null)
		{
			for (Item item : prevItems)
			{
				int count = diffMap.getOrDefault(item.getId(), 0);
				diffMap.put(item.getId(), count - item.getQuantity());
			}
		}
		if (currItems != null)
		{
			for (Item item : currItems)
			{
				int count = diffMap.getOrDefault(item.getId(), 0);
				diffMap.put(item.getId(), count + item.getQuantity());
			}
		}

		List<Item> diff = new ArrayList<>();
		for (Map.Entry<Integer, Integer> e : diffMap.entrySet())
		{
			// Ignore anything that didn't change or was removed.
			// We only care about new items.
			if (e.getValue() > 0)
			{
				diff.add(client.createItem(e.getKey(), e.getValue()));
			}
		}

		return diff;
	}


	/**
	 * Grabs loot for specific WorldPoint and Returns new items (Loot)
	 *
	 * @param location WorldPoint to check for new items
	 * @return Item id and quantity Map (Integer,Integer) for new items
	 */
	private List<Item> getItemDifferencesAt(WorldPoint location)
	{
		int regionX = location.getX() - client.getBaseX();
		int regionY = location.getY() - client.getBaseY();
		if (regionX < 0 || regionX >= Constants.REGION_SIZE ||
				regionY < 0 || regionY >= Constants.REGION_SIZE)
		{
			return null;
		}

		Tile tile = client.getRegion().getTiles()[location.getPlane()][regionX][regionY];
		if (!changedItemLayerTiles.contains(tile))
		{
			// No items on the tile changed
			return null;
		}

		// The tile might previously have contained items so we need to compare.
		List<Item> prevItems = groundItemsLastTick.get(location) != null ? groundItemsLastTick.get(location) : new ArrayList<>();
		List<Item> currItems = tile.getGroundItems();

		return getNewGroundItems(prevItems, currItems);
	}



	/**
	 * Memorizes any NPCs the local player is interacting with (Including AOE/Cannon)
	 */
	private void checkInteracting()
	{
		// We should memorize which actors the player has interacted with
		// Other players might be killing some monsters nearby and in some
		// rare cases loot appears on the same tick as their monster dies

		Player player = client.getLocalPlayer();
		Actor interacting = player.getInteracting();
		if (interacting != null)
		{
			if (interacting instanceof NPC)
			{
				interactedActors.put(interacting, new MemorizedNpc((NPC)interacting));
			}
			else if (interacting instanceof Player)
			{
				interactedActors.put(interacting, new MemorizedPlayer((Player)interacting));
			}
		}
	}

	/**
	 * Determine where the NPCs loot will spawn
	 *
	 * @param pad The MemorizedActor that we are checking
	 * @return A List of WorldPoint's where the NPC might spawn loot
	 */
	private WorldPoint[] getExpectedDropLocations(MemorizedActor pad)
	{
		WorldPoint defaultLocation = pad.getActor().getWorldLocation();
		if (pad instanceof MemorizedNpc)
		{
			// Some bosses drop their loot in specific locations
			switch (((MemorizedNpc) pad).getNpcComposition().getId())
			{
				case NpcID.KRAKEN:
				case NpcID.KRAKEN_6640:
				case NpcID.KRAKEN_6656:
					return new WorldPoint[]
							{
									playerLocationLastTick
							};

				case NpcID.CAVE_KRAKEN:
					if (pad instanceof MemorizedNpcAndLocation)
					{
						return new WorldPoint[]
								{
										((MemorizedNpcAndLocation) pad).getExpectedDropLocation()
								};
					}
					break;

				case NpcID.DUSK:
				case NpcID.DUSK_7851:
				case NpcID.DUSK_7854:
				case NpcID.DUSK_7855:
				case NpcID.DUSK_7882:
				case NpcID.DUSK_7883:
				case NpcID.DUSK_7886:
				case NpcID.DUSK_7887:
				case NpcID.DUSK_7888:
				case NpcID.DUSK_7889:
				{
					return new WorldPoint[]
							{
									new WorldPoint(
											defaultLocation.getX() + 3,
											defaultLocation.getY() + 3,
											defaultLocation.getPlane())
							};
				}

				case NpcID.ZULRAH: // Green
				case NpcID.ZULRAH_2043: // Red
				case NpcID.ZULRAH_2044: // Blue
				{
					// The drop appears on whatever tile where zulrah scales appeared
					WorldPoint location = changedItemLayerTiles.stream()
							.filter(x ->
							{
								List<Item> groundItems = x.getGroundItems();
								if (groundItems != null)
								{
									return groundItems.stream().anyMatch(y -> y.getId() == ItemID.ZULRAHS_SCALES);
								}
								return false;
							})
							.map(Tile::getWorldLocation)
							// If player drops some zulrah scales themselves on the same tick,
							// the ones that appeared further away will be chosen instead.
							.sorted((x, y) -> y.distanceTo(playerLocationLastTick) - x.distanceTo(playerLocationLastTick))
							.findFirst().orElse(null);
					if (location == null)
					{
						return new WorldPoint[] {};
					}
					return new WorldPoint[] { location };
				}

				case NpcID.VORKATH:
				case NpcID.VORKATH_8058:
				case NpcID.VORKATH_8059:
				case NpcID.VORKATH_8060:
				case NpcID.VORKATH_8061:
				{
					int x = defaultLocation.getX() + 3;
					int y = defaultLocation.getY() + 3;
					if (playerLocationLastTick.getX() < x)
					{
						x = x - 4;
					}
					else if (playerLocationLastTick.getX() > x)
					{
						x = x + 4;
					}
					if (playerLocationLastTick.getY() < y)
					{
						y = y - 4;
					}
					else if (playerLocationLastTick.getY() > y)
					{
						y = y + 4;
					}
					return new WorldPoint[]
							{
									new WorldPoint(x, y, defaultLocation.getPlane())
							};
				}

				case NpcID.CORPOREAL_BEAST:
				{
					return new WorldPoint[]
							{
									new WorldPoint(
											defaultLocation.getX() + 1,
											defaultLocation.getY() + 1,
											defaultLocation.getPlane())
							};
				}

				case NpcID.ABYSSAL_SIRE:
				case NpcID.ABYSSAL_SIRE_5887:
				case NpcID.ABYSSAL_SIRE_5888:
				case NpcID.ABYSSAL_SIRE_5889:
				case NpcID.ABYSSAL_SIRE_5890:
				case NpcID.ABYSSAL_SIRE_5891:
				case NpcID.ABYSSAL_SIRE_5908:
				{
					return new WorldPoint[]
							{
									new WorldPoint(
											pad.getActor().getWorldLocation().getX() + 2,
											pad.getActor().getWorldLocation().getY() + 2,
											pad.getActor().getWorldLocation().getPlane())
							};
				}
			}

			int size = ((MemorizedNpc) pad).getNpcComposition().getSize();
			if (size >= 3)
			{
				// Some large NPCs (mostly bosses) drop their loot in the middle
				// of them rather than on the southwestern spot, so
				// we want to check both of them.
				return new WorldPoint[]
						{
								defaultLocation,
								new WorldPoint(
										defaultLocation.getX() + (size - 1) / 2,
										defaultLocation.getY() + (size - 1) / 2,
										defaultLocation.getPlane())
						};
			}
		}

		return new WorldPoint[] { defaultLocation };
	}

	/**
	 * Loops over deadActorsThisTick and determines what loot the Actor(s) dropped
	 */
	private void checkActorDeaths()
	{
		for (MemorizedActor pad : deadActorsThisTick)
		{
			// Pvp kills can happen in Chambers of Xeric when someone
			// dies and their raid potions drop, but we don't want to
			// log those.
			if (pad instanceof MemorizedPlayer && insideChambersOfXeric)
			{
				continue;
			}

			// Stores new items for each new world point. Some NPCs can drop loot on multiple tiles.
			WorldPoint[] locations = getExpectedDropLocations(pad);
			Multimap<WorldPoint, Item> worldDrops = ArrayListMultimap.create();
			for (WorldPoint location : locations)
			{
				List<Item> drops = getItemDifferencesAt(location);
				if (drops == null || drops.size() == 0)
				{
					continue;
				}

				worldDrops.putAll(location, drops);
			}

			// Didn't find any loot for this Actor
			if (worldDrops.size() == 0)
			{
				log.debug("No Loot found for Actor: {}", pad);
				log.debug("Locations: {}", Arrays.asList(locations));
				continue;
			}

			List<Item> dropList;

			// If multiple interacted NPCs died on the same tick we need to calculate
			// how many npcs died on the same tile to evenly split the loot
			if (deadActorsThisTick.size() > 1)
			{
				boolean foundIndex = false;
				int index = 0;
				int killsAtWP = 0;
				// Support for multiple NPCs dying on the same tick at the same time
				for (MemorizedActor pad2 : deadActorsThisTick)
				{
					if (pad2.getActor().getWorldLocation().distanceTo(pad.getActor().getWorldLocation()) == 0)
					{
						killsAtWP++;
						if (!foundIndex)
						{
							index++;
							if (pad == pad2)
							{
								foundIndex = true;
							}
						}
					}
				}

				// Creating a map in case the quantity needs to be updated for certain items
				Map<Integer, Integer> drops = new HashMap<>();
				for (Map.Entry<WorldPoint, Item> entry : worldDrops.entries())
				{
					// The way we handle multiple kills on the same WorldPoint in the same tick
					// is by splitting up all the drops equally, i.e. if 2 kills happened at the
					// same time and they dropped 3 items of the same type, 1 item would be
					// accounted for the first kill and 2 for the second.
					Item i = entry.getValue();
					int nextCount = (i.getQuantity() * index / killsAtWP) -
							(i.getQuantity() * (index - 1) / killsAtWP);
					if (nextCount == 0)
					{
						continue;
					}
					int count = drops.getOrDefault(i.getId(), 0);
					drops.put(i.getId(), nextCount + count);
				}

				// Convert Map to List of Items to return
				dropList = new ArrayList<>();
				for (Map.Entry<Integer, Integer> e : drops.entrySet())
				{
					dropList.add(client.createItem(e.getKey(), e.getValue()));
				}
			}
			else
			{
				// Creating a map in case the quantity needs to be updated for certain items
				Map<Integer, Integer> drops = new HashMap<>();
				for (Map.Entry<WorldPoint, Item> entry : worldDrops.entries())
				{
					Item i = entry.getValue();
					int count = drops.getOrDefault(i.getId(), 0);
					drops.put(i.getId(), i.getQuantity() + count);
				}

				// Convert Map to List of Items to return
				dropList = new ArrayList<>();
				for (Map.Entry<Integer, Integer> e : drops.entrySet())
				{
					dropList.add(client.createItem(e.getKey(), e.getValue()));
				}
			}

			// Actor type, Calls the wrapper for triggering the proper LootReceived event
			if (pad instanceof MemorizedNpc)
			{
				NPCComposition c = ((MemorizedNpc) pad).getNpcComposition();
				onNewNpcLogCreated(c.getId(), c, pad.getActor().getWorldLocation(), dropList);
			}
			else if (pad instanceof MemorizedPlayer)
			{
				Player p = (Player) pad.getActor();
				onNewPlayerLogCreated(p, p.getWorldLocation(), dropList);
			}
			else
			{
				log.error("Unrecognized actor death");
			}
		}

		deadActorsThisTick.clear();
	}

	/**
	 * Stores all Items still on the floor to the previous tick variable
	 */
	private void updateGroundItemLayers()
	{
		for (Tile tile : this.changedItemLayerTiles)
		{
			WorldPoint wp = tile.getWorldLocation();
			List<Item> groundItems = tile.getGroundItems();
			if (groundItems == null)
			{
				groundItemsLastTick.remove(wp);
			}
			else
			{
				groundItemsLastTick.put(wp, groundItems);
			}
		}

		this.changedItemLayerTiles.clear();
	}

	/*
	 * Subscribe events which do some basics checks and information updating
	 */

	/**
	 * Clear ground items map on region change
	 */
	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOGGED_IN)
		{
			groundItemsLastTick.clear();
		}

		// Clear interacted map when player logs out
		if (e.getGameState() == GameState.LOGIN_SCREEN)
		{
			groundItemsLastTick.clear();
			interactedActors.clear();
		}
	}

	/**
	 * Location Checks
	 */
	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		insideChambersOfXeric = !(client.getVar(Varbits.IN_RAID) == 0);
		if (!insideChambersOfXeric)
		{
			this.hasOpenedRaidsRewardChest = false;
		}

		int theatreState = client.getVar(Varbits.THEATRE_OF_BLOOD);
		if (theatreState == 0 || theatreState == 1)
		{
			this.hasOpenedTheatreOfBloodRewardChest = false;
		}
	}

	/**
	 * Event/Activity loot received management
	 */
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		// Barrows
		if (event.getGroupId() == WidgetID.BARROWS_REWARD_GROUP_ID)
		{
			ItemContainer container = client.getItemContainer(InventoryID.BARROWS_REWARD);
			if (container != null)
			{
				List<Item> items = Arrays.asList(container.getItems());
				onNewEventLogCreated(LootEventType.BARROWS, items);
			}
			else
			{
				log.debug("Error finding Barrows Item Container");
			}
		}
		// Chambers of Xeric / Raids 1
		else if (event.getGroupId() == WidgetID.CHAMBERS_OF_XERIC_REWARD_GROUP_ID && !hasOpenedRaidsRewardChest)
		{
			hasOpenedRaidsRewardChest = true;

			ItemContainer container = client.getItemContainer(InventoryID.CHAMBERS_OF_XERIC_CHEST);
			if (container != null)
			{
				List<Item> items = Arrays.asList(container.getItems());
				onNewEventLogCreated(LootEventType.RAIDS, items);
			}
			else
			{
				log.debug("Error finding Chamber of Xeric Item Container");
			}
		}
		// Theatre of Blood / Raids 2
		else if (event.getGroupId() == WidgetID.THEATRE_OF_BLOOD_GROUP_ID && !hasOpenedTheatreOfBloodRewardChest)
		{
			hasOpenedTheatreOfBloodRewardChest = true;

			ItemContainer container = client.getItemContainer(InventoryID.THEATRE_OF_BLOOD_CHEST);
			if (container != null)
			{
				List<Item> items = Arrays.asList(container.getItems());
				onNewEventLogCreated(LootEventType.RAIDS, items);
			}
			else
			{
				log.debug("Error finding Theatre of Blood Item Container");
			}
		}
		// Clue Scrolls
		else if (event.getGroupId() == WidgetID.CLUE_SCROLL_REWARD_GROUP_ID)
		{
			// TODO: Figure out how to determine clue scroll type
			LootEventType clueType = LootEventType.UNKNOWN_EVENT;

			// Clue Scrolls use same InventoryID as Barrows
			ItemContainer container = client.getItemContainer(InventoryID.BARROWS_REWARD);
			if (container != null)
			{
				List<Item> items = Arrays.asList(container.getItems());
				onNewEventLogCreated(clueType, items);
			}
			else
			{
				log.debug("Error finding clue scroll Item Container");
			}
		}
	}

	/**
	 * Certain NPCs determine loot tile on death animation and not on de-spawn
	 */
	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (!(event.getActor() instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC)event.getActor();
		int npcId = npc.getId();
		if (!NPC_DEATH_ANIMATIONS.containsKey(npcId))
		{
			return;
		}

		if (NPC_DEATH_ANIMATIONS.get(npcId) == npc.getAnimation())
		{
			MemorizedActor memorizedActor = interactedActors.get(npc);
			if (memorizedActor != null)
			{
				if (npcId == NpcID.CAVE_KRAKEN)
				{
					if (memorizedActor instanceof MemorizedNpcAndLocation)
					{
						// Cave kraken decide where to drop the loot right when they
						// start the death animation, but it doesn't appear until
						// the death animation has finished
						((MemorizedNpcAndLocation) memorizedActor).setExpectedDropLocation(playerLocationLastTick);
					}
				}
				else
				{
					deadActorsThisTick.add(memorizedActor);
				}
			}
		}
	}

	/**
	 * Remember dead actors until next tick if we interacted with them
	 */
	@Subscribe
	public void onActorDespawned(ActorDespawned event)
	{
		// This event runs before the ItemLayerChanged event,
		// so we have to wait until the end of the game tick
		// before we know what items were dropped

		MemorizedActor ma = interactedActors.get(event.getActor());
		if (ma != null)
		{
			interactedActors.remove(event.getActor());
			double deathHealth = 0;

			if (event.getActor() instanceof NPC)
			{
				NPC n = (NPC) event.getActor();
				Double ratio = NpcHpDeath.npcDeathHealthPercent(n.getId());
				if (ratio > 0.00)
				{
					deathHealth = Math.ceil(ratio * event.getActor().getHealth());
				}
			}

			if (event.getActor().getHealthRatio() <= deathHealth)
			{
				deadActorsThisTick.add(ma);
			}
		}
	}

	/**
	 * Track tiles where an item layer changed (for each tick)
	 */
	@Subscribe
	public void onItemLayerChanged(ItemLayerChanged event)
	{
		// Note: This event runs 10816 (104*104) times after
		// a new loading screen. Perhaps there is a way to
		// reduce the amount of times it runs?

		this.changedItemLayerTiles.add(event.getTile());
	}

	/**
	 * Every game tick we call all necessary functions to calculate Received Loot
	 *
	 * <p><strong>We must do the following to correctly determine dropped NPC loot</strong></p>
	 * <p>1) Memorize which actors we have interacted with</p>
	 * <p>2) Check for any item changes (Disappearing from floor, Added/Removed from inventory)</p>
	 * <p>3) Loop over all dead actor and determine what loot they dropped</p>
	 * <p><strong>Now that we are done determining loot we need to prepare for the next tick.</strong></p>
	 * <p>1) Move all data to lastTick variables (Ground Items/Inventory Items)</p>
	 * <p>2) Store current player world point</p>
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		checkInteracting();
		checkActorDeaths();
		updateGroundItemLayers();
		playerLocationLastTick = client.getLocalPlayer().getWorldLocation();
	}
}