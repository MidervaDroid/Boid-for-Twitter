package com.teamboid.columns;

import java.util.ArrayList;

import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Tweet;
import twitter4j.TwitterException;
import twitter4j.User;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.teamboid.twitter.Account;
import com.teamboid.twitter.AccountService;
import com.teamboid.twitter.FeedListAdapter;
import com.teamboid.twitter.R;
import com.teamboid.twitter.TimelineCAB;
import com.teamboid.twitter.TweetViewer;
import com.teamboid.twitter.Utilities;
import com.teamboid.twitter.MessageConvoAdapter.DMConversation;
import com.teamboid.twitter.TabsAdapter.BaseListFragment;

public class TimelineFragment extends BaseListFragment {

	private Activity context;
	private FeedListAdapter adapt;
	public static final String ID = "COLUMNTYPE:TIMELINE";

	@Override
	public void onAttach(Activity act) {
		super.onAttach(act);
		context = act;
	}

	@Override
	public void onListItemClick(ListView l, View v, int index, long id) {
		super.onListItemClick(l, v, index, id);
		if(TimelineCAB.getSelectedTweets().length > 0) {
			TimelineCAB.performLongPressAction(getListView(), adapt, index);
		} else {
			Status tweet = (Status)adapt.getItem(index);
			if (tweet.isRetweet()) tweet = tweet.getRetweetedStatus();
			context.startActivity(new Intent(context, TweetViewer.class)
			.putExtra("sr_tweet", Utilities.serializeObject(tweet))
			.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		getListView().setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
		getListView().setOnScrollListener(
				new AbsListView.OnScrollListener() {
					@Override
					public void onScrollStateChanged(AbsListView view, int scrollState) { }
					@Override
					public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
						if (totalItemCount > 0 && (firstVisibleItem + visibleItemCount) >= totalItemCount && totalItemCount > visibleItemCount)
							performRefresh(true);
						if (firstVisibleItem == 0 && context.getActionBar().getTabCount() > 0) {
							if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean("enable_iconic_tabs", true))
								context.getActionBar().getTabAt(getArguments().getInt("tab_index")).setText(R.string.timeline_str);
							else context.getActionBar().getTabAt(getArguments().getInt("tab_index")).setText("");
						}
					}
				});
		getListView().setOnItemLongClickListener(
				new AdapterView.OnItemLongClickListener() {
					@Override
					public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int index, long id) {
						TimelineCAB.performLongPressAction(getListView(), adapt, index);
						return true;
					}
				});
		setRetainInstance(true);
		setEmptyText(getString(R.string.no_tweets));
		reloadAdapter(true);
	}

	@Override
	public void onResume() {
		super.onResume();
		if (getView() != null && adapt != null)
			adapt.restoreLastViewed(getListView());
	}

	@Override
	public void onPause() {
		super.onPause();
		savePosition();
	}

	@Override
	public void performRefresh(final boolean paginate) {
		if (context == null || isLoading || adapt == null)
			return;
		isLoading = true;
		if (adapt.getCount() == 0 && getView() != null)
			setListShown(false);
		adapt.setLastViewed(getListView());
		new Thread(new Runnable() {
			@Override
			public void run() {
				Paging paging = new Paging(1, 50);
				if (paginate)
					paging.setMaxId(adapt.getItemId(adapt.getCount() - 1));
				final Account acc = AccountService.getCurrentAccount();
				if (acc != null) {
					try {
						final ResponseList<Status> feed = acc.getClient()
								.getHomeTimeline(paging);
						context.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								setEmptyText(context
										.getString(R.string.no_tweets));
								int beforeLast = adapt.getCount() - 1;
								final SharedPreferences prefs = PreferenceManager
										.getDefaultSharedPreferences(context);
								int addedCount = adapt.add(feed
										.toArray(new Status[0]), prefs
										.getBoolean("enable_muting", true));
								if (addedCount > 0 || beforeLast > 0) {
									if (getView() != null) {
										if (paginate && addedCount > 0)
											getListView()
											.smoothScrollToPosition(
													beforeLast + 1);
										else if (getView() != null
												&& adapt != null)
											adapt.restoreLastViewed(getListView());
									}
									if (!PreferenceManager
											.getDefaultSharedPreferences(
													context).getBoolean(
															"enable_iconic_tabs",
															true)) {
										context.getActionBar()
										.getTabAt(
												getArguments()
												.getInt("tab_index"))
												.setText(
														context.getString(R.string.timeline_str)
														+ " ("
														+ Integer
														.toString(addedCount)
														+ ")");
									} else
										context.getActionBar()
										.getTabAt(
												getArguments()
												.getInt("tab_index"))
												.setText(
														Integer.toString(addedCount));
								}
							}
						});
					} catch (final TwitterException e) {
						e.printStackTrace();
						context.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								setEmptyText(context
										.getString(R.string.error_str));
								Toast.makeText(context,
										e.getErrorMessage(),
										Toast.LENGTH_SHORT).show();
							}
						});
					}
				}
				context.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						if (getView() != null)
							setListShown(true);
						isLoading = false;
					}
				});
			}
		}).start();
	}

	@Override
	public void reloadAdapter(boolean firstInitialize) {
		if (context == null && getActivity() != null)
			context = getActivity();
		if (AccountService.getCurrentAccount() != null) {
			if (adapt != null && !firstInitialize && getView() != null)
				adapt.setLastViewed(getListView());
			adapt = AccountService.getFeedAdapter(context,
					TimelineFragment.ID, AccountService.getCurrentAccount()
					.getId());
			setListAdapter(adapt);
			if (adapt.getCount() == 0)
				performRefresh(false);
			else if (getView() != null && adapt != null) {
				adapt.restoreLastViewed(getListView());
			}
		}
	}

	@Override
	public void savePosition() {
		if (getView() != null && adapt != null)
			adapt.setLastViewed(getListView());
	}

	@Override
	public void restorePosition() {
		if (getView() != null && adapt != null)
			adapt.restoreLastViewed(getListView());
	}

	@Override
	public void jumpTop() {
		if (getView() != null)
			getListView().setSelectionFromTop(0, 0);
	}

	@Override
	public void filter() {
		if (getListView() == null || adapt == null)
			return;
		AccountService.clearFeedAdapter(context, TimelineFragment.ID,
				AccountService.getCurrentAccount().getId());
		performRefresh(false);
	}

	@Override
	public Status[] getSelectedStatuses() {
		if(adapt == null) return null;
		ArrayList<Status> toReturn = new ArrayList<Status>(); 
		SparseBooleanArray checkedItems = getListView().getCheckedItemPositions();
		if(checkedItems != null) {
			for(int i = 0; i < checkedItems.size(); i++) {
				if(checkedItems.valueAt(i)) {
					toReturn.add((Status)adapt.getItem(checkedItems.keyAt(i)));
				}
			}
		}
		return toReturn.toArray(new Status[0]);
	}

	@Override
	public User[] getSelectedUsers() { return null; }

	@Override
	public Tweet[] getSelectedTweets() { return null; }

	@Override
	public DMConversation[] getSelectedMessages() { return null; }
}
