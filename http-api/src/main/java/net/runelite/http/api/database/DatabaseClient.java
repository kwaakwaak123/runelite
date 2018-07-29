/*
 * Copyright (c) 2018, TheStonedTurtle <http://github.com/TheStonedTurtle>
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
package net.runelite.http.api.database;

import com.google.gson.JsonParseException;
import lombok.extern.slf4j.Slf4j;
import net.runelite.http.api.RuneLiteAPI;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
public class DatabaseClient
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private UUID uuid;

	public DatabaseClient(UUID uuid)
	{
		this.uuid = uuid;
		log.debug("Created Database Client with UUID: {}", this.uuid);
	}

	/**
	 * Wrapper for looking up by NPC Name (API can find records by NPC Name or NPC ID)
	 *
	 * @param username in-game username as String
	 * @param id npc ID to filter for
	 * @return List of requested LootRecords or null if an error querying database.
	 */
	public List<LootRecord> getLootRecordsByNpcId(String username, int id)
	{
		if (uuid == null)
			return null;

		return getLootRecordsByNpcName(username, String.valueOf(id));
	}

	/**
	 * Returns a List of LootRecords for this username filtered by npcName.
	 *
	 * @param username in-game username as String
	 * @param npcName npc name as String
	 * @return List of LootRecords from RuneLite Database
	 */
	public List<LootRecord> getLootRecordsByNpcName(String username, String npcName)
	{
		if (uuid == null)
			return null;

		DatabaseEndpoint bossEndpoint = DatabaseEndpoint.LOOT;
		HttpUrl.Builder builder = bossEndpoint.getDatabaseURL().newBuilder()
				.addQueryParameter("username", username)
				.addQueryParameter("id", npcName);

		HttpUrl url = builder.build();

		log.debug("Built Database URI: {}", url);

		Request request = new Request.Builder()
				.header(RuneLiteAPI.RUNELITE_AUTH, uuid.toString())
				.url(url)
				.build();

		try (Response response = RuneLiteAPI.CLIENT.newCall(request).execute())
		{
			if (response.isSuccessful())
			{
				String result = response.body().string();
				LootRecord[] records = RuneLiteAPI.GSON.fromJson(result, LootRecord[].class);
				return Arrays.asList(records);
			}
			else
			{
				log.debug("Error Executing Database URI request: {}", url);
				log.debug(response.body().toString());
				return null;
			}
		}
		catch (IOException e)
		{
			log.debug("IOException: {}", e.getMessage());
			return null;
		}
		catch (JsonParseException e)
		{
			log.debug("JsonParseException: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Stores the Loot Record via a post request to the API and returns a success boolean
	 *
	 * @param record Loot Record to Store
	 * @param username in-game username as String
	 * @return Success Flag
	 */
	public boolean storeLootRecord(LootRecord record, String username)
	{
		if (uuid == null)
		{
			return false;
		}

		DatabaseEndpoint bossEndpoint = DatabaseEndpoint.LOOT;
		HttpUrl.Builder builder = bossEndpoint.getDatabaseURL().newBuilder()
				.addQueryParameter("username", username);

		HttpUrl url = builder.build();

		log.debug("Built Database URI: {}", url);

		Request request = new Request.Builder()
				.header(RuneLiteAPI.RUNELITE_AUTH, uuid.toString())
				.url(url)
				.post(RequestBody.create(JSON, RuneLiteAPI.GSON.toJson(record)))
				.build();

		log.debug("JSON Data to store: " + RuneLiteAPI.GSON.toJson(record));

		try (Response response = RuneLiteAPI.CLIENT.newCall(request).execute())
		{
			log.debug("Response message: " + response.message());
			return response.message().equals("OK");
		}
		catch (IOException e)
		{
			log.debug("IOException: {}", e.getMessage());
			return false;
		}
		catch (JsonParseException e)
		{
			log.debug("JsonParseException: {}", e.getMessage());
			return false;
		}
	}
}
