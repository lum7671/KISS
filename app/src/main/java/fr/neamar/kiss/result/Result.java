package fr.neamar.kiss.result;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import androidx.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.StyleableRes;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;

import fr.neamar.kiss.BuildConfig;
import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.R;
import fr.neamar.kiss.UIColors;
import fr.neamar.kiss.adapter.RecordAdapter;
import fr.neamar.kiss.db.DBHelper;
import fr.neamar.kiss.normalizer.StringNormalizer;
import fr.neamar.kiss.pojo.AppPojo;
import fr.neamar.kiss.pojo.ContactsPojo;
import fr.neamar.kiss.pojo.PhonePojo;
import fr.neamar.kiss.pojo.Pojo;
import fr.neamar.kiss.pojo.SearchPojo;
import fr.neamar.kiss.pojo.SettingPojo;
import fr.neamar.kiss.pojo.ShortcutPojo;
import fr.neamar.kiss.pojo.TagDummyPojo;
import fr.neamar.kiss.searcher.QueryInterface;
import fr.neamar.kiss.ui.ListPopup;
import fr.neamar.kiss.utils.fuzzy.FuzzyScore;
import fr.neamar.kiss.utils.fuzzy.MatchInfo;

public abstract class Result<T extends Pojo> {

    /**
     * Current information pojo
     */
    @NonNull
    protected final T pojo;

    Result(@NonNull T pojo) {
        this.pojo = pojo;
    }

    public static Result<?> fromPojo(QueryInterface parent, @NonNull Pojo pojo) {
        if (pojo instanceof AppPojo)
            return new AppResult((AppPojo) pojo);
        else if (pojo instanceof ContactsPojo)
            return new ContactsResult(parent, (ContactsPojo) pojo);
        else if (pojo instanceof SearchPojo)
            return new SearchResult((SearchPojo) pojo);
        else if (pojo instanceof SettingPojo)
            return new SettingsResult((SettingPojo) pojo);
        else if (pojo instanceof PhonePojo)
            return new PhoneResult((PhonePojo) pojo);
        else if (pojo instanceof ShortcutPojo)
            return new ShortcutsResult((ShortcutPojo) pojo);
        else if (pojo instanceof TagDummyPojo)
            return new TagDummyResult((TagDummyPojo) pojo);

        throw new UnsupportedOperationException("Unable to create a result from POJO");
    }

    public String getPojoId() {
        return pojo.id;
    }

    @Override
    public String toString() {
        return pojo.getName();
    }

    /**
     * How to display this record ?
     *
     * @param context     android context
     * @param convertView a view to be recycled
     * @param parent      view that provides a set of LayoutParams values
     * @param fuzzyScore  information for highlighting search result
     * @return a view to display as item
     */
    @NonNull
    public abstract View display(Context context, View convertView, @NonNull ViewGroup parent, FuzzyScore fuzzyScore);

    @NonNull
    public View inflateFavorite(@NonNull Context context, @NonNull ViewGroup parent) {
        View favoriteView = LayoutInflater.from(context).inflate(R.layout.favorite_item, parent, false);
        ImageView favoriteImage = favoriteView.findViewById(R.id.favorite);
        setAsyncDrawable(favoriteImage, R.drawable.ic_launcher_white);
        favoriteView.setContentDescription(pojo.getName());
        return favoriteView;
    }

    void displayHighlighted(String text, List<Pair<Integer, Integer>> positions, TextView view, Context context) {
        SpannableString enriched = new SpannableString(text);
        int primaryColor = UIColors.getPrimaryColor(context);

        for (Pair<Integer, Integer> position : positions) {
            enriched.setSpan(
                    new ForegroundColorSpan(primaryColor),
                    position.first,
                    position.second,
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE
            );
        }
        view.setText(enriched);
    }

    boolean displayHighlighted(StringNormalizer.Result normalized, String text, FuzzyScore fuzzyScore,
                               TextView view, Context context) {
        MatchInfo matchInfo = fuzzyScore.match(normalized.codePoints);

        if (!matchInfo.match) {
            view.setText(text);
            return false;
        }

        SpannableString enriched = new SpannableString(text);
        int primaryColor = UIColors.getPrimaryColor(context);

        for (Pair<Integer, Integer> position : matchInfo.getMatchedSequences()) {
            enriched.setSpan(
                    new ForegroundColorSpan(primaryColor),
                    normalized.mapPosition(position.first),
                    normalized.mapPosition(position.second),
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE
            );
        }
        view.setText(enriched);
        return true;
    }

