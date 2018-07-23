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

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import lombok.Getter;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.components.shadowlabel.JShadowedLabel;
import net.runelite.client.util.StackFormatter;
import net.runelite.http.api.item.ItemPrice;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;

@Getter
public class ItemStackPanel extends JPanel
{
	private ItemStack item;
	private ItemComposition comp;
	private int value;

	public ItemStackPanel(ItemStack item, ItemManager itemManager)
	{
		this.item = item;
		this.comp = itemManager.getItemComposition(item.getId());
		ItemPrice p = itemManager.getItemPrice(item.getId());
		int v = p != null ? p.getPrice() : 0;
		this.value = v >= 0 ? v : 0;
		if (item.getId() == ItemID.COINS_995)
		{
			this.value = 1;
		}

		this.setLayout(new GridBagLayout());
		this.setBorder(new EmptyBorder(3, 0, 3, 0));
		this.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Item Image Icon
		JLabel icon = new JLabel();
		itemManager.getImage(item.getId(), item.getQuantity(), item.getQuantity() > 1 || comp.isStackable()).addTo(icon);
		icon.setHorizontalAlignment(JLabel.CENTER);

		// Container for Info
		JPanel uiInfo = new JPanel(new GridLayout(2, 1));
		uiInfo.setBorder(new EmptyBorder(0, 5, 0, 0));
		uiInfo.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JShadowedLabel labelName = new JShadowedLabel(comp.getName());
		labelName.setForeground(Color.WHITE);
		colorLabel(labelName, this.value);
		labelName.setVerticalAlignment(SwingUtilities.BOTTOM);

		int total = value * item.getQuantity();
		JShadowedLabel labelValue = new JShadowedLabel(StackFormatter.quantityToStackSize(total) + " gp");
		labelValue.setFont(FontManager.getRunescapeSmallFont());
		colorLabel(labelValue, total);
		labelValue.setVerticalAlignment(SwingUtilities.TOP);

		uiInfo.add(labelName);
		uiInfo.add(labelValue);

		// Create and append elements to container panel
		JPanel panel = new JPanel();
		panel.setLayout(new BorderLayout());
		panel.setBorder(new EmptyBorder(0, 15, 0, 15));
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		panel.add(icon, BorderLayout.LINE_START);
		panel.add(uiInfo, BorderLayout.CENTER);

		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1;
		c.gridx = 0;
		c.gridy = 0;
		c.ipady = 20;

		this.add(panel, c);
	}

	// Color label to match RuneScape coloring
	private void colorLabel(JLabel label, long val)
	{
		Color labelColor = (val >= 10000000) ? Color.GREEN : (val >= 100000) ? Color.WHITE : Color.YELLOW;
		label.setForeground(labelColor);
	}
}