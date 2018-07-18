/*
 * Copyright (c) 2018, TheStonedTurtle <www.github.com/TheStonedTurtle>
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.AsyncBufferedImage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.droplogger.DropLoggerPlugin;
import net.runelite.client.plugins.droplogger.data.Boss;
import net.runelite.client.plugins.droplogger.data.UniqueItem;
import net.runelite.client.plugins.droplogger.data.LootEntry;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

import static net.runelite.client.plugins.droplogger.ui.Constants.BACKGROUND_COLOR;
import static net.runelite.client.plugins.droplogger.ui.Constants.BUTTON_COLOR;
import static net.runelite.client.plugins.droplogger.ui.Constants.BUTTON_HOVER_COLOR;


@Slf4j
public class LoggerPanel extends PluginPanel
{
	private final ItemManager itemManager;
	private final DropLoggerPlugin plugin;

	// Displayed on Recorded Loot Page (updated for each tab)
	private LootPanel lootPanel;
	private Boss currentTab = null;

	private JPanel content;
	private JPanel title;

	// Icons for navigation
	public static final BufferedImage BACK;
	public static final BufferedImage REFRESH;
	public static final BufferedImage DELETE;
	static
	{
		BufferedImage back;
		BufferedImage refresh;
		BufferedImage delete;

		try
		{
			synchronized (ImageIO.class)
			{
				back = ImageIO.read(DropLoggerPlugin.class.getResourceAsStream("back-arrow-white.png"));
				refresh = ImageIO.read(DropLoggerPlugin.class.getResourceAsStream("refresh-white.png"));
				delete = ImageIO.read(DropLoggerPlugin.class.getResourceAsStream("delete-white.png"));
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}

		BACK = back;
		REFRESH = refresh;
		DELETE = delete;
	}

	@Inject
	public LoggerPanel(DropLoggerPlugin DropLoggerPlugin, ItemManager itemManager)
	{
		super(false);

		this.itemManager = itemManager;
		this.plugin = DropLoggerPlugin;

		this.setLayout(new BorderLayout());
		this.setBackground(BACKGROUND_COLOR);

		content = new JPanel();
		content.setBorder(new EmptyBorder(0, 8, 0, 0));
		content.setLayout(new GridBagLayout());
		content.setBackground(BACKGROUND_COLOR);

		title = new JPanel();
		title.setBorder(new CompoundBorder(
				new EmptyBorder(10, 8, 8, 8),
				new MatteBorder(0, 0, 1, 0, Color.GRAY)
		));
		title.setLayout(new BorderLayout());
		title.setBackground(BACKGROUND_COLOR);

		createLandingPanel();
	}

	// Landing page (Boss Selection Screen)
	private void createLandingPanel()
	{
		currentTab = null;

		// Clear the current content containers
		this.removeAll();
		title.removeAll();
		content.removeAll();

		// Title Element
		PluginErrorPanel header = new PluginErrorPanel();
		header.setBorder(new EmptyBorder(10, 25, 10, 25));
		header.setContent("Boss Logger Plugin", "Select a boss icon to view the recorded loot for it");
		title.add(header);

		// Content Element
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.NORTHWEST;
		c.weightx = 1;
		c.weighty = 0;
		c.gridx = 0;
		c.gridy = 0;

		// Add the Boss selection elements by category
		Set<String> categories = Boss.categories;
		for (String categoryName : categories)
		{
			// Category Name
			JLabel name = new JLabel(categoryName);
			name.setBorder(new EmptyBorder(8, 0, 0, 0));
			name.setForeground(Color.WHITE);
			name.setVerticalAlignment(SwingConstants.CENTER);

			MaterialTabGroup icons = createTabCategory(categoryName);

			content.add(name, c);
			c.gridy++;
			content.add(icons, c);
			c.gridy++;
		}

		// Re-add containers to the page.
		this.add(title, BorderLayout.NORTH);
		this.add(wrapContainer(content), BorderLayout.CENTER);
	}

	// Creates icons used for tab selection for a specific category
	private MaterialTabGroup createTabCategory(String categoryName)
	{
		MaterialTabGroup thisTabGroup = new MaterialTabGroup();
		thisTabGroup.setLayout(new GridLayout(0, 4, 7, 7));
		thisTabGroup.setBorder(new EmptyBorder(4, 0, 0, 0));

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
				this.showTabDisplay(boss);
				materialTab.unselect();
				materialTab.setBackground(BACKGROUND_COLOR);
				return true;
			});

			thisTabGroup.addTab(materialTab);
		}

		return thisTabGroup;
	}

	// Landing page (Boss Selection Screen)
	private void createTabPanel(Boss boss)
	{
		currentTab = boss;

		// Clear all Data
		this.removeAll();
		title.removeAll();
		content.removeAll();

		// Tile Update
		title = createLootPanelTitle(title, boss.getBossName());


		// Content Update
		// Ensure stored data is up to date with file.
		plugin.loadTabData(boss);
		// Grab Data to display from plugin
		ArrayList<LootEntry> data = plugin.getData(boss);

		// Do we have any unique items to track?
		ArrayList<UniqueItem> list = UniqueItem.getByActivityName(boss.getName());
		// Sort the list by requested position inside UI (negative->positive)
		Map<Integer, ArrayList<UniqueItem>> sets = UniqueItem.createPositionSetMap(list);

		// Create & Return Loot Panel
		LootPanel panel = new LootPanel(data, sets, itemManager);

		this.add(title, BorderLayout.NORTH);
		this.add(wrapContainer(panel), BorderLayout.CENTER);
	}

	// Icon Label with Hover effects
	private JLabel createIconLabel(BufferedImage icon)
	{
		JLabel label = new JLabel();
		label.setIcon(new ImageIcon(icon));
		label.setOpaque(true);
		label.setBackground(BACKGROUND_COLOR);

		label.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				label.setBackground(BUTTON_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				label.setBackground(BACKGROUND_COLOR);
			}
		});

		return label;
	}

	// Creates the title panel for the recorded loot tab inside the requested container
	private JPanel createLootPanelTitle(JPanel container, String name)
	{
		// Container for Back button and Name
		JPanel first = new JPanel();
		first.setBackground(BACKGROUND_COLOR);

		// Back Button
		JLabel back = createIconLabel(BACK);
		back.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				showLandingPage();
			}
		});

		// Name
		JLabel text = new JLabel(name);
		text.setForeground(Color.WHITE);

		first.add(back);
		first.add(text);

		// Container for Action Buttons
		JPanel second = new JPanel();
		second.setBackground(BACKGROUND_COLOR);

		// Refresh Data button
		JLabel refresh = createIconLabel(REFRESH);
		refresh.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				refreshLootPanel(lootPanel, currentTab);
			}
		});

		// Clear data button
		JLabel clear = createIconLabel(DELETE);
		clear.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				clearData(currentTab);
			}
		});

		second.add(refresh);
		second.add(clear);

		container.add(first, BorderLayout.WEST);
		container.add(second, BorderLayout.EAST);

		return container;
	}

	private void showTabDisplay(Boss boss)
	{
		createTabPanel(boss);

		this.revalidate();
		this.repaint();
	}

	private void showLandingPage()
	{
		createLandingPanel();

		this.revalidate();
		this.repaint();
	}

	// Wrap the panel inside a scroll pane
	private JScrollPane wrapContainer(JPanel container)
	{
		JPanel wrapped = new JPanel(new BorderLayout());
		wrapped.add(container, BorderLayout.NORTH);
		wrapped.setBackground(BACKGROUND_COLOR);

		JScrollPane scroller = new JScrollPane(wrapped);
		scroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroller.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
		scroller.setBackground(BACKGROUND_COLOR);

		return scroller;
	}

	// Refresh the Loot Panel with updated data (requests the data from file)
	private void refreshLootPanel(LootPanel lootPanel, Boss boss)
	{
		// Refresh data for necessary tab
		plugin.loadTabData(boss);

		// Recreate the loot panel
		lootPanel.updateRecords(plugin.getData(boss));

		// Ensure changes are applied
		this.revalidate();
		this.repaint();
	}

	private void clearData(Boss boss)
	{
		if (lootPanel.getRecords().size() == 0)
		{
			JOptionPane.showMessageDialog(this.getRootPane(), "No data to remove!");
			return;
		}

		int delete = JOptionPane.showConfirmDialog(this.getRootPane(), "<html>Are you sure you want to clear all data for this tab?<br/>There is no way to undo this action.</html>", "Warning", JOptionPane.YES_NO_OPTION);
		if (delete == JOptionPane.YES_OPTION)
		{
			plugin.clearData(boss);
			// Refresh current panel
			refreshLootPanel(lootPanel, boss);
		}
	}
}