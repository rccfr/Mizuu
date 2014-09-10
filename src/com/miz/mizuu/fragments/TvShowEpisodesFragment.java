/*
 * Copyright (C) 2014 Michell Bak
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

package com.miz.mizuu.fragments;

import static com.miz.functions.PreferenceKeys.TVSHOWS_COLLECTION_LAYOUT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.miz.apis.trakt.Trakt;
import com.miz.db.DbAdapterTvShowEpisodes;
import com.miz.functions.CoverItem;
import com.miz.functions.GridEpisode;
import com.miz.functions.LibrarySectionAsyncTask;
import com.miz.functions.MizLib;
import com.miz.functions.TvShowEpisode;
import com.miz.mizuu.IdentifyTvShowEpisode;
import com.miz.mizuu.MizuuApplication;
import com.miz.mizuu.R;
import com.miz.mizuu.TvShowEpisodeDetails;
import com.miz.utils.LocalBroadcastUtils;
import com.miz.utils.TvShowDatabaseUtils;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import com.squareup.picasso.Picasso;

public class TvShowEpisodesFragment extends Fragment {

	private static final String SHOW_ID = "showId";
	private static final String SEASON = "season";

	private Set<Integer> mCheckedEpisodes = new HashSet<Integer>();
	private List<GridEpisode> mItems = new ArrayList<GridEpisode>();
	private Context mContext;
	private GridView mGridView;
	private ProgressBar mProgressBar;
	private ImageAdapter mAdapter;
	private String mShowId;
	private boolean mUseGridView, mContextualActionBarEnabled;
	private int mSeason, mImageThumbSize, mImageThumbSpacing, mResizedWidth, mResizedHeight;
	private Picasso mPicasso;
	private Config mConfig;
	private Bus mBus;

	public static TvShowEpisodesFragment newInstance(String showId, int season) {
		TvShowEpisodesFragment frag = new TvShowEpisodesFragment();
		Bundle b = new Bundle();
		b.putString(SHOW_ID, showId);
		b.putInt(SEASON, season);
		frag.setArguments(b);
		return frag;
	}

	public TvShowEpisodesFragment() {}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mContext = getActivity().getApplicationContext();

		mPicasso = MizuuApplication.getPicasso(mContext);
		mConfig = MizuuApplication.getBitmapConfig();

		mBus = MizuuApplication.getBus();
		mBus.register(this);

		mImageThumbSize = getResources().getDimensionPixelSize(R.dimen.backdrop_thumbnail_width);
		mImageThumbSpacing = getResources().getDimensionPixelSize(R.dimen.image_thumbnail_spacing);

		mShowId = getArguments().getString(SHOW_ID);
		mSeason = getArguments().getInt(SEASON);
		mUseGridView = PreferenceManager.getDefaultSharedPreferences(mContext).getString(TVSHOWS_COLLECTION_LAYOUT, getString(R.string.gridView)).equals(getString(R.string.gridView));
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		mBus.unregister(this);
	}

	@Subscribe
	public void refreshData(com.miz.mizuu.TvShowEpisode episode) {		
		loadEpisodes();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.image_grid_fragment, container, false);
	}

	public void onViewCreated(View v, Bundle savedInstanceState) {
		super.onViewCreated(v, savedInstanceState);

		if (MizLib.isTablet(mContext) && !MizLib.isPortrait(mContext)) {
			v.findViewById(R.id.container).setBackgroundColor(Color.parseColor("#05FFFFFF"));
		}

		mAdapter = new ImageAdapter(mContext);

		mProgressBar = (ProgressBar) v.findViewById(R.id.progress);

		mGridView = (GridView) v.findViewById(R.id.gridView);
		mGridView.setColumnWidth(mImageThumbSize);
		mGridView.setChoiceMode(GridView.CHOICE_MODE_MULTIPLE_MODAL);
		mGridView.setEmptyView(v.findViewById(R.id.progress));
		mGridView.setAdapter(mAdapter);

		if (mUseGridView) {
			// Calculate the total column width to set item heights by factor 1.5
			mGridView.getViewTreeObserver().addOnGlobalLayoutListener(
					new ViewTreeObserver.OnGlobalLayoutListener() {
						@Override
						public void onGlobalLayout() {
							if (mAdapter.getNumColumns() == 0) {
								final int numColumns = (int) Math.floor(mGridView.getWidth() / (mImageThumbSize + mImageThumbSpacing));
								if (numColumns > 0) {
									mAdapter.setNumColumns(numColumns);
									mResizedWidth = (int) (((mGridView.getWidth() - (numColumns * mImageThumbSpacing))
											/ numColumns) * 1.1); // * 1.1 is a hack to make images look slightly less blurry
									mResizedHeight = (int) (mResizedWidth / 1.778);
								}
							}
						}
					});
		} else {
			mGridView.setNumColumns(1);
			mAdapter.setNumColumns(1);
		}
		mGridView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {	
				Intent i = new Intent(mContext, TvShowEpisodeDetails.class);
				i.putExtra(SHOW_ID, mShowId);
				i.putExtra("episode", mItems.get(arg2).getEpisode());
				i.putExtra("season", mSeason);
				getActivity().startActivityForResult(i, 0);
			}
		});
		mGridView.setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				mGridView.setItemChecked(position, true);
				return true;
			}
		});
		mGridView.setMultiChoiceModeListener(new MultiChoiceModeListener() {
			@Override
			public boolean onCreateActionMode(ActionMode mode, Menu menu) {
				MenuInflater inflater = mode.getMenuInflater();
				inflater.inflate(R.menu.episodes_contextual, menu);

				mContextualActionBarEnabled = true;
				return true;
			}

			@Override
			public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
				return true;
			}

			@Override
			public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
				switch (item.getItemId()) {
				case R.id.watched:
					changeWatchedStatus(true);
					break;
				case R.id.unwatched:
					changeWatchedStatus(false);
					break;
				case R.id.remove:
					removeSelectedEpisodes(new HashSet<Integer>(mCheckedEpisodes));
					break;
				case R.id.identify:
					identify(new HashSet<Integer>(mCheckedEpisodes));
					break;
				}

				mode.finish();

				// Make the seasons grid refresh
				mBus.post(new com.miz.mizuu.TvShowEpisode());

				return true;
			}

			@Override
			public void onDestroyActionMode(ActionMode mode) {
				mContextualActionBarEnabled = false;
				mCheckedEpisodes.clear();
			}

			@Override
			public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {				
				if (checked)
					mCheckedEpisodes.add(mItems.get(position).getEpisode());
				else
					mCheckedEpisodes.remove(mItems.get(position).getEpisode());

				int count = mCheckedEpisodes.size();
				mode.setTitle(count + " " + getResources().getQuantityString(R.plurals.episodes_selected, count, count));

				// Nasty hack to update the selected items highlight...
				mAdapter.notifyDataSetChanged();
			}
		});

		// The layout has been created - let's load the data
		loadEpisodes();
	}

	private void changeWatchedStatus(boolean watched) {
		// Create and open database
		DbAdapterTvShowEpisodes db = MizuuApplication.getTvEpisodeDbAdapter();

		// This ought to be done in the background, but performance is fairly decent
		for (int episode : mCheckedEpisodes) {
			db.setEpisodeWatchStatus(mShowId, MizLib.addIndexZero(mSeason), MizLib.addIndexZero(episode), watched);
		}

		if (MizLib.isOnline(mContext) && Trakt.hasTraktAccount(mContext))
			syncWatchedStatusWithTrakt(mCheckedEpisodes, watched);

		loadEpisodes();
	}

	private void identify(final Set<Integer> checkedEpisodes) {
		ArrayList<String> filepaths = new ArrayList<String>();

		for (int episode : checkedEpisodes) {
			ArrayList<String> paths = MizuuApplication.getTvShowEpisodeMappingsDbAdapter().getFilepathsForEpisode(mShowId, MizLib.addIndexZero(mSeason), MizLib.addIndexZero(episode));
			for (String path : paths)
				filepaths.add(path);
		}
		
		Intent i = new Intent(getActivity(), IdentifyTvShowEpisode.class);
		i.putExtra("filepaths", filepaths);
		i.putExtra("showId", mShowId);
		i.putExtra("showTitle", MizuuApplication.getTvDbAdapter().getShowTitle(mShowId));
		
		getActivity().startActivityForResult(i, 0);
	}

	private void syncWatchedStatusWithTrakt(final Set<Integer> checkedEpisodes, final boolean watched) {
		new com.miz.functions.AsyncTask<Void, Boolean, Boolean>() {

			private Set<Integer> mSelectedEpisodes;
			private List<TvShowEpisode> mEpisodes = new ArrayList<TvShowEpisode>();

			@Override
			protected void onPreExecute() {
				mSelectedEpisodes = new HashSet<Integer>(checkedEpisodes);

				for (int episode : mSelectedEpisodes) {
					mEpisodes.add(new TvShowEpisode(mShowId, episode, mSeason));
				}
			}

			@Override
			protected Boolean doInBackground(Void... params) {
				boolean result = Trakt.markEpisodeAsWatched(mShowId, mEpisodes, mContext, watched);
				if (!result) // Try again if it failed
					result = Trakt.markEpisodeAsWatched(mShowId, mEpisodes, mContext, watched);

				return result;
			}

			@Override
			protected void onPostExecute(Boolean result) {
				if (!result)
					Toast.makeText(mContext, R.string.sync_error, Toast.LENGTH_LONG).show();
			}
		}.execute();
	}

	private void removeSelectedEpisodes(final Set<Integer> selectedEpisodes) {
		// Get the Activity Context
		final Context activityContext = getActivity();

		AlertDialog.Builder builder = new AlertDialog.Builder(activityContext);
		builder.setTitle(R.string.remove_selected_episodes);
		builder.setMessage(R.string.areYouSure);
		builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// Go through all episodes and remove the selected ones
				for (int episode : selectedEpisodes) {
					TvShowDatabaseUtils.removeEpisode(activityContext, mShowId, mSeason, episode);
				}

				// Check if we've removed all TV show episodes for the specific season
				if (MizuuApplication.getTvEpisodeDbAdapter().getEpisodeCountForSeason(mShowId, MizLib.addIndexZero(mSeason)) == 0) {

					// Update the TV show library
					LocalBroadcastUtils.updateTvShowLibrary(activityContext);

					// Finish the Activity
					getActivity().finish();
				} else {
					// There's still episodes left, so re-load the TV show seasons
					loadEpisodes();
				}
			}
		});
		builder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.cancel();
			}
		});
		builder.show();
	}

	private void loadEpisodes() {
		new EpisodeLoader().execute();
	}

	private class ImageAdapter extends BaseAdapter {

		private LayoutInflater inflater;
		private final Context mContext;
		private int mNumColumns = 0;
		private List<GridEpisode> mItems = new ArrayList<GridEpisode>();

		public ImageAdapter(Context context) {
			mContext = context;
			inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		// This is necessary in order to avoid random ArrayOutOfBoundsException when changing the items
		public void setItems(List<GridEpisode> items) {
			mItems = new ArrayList<GridEpisode>(items);
			notifyDataSetChanged();
		}

		@Override
		public int getCount() {
			return mItems.size();
		}

		@Override
		public boolean isEmpty() {
			return mItems.size() == 0;
		}

		@Override
		public Object getItem(int position) {
			return position;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public int getItemViewType(int position) {
			return 0;
		}

		@Override
		public int getViewTypeCount() {
			return 1;
		}

		@Override
		public View getView(final int position, View convertView, ViewGroup container) {
			final CoverItem holder;
			final GridEpisode episode = mItems.get(position);

			if (convertView == null) {
				if (mUseGridView)
					convertView = inflater.inflate(R.layout.grid_episode, container, false);
				else
					convertView = inflater.inflate(R.layout.list_episode, container, false);
				holder = new CoverItem();

				holder.mLinearLayout = (LinearLayout) convertView.findViewById(R.id.card_layout);
				holder.cover = (ImageView) convertView.findViewById(R.id.cover);
				holder.text = (TextView) convertView.findViewById(R.id.text);
				if (mUseGridView)
					holder.text.setSingleLine(true);
				holder.subtext = (TextView) convertView.findViewById(R.id.gridCoverSubtitle);
				if (mUseGridView)
					holder.subtext.setSingleLine(true);
				holder.highlight = (ImageView) convertView.findViewById(R.id.highlight);

				holder.text.setTypeface(MizuuApplication.getOrCreateTypeface(mContext, "Roboto-Medium.ttf"));

				convertView.setTag(holder);
			} else {
				holder = (CoverItem) convertView.getTag();
			}

			// Android's GridView is pretty stupid regarding selectors
			// so we have to highlight the selected view manually - yuck!
			if (mContextualActionBarEnabled) {
				if (mCheckedEpisodes.contains(episode.getEpisode())) {
					holder.highlight.setVisibility(View.VISIBLE);
				} else {
					holder.highlight.setVisibility(View.GONE);
				}
			} else {
				holder.highlight.setVisibility(View.GONE);
			}

			holder.text.setText(episode.getTitle());

			if (mUseGridView)
				holder.subtext.setText(episode.getSubtitleText());
			else
				holder.subtext.setText(episode.getSubtitleText() + "\n" + MizLib.getPrettyDatePrecise(mContext, episode.getAirDate()));

			if (mResizedWidth > 0)
				mPicasso.load(episode.getCover()).placeholder(R.color.card_background_dark).error(R.drawable.nobackdrop).resize(mResizedWidth, mResizedHeight).config(mConfig).into(holder.cover);
			else
				mPicasso.load(episode.getCover()).placeholder(R.color.card_background_dark).error(R.drawable.nobackdrop).config(mConfig).into(holder.cover);

			return convertView;
		}

		public void setNumColumns(int numColumns) {
			mNumColumns = numColumns;
		}

		public int getNumColumns() {
			return mNumColumns;
		}
	}

	private class EpisodeLoader extends LibrarySectionAsyncTask<Void, Void, Void> {
		@Override
		protected void onPreExecute() {
			mProgressBar.setVisibility(View.VISIBLE);
		}

		@Override
		protected Void doInBackground(Void... params) {
			mItems.clear();
			mItems.addAll(MizuuApplication.getTvEpisodeDbAdapter().getEpisodesInSeason(mContext, mShowId, mSeason));

			Collections.sort(mItems);

			return null;
		}

		@Override
		public void onPostExecute(Void result) {
			mProgressBar.setVisibility(View.GONE);
			mAdapter.setItems(mItems);
		}
	}
}