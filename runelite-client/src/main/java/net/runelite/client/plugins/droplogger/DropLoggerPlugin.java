/*
 * Copyright (c) 2018, Woox <https://github.com/wooxsolo>
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
package net.runelite.client.plugins.droplogger;

import com.google.common.eventbus.Subscribe;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ConfigChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.Notifier;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.loot.LootEventType;
import net.runelite.client.game.loot.events.EventLootReceived;
import net.runelite.client.game.loot.events.NpcLootReceived;
import net.runelite.client.game.loot.events.PlayerLootReceived;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.droplogger.data.LootEntry;
import net.runelite.client.plugins.droplogger.data.Pet;
import net.runelite.client.plugins.droplogger.data.WatchNpcs;
import net.runelite.client.plugins.droplogger.ui.LoggerPanel;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginToolbar;
import net.runelite.client.util.Text;

@PluginDescriptor(
		name = "Drop Logger",
		description = "Log loot from Players and NPCs",
		tags = {"drop", "logger", "recorder", "loot", "tracker"}
)
@Slf4j
public class DropLoggerPlugin extends Plugin
{
	// IN_GAME_BA, IN_RAID

	@Inject
	private Client client;

	@Inject
	@Getter
	private DropLoggerConfig config;

	@Inject
	private PluginToolbar pluginToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private Notifier notifier;

	@Inject
	private ChatMessageManager chatMessageManager;

	private NavigationButton navButton;
	private LoggerPanel panel;

	// Chat Message Regex
	private static final Pattern NUMBER_PATTERN = Pattern.compile("([0-9]+)");
	private static final Pattern BOSS_NAME_PATTERN = Pattern.compile("Your (.*) kill count is:");
	private static final Pattern PET_RECEIVED_PATTERN = Pattern.compile("You have a funny feeling like ");
	private static final Pattern PET_RECEIVED_INVENTORY_PATTERN = Pattern.compile("You feel something weird sneaking into your backpack.");
	private static final Pattern CLUE_SCROLL_PATTERN = Pattern.compile("You have completed (\\d*) (\\w*) Treasure Trails.");

	// In-game notification message color
	private String messageColor = "";
	private boolean gotPet = false;

	// Mapping Variables
	private Map<String, ArrayList<LootEntry>> lootMap = new HashMap<>(); // Store loot entries for each NPC/Boss name
	private Map<String, Integer> killcountMap = new HashMap<>(); 		 // Store kill count by name

	@Getter
	private TreeSet<String> sessionActors = new TreeSet<>();

	@Provides
	DropLoggerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DropLoggerConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		init();

		BufferedImage icon;
		synchronized (ImageIO.class)
		{
			icon = ImageIO.read(DropLoggerPlugin.class.getResourceAsStream("panel_icon.png"));
		}
		panel = new LoggerPanel(this, itemManager);

		navButton = NavigationButton.builder()
			.tooltip("Drop Logger")
			.priority(3)
			.icon(icon)
			.panel(panel)
			.build();
		this.pluginToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() throws Exception
	{
		pluginToolbar.removeNavigation(navButton);
	}

	@Subscribe
	protected void onPlayerLootReceived(PlayerLootReceived e)
	{
		log.info("Player loot Received: {}", e);
	}

	private void init()
	{
		// Create maps for easy management of certain features
		lootMap =  new HashMap<>();
		killcountMap = new HashMap<>();

		// Ensure we are using the requested message coloring for in-game messages
		updateMessageColor();
	}

	public ArrayList<LootEntry> getData(String name)
	{
		return lootMap.get(name.toUpperCase());
	}

	private Item handlePet(String name)
	{
		gotPet = false;
		int petID = getPetId(name);
		dropLoggedAlert("Oh lookie a pet! Don't forget to insure it!");
		return client.createItem(petID, 1);
	}

	private int getPetId(String name)
	{
		Pet pet = Pet.getByBossName(name);
		if (pet != null)
		{
			return pet.getPetID();
		}

		return -1;
	}

	// All alerts from this plugin should use this function
	private void dropLoggedAlert(String message)
	{
		message = "Drop Logger: " + message;
		if (config.showChatMessages())
		{
			final ChatMessageBuilder chatMessage = new ChatMessageBuilder()
					.append("<col=" + messageColor + ">")
					.append(message)
					.append("</col>");

			chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.EXAMINE_ITEM)
					.runeLiteFormattedMessage(chatMessage.build())
					.build());
		}

		if (config.showTrayAlerts())
		{
			notifier.notify(message);
		}
	}

	private void addLootEntry(String name, LootEntry entry)
	{
		name = name.toUpperCase();
		ArrayList<LootEntry> loots = lootMap.get(name);
		if (loots == null)
		{
			loots = new ArrayList<>();
		}

		loots.add(entry);
		lootMap.put(name, loots);
	}

	// Add drop to last Loot Entry in map or create if doesn't exist.
	private void addDropToLastLootEntry(String name, Item newDrop)
	{
		String nameCased = name.toUpperCase();
		ArrayList<LootEntry> loots = lootMap.get(nameCased);
		if (loots == null || loots.size() == 0)
		{
			LootEntry entry = new LootEntry(name, -1, new ArrayList<Item>());
			entry.addDropItem(newDrop);
			loots = new ArrayList<>();
			loots.add(entry);
		}
		else
		{
			LootEntry entry = loots.get(loots.size() - 1);
			entry.addDropItem(newDrop);
			loots.add(loots.size() - 1, entry);
		}

		// Ensure updates is applied, may not be necessary
		lootMap.put(nameCased, loots);
	}

	// Upon cleaning an Unsired add the item to the previous LootEntry
	private void receivedUnsiredLoot(int itemID)
	{
		Item drop = client.createItem(itemID, 1);
		// Update the last drop
		addDropToLastLootEntry(Boss.ABYSSAL_SIRE.getBossName(), drop);
	}

	public void clearData(String name)
	{
		name = name.toUpperCase();
		lootMap.put(name, new ArrayList<>());
		log.debug("Cleared data for NPCs named: {}", name);
	}

	// Updates in-game alert chat color based on config settings
	private void updateMessageColor()
	{
		Color c = config.chatMessageColor();
		messageColor = String.format("%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}

	@Subscribe
	protected void onEventLootReceived(EventLootReceived e)
	{
		int kc = -1;
		String eventName = e.getEvent().name().replaceAll("_", " ");
		switch (e.getEvent())
		{
			case THEATRE_OF_BLOOD:
				kc = killcountMap.get("RAIDS 2");
				break;
			case BARROWS:
			case CHAMBERS_OF_XERIC:
			case CLUE_SCROLL_EASY:
			case CLUE_SCROLL_MEDIUM:
			case CLUE_SCROLL_HARD:
			case CLUE_SCROLL_ELITE:
			case CLUE_SCROLL_MASTER:
				kc = killcountMap.get(eventName);
				break;
			case UNKNOWN_EVENT:
				log.debug("Unknown Event: {}", e);
				break;
			default:
				log.debug("Unhandled Event: {}", e.getEvent());
		}
		if (kc == -1)
			return;

		// Create loot entry and store it to file
		LootEntry entry = new LootEntry(kc, e.getItems());
		// Got a pet?
		if (gotPet)
			entry.addDropItem(handlePet(eventName));
		addLootEntry(eventName, entry);

		dropLoggedAlert("Loot from " + eventName.toLowerCase() + " added to log.");
	}

	// Only check for Boss NPCs
	@Subscribe
	protected void onNpcLootReceived(NpcLootReceived e)
	{
		log.info("NPC loot Received: {}", e);

		String name = e.getComposition().getName();

		// Certain NPCs we care about their kill count.
		WatchNpcs watchList = WatchNpcs.getByNpcId(e.getNpcId());
		LootEntry lootEntry = null;
		if (watchList != null)
		{
			// Find tab that cares about this NPC
			Boss boss = Boss.getByBossName(watchList.getName());
			if (boss == null)
			{
				log.warn("Couldn't find a tab for WatchNpcs: ", watchList);
			}
			else
			{
				name = boss.getBossName().toUpperCase();
				int KC = killcountMap.get(name);
				lootEntry = new LootEntry(name, KC, e.getItems());

				if (gotPet)
				{
					lootEntry.addDropItem(handlePet(name));
				}
			}
		}

		if (lootEntry == null)
		{
			lootEntry = new LootEntry(e.getNpcId(), e.getComposition().getName(), e.getItems());
		}

		// Add the loot to the file
		addLootEntry(name, lootEntry);
		addNewSessionActor(name);
	}

	private void addNewSessionActor(String name)
	{
		int old = sessionActors.size();
		sessionActors.add(name);
		if (old < sessionActors.size())
		{
			// New SessionActor, refresh landing page.
			panel.newSessionActor(sessionActors);
		}
	}

	// Check for Unsired loot reclaiming
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		// Received unsired loot?
		if (event.getGroupId() == WidgetID.DIALOG_SPRITE_GROUP_ID)
		{
			Widget text = client.getWidget(WidgetInfo.DIALOG_SPRITE_TEXT);
			if ("The Font consumes the Unsired and returns you a reward.".equals(text.getText()))
			{
				Widget sprite = client.getWidget(WidgetInfo.DIALOG_SPRITE);
				receivedUnsiredLoot(sprite.getItemId());
			}
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		// Only update if our plugin config was changed
		if (!event.getGroup().equals("droplogger"))
		{
			return;
		}

		switch (event.getKey())
		{
			case "chatMessageColor":
				// Update in-game alert color
				updateMessageColor();
				dropLoggedAlert("Example Message");
				return;
			default:
				break;
		}
	}

	// Chat Message parsing kill count value and/or pet drop
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.SERVER && event.getType() != ChatMessageType.FILTERED)
		{
			return;
		}

		String chatMessage = event.getMessage();

		// Barrows KC
		if (chatMessage.startsWith("Your Barrows chest count is"))
		{
			Matcher m = NUMBER_PATTERN.matcher(Text.removeTags(chatMessage));
			if (m.find())
			{
				killcountMap.put("BARROWS", Integer.valueOf(m.group()));
				return;
			}
		}

		// Raids KC
		if (chatMessage.startsWith("Your completed Chambers of Xeric count is"))
		{
			Matcher m = NUMBER_PATTERN.matcher(Text.removeTags(chatMessage));
			if (m.find())
			{
				killcountMap.put("RAIDS", Integer.valueOf(m.group()));
				return;
			}
		}

		// Raids KC
		if (chatMessage.startsWith("Your completed Theatre of Blood count is"))
		{
			Matcher m = NUMBER_PATTERN.matcher(Text.removeTags(chatMessage));
			if (m.find())
			{
				killcountMap.put("RAIDS 2", Integer.valueOf(m.group()));
				return;
			}
		}

		Matcher clueScroll = CLUE_SCROLL_PATTERN.matcher(chatMessage);
		if (clueScroll.find())
		{
			LootEventType type = null;
			switch (clueScroll.group(2).toUpperCase())
			{
				case "EASY":
					type = LootEventType.CLUE_SCROLL_EASY;
					break;
				case "MEDIUM":
					type = LootEventType.CLUE_SCROLL_MEDIUM;
					break;
				case "HARD":
					type = LootEventType.CLUE_SCROLL_HARD;
					break;
				case "ELITE":
					type = LootEventType.CLUE_SCROLL_ELITE;
					break;
				case "MASTER":
					type = LootEventType.CLUE_SCROLL_MASTER;
					break;
			}

			if (type == null)
				return;
			String name = type.name().replaceAll("_", " ");

			killcountMap.put(name, Integer.valueOf(clueScroll.group(1)));
			return;
		}

		// Handle all other boss
		Matcher boss = BOSS_NAME_PATTERN.matcher(Text.removeTags(chatMessage));
		if (boss.find())
		{
			String bossName = boss.group(1);
			Matcher m = NUMBER_PATTERN.matcher(Text.removeTags(chatMessage));
			if (!m.find())
				return;
			int KC = Integer.valueOf(m.group());
			killcountMap.put(bossName.toUpperCase(), KC);
		}

		// Pet Drop
		Matcher pet1 = PET_RECEIVED_PATTERN.matcher(Text.removeTags(chatMessage));
		Matcher pet2 = PET_RECEIVED_INVENTORY_PATTERN.matcher(Text.removeTags(chatMessage));
		if (pet1.find() || pet2.find())
		{
			gotPet = true;
		}
	}
}