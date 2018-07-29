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

import net.runelite.api.ItemID;
import net.runelite.client.game.AsyncBufferedImage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.data.LootRecord;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.StackFormatter;
import net.runelite.http.api.item.ItemPrice;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.Arrays;

public class ItemGridPanel extends JPanel
{
	private static final int ITEMS_PER_ROW = 5;

	private final ItemManager itemManager;
	private LootRecord record;

	public ItemGridPanel(LootRecord record, ItemManager itemManager)
	{
		this.itemManager = itemManager;
		this.record = record;

		this.setLayout(new BorderLayout());

		createItemPanel();
	}

	public void addItems(ItemStack[] newItems)
	{
		this.record.getDrops().addAll(Arrays.asList(newItems));
		this.record = LootRecord.consildateDropEntries(this.record);

		this.removeAll();
		createItemPanel();

		this.repaint();
		this.revalidate();
	}

	private void createItemPanel()
	{
		double priceTotal = 0;

		ItemStack[] itemList = this.record.getDrops().toArray(new ItemStack[0]);
		int rowSize = ((itemList.length % ITEMS_PER_ROW == 0) ? 0 : 1) + itemList.length / ITEMS_PER_ROW;

		JPanel itemContainer = new JPanel();
		itemContainer.setLayout(new GridLayout(rowSize, ITEMS_PER_ROW, 1, 1));

		for (int i = 0; i < rowSize * ITEMS_PER_ROW; i++)
		{
			JPanel slotContainer = new JPanel();
			slotContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);

			if (i < itemList.length)
			{
				ItemStack item = itemList[i];

				String itemName = itemManager.getItemComposition(item.getId()).getName();
				ItemPrice p = itemManager.getItemPrice(item.getId());
				int price = (p == null ? 0 : p.getPrice());
				int realPrice = (item.getId() == ItemID.COINS_995 ? 1 : (price < 0 ? 0 : price));
				double total = (double) realPrice * item.getQuantity();
				priceTotal += total;

				AsyncBufferedImage icon = itemManager.getImage(item.getId(), item.getQuantity(), item.getQuantity() > 1);
				Runnable addImage = () ->
				{
					SwingUtilities.invokeLater(() ->
					{
						JLabel imageLabel = new JLabel(new ImageIcon(icon));
						imageLabel.setVerticalAlignment(SwingConstants.CENTER);
						imageLabel.setHorizontalAlignment(SwingConstants.CENTER);

						imageLabel.setToolTipText("<html>" + itemName + "<br/>"
								+ "Price: " + StackFormatter.formatNumber(realPrice) + "<br/>"
								+ "Total: " + StackFormatter.formatNumber(total) + "</html>");
						slotContainer.add(imageLabel);
						slotContainer.revalidate();
						slotContainer.repaint();
					});
				};
				icon.onChanged(addImage);
				addImage.run();
			}

			itemContainer.add(slotContainer);
		}

		JLabel priceLabel = new JLabel("Total Value: " + StackFormatter.formatNumber(priceTotal) + " gp");
		priceLabel.setHorizontalAlignment(SwingUtilities.CENTER);
		priceLabel.setBorder(new EmptyBorder(5, 0, 5, 0));
		priceLabel.setFont(FontManager.getRunescapeFont());

		this.add(priceLabel, BorderLayout.NORTH);
		this.add(itemContainer, BorderLayout.CENTER);
	}
}
