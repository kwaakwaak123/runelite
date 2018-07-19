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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ConfigChanged;
import net.runelite.api.events.GameTick;
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
import net.runelite.client.plugins.droplogger.data.Boss;
import net.runelite.client.plugins.droplogger.data.GroundItem;
import net.runelite.client.plugins.droplogger.data.LootEntry;
import net.runelite.client.plugins.droplogger.data.Pet;
import net.runelite.client.plugins.droplogger.data.SessionLog;
import net.runelite.client.plugins.droplogger.data.SessionLogData;
import net.runelite.client.plugins.droplogger.data.SessionNpcLog;
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

	private BossLoggerWriter writer;

	private LoggerPanel panel;

	// Chat Message Regex
	private static final Pattern NUMBER_PATTERN = Pattern.compile("([0-9]+)");
	private static final Pattern BOSS_NAME_PATTERN = Pattern.compile("Your (.*) kill count is:");
	private static final Pattern PET_RECEIVED_PATTERN = Pattern.compile("You have a funny feeling like ");
	private static final Pattern PET_RECEIVED_INVENTORY_PATTERN = Pattern.compile("You feel something weird sneaking into your backpack.");
	private static final Pattern CLUE_SCROLL_PATTERN = Pattern.compile("You have completed (\\d*) (\\w*) Treasure Trails.");

	// Time as in amount of game ticks
	private static final int NPC_DROP_DISAPPEAR_TIME = 199; // 2 minutes if item was dropped by an NPC
	private static final int PLAYER_DROP_DISAPPEAR_TIME = 299; // 3 minutes if player drops an item
	private static final int INSTANCE_DROP_DISAPPEAR_TIME = 2999; // 30 minutes for various instances

	// In-game notification message color
	private String messageColor = "";
	private boolean gotPet = false;

	private int tickCounter = 0;

	// Mapping Variables
	private Map<Boss, Boolean> recordingMap = new HashMap<>(); 				// Store config recording value for each Tab
	private Map<Boss, ArrayList<LootEntry>> lootMap = new HashMap<>();		// Store loot entries for each Tab
	private Map<Boss, String> filenameMap = new HashMap<>(); 				// Stores filename for each Tab
	private Map<String, Integer> killcountMap = new HashMap<>(); 			// Store boss kill count by boss name

	// Session Variables
	private Multimap<WorldPoint, GroundItem> myItems = ArrayListMultimap.create();
	private Multimap<Integer, GroundItem> itemDisappearMap = Multimaps.newListMultimap(Maps.newTreeMap(), Lists::newArrayList);
	private Multimap<Integer, SessionLog> responsibleLogs = Multimaps.newSetMultimap(Maps.newHashMap(), Sets::newHashSet);

	@Getter
	private SessionLogData sessionLogData;

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
		panel.createSessionPanel(sessionLogData);

		NavigationButton navButton = NavigationButton.builder()
			.tooltip("Drop Logger")
			.priority(3)
			.icon(icon)
			.panel(panel)
			.build();
		this.pluginToolbar.addNavigation(navButton);

		writer = new BossLoggerWriter(client, filenameMap);
	}

	@Override
	protected void shutDown() throws Exception
	{
		this.tickCounter = 0;
	}

	@Subscribe
	public void onGameTick(GameTick t)
	{
		tickCounter++;
	}

	@Subscribe
	protected void onPlayerLootReceived(PlayerLootReceived e)
	{
		log.info("Player loot Received: {}", e);
	}

	private void init()
	{
		this.tickCounter = 0;

		this.sessionLogData = new SessionLogData();

		// Create maps for easy management of certain features
		Map<Boss, Boolean> mapRecording = new HashMap<>();
		Map<Boss, ArrayList<LootEntry>> mapLoot = new HashMap<>();
		Map<Boss, String> mapFilename = new HashMap<>();
		Map<String, Integer> mapKillcount = new HashMap<>();
		for (Boss tab : Boss.values())
		{
			// Is Boss being recorded?
			mapRecording.put(tab, true);
			// Loot Entries by Tab Name
			ArrayList<LootEntry> array = new ArrayList<LootEntry>();
			mapLoot.put(tab, array);
			// Filenames. Removes all spaces, periods, and apostrophes
			String filename = tab.getName().replaceAll("( |'|\\.)", "").toLowerCase() + ".log";
			mapFilename.put(tab, filename);
			// Kill Count
			int killcount = 0;
			mapKillcount.put(tab.getBossName().toUpperCase(), killcount);
		}
		recordingMap = mapRecording;
		lootMap = mapLoot;
		killcountMap = mapKillcount;
		filenameMap = mapFilename;

		// Ensure we are using the requested message coloring for in-game messages
		updateMessageColor();
	}

	public void loadTabData(Boss tab)
	{
		loadLootEntries(tab);
	}

	// Load data for all bosses being recorded
	private void loadAllData()
	{
		for (Boss tab : Boss.values())
		{
			loadLootEntries(tab);
		}
	}

	// Returns stored data by tab
	public ArrayList<LootEntry> getData(Boss tab)
	{
		// Loot Entries are stored on lootMap by boss name (upper cased)
		return lootMap.get(tab);
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

	// Keep the subscribe a bit cleaner, may be a better way to handle this
	private void handleConfigChanged(String eventKey)
	{
		switch (eventKey)
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

	// Wrapper for changing local writing directory
	private void updatePlayerFolder()
	{
		boolean changed = writer.updatePlayerFolder();
		if (changed)
		{
			// Reset stored data
			for (Boss boss : Boss.values())
			{
				lootMap.put(boss, new ArrayList<>());
			}
		}
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

	// Adds the data to the correct boss log file
	private void AddBossLootEntry(String bossName, List<Item> drops)
	{
		int KC = killcountMap.get(bossName.toUpperCase());
		LootEntry newEntry = new LootEntry(KC, drops);
		if (gotPet)
			newEntry.addDrop(handlePet(bossName));
		addLootEntry(bossName, newEntry);
		dropLoggedAlert(bossName + " kill added to log.");
	}

	// Wrapper for writer.addLootEntry
	private void addLootEntry(String bossName, LootEntry entry)
	{
		updatePlayerFolder();

		Boss boss = Boss.getByBossName(bossName);
		if (boss == null)
		{
			log.debug("Cant find tab for boss: {}", bossName);
			return;
		}

		// Update data inside plugin
		ArrayList<LootEntry> loots = lootMap.get(boss);
		loots.add(entry);
		lootMap.put(boss, loots);

		boolean success = writer.addLootEntry(boss, entry);

		if (!success)
		{
			log.debug("Couldn't add entry to tab. (tab: {} | entry: {})", boss, entry);
		}
	}

	// Receive Loot from the necessary file
	private synchronized void loadLootEntries(Boss boss)
	{
		updatePlayerFolder();

		ArrayList<LootEntry> data = writer.loadLootEntries(boss);

		if (data == null)
		{
			log.debug("Couldn't find local data for boss: {}", boss);
			lootMap.put(boss, new ArrayList<>());
			return;
		}

		// Update Loot Map with new data
		lootMap.put(boss, data);

		// Update Killcount map with latest value
		if (data.size() > 0)
		{
			int killcount = data.get(data.size() - 1).getKillCount();
			killcountMap.put(boss.getBossName().toUpperCase(), killcount);
		}
	}

	// Add Loot Entry to the necessary file
	private void addDropToLastLootEntry(Boss boss, Item newDrop)
	{
		// Update data inside plugin
		ArrayList<LootEntry> loots = lootMap.get(boss);
		LootEntry entry = loots.get(loots.size() - 1);
		entry.addDrop(newDrop);
		// Ensure updates are applied, may not be necessary
		loots.add(loots.size() - 1, entry);
		lootMap.put(boss, loots);

		updatePlayerFolder();

		rewriteLootFile(boss, loots);
	}

	// Wrapper for writer.rewriteLootFile
	private void rewriteLootFile(Boss boss, ArrayList<LootEntry> loots)
	{
		boolean success = writer.rewriteLootFile(boss, loots);
		if (!success)
		{
			log.debug("Couldn't add drop to last loot entry");
		}
	}

	// Upon cleaning an Unsired add the item to the previous LootEntry
	private void receivedUnsiredLoot(int itemID)
	{
		Item drop = client.createItem(itemID, 1);
		// Update the last drop
		addDropToLastLootEntry(Boss.ABYSSAL_SIRE, drop);
	}

	// Clear stored data for specific boss
	public void clearData(Boss boss)
	{
		log.debug("Clearing data for boss: " + boss.getName());
		writer.clearLootFile(boss);
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
			entry.addDrop(handlePet(eventName));
		addLootEntry(eventName, entry);

		dropLoggedAlert("Loot from " + eventName.toLowerCase() + " added to log.");
	}

	// Only check for Boss NPCs
	@Subscribe
	protected void onNpcLootReceived(NpcLootReceived e)
	{
		log.info("NPC loot Received: {}", e);

		// Session Code
		List<Item> items = e.getItems();
		SessionLog detailedLog = new SessionNpcLog(items, e.getComposition());

		int itemDuration = (client.isInInstancedRegion() ? INSTANCE_DROP_DISAPPEAR_TIME : NPC_DROP_DISAPPEAR_TIME);
		int disappearsOnTick = tickCounter + itemDuration;
		for (Item i : items)
		{
			GroundItem groundItem = new GroundItem(i.getId(), i.getQuantity(), e.getLocation(), disappearsOnTick, detailedLog);

			// Memorize which items on the ground were dropped users kills and when we can forget them
			myItems.put(groundItem.getLocation(), groundItem);
			itemDisappearMap.put(disappearsOnTick, groundItem);
		}

		sessionLogData.getSessionLogs().add(detailedLog);
		panel.updatedSessionLog();


		// Boss Logging only cares about certain NPCs
		WatchNpcs watchList = WatchNpcs.getByNpcId(e.getNpcId());
		if (watchList == null)
			return;

		// Find tab that cares about this NPC
		Boss boss = Boss.getByBossName(watchList.getName());
		if (boss == null)
		{
			log.warn("Couldn't find a tab for WatchNpcs: ", watchList);
			return;
		}

		// User wants us to record this tab?
		Boolean recordingFlag = recordingMap.get(boss);
		if (recordingFlag == null || !recordingFlag)
			return;

		// Add the loot to the file
		AddBossLootEntry(boss.getBossName(), e.getItems());
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

		handleConfigChanged(event.getKey());
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