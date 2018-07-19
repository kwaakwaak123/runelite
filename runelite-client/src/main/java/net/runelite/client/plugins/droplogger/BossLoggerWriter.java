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
package net.runelite.client.plugins.droplogger;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.plugins.droplogger.data.LootEntry;
import net.runelite.http.api.RuneLiteAPI;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import static net.runelite.client.RuneLite.LOOTS_DIR;

@Slf4j
class BossLoggerWriter
{
	private File playerFolder;

	private Map<String, String> filenameMap;

	private Client client;

	BossLoggerWriter(Client client, Map<String, String> filenameMap)
	{
		// Ensure Loot Directory has been created
		LOOTS_DIR.mkdir();

		this.client = client;
		this.filenameMap = filenameMap;

		updatePlayerFolder();
	}

	/**
	 * Ensure we are referencing the right folder for the current Player
	 * @return Folder Directory Changed?
	 */
	boolean updatePlayerFolder()
	{
		String old = "";
		if (playerFolder != null)
		{
			old = playerFolder.toString();
		}

		if (client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null)
		{
			playerFolder = new File(LOOTS_DIR, client.getLocalPlayer().getName());
			// Ensure player folder is made
			playerFolder.mkdir();
		}
		else
		{
			playerFolder = LOOTS_DIR;
		}

		// Did storage directory change?
		return !playerFolder.toString().equals(old);
	}

	/**
	 * Adds the LootEntry to the requested .log file
	 * @param name Npc/Event/Boss Name
	 * @param entry LootEntry to add
	 * @return Success Boolean
	 */
	boolean addLootEntry(String name, LootEntry entry)
	{
		// Convert entry to JSON
		String dataAsString = RuneLiteAPI.GSON.toJson(entry);

		String fileName = getFileNameFromMap(name);

		// Open File and append data
		File lootFile = new File(playerFolder, fileName);
		try
		{
			BufferedWriter file = new BufferedWriter(new FileWriter(String.valueOf(lootFile), true));
			file.append(dataAsString);
			file.newLine();
			file.close();
			return true;
		}
		catch (IOException ioe)
		{
			log.warn("Error writing loot data to file: {}", ioe.getMessage());
			return false;
		}
	}

	/**
	 * Rewrites the Entire .log file, useful for updating previous entries.
	 * @param name Npc/Event/Boss Name
	 * @param loots List of records to write
	 * @return Success Boolean
	 */
	boolean rewriteLootFile(String name, ArrayList<LootEntry> loots)
	{
		String fileName = getFileNameFromMap(name);

		// Rewrite the log file (to update the last loot entry)
		File lootFile = new File(playerFolder, fileName);
		try
		{
			BufferedWriter file = new BufferedWriter(new FileWriter(String.valueOf(lootFile), false));
			for ( LootEntry lootEntry : loots)
			{
				// Convert entry to JSON
				String dataAsString = RuneLiteAPI.GSON.toJson(lootEntry);
				file.append(dataAsString);
				file.newLine();
			}
			file.close();

			return true;
		}
		catch (IOException ioe)
		{
			log.warn("Error rewriting loot data to file: {}", ioe.getMessage());
			return false;
		}
	}

	/**
	 * Deletes the requested file.
	 * @param name Npc/Event/Boss Name
	 * @return Success Boolean
	 */
	synchronized boolean clearLootFile(String name)
	{
		String fileName = getFileNameFromMap(name);
		File lootFile = new File(playerFolder, fileName);

		if (lootFile.delete())
		{
			log.debug("Deleted loot file: {}", lootFile);
			return true;
		}
		else
		{
			log.debug("Couldn't delete file: {}", lootFile);
			return false;
		}
	}

	/**
	 * Load LootEntry's from the respective .log file.
	 * @param name Npc/Event/Boss Name
	 * @return ArrayList of LootEntry
	 */
	synchronized ArrayList<LootEntry> loadLootEntries(String name)
	{
		ArrayList<LootEntry> data = new ArrayList<>();

		String fileName = getFileNameFromMap(name);

		// Open File and read line by line
		File file = new File(playerFolder, fileName);
		try (BufferedReader br = new BufferedReader(new FileReader(file)))
		{
			String line;
			while ((line = br.readLine()) != null)
			{
				// Convert JSON to LootEntry and add to data ArrayList
				if (line.length() > 0)
				{
					LootEntry entry = RuneLiteAPI.GSON.fromJson(line, LootEntry.class);
					data.add(entry);
				}
			}

			return data;
		}
		catch (FileNotFoundException e)
		{
			log.debug("File not found: {}", fileName);
			return null;
		}
		catch (IOException e)
		{
			log.warn("Unexpected error: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Retrieve filename from the map or create one if it doesn't exist.
	 * @param name Npc/Event/Boss Name to check for/use as a template.
	 * @return Filename from the map.
	 */
	private String getFileNameFromMap(String name)
	{
		String fileName = filenameMap.get(name);
		if (fileName == null)
		{
			String newFileName = name.toLowerCase().replaceAll("( |'|\\.)", "").toLowerCase() + ".log";
			filenameMap.put(name, newFileName);
			return newFileName;
		}
		return fileName;
	}
}