    public String getSection() {
        try {
            // get the normalized first letter of the pojo
            // Ensure accented characters are never displayed. (É => E)
            String ch = Character.toString((char) pojo.normalizedName.codePoints[0]);
            // convert to uppercase otherwise lowercase a -z will be sorted
            // after upper A-Z
            return ch.toUpperCase(Locale.getDefault());
        } catch (ArrayIndexOutOfBoundsException e) {
            // Normalized name is empty.
            return "-";
        }
    }

    /**
     * How to display the popup menu
     *
     * @return a PopupMenu object
     */
    public ListPopup getPopupMenu(final Context context, final RecordAdapter parent, final View parentView) {
        ArrayAdapter<ListPopup.Item> popupMenuAdapter = new ArrayAdapter<>(context, R.layout.popup_list_item);
        ListPopup menu = buildPopupMenu(context, popupMenuAdapter, parent, parentView);

        menu.setOnItemClickListener((adapter, view, position) -> {
            @StringRes int stringId = ((ListPopup.Item) adapter.getItem(position)).stringId;
            popupMenuClickHandler(view.getContext(), parent, stringId, parentView);
        });

        return menu;
    }

    /**
     * Default popup menu implementation, can be overridden by children class to display a more specific menu
     *
     * @return an inflated, listener-free PopupMenu
     */
    ListPopup buildPopupMenu(Context context, ArrayAdapter<ListPopup.Item> adapter, final RecordAdapter parent, View parentView) {
        adapter.add(new ListPopup.Item(context, R.string.menu_remove));
        adapter.add(new ListPopup.Item(context, R.string.menu_favorites_add));
        adapter.add(new ListPopup.Item(context, R.string.menu_favorites_remove));
        return inflatePopupMenu(adapter, context);
    }

    ListPopup inflatePopupMenu(ArrayAdapter<ListPopup.Item> adapter, Context context) {
        ListPopup menu = new ListPopup(context);
        menu.setAdapter(adapter);

        // If app already pinned, do not display the "add to favorite" option
        // otherwise don't show the "remove favorite button"
        String favApps = PreferenceManager.getDefaultSharedPreferences(context).
                getString("favorite-apps-list", "");
        if (favApps.contains(this.pojo.id + ";")) {
            for (int i = 0; i < adapter.getCount(); i += 1) {
                ListPopup.Item item = adapter.getItem(i);
                assert item != null;
                if (item.stringId == R.string.menu_favorites_add) {
                    adapter.remove(item);
                }
            }
        } else {
            for (int i = 0; i < adapter.getCount(); i += 1) {
                ListPopup.Item item = adapter.getItem(i);
                assert item != null;
                if (item.stringId == R.string.menu_favorites_remove) {
                    adapter.remove(item);
                }
            }
        }

        if (BuildConfig.DEBUG) {
            adapter.add(new ListPopup.Item("Relevance: " + pojo.relevance));
        }

        return menu;
    }

    /**
     * Handler for popup menu action.
     * Default implementation only handle remove from history action.
     *
     * @return Works in the same way as onOptionsItemSelected, return true if the action has been handled, false otherwise
     */
    boolean popupMenuClickHandler(Context context, RecordAdapter parent, @StringRes int stringId, View parentView) {
        if (stringId == R.string.menu_remove) {
            removeFromResultsAndHistory(context, parent);
            return true;
        } else if (stringId == R.string.menu_favorites_add) {
            launchAddToFavorites(context, pojo);
        } else if (stringId == R.string.menu_favorites_remove) {
            launchRemoveFromFavorites(context, pojo);
        }

        MainActivity mainActivity = (MainActivity) context;
        // Update favorite bar
        mainActivity.onFavoriteChange();
        mainActivity.launchOccurred();
        // Update Search to reflect favorite add, if the "exclude favorites" option is active
        if (mainActivity.prefs.getBoolean("exclude-favorites-history", false) && mainActivity.isViewingSearchResults()) {
            mainActivity.updateSearchRecords();
        }

        return false;
    }

