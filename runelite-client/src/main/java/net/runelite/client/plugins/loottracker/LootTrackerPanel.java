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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.loottracker.data.LootRecord;
import net.runelite.client.plugins.loottracker.ui.LootRecordPanel;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.SwingUtil;

@Slf4j
public class LootTrackerPanel extends PluginPanel
{
	private static final ImageIcon RESET_ICON;
	private static final ImageIcon SETTINGS_ICON;
	private static final ImageIcon RESET_CLICK_ICON;
	private static final ImageIcon SETTINGS_CLICK_ICON;

	private final GridBagConstraints constraints = new GridBagConstraints();

	private final JPanel logsContainer = new JPanel();
	private final List<LootRecord> records = new ArrayList<>();

	@Inject
	private ItemManager itemManager;

	private LootTrackerConfig config;

	static
	{
		try
		{
			synchronized (ImageIO.class)
			{
				BufferedImage resetIcon = ImageIO.read(LootTrackerPanel.class.getResourceAsStream("reset.png"));
				RESET_ICON = new ImageIcon(resetIcon);
				RESET_CLICK_ICON = new ImageIcon(SwingUtil.grayscaleOffset(resetIcon, -100));

				BufferedImage settingsIcon = ImageIO.read(LootTrackerPanel.class.getResourceAsStream("settings.png"));
				SETTINGS_ICON = new ImageIcon(settingsIcon);
				SETTINGS_CLICK_ICON = new ImageIcon(SwingUtil.grayscaleOffset(settingsIcon, -100));
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	void init(LootTrackerConfig config)
	{
		this.config = config;

		getScrollPane().setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 11));

		JPanel headerPanel = new JPanel(new BorderLayout());
		headerPanel.setBorder(new EmptyBorder(1, 0, 10, 0));

		JLabel pluginTitle = new JLabel("Loot Tracker");
		pluginTitle.setForeground(Color.WHITE);

		JPanel actionsContainer = new JPanel(new GridLayout(1, 2, 10, 0));

		final JLabel reset = new JLabel(RESET_ICON);
		reset.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent mouseEvent)
			{
				reset.setIcon(RESET_CLICK_ICON);
				reset();
			}

			@Override
			public void mouseReleased(MouseEvent mouseEvent)
			{
				reset.setIcon(RESET_ICON);
			}
		});

		final JLabel settings = new JLabel(SETTINGS_ICON);
		settings.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent mouseEvent)
			{
				settings.setIcon(SETTINGS_CLICK_ICON);
				//TODO open display configs here
			}

			@Override
			public void mouseReleased(MouseEvent mouseEvent)
			{
				settings.setIcon(SETTINGS_ICON);
			}
		});

		actionsContainer.add(reset);
		actionsContainer.add(settings);

		headerPanel.add(pluginTitle, BorderLayout.WEST);
		headerPanel.add(actionsContainer, BorderLayout.EAST);

		JPanel centerPanel = new JPanel(new BorderLayout());

		logsContainer.setLayout(new GridBagLayout());

		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1;
		constraints.gridx = 0;
		constraints.gridy = 0;

		centerPanel.add(logsContainer, BorderLayout.CENTER);

		add(headerPanel, BorderLayout.NORTH);
		add(centerPanel, BorderLayout.CENTER);
	}

	void addLootRecord(LootRecord entry)
	{
		assert SwingUtilities.isEventDispatchThread();

		this.records.add(entry);

		// Just add the Loot Record to the Panel if Ordering by Kill
		if (config.monsterDisplaySetting() == LootTrackerConfig.MonsterDisplays.KILL_ORDER)
		{
			logsContainer.add(new LootRecordPanel(entry, itemManager, config), constraints);
			constraints.gridy++;
		}
		else
		{
			reset();

			Collection<LootRecord> records = new ArrayList<>();
			if (config.monsterDisplaySetting() == LootTrackerConfig.MonsterDisplays.NAME)
			{
				records = LootRecord.consolidateLootRecordsByName(this.records);
			}
			else if (config.monsterDisplaySetting() == LootTrackerConfig.MonsterDisplays.ID)
			{
				records = LootRecord.consolidateLootRecordsById(this.records);
			}

			for (LootRecord r : records)
			{
				LootRecord rec = null;
				if (config.itemGroupingSetting() == LootTrackerConfig.Groupings.CONSOLIDATED)
				{
					rec = LootRecord.consildateDropEntries(r);
				}
				else if (config.itemGroupingSetting() == LootTrackerConfig.Groupings.INDIVIDUAL)
				{
					rec = r;
				}

				logsContainer.add(new LootRecordPanel(rec, itemManager, config), constraints);
				constraints.gridy++;
			}
		}

		logsContainer.revalidate();
		logsContainer.repaint();
	}

	public void reset()
	{
		logsContainer.removeAll();
		logsContainer.revalidate();
		logsContainer.repaint();
	}

}
