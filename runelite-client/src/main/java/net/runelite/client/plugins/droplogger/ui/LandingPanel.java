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

import net.runelite.client.game.ItemManager;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Set;
import java.util.TreeSet;

import static net.runelite.client.plugins.droplogger.ui.Constants.BACKGROUND_COLOR;
import static net.runelite.client.plugins.droplogger.ui.Constants.BUTTON_COLOR;
import static net.runelite.client.plugins.droplogger.ui.Constants.BUTTON_HOVER_COLOR;
import static net.runelite.client.plugins.droplogger.ui.Constants.CONTENT_BORDER;

public class LandingPanel extends JPanel
{
	private Set<String> sessionActors;
	private final LoggerPanel parent;
	private final ItemManager itemManager;
	private JPanel eventList;

	LandingPanel(Set<String> sessionActors, LoggerPanel parent, ItemManager itemManager)
	{
		this.sessionActors = sessionActors;
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
		this.eventList = createSessionEventList(sessionActors);
		this.add(eventList, c);
		c.gridy++;
	}

	private JPanel createSessionEventList(Set<String> options)
	{
		JPanel container = new JPanel();
		container.setLayout(new GridBagLayout());

		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.NORTHWEST;
		c.weightx = 1;
		c.weighty = 0;
		c.gridx = 0;
		c.gridy = 0;


		for (String s : options)
		{
			JLabel l = new JLabel(s);
			l.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					l.setBackground(BUTTON_COLOR);
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					l.setBackground(BUTTON_HOVER_COLOR);
				}

				@Override
				public void mouseClicked(MouseEvent e)
				{
					parent.showTabDisplay(s);
				}
			});
			this.add(l, c);
			c.gridy++;
		}

		return container;
	}

	// Update UI to show new sessionActors
	void setSessionActors(TreeSet<String> sessionActors)
	{
		if (this.sessionActors.equals(sessionActors))
		{
			return;
		}

		this.sessionActors = sessionActors;

		SwingUtilities.invokeLater(() ->
		{
			this.eventList = createSessionEventList(sessionActors);

			this.eventList.repaint();
			this.eventList.revalidate();

			this.repaint();
			this.revalidate();
		});
	}
}
