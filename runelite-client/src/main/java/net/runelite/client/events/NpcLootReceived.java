package net.runelite.client.events;

import java.util.Collection;
import lombok.Value;
import net.runelite.api.NPC;
import net.runelite.client.game.ItemStack;

@Value
public class NpcLootReceived
{
	private final NPC npc;
	private final Collection<ItemStack> items;
}