    private void launchAddToFavorites(Context context, Pojo pojo) {
        String msg = context.getResources().getString(R.string.toast_favorites_added);
        KissApplication.getApplication(context).getDataHandler().addToFavorites(pojo.getFavoriteId());
        Toast.makeText(context, String.format(msg, pojo.getName()), Toast.LENGTH_SHORT).show();
    }

    private void launchRemoveFromFavorites(Context context, Pojo pojo) {
        String msg = context.getResources().getString(R.string.toast_favorites_removed);
        KissApplication.getApplication(context).getDataHandler().removeFromFavorites(pojo.getFavoriteId());
        Toast.makeText(context, String.format(msg, pojo.getName()), Toast.LENGTH_SHORT).show();
    }

    /**
     * Remove the current result from the list
     *
     * @param context android context
     * @param parent  adapter on which to remove the item
     */
    private void removeFromResultsAndHistory(Context context, RecordAdapter parent) {
        removeFromHistory(context);
        Toast.makeText(context, R.string.removed_item, Toast.LENGTH_SHORT).show();
        parent.removeResult(context, this);
    }

    public final void launch(Context context, View v, int position) {
    Log.i("KISS_RESULT_TRACE", "launch() called: Result type=" + this.getClass().getSimpleName() + ", pojo=" + (pojo != null ? pojo.getClass().getSimpleName() : "null") + ", id=" + (pojo != null ? pojo.id : "null") + ", position=" + position);

    doLaunch(context, v);
    recordLaunch(context, null);
    }

    public final void launch(Context context, View v, @Nullable QueryInterface queryInterface) {
        Log.i(this.getClass().getSimpleName(), "Launching " + pojo.id);

        // Launch
        doLaunch(context, v);

        recordLaunch(context, queryInterface);
    }

