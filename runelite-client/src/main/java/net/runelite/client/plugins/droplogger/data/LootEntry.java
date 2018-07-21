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
package net.runelite.client.plugins.droplogger.data;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.runelite.api.Item;

public class LootEntry
{
	@Getter
	private final Integer killCount;
	@Getter
	private final int npcID;
	@Getter
	private final String npcName;
	@Getter
	final List<ItemStack> drops;

	// Full Kill with Kill Count
	public LootEntry(int npcId, String npcName, int kc, List drops)
	{
		this.npcID = npcId;
		this.npcName = npcName;
		this.killCount = kc;
		this.drops = listCheck(drops);
	}

	// Full Kill without Kill Count
	public LootEntry(int npcId, String npcName, List drops)
	{
		this.npcID = npcId;
		this.npcName = npcName;
		this.killCount = -1;
		this.drops = listCheck(drops);
	}

	// Npc ID Only Kill with Kill Count
	public LootEntry(int npcId, int kc, List drops)
	{
		this.npcID = npcId;
		this.npcName = null;
		this.killCount = kc;
		this.drops = listCheck(drops);
	}

	// Npc ID Only Kill without Kill Count
	public LootEntry(int npcId, List drops)
	{
		this.npcID = npcId;
		this.npcName = null;
		this.killCount = -1;
		this.drops = listCheck(drops);
	}

	// Name Only Kill with Kill Count
	public LootEntry(String npcName, int kc, List drops)
	{
		this.npcID = -1;
		this.npcName = npcName;
		this.killCount = kc;
		this.drops = listCheck(drops);
	}

	// Name Only Kill without Kill Count
	public LootEntry(String npcName, List drops)
	{
		this.npcID = -1;
		this.npcName = npcName;
		this.killCount = -1;
		this.drops = listCheck(drops);
	}

	// Allows for creating LootEntry's with either a List of DropEntry's or Item's
	private List<DropEntry> listCheck(List drops)
	{
		if (drops == null || drops.size() < 1)
		{
			return new ArrayList<>();
		}
		else
		{
			// If this is already a List of DropEntry just return the list
			Object i = drops.get(0);
			if (i instanceof DropEntry)
			{
				return drops;
			}
			else if (i instanceof Item)
			{
				List<DropEntry> result = new ArrayList<>();
				for (Object each : drops)
				{
					Item item = (Item) each;
					result.add(new DropEntry(item.getId(), item.getQuantity()));
				}

				return result;
			}
			else
			{
				// Trying to pass a list of some other Object type
				return new ArrayList<>();
			}
		}
	}

	/**
	 * Add the request DropEntry to this LootEntry
	 * @param drop DropEntry to add
	 */
	public void addDropEntry(DropEntry drop)
	{
		drops.add(drop);
	}

	/**
	 * Converts the Requested item into a DropEntry and then adds it to the LootEntry
	 * @param drop Item to add as DropEntry
	 */
	public void addDropItem(Item drop)
	{
		DropEntry d = new DropEntry(drop.getId(), drop.getQuantity());
		drops.add(d);
	}

	@Override
	public String toString()
	{
		StringBuilder m = new StringBuilder();
		m.append("LootEntry{npcID=")
				.append(npcID)
				.append(", npcName=")
				.append(npcName)
				.append(", killCount=")
				.append(killCount)
				.append(", drops=");

		if (drops != null)
		{
			m.append("[");
			boolean addComma = false;
			for (DropEntry d : drops)
			{
				if (addComma)
				{
					m.append(", ");
				}

				m.append(d.toString());
				addComma = true;
			}
			m.append("]");
		}
		else
		{
			m.append("null");
		}
		m.append("}");

		return m.toString();
	}
}