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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.droplogger.DropLoggerPlugin;
import net.runelite.client.plugins.droplogger.data.UniqueItem;
import net.runelite.client.plugins.droplogger.data.LootEntry;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.PluginErrorPanel;

import static net.runelite.client.plugins.droplogger.ui.Constants.BACKGROUND_COLOR;
import static net.runelite.client.plugins.droplogger.ui.Constants.BUTTON_HOVER_COLOR;
import static net.runelite.client.plugins.droplogger.ui.Constants.CONFIRM_DELETE_MESSAGE;
import static net.runelite.client.plugins.droplogger.ui.Constants.NO_DATA_MESSAGE;
import static net.runelite.client.plugins.droplogger.ui.Constants.PLUGIN_DESCRIPTION;
import static net.runelite.client.plugins.droplogger.ui.Constants.PLUGIN_ERROR_PANEL_BORDER;
import static net.runelite.client.plugins.droplogger.ui.Constants.PLUGIN_NAME;
import static net.runelite.client.plugins.droplogger.ui.Constants.SCROLL_BAR_SIZE;
import static net.runelite.client.plugins.droplogger.ui.Constants.TITLE_BORDER;
import static net.runelite.client.plugins.droplogger.ui.Constants.BACK;
import static net.runelite.client.plugins.droplogger.ui.Constants.DELETE;
import static net.runelite.client.plugins.droplogger.ui.Constants.REFRESH;


@Slf4j
public class LoggerPanel extends PluginPanel
{
	@Getter
	private final ItemManager itemManager;
	private final DropLoggerPlugin plugin;

	// Keep Reference to current LootPanel, needed to clear data
	private String currentTab = null;	// NPC Name

	private JPanel title;
	private JPanel footer;

	private LandingPanel landingPanel;
	private LootPanel lootPanel;

	@Inject
	public LoggerPanel(DropLoggerPlugin DropLoggerPlugin, ItemManager itemManager)
	{
		super(false);

		this.itemManager = itemManager;
		this.plugin = DropLoggerPlugin;

		this.setLayout(new BorderLayout());
		this.setBackground(BACKGROUND_COLOR);

		title = new JPanel();
		title.setBorder(TITLE_BORDER);
		title.setLayout(new BorderLayout());
		title.setBackground(BACKGROUND_COLOR);

		footer = new JPanel();
		footer.setBorder(TITLE_BORDER);
		footer.setLayout(new BorderLayout());
		footer.setBackground(BACKGROUND_COLOR);

		createLandingPanel();
	}

	// Landing page (Boss Selection Screen)
	private void createLandingPanel()
	{
		currentTab = null;

		// Clear the current content containers
		this.removeAll();
		title.removeAll();

		// Title Element
		PluginErrorPanel header = new PluginErrorPanel();
		header.setBorder(PLUGIN_ERROR_PANEL_BORDER);
		header.setContent(PLUGIN_NAME, PLUGIN_DESCRIPTION);
		title.add(header);

		// Content Element
		landingPanel = new LandingPanel(this, itemManager);

		// Re-add containers to the page.
		this.add(title, BorderLayout.NORTH);
		this.add(wrapContainer(landingPanel), BorderLayout.CENTER);
	}

	// Landing page (Boss Selection Screen)
	private void createTabPanel(String name)
	{
		currentTab = name;

		// Clear all Data
		this.removeAll();
		title.removeAll();

		// Tile Update
		title = createLootPanelTitle(title, name);


		// Content Update
		// Ensure stored data is up to date with file.
		plugin.loadTabData(name);
		// Grab Data to display from plugin
		ArrayList<LootEntry> data = plugin.getData(name);

		// Do we have any unique items to track?
		ArrayList<UniqueItem> list = UniqueItem.getByActivityName(name);
		// Sort the list by requested position inside UI (negative->positive)
		Map<Integer, ArrayList<UniqueItem>> sets = UniqueItem.createPositionSetMap(list);

		// Create & Return Loot Panel
		lootPanel = new LootPanel(data, sets, itemManager);

		this.add(title, BorderLayout.NORTH);
		this.add(wrapContainer(lootPanel), BorderLayout.CENTER);
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

	void showTabDisplay(String name)
	{
		createTabPanel(name);

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
		scroller.getVerticalScrollBar().setPreferredSize(SCROLL_BAR_SIZE);
		scroller.setBackground(BACKGROUND_COLOR);

		return scroller;
	}

	// Refresh the Loot Panel with updated data (requests the data from file)
	private void refreshLootPanel(LootPanel lootPanel, String name)
	{
		// Refresh data for necessary tab
		plugin.loadTabData(name);

		// Recreate the loot panel
		lootPanel.updateRecords(plugin.getData(name));

		// Ensure changes are applied
		this.revalidate();
		this.repaint();
	}

	private void clearData(String name)
	{
		if (lootPanel.getRecords().size() == 0)
		{
			JOptionPane.showMessageDialog(this.getRootPane(), NO_DATA_MESSAGE);
			return;
		}

		int delete = JOptionPane.showConfirmDialog(this.getRootPane(), CONFIRM_DELETE_MESSAGE, "Warning", JOptionPane.YES_NO_OPTION);
		if (delete == JOptionPane.YES_OPTION)
		{
			plugin.clearData(name);
			// Refresh current panel
			refreshLootPanel(lootPanel, name);
		}
	}
}