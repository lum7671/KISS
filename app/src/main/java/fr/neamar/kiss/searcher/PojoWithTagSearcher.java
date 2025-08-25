package fr.neamar.kiss.searcher;

import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.db.HistoryMode;
import fr.neamar.kiss.pojo.Pojo;
import fr.neamar.kiss.pojo.PojoWithTags;

/**
 * Returns a list of all results that match the specified pojo with tags.
 */
public abstract class PojoWithTagSearcher extends Searcher {

    private final SharedPreferences prefs;

    public PojoWithTagSearcher(MainActivity activity, String query) {
        super(activity, query, false);
        prefs = PreferenceManager.getDefaultSharedPreferences(activity);
    }

    @Override
    protected void doInBackground() {
        MainActivity activity = activityWeakReference.get();
        if (activity == null)
            return;

        // 태그 검색인 경우 최적화된 메서드 사용
        if (this instanceof fr.neamar.kiss.searcher.TagsSearcher && query != null && !query.equals("<tags>")) {
            KissApplication.getApplication(activity).getDataHandler().requestRecordsByTag(query, this);
        } else {
            // 기존 방식 (태그가 없는 앱 검색 등)
            KissApplication.getApplication(activity).getDataHandler().requestAllRecords(this);
        }
    }

    @Override
    public boolean addResults(List<? extends Pojo> pojos) {
        List<Pojo> filteredPojos = new ArrayList<>();
        for (Pojo pojo : pojos) {
            if (!(pojo instanceof PojoWithTags)) {
                continue;
            }
            PojoWithTags pojoWithTags = (PojoWithTags) pojo;
            if (acceptPojo(pojoWithTags)) {
                filteredPojos.add(pojoWithTags);
            }
        }

        MainActivity activity = activityWeakReference.get();
        if (activity == null) {
            return false;
        }

        KissApplication.getApplication(activity).getDataHandler().applyRelevanceFromHistory(filteredPojos, getTaggedResultSortMode());

        return super.addResults(filteredPojos);
    }

    @NonNull
    private HistoryMode getTaggedResultSortMode() {
        String sortMode = prefs.getString("tagged-result-sort-mode", "default");
        if ("default".equals(sortMode)) {
            return KissApplication.getApplication(activityWeakReference.get()).getDataHandler().getHistoryMode();
        }
        return HistoryMode.valueById(sortMode);

    }

    protected int getMaxResultCount() {
        return Integer.MAX_VALUE;
    }

    abstract protected boolean acceptPojo(PojoWithTags pojoWithTags);
}
