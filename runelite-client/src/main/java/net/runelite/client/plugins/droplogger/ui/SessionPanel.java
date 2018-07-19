/*
 * Copyright (c) 2018, Woox <https://github.com/WooxSolo>
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
package net.runelite.client.plugins.droplogger.ui;

import net.runelite.api.ItemComposition;
import net.runelite.api.ItemID;
import net.runelite.client.plugins.droplogger.data.EventOccurrenceCollection;
import net.runelite.client.plugins.droplogger.data.LoggedItem;
import net.runelite.client.plugins.droplogger.filter.DropCollectFilter;
import net.runelite.client.plugins.droplogger.filter.DropTypeFilter;
import net.runelite.client.plugins.droplogger.filter.GroupFilter;
import net.runelite.client.plugins.droplogger.data.SessionLogData;
import net.runelite.client.plugins.droplogger.filter.SessionLogsFilter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.ComboBoxListRenderer;
import net.runelite.client.ui.components.RuneliteList;
import net.runelite.client.ui.components.RuneliteListItemRenderer;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.runelite.client.util.StackFormatter;

public class SessionPanel extends JPanel
{
	private static final int MILLISECONDS_PER_HOUR = 3600 * 1000;
	private static final NumberFormat SMALL_NUMBER_FORMATTER = DecimalFormat.getInstance(Locale.US);
	static
	{
		((DecimalFormat)SMALL_NUMBER_FORMATTER).applyPattern("#0.0");
	}

	protected LoggerPanel parent;
	private SessionLogData logData;
	private SessionLogsFilter filter;

	private JPanel sessionDropsPanel;
	private JList npcList;
	private DefaultListModel npcListModel;

	SessionPanel(LoggerPanel parent, SessionLogData logData)
	{
		this.parent = parent;
		this.logData = logData;
		this.filter = new SessionLogsFilter();

		this.setLayout(new BorderLayout());

		JPanel dropsPanelContainer = new JPanel(new BorderLayout());
		dropsPanelContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		JPanel dropsPanel = new JPanel(new GridBagLayout());
		dropsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		dropsPanelContainer.add(dropsPanel, BorderLayout.NORTH);
		JScrollPane dropsScrollPane = new JScrollPane(dropsPanelContainer);
		dropsScrollPane.getVerticalScrollBar().setUnitIncrement(16);
		this.sessionDropsPanel = dropsPanel;

		updateLogIconTable(sessionDropsPanel, null, null);
		this.add(dropsScrollPane, BorderLayout.CENTER);

		JPanel filterContainer = new JPanel(new GridBagLayout());
		filterContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		filterContainer.setVisible(false);
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1;
		c.gridx = 0;
		c.gridy = 0;
		c.insets = new Insets(4, 0, 0, 0);
		c.gridwidth = 1;

		JComboBox<String> typeComboBox = new JComboBox<>(new String[]
				{
						"PvM",
						"PvP",
						"PvM and PvP"
				});
		typeComboBox.setRenderer(new ComboBoxListRenderer());
		typeComboBox.setFocusable(false);
		typeComboBox.setPreferredSize(new Dimension(this.getWidth(), 25));
		typeComboBox.addActionListener(e ->
		{
			int index = typeComboBox.getSelectedIndex();
			filter.setDropTypeFilter(DropTypeFilter.fromIndex(index));
			this.reloadDetailedLogs();
		});
		filterContainer.add(typeComboBox, c);
		c.gridy++;

		JComboBox<String> collectComboBox = new JComboBox<>(new String[]
				{
						"All drops",
						"Only picked drops",
						"Drops left on ground"
				});
		collectComboBox.setRenderer(new ComboBoxListRenderer());
		collectComboBox.setFocusable(false);
		collectComboBox.setPreferredSize(new Dimension(this.getWidth(), 25));
		collectComboBox.addActionListener(e ->
		{
			int index = collectComboBox.getSelectedIndex();
			filter.setDropCollectFilter(DropCollectFilter.fromIndex(index));
			this.reloadDetailedLogs();
		});
		filterContainer.add(collectComboBox, c);
		c.gridy++;

		JComboBox<String> areaComboBox = new JComboBox<>(new String[]
				{
						"Id, name, level",
						"Name",
						"Name, level",
				});
		areaComboBox.setRenderer(new ComboBoxListRenderer());
		areaComboBox.setFocusable(false);
		areaComboBox.setPreferredSize(new Dimension(this.getWidth(), 25));
		areaComboBox.addActionListener(e ->
		{
			int index = areaComboBox.getSelectedIndex();
			filter.setGroupFilter(GroupFilter.fromIndex(index));
			npcListModel.clear();
			filter.getEnabledEvents().clear();
			this.reloadDetailedLogs();
		});
		filterContainer.add(areaComboBox, c);
		c.gridy++;

		RuneliteList eventList = new RuneliteList();
		eventList.setCellRenderer(new RuneliteListItemRenderer());
		eventList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		DefaultListModel listModel = new DefaultListModel<>();
		eventList.setModel(listModel);
		this.npcList = eventList;
		this.npcListModel = listModel;
		eventList.setSelectionModel(new DefaultListSelectionModel()
		{
			@Override
			public void setSelectionInterval(int index0, int index1)
			{
				boolean shouldAdd = !this.isSelectedIndex(index1);
				int start = Math.min(index0, index1);
				int end = Math.max(index0, index1);
				for (int i = start; i <= end; i++)
				{
					if (shouldAdd)
					{
						super.addSelectionInterval(i, i);
						filter.getEnabledEvents().add((String)listModel.getElementAt(i));
						reloadDetailedLogs();
					}
					else
					{
						super.removeSelectionInterval(i, i);
						filter.getEnabledEvents().remove(listModel.getElementAt(i));
						reloadDetailedLogs();
					}
				}
			}
		});
		JScrollPane eventListScrollPane = new JScrollPane(eventList);
		eventListScrollPane.setPreferredSize(new Dimension(0, 100));
		filterContainer.add(eventListScrollPane, c);

		JPanel buttonContainer = new JPanel(new GridBagLayout());
		buttonContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
		GridBagConstraints buttonConstraints = new GridBagConstraints();
		buttonConstraints.fill = GridBagConstraints.HORIZONTAL;
		buttonConstraints.weightx = 1;
		buttonConstraints.gridx = 0;
		buttonConstraints.gridy = 0;
		buttonConstraints.anchor = GridBagConstraints.SOUTH;
		buttonConstraints.insets = new Insets(8, 0, 0, 0);
		buttonConstraints.gridwidth = 1;

		JButton filterButton = new JButton("Filter");
		filterButton.setFocusable(false);
		filterButton.addActionListener(e ->
				filterContainer.setVisible(!filterContainer.isVisible()));
		buttonContainer.add(filterButton, buttonConstraints);
		buttonConstraints.gridx++;

		buttonConstraints.insets = new Insets(0, 8, 0, 0);
		JButton resetButton = new JButton("Reset session");
		resetButton.setFocusable(false);
		resetButton.addActionListener(x ->
		{
			npcListModel.clear();
			filter.getEnabledEvents().clear();
			logData.getSessionLogs().clear();
			//plugin.clearSessionDrops();
			this.reloadDetailedLogs();
		});
		buttonContainer.add(resetButton, buttonConstraints);

		JPanel configPanel = new JPanel(new GridBagLayout());
		configPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		GridBagConstraints configConstraints = new GridBagConstraints();
		configConstraints.fill = GridBagConstraints.HORIZONTAL;
		configConstraints.weightx = 1;
		configConstraints.gridx = 0;
		configConstraints.gridy = 0;
		configConstraints.insets = new Insets(0, 0, 0, 0);
		configPanel.add(filterContainer, configConstraints);
		configConstraints.gridy++;
		configPanel.add(buttonContainer, configConstraints);

		typeComboBox.setSelectedIndex(2);
		areaComboBox.setSelectedIndex(2);

		this.add(configPanel, BorderLayout.SOUTH);
	}

	public void onLogShouldUpdate()
	{
		reloadDetailedLogs();
	}

	private void reloadDetailedLogs()
	{
		Map<Integer, Integer> drops = new HashMap<>();
		Map<String, EventOccurrenceCollection> events = new TreeMap<>();
		logData.getSessionLogs().forEach(x ->
		{
			String eventName = this.filter.getEventName(x);

			EventOccurrenceCollection event = events.get(eventName);
			if (event == null)
			{
				event = new EventOccurrenceCollection(eventName);
				events.put(eventName, event);
			}

			if (this.filter.getEnabledEvents().size() > 0 &&
					!this.filter.getEnabledEvents().contains(eventName))
			{
				return;
			}

			event.addOccurrence(x.getInstant());

			Map<Integer, Integer> itemQuantityMap = filter.getFilteredItems(x);
			if (itemQuantityMap != null)
			{
				for (Map.Entry<Integer, Integer> entry : itemQuantityMap.entrySet())
				{
					int count = drops.getOrDefault(entry.getKey(), 0);
					drops.put(entry.getKey(), count + entry.getValue());
					// TODO: Pull value from itemManager
					event.setValue(event.getValue() + entry.getValue());
				}
			}
		});

		Iterator<String> it = events.keySet().iterator();
		for (int i = 0; i < events.size(); i++)
		{
			String eventName = it.next();
			if (npcListModel.size() <= i || !eventName.equals(npcListModel.get(i)))
			{
				npcListModel.add(i, eventName);
				npcList.removeSelectionInterval(i, i);
			}
		}

		List<LoggedItem> itemList = drops.entrySet().stream()
				.collect(Collectors.groupingBy(x ->
				{
					ItemComposition unnotedItem = parent.getItemManager().getUnnotedItemComposition(x.getKey());
					return unnotedItem.getId();
				}, Collectors.summingInt(Map.Entry::getValue)))
				.entrySet().stream().map(x ->
				{
					ItemComposition item = parent.getItemManager().getItemComposition(x.getKey());
					// TODO: Pull value from itemManager
					return new LoggedItem(x.getKey(), x.getValue(), item, 1);
				})
				.sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
				.collect(Collectors.toList());
		List<EventOccurrenceCollection> eventList = events.entrySet().stream()
				.filter(x -> x.getValue().getCount() > 0)
				.sorted((a, b) -> (int)(b.getValue().getValue() - a.getValue().getValue()))
				.map(Map.Entry::getValue).collect(Collectors.toList());

		SwingUtilities.invokeLater(() ->
		{
			for (LoggedItem item : itemList)
			{
				item.setImage(parent.getItemManager().getImage(item.getItemId(),
						item.getQuantity(), item.getQuantity() != 1 || item.getComposition().isStackable()));
			}

			SwingUtilities.invokeLater(() ->
			{
				// Useful code for testing the panel with many items, uncomment if you want to test it
				/*List<LoggedItem> l = new ArrayList<>();
				for (int i = 0; i < 100; i++)
				{
					int itemId = 995;
					LoggedItem item = new LoggedItem(itemId, 10000, parent.getItemManager().getItemComposition(itemId), 1);
					item.setImage(parent.getItemManager().getImage(itemId, 10000, true));
					l.add(item);
				}
				updateLogIconTable(sessionDropsPanel, eventList, l);*/

				updateLogIconTable(sessionDropsPanel, eventList, itemList);
				this.parent.repaint();
			});
		});
	}









	private String formatPerHour(long occurences, long durationInMilliseconds)
	{
		double occurencesPerHour = (double)occurences * MILLISECONDS_PER_HOUR / durationInMilliseconds;
		return (occurencesPerHour < 100 ?
				// Use 1 decimal point when there's a small amonut of occurences
				SMALL_NUMBER_FORMATTER.format(occurencesPerHour) :
				StackFormatter.quantityToStackSize((long)occurencesPerHour));
	}

	private String getKillsPerHour(EventOccurrenceCollection event)
	{
		Instant firstInstant = event.getFirstOccurrence();
		Instant lastInstant = event.getLastOccurrence();
		long durationInMilliseconds = Duration.between(firstInstant, lastInstant).toMillis();
		if (durationInMilliseconds <= 0)
		{
			return null;
		}

		// Don't count the first kill as our time calculations only starts
		// after the first kill has already been completed
		return formatPerHour(event.getCount() - 1, durationInMilliseconds);
	}

	void updateLogIconTable(JPanel container, List<EventOccurrenceCollection> events, List<LoggedItem> drops)
	{
		container.removeAll();

		GridBagConstraints c = new GridBagConstraints();
		c.weightx = 1;
		c.gridx = 0;
		c.gridy = 0;
		c.anchor = GridBagConstraints.NORTH;
		c.insets = new Insets(8, 0, 0, 0);

		if (events == null || events.size() == 0 || drops == null || drops.size() == 0)
		{
			c.insets = new Insets(16, 0, 16, 0);
			String namesStr = "You have not obtained any loot yet.";
			namesStr += "<br/>This panel will update automatically";
			namesStr += "<br/>as soon as there is loot to display.";
			JLabel infoLabel = new JLabel("<html>" + namesStr + "</html>");
			container.add(infoLabel, c);
			c.gridy++;
			return;
		}

		if (events.size() < 5)
		{
			// Add up to 4 events if there aren't many of them
			for (EventOccurrenceCollection event : events)
			{
				JLabel eventLabel = new JLabel(
						StackFormatter.formatNumber(event.getCount()) +
								" x " + event.getEventName());
				container.add(eventLabel, c);
				c.gridy++;
				c.insets = new Insets(0, 0, 0, 0);

				String killsPerHour = getKillsPerHour(event);
				if (killsPerHour != null)
				{
					eventLabel.setToolTipText(killsPerHour + " per hour");
				}
			}
		}
		else
		{
			// Show the 3 most valuable events and mark the rest as "other"
			for (int i = 0; i < 3; i++)
			{
				JLabel eventLabel = new JLabel(
						StackFormatter.formatNumber(events.get(i).getCount()) +
								" x " + events.get(i).getEventName());
				container.add(eventLabel, c);
				c.gridy++;
				c.insets = new Insets(0, 0, 0, 0);

				String killsPerHour = getKillsPerHour(events.get(i));
				if (killsPerHour != null)
				{
					eventLabel.setToolTipText(killsPerHour + " per hour");
				}
			}
			int countRest = 0;
			for (int i = 3; i < events.size(); i++)
			{
				countRest += events.get(i).getCount();
			}
			JLabel otherEventLabel = new JLabel(
					StackFormatter.formatNumber(countRest) + " x Other");
			container.add(otherEventLabel, c);
			c.gridy++;
		}

		c.insets = new Insets(8, 0, 0, 0);

		JPanel iconPanel = new JPanel(new GridLayout(0, 5, 4, 4));
		iconPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		long totalValue = 0;
		for (LoggedItem loggedItem : drops)
		{
			BufferedImage itemImage = loggedItem.getImage();
			JLabel imageLabel = new JLabel(new ImageIcon(itemImage));
			String tooltip = "<html>" +
					StackFormatter.formatNumber(loggedItem.getQuantity()) +
					" x " + loggedItem.getComposition().getName();
			if (loggedItem.getPrice() != null)
			{
				if (loggedItem.getItemId() != ItemID.COINS_995)
				{
					tooltip += "<br/>GE: " + StackFormatter.quantityToStackSize(
							loggedItem.getPrice() * loggedItem.getQuantity()) + " gp";
					if (loggedItem.getQuantity() > 1)
					{
						tooltip += " (" + StackFormatter.quantityToStackSize(
								loggedItem.getPrice()) + " ea)";
					}
				}
				totalValue += loggedItem.getPrice() * loggedItem.getQuantity();
			}
			tooltip += "</html>";
			imageLabel.setToolTipText(tooltip);
			iconPanel.add(imageLabel);
		}
		container.add(iconPanel, c);
		c.gridy++;

		JLabel totalValueLabel = new JLabel("GE Value: " +
				StackFormatter.quantityToStackSize(totalValue));
		container.add(totalValueLabel, c);
		c.gridy++;
		c.insets = new Insets(0, 0, 0, 0);

		// We only count time after the first kill has already been done,
		// so the first kill should not be part of the calculation.
		int timedKills = events.stream().mapToInt(EventOccurrenceCollection::getCount).sum() - 1;
		long timedValue = Math.round((double)totalValue * timedKills / (timedKills + 1));

		Instant firstKill = events.stream()
				.min(Comparator.comparing(EventOccurrenceCollection::getFirstOccurrence))
				.map(EventOccurrenceCollection::getFirstOccurrence).orElse(null);
		Instant lastKill = events.stream()
				.max(Comparator.comparing(EventOccurrenceCollection::getLastOccurrence))
				.map(EventOccurrenceCollection::getLastOccurrence).orElse(null);
		long durationInMilliseconds = Duration.between(firstKill, lastKill).toMillis();
		if (durationInMilliseconds > 0)
		{
			String killsPerHour = formatPerHour(timedKills, durationInMilliseconds);
			JLabel killsPerHourLabel = new JLabel("Kills p/h: " + killsPerHour);
			container.add(killsPerHourLabel, c);
			c.gridy++;

			String profitPerHour = formatPerHour(timedValue, durationInMilliseconds);
			JLabel profitPerHourLabel = new JLabel("Profit p/h: " + profitPerHour + " gp");
			container.add(profitPerHourLabel, c);
			c.gridy++;
		}
	}
}
