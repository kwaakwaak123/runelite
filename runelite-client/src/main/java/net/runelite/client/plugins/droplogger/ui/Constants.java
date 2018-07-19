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

import net.runelite.client.plugins.droplogger.DropLoggerPlugin;
import net.runelite.client.ui.ColorScheme;
import javax.imageio.ImageIO;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class Constants
{
	public final static String PLUGIN_NAME = "Drop Logger Plugin";
	public final static String PLUGIN_DESCRIPTION = "Please select the NPC or Activity you would like to view recorded loot for";

	public final static String KILL_COUNT = "Kill Count:";
	public final static String RECORDED_COUNT = "Recorded Count:";
	public final static String TOTAL_VALUE = "Total Value:";

	public final static String NO_DATA_MESSAGE = "No data to remove!";
	public final static String CONFIRM_DELETE_MESSAGE = "<html>Are you sure you want to clear all recorded data for this tab?<br/>WARNING: There is no way to undo this action.</html>";

	// Color Schemes
	public final static Color BACKGROUND_COLOR = ColorScheme.DARK_GRAY_COLOR;
	public final static Color BUTTON_COLOR = ColorScheme.DARKER_GRAY_COLOR;
	public final static Color BUTTON_HOVER_COLOR = ColorScheme.DARKER_GRAY_HOVER_COLOR;

	// Borders
	public final static Border CONTENT_BORDER = new EmptyBorder(0, 10, 0, 10);
	public final static Border TITLE_BORDER = new CompoundBorder(
			new EmptyBorder(10, 8, 8, 8),
			new MatteBorder(0, 0, 1, 0, Color.GRAY)
	);
	public final static Border TOP_BORDER = new EmptyBorder(10, 0, 0, 0);
	public final static Border PLUGIN_ERROR_PANEL_BORDER = new EmptyBorder(10, 10, 10, 10);
	public final static Border RECORD_BORDER = new EmptyBorder(3, 0, 3, 0);

	// Sizes
	public final static Dimension SCROLL_BAR_SIZE = new Dimension(8, 0);

	// Alpha Values
	public final static float ALPHA_MISSING = 0.35f;
	public final static float ALPHA_HAS = 1.0f;

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
}
