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
package net.runelite.client.plugins.droplogger;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import java.awt.Color;

@ConfigGroup("droplogger")
public interface DropLoggerConfig extends Config
{
	@ConfigItem(
		position = 0,
		keyName = "recordPlayerKills",
		name = "Record Loot from Player Kills",
		description = "Configure whether Loot Players is recorded"
	)
	default boolean recordPlayerKills()
	{
		return true;
	}

	@ConfigItem(
		position = 1,
		keyName = "recordNpcKills",
		name = "Record Loot from NPC Kills",
		description = "Configure whether Loot NPCs is recorded"
	)
	default boolean recordNpcKills()
	{
		return true;
	}

	@ConfigItem(
		position = 94,
		keyName = "hideChambersOfXeric",
		name = "Hide Chambers of Xeric NPCs",
		description = "Don't show loot from NPCs inside Chambers of Xeric"
	)
	default boolean hideChambersOfXeric()
	{
		return true;
	}

	@ConfigItem(
		position = 95,
		keyName = "hideBarbarianAssault",
		name = "Hide Barbarian Assault NPCs",
		description = "Don't show loot from NPCs inside Barbarian Assault"
	)
	default boolean hideBarbarianAssault()
	{
		return true;
	}

	@ConfigItem(
		position = 96,
		keyName = "showChatMessages",
		name = "In-game Chat Message Alerts",
		description = "In-Game Chat Messages when Loot Recorded"
	)
	default boolean showChatMessages()
	{
		return true;
	}

	@ConfigItem(
		position = 97,
		keyName = "chatMessageColor",
		name = "Chat Message Color",
		description = "Color of the Chat Message alerts"
	)
	default Color chatMessageColor()
	{
		return new Color(0, 75, 255);
	}

	@ConfigItem(
		position = 98,
		keyName = "showTrayAlerts",
		name = "Notification Tray Alerts",
		description = "Create Notification Tray alerts when Loot Recorded?"
	)
	default boolean showTrayAlerts()
	{
		return true;
	}

	@ConfigItem(
		position = 99,
		keyName = "showLootPanel",
		name = "Show Loot Panel",
		description = "Configures whether or not the Loot Panel is shown"
	)
	default boolean showLootTotals()
	{
		return true;
	}
}