    protected final void recordLaunch(Context context, @Nullable QueryInterface queryInterface) {
        // Save in history
        if (canAddToHistory()) {
            KissApplication.getApplication(context).getDataHandler().addToHistory(pojo.getHistoryId());
        }
        // Record the launch after some period,
        // * to ensure the animation runs smoothly
        // * to avoid a flickering -- launchOccurred will refresh the list
        // Thus TOUCH_DELAY * 3
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            if (queryInterface != null) {
                queryInterface.launchOccurred();
            }
        }, KissApplication.TOUCH_DELAY * 3);
    }

    /**
     * to check if launch can be added to history
     * @return true, if launch can be added to history
     */
    protected boolean canAddToHistory() {
        return !pojo.isDisabled();
    }

    /**
     * How to launch this record ? Most probably, will fire an intent.
     *
     * @param context android context
     */
    protected abstract void doLaunch(Context context, View v);

    /**
     * How to launch this record "quickly" ? Most probably, same as doLaunch().
     * Override to define another behavior.
     *
     * @param context android context
     */
    public void fastLaunch(Context context, View v) {
        this.launch(context, v, -1);
        this.launch(context, v, null);
    }

    /**
     * Return the icon for this Result, or null if non existing.
     *
     * @param context android context
     */
    public Drawable getDrawable(Context context) {
        return null;
    }

    /**
     * Does the drawable changes regularly?
     * If so, it can't be kept in cache for long.
     *
     * @return true when dynamic
     */
    public boolean isDrawableDynamic() {
        return false;
    }

    boolean isDrawableCached() {
        return false;
    }

    void setDrawableCache(Drawable drawable) {
    }

    void setAsyncDrawable(ImageView view) {
        setAsyncDrawable(view, android.R.color.transparent);
    }

    void setAsyncDrawable(ImageView view, @DrawableRes int resId) {
        setAsyncDrawable(view, resId, true);
    }
    
    /**
     * 이미지를 비동기로 로딩합니다. 뷰포트 체크 옵션을 제공합니다.
     * Kotlin Coroutines 기반으로 변환됨 (AsyncSetImage → SetImageCoroutine)
     * 
     * @param view 이미지를 표시할 ImageView
     * @param resId 플레이스홀더 리소스 ID
     * @param checkViewport 뷰포트 내에 있는지 확인할지 여부
     */
    void setAsyncDrawable(ImageView view, @DrawableRes int resId, boolean checkViewport) {
        // 뷰포트 체크를 더 관대하게 처리 - 완전히 화면 밖에 있을 때만 스킵
        if (checkViewport && !isViewInViewport(view)) {
            view.setImageResource(resId);
            view.setTag(null);
            // 지연 로딩: 잠시 후 다시 시도
            view.post(() -> {
                if (isViewInViewport(view)) {
                    setAsyncDrawable(view, resId, false); // 재시도 시 viewport 체크 비활성화
                }
            });
            return;
        }
        
        // getting this called multiple times in parallel may result in empty icons
        synchronized (this) {
            // Check if we're already loading the same result
            Object currentTag = view.getTag();
            if (this.equals(currentTag)) {
                return; // 이미 로딩 중이면 중복 방지
            }
            
            if (isDrawableCached()) {
                view.setImageDrawable(getDrawable(view.getContext()));
                view.setTag(this);
            } else {
                // Use Kotlin Coroutines - it handles cancellation internally
                SetImageCoroutine.INSTANCE.setImageAsync(view, this, resId);
            }
        }
    }
    
    /**
     * 뷰가 현재 화면에 보이는지 확인합니다.
     * 
     * @param view 확인할 뷰
     * @return 화면에 보이면 true
     */
    private boolean isViewInViewport(ImageView view) {
        if (view == null) return true;
        
        // 뷰가 부모 스크롤 컨테이너에서 보이는지 확인
        ViewGroup parent = (ViewGroup) view.getParent();
        while (parent != null) {
            if (parent instanceof android.widget.ListView || 
                parent instanceof androidx.recyclerview.widget.RecyclerView ||
                parent instanceof android.widget.ScrollView) {
                
                int[] viewLocation = new int[2];
                view.getLocationOnScreen(viewLocation);
                
                int[] parentLocation = new int[2];
                parent.getLocationOnScreen(parentLocation);
                
                int viewTop = viewLocation[1];
                int viewBottom = viewTop + view.getHeight();
                int parentTop = parentLocation[1];
                int parentBottom = parentTop + parent.getHeight();
                
                // 여유 공간을 두어 더 관대하게 판단 (뷰 높이의 2배 여유)
                int margin = view.getHeight() * 2;
                return viewTop < (parentBottom + margin) && viewBottom > (parentTop - margin);
            }
            parent = (ViewGroup) parent.getParent();
        }
        
        // 스크롤 컨테이너를 찾지 못한 경우 항상 로딩
        return true;
    }

    /**
     * Helper function to get a view
     *
     * @param context android context
     * @param id      id to inflate
     * @param parent  view that provides a set of LayoutParams values
     * @return the view specified by the id
     */
    View inflateFromId(Context context, @LayoutRes int id, @NonNull ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(context);
        return inflater.inflate(id, parent, false);
    }

    void removeFromHistory(Context context) {
        DBHelper.removeFromHistory(context, pojo.getHistoryId());
    }

    /*
     * Get fill color from theme
     *
     */
    int getThemeFillColor(Context context) {
        @SuppressLint("ResourceType") @StyleableRes
        int[] attrs = new int[]{R.attr.resultColor /* index 0 */};
        TypedArray ta = context.obtainStyledAttributes(attrs);
        int color = ta.getColor(0, Color.WHITE);
        ta.recycle();
        return color;
    }

    public long getUniqueId() {
        // we can consider hashCode unique enough in this context
        return this.pojo.id.hashCode();
    }

    protected boolean isHideIcons(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("icons-hide", false);
    }

    protected boolean isTagsVisible(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("tags-visible", true);
    }

    protected boolean isSubIconVisible(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean("subicon-visible", true);
    }

    protected void setSourceBounds(@NonNull Intent intent, @Nullable View view) {
        intent.setSourceBounds(getViewBounds(view));
    }

    @Nullable
    protected Rect getViewBounds(@Nullable View view) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 && view != null) {
            return view.getClipBounds();
        }
        return null;
    }

}
