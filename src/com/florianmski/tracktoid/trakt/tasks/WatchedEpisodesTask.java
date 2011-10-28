/*
 * Copyright 2011 Florian Mierzejewski
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.florianmski.tracktoid.trakt.tasks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import android.content.Context;

import com.florianmski.tracktoid.db.DatabaseWrapper;
import com.florianmski.tracktoid.trakt.TraktManager;
import com.jakewharton.trakt.entities.TvShow;
import com.jakewharton.trakt.entities.TvShowEpisode;
import com.jakewharton.trakt.entities.TvShowSeason;
import com.jakewharton.trakt.services.ShowService.EpisodeSeenBuilder;
import com.jakewharton.trakt.services.ShowService.EpisodeUnseenBuilder;

public class WatchedEpisodesTask extends TraktTask
{
	private String tvdbId;
	private int [] seasons;
	private List<Map<Integer, Boolean>> listWatched;
	private List<TvShowSeason> seasonsList;
	
	private TvShow show;

	public WatchedEpisodesTask(TraktManager tm, Context context, String tvdbId, int [] seasons, List<Map<Integer, Boolean>> listWatched) 
	{
		super(tm, context);
		
		this.tvdbId = tvdbId;
		this.seasons = seasons;
		this.listWatched = listWatched;
	}

	public WatchedEpisodesTask(TraktManager tm, Context context, String tvdbId, int season, int episode, boolean watched) 
	{
		super(tm, context);
		
		this.tvdbId = tvdbId;
		this.seasons = new int[]{season};
		this.listWatched = new ArrayList<Map<Integer,Boolean>>();
		this.listWatched.add(new HashMap<Integer, Boolean>());
		this.listWatched.get(0).put(episode, watched);
	}
	
	public WatchedEpisodesTask(TraktManager tm, Context context, String tvdbId, List<TvShowSeason> seasons, boolean watched) 
	{
		super(tm, context);
		
		this.tvdbId = tvdbId;
		this.listWatched = new ArrayList<Map<Integer,Boolean>>();
		this.seasons = new int[seasons.size()];
		
		for(int i = 0; i < seasons.size(); i++)
		{
			this.seasons[i] = seasons.get(i).getSeason();
			this.listWatched.add(new HashMap<Integer, Boolean>());
			for(int j = 1; j <= seasons.get(i).getEpisodes().getCount(); j++)
				this.listWatched.get(i).put(j, watched);
		}
	}
	
	@Override
	protected void doTraktStuffInBackground()
	{
		this.publishProgress("toast", "0", "Sending...");
		
		EpisodeSeenBuilder seenBuilder = tm.showService().episodeSeen(Integer.valueOf(tvdbId));
		EpisodeUnseenBuilder unseenBuilder = tm.showService().episodeUnseen(Integer.valueOf(tvdbId));

		int seenEpisodes = 0;
		int unseenEpisodes = 0;

		for(int i = 0; i < seasons.length; i++)
		{
			Map<Integer, Boolean> listEpisodes = listWatched.get(i);
			for (Iterator<Integer> it = listEpisodes.keySet().iterator(); it.hasNext() ;)
			{
				Integer episode = (Integer) it.next();
				Boolean watched = listEpisodes.get(episode);

				if(watched)
				{
					seenEpisodes++;
					seenBuilder.episode(seasons[i], episode);
				}
				else
				{
					unseenEpisodes++;
					unseenBuilder.episode(seasons[i], episode);
				}
			}
		}

		if(seenEpisodes > 0)
			seenBuilder.fire();
		if(unseenEpisodes > 0)
			unseenBuilder.fire();

		if(seenEpisodes > 0 || unseenEpisodes > 0)
		{
			DatabaseWrapper dbw = new DatabaseWrapper(context);
			dbw.open();

			for(int i = 0; i < seasons.length; i++)
			{
				Map<Integer, Boolean> listEpisodes = listWatched.get(i);
				for (Iterator<Integer> it = listEpisodes.keySet().iterator() ; it.hasNext() ; )
				{
					Integer episode = (Integer) it.next();
					Boolean watched = listEpisodes.get(episode);

					dbw.markEpisodeAsWatched(watched, tvdbId, seasons[i], episode);
				}
			}

			dbw.refreshPercentage(tvdbId);
			show = dbw.getShow(tvdbId);			
			show.setSeasons(dbw.getSeasons(tvdbId, true, true));
			
			dbw.close();
			
			this.publishProgress("toast", "0", "Send to Trakt!");
		}
	}
	
	@Override
	protected void onPostExecute(Boolean success)
	{
		super.onPostExecute(success);
		
		if(success)
			tm.onShowUpdated(show);
	}
}