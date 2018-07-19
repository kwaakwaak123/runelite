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
package net.runelite.client.plugins.droplogger.ui;

import net.runelite.client.game.AsyncBufferedImage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.droplogger.data.Boss;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.RuneliteList;
import net.runelite.client.ui.components.RuneliteListItemRenderer;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static net.runelite.client.plugins.droplogger.ui.Constants.BACKGROUND_COLOR;
import static net.runelite.client.plugins.droplogger.ui.Constants.BUTTON_COLOR;
import static net.runelite.client.plugins.droplogger.ui.Constants.BUTTON_HOVER_COLOR;
import static net.runelite.client.plugins.droplogger.ui.Constants.CONTENT_BORDER;
import static net.runelite.client.plugins.droplogger.ui.Constants.TOP_BORDER;

public class LandingPanel extends JPanel
{
	private final LoggerPanel parent;
	private final ItemManager itemManager;
	private final RuneliteList eventList;

	LandingPanel(LoggerPanel parent, ItemManager itemManager)
	{
		this.parent = parent;
		this.itemManager = itemManager;

		this.setBorder(CONTENT_BORDER);
		this.setLayout(new GridBagLayout());
		this.setBackground(BACKGROUND_COLOR);

		// Content Element
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.NORTHWEST;
		c.weightx = 1;
		c.weighty = 0;
		c.gridx = 0;
		c.gridy = 0;

		// All NPCs killed this Session
		this.eventList = createSessionEventList(new ArrayList<String>());
		this.add(eventList, c);
		c.gridy++;

		// Add the Boss selection elements by category
		Set<String> categories = Boss.categories;
		for (String categoryName : categories)
		{
			// Category Name
			JLabel name = new JLabel(categoryName);
			name.setBorder(TOP_BORDER);
			name.setForeground(Color.WHITE);
			name.setVerticalAlignment(SwingConstants.CENTER);

			MaterialTabGroup icons = createTabCategory(categoryName);

			this.add(name, c);
			c.gridy++;
			this.add(icons, c);
			c.gridy++;
		}
	}

	private RuneliteList createSessionEventList(List<String> options)
	{
		// Create the list of NPCs for this session
		RuneliteList eventList = new RuneliteList();
		eventList.setCellRenderer(new RuneliteListItemRenderer());
		eventList.setBackground(ColorScheme.DARK_GRAY_COLOR);
		DefaultListModel listModel = new DefaultListModel<>();
		eventList.setModel(listModel);

		int index = 0;
		for (String s : options)
		{
			listModel.add(index, s);
			index++;
		}

		return eventList;
	}

	// Creates icons used for tab selection for a specific category
	private MaterialTabGroup createTabCategory(String categoryName)
	{
		MaterialTabGroup thisTabGroup = new MaterialTabGroup();
		thisTabGroup.setLayout(new GridLayout(0, 4, 7, 7));
		thisTabGroup.setBorder(TOP_BORDER);

		ArrayList<Boss> categoryBosses = Boss.getByCategoryName(categoryName);
		for (Boss boss : categoryBosses)
		{
			// Create tab (with hover effects/text)
			MaterialTab materialTab = new MaterialTab("", thisTabGroup, null);
			materialTab.setName(boss.getName());
			materialTab.setToolTipText(boss.getBossName());
			materialTab.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					materialTab.setBackground(BUTTON_HOVER_COLOR);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					materialTab.setBackground(BUTTON_COLOR);
				}
			});

			// Attach Icon to the Tab
			AsyncBufferedImage image = itemManager.getImage(boss.getItemID());
			Runnable resize = () ->
			{
				materialTab.setIcon(new ImageIcon(image.getScaledInstance(35, 35, Image.SCALE_SMOOTH)));
				materialTab.setOpaque(true);
				materialTab.setBackground(BUTTON_COLOR);
				materialTab.setHorizontalAlignment(SwingConstants.CENTER);
				materialTab.setVerticalAlignment(SwingConstants.CENTER);
				materialTab.setPreferredSize(new Dimension(35, 35));
			};
			image.onChanged(resize);
			resize.run();

			materialTab.setOnSelectEvent(() ->
			{
				parent.showTabDisplay(boss);
				materialTab.unselect();
				materialTab.setBackground(BACKGROUND_COLOR);
				return true;
			});

			thisTabGroup.addTab(materialTab);
		}

		return thisTabGroup;
	}
}
