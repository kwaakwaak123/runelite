/*
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
package net.runelite.client.plugins.loottracker.ui;

import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootTrackerConfig;
import net.runelite.client.plugins.loottracker.data.LootRecord;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;

public class LootRecordPanel extends JPanel
{
	public LootRecordPanel(LootRecord record, ItemManager itemManager, LootTrackerConfig config)
	{
		String npcName = record.getName();
		int npcLevel = record.getLevel();
		ItemStack[] items = record.getDrops().toArray(new ItemStack[0]);

		this.setLayout(new BorderLayout(0, 1));

		JPanel logTitle = new JPanel(new BorderLayout(5, 0));
		logTitle.setBorder(new EmptyBorder(7, 7, 7, 7));
		logTitle.setBackground(ColorScheme.DARKER_GRAY_COLOR.darker());

		JLabel npcNameLabel = new JLabel(npcName);
		npcNameLabel.setFont(FontManager.getRunescapeSmallFont());
		npcNameLabel.setForeground(Color.WHITE);

		logTitle.add(npcNameLabel, BorderLayout.WEST);

		// For events outside of npc drops (barrows, raids, etc) the level should be -1
		if (npcLevel > -1)
		{
			JLabel npcLevelLabel = new JLabel("(lvl. " + npcLevel + ")");
			npcLevelLabel.setFont(FontManager.getRunescapeSmallFont());
			npcLevelLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

			logTitle.add(npcLevelLabel, BorderLayout.CENTER);
		}

		JPanel content;
		if (config.itemDisplaySetting() == LootTrackerConfig.ItemDisplays.GRID)
		{
			content = new ItemGridPanel(items, itemManager);
		}
		else
		{
			content = new JPanel(new GridLayout(0, 1, 0, 10));
			for (ItemStack s : items)
			{
				content.add(new ItemStackPanel(s, itemManager));
			}
		}

		this.add(logTitle, BorderLayout.NORTH);
		this.add(content, BorderLayout.CENTER);
	}
}
