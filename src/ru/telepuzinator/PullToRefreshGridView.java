package ru.telepuzinator;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class PullToRefreshGridView extends LinearLayout {
	private static final int PULL_TO_REFRESH = 0;
	private static final int RELEASE_TO_REFRESH = 2;
	private static final int REFRESHING = 3;

	private static final int EVENT_COUNT = 3;

	private int state = PULL_TO_REFRESH;

	private GridView gridView;
	private RelativeLayout header;
	private TextView headerText;
	private ImageView headerImage;
	private ProgressBar headerProgress;
	private Animation flipAnimation, reverseAnimation;
	
	private BaseAdapter gridAdapter;
	private EmptyViewAdapter emptyAdapter;

	private int headerHeight;
	private float startY = -1;
	private Handler handler = new Handler();

	private OnItemClickListener onItemClickListener;
	private OnTouchListener onTouchListener;
	private OnRefreshListener onRefreshListener;

	private float[] lastYs = new float[EVENT_COUNT];
	private boolean canPullDownToRefresh = true;

	public interface OnRefreshListener {
		public void onRefresh();
	}

	private OnTouchListener gridViewOnTouchListener = new OnTouchListener() {
		@Override
		public boolean onTouch(View arg0, MotionEvent arg1) {
			return onGridViewTouch(arg0, arg1);
		}
	};

	private Runnable hideHeaderRunnable = new Runnable() {
		@Override
		public void run() {
			hideHeader();
		}
	};

	public PullToRefreshGridView(Context context) {
		this(context, null);
	}

	public PullToRefreshGridView(Context context, AttributeSet attrs) {
		super(context, attrs);
	
		init(context, attrs);
	}

	public void setAdapter(BaseAdapter adapter) {
		this.gridAdapter = adapter;
		gridView.setAdapter(adapter);
		resetHeader();
	}

	public GridView getGridView() {
		return gridView;
	}

	public void setSelection(int position) {
		if (state == REFRESHING) {
			position++;
		}

		gridView.setSelection(position);
	}

	public void setOnItemClickListener(OnItemClickListener listener) {
		onItemClickListener = listener;

		gridView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				if (onItemClickListener == null) {
					return;
				}
				int offset = 0;
				if (state == REFRESHING) {
					offset = 1;
				}
				onItemClickListener.onItemClick(arg0, arg1, arg2 - offset, arg3);
			}
		});
	}

	public void onRefreshComplete() {
		resetHeader();
	}

	public void setOnRefreshListener(OnRefreshListener listener) {
		onRefreshListener = listener;
	}

	public int getFirstVisiblePosition() {
		return gridView.getFirstVisiblePosition();
	}

	public int getLastVisiblePosition() {
		if (state == REFRESHING) {
			return gridView.getLastVisiblePosition() - 1;
		} else {
			return gridView.getLastVisiblePosition();
		}
	}

	public void onDestroy() {
		gridView = null;
		onRefreshListener = null;
		onItemClickListener = null;
	}

	public void setRefreshed() {
		setState(REFRESHING);
		
		setHeaderScroll(headerHeight);
		
		setUpdating(true);
	}

	public ListAdapter getAdapter() {
		return gridAdapter;
	}

	public void setOnScrollListener(OnScrollListener listener) {
		gridView.setOnScrollListener(listener);
	}

	public boolean isGridViewShown() {
		return gridView.isShown();
	}

	public View getGridViewChildAt(int index) {
		if (state == REFRESHING) {
			index++;
		}

		return gridView.getChildAt(index);
	}

	public void setEmptyText(String text) {
		emptyAdapter.setText(text);
	}
	
	boolean empty = true;
	public void showEmptyView(boolean show) {
		empty = show;
		if(show) {
			gridView.setAdapter(emptyAdapter);
		} else {
			gridView.setAdapter(gridAdapter);
		}
	}
	
	private void changeEmptyView(boolean showProgress) {
		emptyAdapter.changeEmptyView(showProgress);
		if(empty) {
			gridView.setAdapter(emptyAdapter);
		}
	}
	
	@Override
	public void setOnTouchListener(OnTouchListener listener) {
		onTouchListener = listener;
	}
	
	@Override
	protected Parcelable onSaveInstanceState() {
		Bundle savedState = new Bundle();
		savedState.putParcelable("state", super.onSaveInstanceState());
		savedState.putParcelable("gridState", gridView.onSaveInstanceState());
		return savedState;
	}
	
	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		if(!(state instanceof Bundle)) return;
		Bundle savedState = (Bundle) state;
		try {
			this.gridView.onRestoreInstanceState(savedState.getParcelable("gridState"));
		} catch (Exception e) {
		}
		super.onRestoreInstanceState(savedState.getParcelable("state"));
	}
	
	private void init(Context context, AttributeSet attrs) {
		setOrientation(LinearLayout.VERTICAL);
	
		header = (RelativeLayout) LayoutInflater.from(context).inflate(R.layout.pull_to_refresh_header, this, false);
	
		headerText = (TextView) header.findViewById(R.id.pull_to_refresh_text);
		headerImage = (ImageView) header.findViewById(R.id.pull_to_refresh_image);
		headerProgress = (ProgressBar) header.findViewById(R.id.pull_to_refresh_progress);
	
		LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);
	
		addView(header, lp);
	
		measureView(header);
		headerHeight = header.getMeasuredHeight();
	
		gridView = new GridView(context, attrs);
		gridView.setId(View.NO_ID);
		gridView.setOnTouchListener(gridViewOnTouchListener);
	
		lp = new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT);
	
		addView(gridView, lp);
	
		flipAnimation = new RotateAnimation(0, -180,
			RotateAnimation.RELATIVE_TO_SELF, 0.5f,
			RotateAnimation.RELATIVE_TO_SELF, 0.5f);
		flipAnimation.setInterpolator(new LinearInterpolator());
		flipAnimation.setDuration(250);
		flipAnimation.setFillAfter(true);
		
		reverseAnimation = new RotateAnimation(-180, 0,
			RotateAnimation.RELATIVE_TO_SELF, 0.5f,
			RotateAnimation.RELATIVE_TO_SELF, 0.5f);
		reverseAnimation.setInterpolator(new LinearInterpolator());
		reverseAnimation.setDuration(250);
		reverseAnimation.setFillAfter(true);
		
		emptyAdapter = new EmptyViewAdapter(context);
		gridView.setAdapter(emptyAdapter);
	
		setPadding(getPaddingLeft(), -headerHeight, getPaddingRight(), getPaddingBottom());
	}
	
	private void setState(int state) {
		this.state = state;
		changeEmptyView(state == REFRESHING);
		switch(state) {
		case PULL_TO_REFRESH:
			headerText.setText(R.string.pull_to_refresh_pull_label);
			break;
		case REFRESHING:
			headerText.setText(R.string.pull_to_refresh_refreshing_label);
			break;
		case RELEASE_TO_REFRESH:
			headerText.setText(R.string.pull_to_refresh_release_label);
			break;
		}
	}

	private void measureView(View child) {
		ViewGroup.LayoutParams p = child.getLayoutParams();
		if (p == null) {
			p = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
		}

		int childWidthSpec = ViewGroup.getChildMeasureSpec(0, 0, p.width);
		int lpHeight = p.height;
		int childHeightSpec;
		if (lpHeight > 0) {
			childHeightSpec = MeasureSpec.makeMeasureSpec(lpHeight, MeasureSpec.EXACTLY);
		} else {
			childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
		}
		child.measure(childWidthSpec, childHeightSpec);
	}

	private boolean onGridViewTouch(View view, MotionEvent event) {
		switch (event.getAction()) {
		case MotionEvent.ACTION_MOVE:
			updateEventStates(event);
		
			if (isPullingDownToRefresh() && startY == -1) {
				if (startY == -1) {
					startY = event.getY();
				}
				return false;
			}
		
			if (startY != -1 && !gridView.isPressed()) {
				pullDown(event, startY);
				return true;
			}
			break;
		case MotionEvent.ACTION_UP:
			initializeYsHistory();
			startY = -1;
		
			if (state == RELEASE_TO_REFRESH) {
				setRefreshed();
				if (onRefreshListener != null) {
					onRefreshListener.onRefresh();
				}
			} else {
				ensureHeaderPosition();
			}
			break;
		}
	
		if (onTouchListener != null) {
			return onTouchListener.onTouch(view, event);
		}
		return false;
	}

	private void resetHeader() {
		setState(PULL_TO_REFRESH);
		initializeYsHistory();
		startY = -1;
		setUpdating(false);
		headerImage.clearAnimation();
	
		setHeaderScroll(0);
		
		if(gridAdapter != null && gridAdapter.getCount() > 0) {
			showEmptyView(false);
		} else {
			showEmptyView(true);
		}
	}
	
	private void setUpdating(boolean updating) {
		headerImage.clearAnimation();
		headerProgress.setVisibility(updating ? View.VISIBLE : View.INVISIBLE);
		headerImage.setVisibility(updating ? View.INVISIBLE : View.VISIBLE);
	}

	private void pullDown(MotionEvent event, float firstY) {
		float averageY = average(lastYs);
	
		int height = (int) (Math.max(averageY - firstY, 0));
	
		setHeaderScroll((int) (height));
	
		if (state == PULL_TO_REFRESH && height - headerHeight > 0) {
			setState(RELEASE_TO_REFRESH);
			headerImage.clearAnimation();
			headerImage.startAnimation(flipAnimation);
		}
		if (state == RELEASE_TO_REFRESH && height - headerHeight <= 0) {
			setState(PULL_TO_REFRESH);
			headerImage.clearAnimation();
			headerImage.startAnimation(reverseAnimation);
		}
	}

	private void setHeaderScroll(int y) {
		scrollTo(0, -y);
	}

	private int getHeaderScroll() {
		return -getScrollY();
	}

	private float average(float[] ysArray) {
		float avg = 0;
		for (int i = 0; i < EVENT_COUNT; i++) {
			avg += ysArray[i];
		}
		return avg / EVENT_COUNT;
	}

	private void initializeYsHistory() {
		for (int i = 0; i < EVENT_COUNT; i++) {
			lastYs[i] = 0;
		}
	}

	private void updateEventStates(MotionEvent event) {
		for (int i = 0; i < EVENT_COUNT - 1; i++) {
			lastYs[i] = lastYs[i + 1];
		}

		float y = event.getY();
		int top = gridView.getTop();
		lastYs[EVENT_COUNT - 1] = y + top;
	}

	private boolean isPullingDownToRefresh() {
		return canPullDownToRefresh && state != REFRESHING && isIncremental()
			&& isFirstVisible();
	}

	private boolean isFirstVisible() {
		if (this.gridView.getCount() == 0) {
			return true;
		} else if (gridView.getFirstVisiblePosition() == 0) {
			return gridView.getChildAt(0) == null ||
					gridView.getChildAt(0).getTop() >= gridView.getTop() - 3;
		} else {
			return false;
		}
	}

	private boolean isIncremental() {
		return this.isIncremental(0, EVENT_COUNT - 1);
	}

	private boolean isIncremental(int from, int to) {
		return lastYs[from] != 0 && lastYs[to] != 0
			&& Math.abs(lastYs[from] - lastYs[to]) > 10
			&& lastYs[from] < lastYs[to];
	}

	private void ensureHeaderPosition() {
		handler.post(hideHeaderRunnable);
	}

	private void hideHeader() {
		int padding = getHeaderScroll();
		if (padding != 0) {
			int top = padding - (int) (padding / 2);
			if (top < 2) {
				top = 0;
			}
		
			setHeaderScroll(top);
		
			handler.postDelayed(hideHeaderRunnable, 20);
		}
	}
	
	private class EmptyViewAdapter extends BaseAdapter {
		private View emptyView;
		private TextView emptyText;
		private ProgressBar emptyProgress;
		
		private Context context;
		private String text;
		private boolean showProgress = true;
		
		public EmptyViewAdapter(Context context) {
			this.context = context;
		}
		
		public void changeEmptyView(boolean showProgress) {
			this.showProgress = showProgress;
			notifyDataSetChanged();
		}

		public void setText(String text) {
			this.text = text;
		}

		@Override
		public int getCount() {
			return 1;
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if(convertView == null) {
				emptyView = LayoutInflater.from(context).inflate(R.layout.empty_view, parent, false);
				copyLayout(emptyView);
				emptyText = (TextView) emptyView.findViewById(R.id.empty_view_text);
				emptyProgress = (ProgressBar) emptyView.findViewById(R.id.empty_view_progress);
				
				if(text.length() > 0) emptyText.setText(text);
				
				emptyText.setVisibility(showProgress ? View.GONE : View.VISIBLE);
				emptyProgress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
				return emptyView;
			} else {
				copyLayout(convertView);
				return convertView;
			}
		}
		
		//set to center
		private void copyLayout(View child) {
			if(gridView.getHeight() > 0) {
				ViewGroup.LayoutParams lp = emptyView.getLayoutParams();
				lp.height = gridView.getHeight();
				lp.width = gridView.getWidth();
				emptyView.setLayoutParams(lp);
			}
		}
	}
}