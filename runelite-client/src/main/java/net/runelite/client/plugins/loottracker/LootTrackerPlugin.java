/*
 * Copyright (c) 2018, Psikoi <https://github.com/psikoi>
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
package net.runelite.client.plugins.loottracker;

import com.google.common.eventbus.Subscribe;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Varbits;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.WidgetID;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginToolbar;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Loot Tracker",
	description = "", //TODO write plugin description
	tags = {"loot", "drops"}, //TODO write plugin tags
	loadWhenOutdated = true
)
@Slf4j
public class LootTrackerPlugin extends Plugin
{
	@Inject
	private PluginToolbar pluginToolbar;

	@Inject
	private Client client;

	private LootTrackerPanel panel;
	private NavigationButton navButton;

	// Activity/Event loot handling
	private static final Pattern CLUE_SCROLL_PATTERN = Pattern.compile("You have completed [0-9]+ ([a-z]+) Treasure Trails.");
	private String eventType = null;

	@Override
	protected void startUp() throws Exception
	{
		panel = injector.getInstance(LootTrackerPanel.class);
		panel.init();

		BufferedImage icon;
		synchronized (ImageIO.class)
		{
			icon = ImageIO.read(LootTrackerPanel.class.getResourceAsStream("reset.png"));
		}

		navButton = NavigationButton.builder()
			.tooltip("Loot Tracker")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();

		pluginToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		pluginToolbar.removeNavigation(navButton);
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived npcLootReceived)
	{
		NPC npc = npcLootReceived.getNpc();
		Collection<ItemStack> items = npcLootReceived.getItems();
		final String name = npc.getName();
		final int combat = npc.getCombatLevel();
		SwingUtilities.invokeLater(() -> panel.addLog(name, combat, items.toArray(new ItemStack[items.size()])));
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		ItemContainer container = null;
		switch (event.getGroupId())
		{
			case (WidgetID.BARROWS_REWARD_GROUP_ID):
				eventType = "Barrows";
				container = client.getItemContainer(InventoryID.BARROWS_REWARD);
				break;
			case (WidgetID.CHAMBERS_OF_XERIC_REWARD_GROUP_ID):
				eventType = "Chambers of Xeric";
				container = client.getItemContainer(InventoryID.CHAMBERS_OF_XERIC_CHEST);
				break;
			case (WidgetID.THEATRE_OF_BLOOD_GROUP_ID):
				eventType = "Theatre of Blood";
				container = client.getItemContainer(InventoryID.THEATRE_OF_BLOOD_CHEST);
				break;
			case (WidgetID.CLUE_SCROLL_REWARD_GROUP_ID):
				// event type should be set via ChatMessage for clue scrolls.
				// Clue Scrolls use same InventoryID as Barrows
				container = client.getItemContainer(InventoryID.BARROWS_REWARD);
				break;
			default:
				return;
		}

		if (container != null)
		{
			// Convert container items to collection of ItemStack
			Collection<ItemStack> items = new ArrayList<>();
			for (Item item : container.getItems())
			{
				items.add(new ItemStack(item.getId(), item.getQuantity()));
			}

			if (!items.isEmpty())
			{
				log.debug("Loot Received from Event: {}", eventType);
				for (ItemStack item : items)
				{
					log.debug("Item Received: {}x {}", item.getQuantity(), item.getId());
				}
				SwingUtilities.invokeLater(() -> panel.addLog(eventType, -1, items.toArray(new ItemStack[items.size()])));
			}
			else
			{
				log.debug("No items to find for Event: {} | Container: {}", eventType, container);
			}
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.SERVER && event.getType() != ChatMessageType.FILTERED)
		{
			return;
		}

		String chatMessage = event.getMessage();

		// Check if message is for a clue scroll reward
		Matcher m = CLUE_SCROLL_PATTERN.matcher(Text.removeTags(chatMessage));
		if (m.find())
		{
			String type = m.group(1).toLowerCase();
			switch (type)
			{
				case "easy":
					eventType = "Clue Scroll (Easy)";
					break;
				case "medium":
					eventType = "Clue Scroll (Medium)";
					break;
				case "hard":
					eventType = "Clue Scroll (Hard)";
					break;
				case "elite":
					eventType = "Clue Scroll (Elite)";
					break;
				case "master":
					eventType = "Clue Scroll (Master)";
					break;
			}
		}
	}
}
