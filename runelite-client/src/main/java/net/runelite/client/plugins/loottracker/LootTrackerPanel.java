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
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collection;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.data.LootRecord;
import net.runelite.client.plugins.loottracker.ui.ItemGridPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.StackFormatter;
import net.runelite.client.util.SwingUtil;
import net.runelite.http.api.item.ItemPrice;

@Slf4j
public class LootTrackerPanel extends PluginPanel
{
	private static final ImageIcon RESET_ICON;
	private static final ImageIcon RESET_CLICK_ICON;

	private final GridBagConstraints constraints = new GridBagConstraints();

	private final JPanel logsContainer = new JPanel();

	@Inject
	private ItemManager itemManager;

	static
	{
		try
		{
			synchronized (ImageIO.class)
			{
				BufferedImage resetIcon = ImageIO.read(LootTrackerPanel.class.getResourceAsStream("reset.png"));
				RESET_ICON = new ImageIcon(resetIcon);
				RESET_CLICK_ICON = new ImageIcon(SwingUtil.grayscaleOffset(resetIcon, -100));
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	void init()
	{
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

		actionsContainer.add(reset);

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

	void addLog(LootRecord record)
	{
		assert SwingUtilities.isEventDispatchThread();

		// Kill Order or Consolidated?
		String npcName = record.getName();
		int npcLevel = record.getLevel();
		ItemStack[] items = record.getDrops().toArray(new ItemStack[0]);

		JPanel logContainer = new JPanel();
		logContainer.setLayout(new BorderLayout(0, 1));

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
			JLabel npcLevelLabel = new JLabel("(lvl-" + npcLevel + ")");
			npcLevelLabel.setFont(FontManager.getRunescapeSmallFont());
			npcLevelLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

			logTitle.add(npcLevelLabel, BorderLayout.CENTER);
		}

		long price = calculatePrice(items);
		if (price > 0)
		{
			JLabel priceLabel = new JLabel(StackFormatter.quantityToStackSize(price));
			priceLabel.setFont(FontManager.getRunescapeSmallFont());
			priceLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

			logTitle.add(priceLabel, BorderLayout.EAST);
		}

		// Change to true for testing large panels
		if (true)
		{
			Collection<ItemStack> drops = record.getDrops();
			for (int i = 550; i < 565; i++)
			{
				drops.add(new ItemStack(i, 100));
			}
			items = drops.toArray(new ItemStack[0]);
		}
		ItemGridPanel itemContainer = new ItemGridPanel(items, itemManager);

		logContainer.add(logTitle, BorderLayout.NORTH);
		logContainer.add(itemContainer, BorderLayout.CENTER);

		logsContainer.add(logContainer, constraints);
		constraints.gridy++;

		logsContainer.add(Box.createRigidArea(new Dimension(0, 10)), constraints);
		constraints.gridy++;
	}

	public void reset()
	{
		if (!confirmReset())
		{
			return;
		}

		logsContainer.removeAll();
		logsContainer.revalidate();
		logsContainer.repaint();
	}

	private boolean confirmReset()
	{
		int delete = JOptionPane.showConfirmDialog(this, "<html>Are you sure you want to clear all data for this panel?<br/>There is no way to undo this action.</html>", "Warning", JOptionPane.YES_NO_OPTION);
		return delete == JOptionPane.YES_OPTION;
	}

	private long calculatePrice(ItemStack[] itemStacks)
	{
		long total = 0;
		for (ItemStack itemStack : itemStacks)
		{
			ItemPrice itemPrice = itemManager.getItemPrice(itemStack.getId());
			if (itemPrice != null)
			{
				total += (long) itemPrice.getPrice() * itemStack.getQuantity();
			}
		}
		return total;
	}
}
