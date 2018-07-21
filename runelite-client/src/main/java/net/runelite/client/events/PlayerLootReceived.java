package net.runelite.client.events;

import java.util.Collection;
import lombok.Value;
import net.runelite.api.Player;
import net.runelite.client.game.ItemStack;

@Value
public class PlayerLootReceived
{
	private final Player player;
	private final Collection<ItemStack> items;
}