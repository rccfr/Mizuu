package com.miz.mizuu;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;

import com.crashlytics.android.Crashlytics;
import com.miz.db.DbAdapter;
import com.miz.db.DbAdapterSources;
import com.miz.db.DbAdapterTvShow;
import com.miz.db.DbAdapterTvShowEpisode;
import com.miz.functions.MizLib;
import com.miz.functions.PicassoDownloader;
import com.squareup.picasso.LruCache;
import com.squareup.picasso.Picasso;

public class MizuuApplication extends Application {

	private static DbAdapterTvShow dbTvShow;
	private static DbAdapterTvShowEpisode dbTvShowEpisode;
	private static DbAdapterSources dbSources;
	private static DbAdapter db;
	private static HashMap<String, String[]> map = new HashMap<String, String[]>();
	private static Picasso mPicasso;
	private static LruCache mLruCache;
	private static File mMovieThumbFolder;

	@Override
	public void onCreate() {
		super.onCreate();

		jcifs.Config.setProperty("jcifs.smb.client.disablePlainTextPasswords", "false");

		if (!(0 != ( getApplicationInfo().flags &= ApplicationInfo.FLAG_DEBUGGABLE)))
			Crashlytics.start(this);

		// Database setup
		dbTvShow = new DbAdapterTvShow(this);
		dbTvShowEpisode = new DbAdapterTvShowEpisode(this);
		dbSources = new DbAdapterSources(this);
		db = new DbAdapter(this);

		mLruCache = new LruCache(calculateMemoryCacheSize(this));
	}

	@Override
	public void onTerminate() {
		super.onTerminate();

		dbTvShow.close();
		dbTvShowEpisode.close();
		dbSources.close();
		db.close();
	}

	public static DbAdapterTvShow getTvDbAdapter() {
		return dbTvShow;
	}

	public static DbAdapterTvShowEpisode getTvEpisodeDbAdapter() {
		return dbTvShowEpisode;
	}

	public static DbAdapterSources getSourcesAdapter() {
		return dbSources;
	}

	public static DbAdapter getMovieAdapter() {
		return db;
	}

	public static String[] getCifsFilesList(String parentPath) {
		return map.get(parentPath);
	}

	public static void putCifsFilesList(String parentPath, String[] list) {
		if (!map.containsKey(parentPath))
			map.put(parentPath, list);
	}

	public static Picasso getPicassoForCovers(Context context) {
		mPicasso = new Picasso.Builder(context).downloader(new PicassoDownloader(context)).executor(getThreadPoolExecutor()).memoryCache(getLruCache()).build();
		
		return mPicasso;
	}
	
	public static Picasso getPicassoForWeb(Context context) {
		mPicasso = new Picasso.Builder(context).executor(getThreadPoolExecutor()).memoryCache(getLruCache()).build();
		
		return mPicasso;
	}

	private static ThreadPoolExecutor getThreadPoolExecutor() {
		return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
				new LinkedBlockingQueue<Runnable>(), new PicassoThreadFactory());
	}

	static class PicassoThreadFactory implements ThreadFactory {
		public Thread newThread(Runnable r) {
			return new PicassoThread(r);
		}
	}

	private static class PicassoThread extends Thread {
		public PicassoThread(Runnable r) {
			super(r);
		}

		@Override public void run() {
			android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY);
			super.run();
		}
	}

	public static LruCache getLruCache() {
		return mLruCache;
	}

	public static File getMovieThumbFolder(Context context) {
		if (mMovieThumbFolder == null)
			mMovieThumbFolder = MizLib.getMovieThumbFolder(context);
		return mMovieThumbFolder;
	}

	public static int calculateMemoryCacheSize(Context context) {
		ActivityManager am = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
		int memoryClass = am.getLargeMemoryClass();
		
		// Target 20% of the available heap.
		return 1024 * 1024 * memoryClass / 5;
	}
}