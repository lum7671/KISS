package fr.neamar.kiss.result;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.util.HashSet;
import java.util.Set;

import fr.neamar.kiss.R;

/**
 * 뷰포트 기반 Lazy Image Loading 시스템
 * 화면에 보이는 이미지만 로딩하여 성능을 최적화합니다.
 */
public class LazyImageLoader {
    
    private static final String TAG = "LazyImageLoader";
    
    // 현재 화면에 보이는 아이템들을 추적
    private final Set<String> visibleItems = new HashSet<>();
    
    // 플레이스홀더 리소스 ID
    private static final int PLACEHOLDER_RES = android.R.color.transparent;
    
    private static LazyImageLoader instance;
    
    public static LazyImageLoader getInstance() {
        if (instance == null) {
            instance = new LazyImageLoader();
        }
        return instance;
    }
    
    private LazyImageLoader() {
        // private constructor
    }
    
    /**
     * 뷰포트 내에 있는 경우에만 이미지를 로딩합니다.
     * 
     * @param result Result 객체
     * @param imageView 이미지를 표시할 ImageView
     * @param uniqueKey 고유 키 (포지션 또는 ID)
     * @param parentView 부모 ListView 또는 RecyclerView
     */
    public void loadImageIfVisible(Result<?> result, ImageView imageView, String uniqueKey, ViewGroup parentView) {
        if (isViewInViewport(imageView, parentView)) {
            // 화면에 보이는 경우 실제 이미지 로딩
            visibleItems.add(uniqueKey);
            result.setAsyncDrawable(imageView, PLACEHOLDER_RES);
        } else {
            // 화면에 보이지 않는 경우 플레이스홀더 설정
            visibleItems.remove(uniqueKey);
            imageView.setImageResource(PLACEHOLDER_RES);
            imageView.setTag(null); // 이전 태스크 정리
        }
    }
    
    /**
     * 뷰가 뷰포트 내에 있는지 확인합니다.
     * 
     * @param view 확인할 뷰
     * @param parentView 부모 뷰
     * @return 뷰포트 내에 있으면 true
     */
    private boolean isViewInViewport(View view, ViewGroup parentView) {
        if (view == null || parentView == null) {
            return true; // 안전을 위해 true 반환
        }
        
        Rect parentRect = new Rect();
        parentView.getGlobalVisibleRect(parentRect);
        
        Rect viewRect = new Rect();
        view.getGlobalVisibleRect(viewRect);
        
        // 뷰가 부모의 보이는 영역과 교차하는지 확인
        return Rect.intersects(parentRect, viewRect);
    }
    
    /**
     * 스크롤이 멈춘 후 보이는 모든 아이템의 이미지를 로딩합니다.
     * 
     * @param parentView 부모 ListView 또는 RecyclerView
     */
    public void loadVisibleImages(ViewGroup parentView) {
        if (parentView == null) return;
        
        for (int i = 0; i < parentView.getChildCount(); i++) {
            View childView = parentView.getChildAt(i);
            if (childView != null && isViewInViewport(childView, parentView)) {
                // 각 child view에서 ImageView를 찾아 로딩
                ImageView appIcon = childView.findViewById(R.id.item_app_icon);
                if (appIcon != null && appIcon.getTag() instanceof Result) {
                    Result<?> result = (Result<?>) appIcon.getTag();
                    if (!result.isDrawableCached()) {
                        result.setAsyncDrawable(appIcon);
                    }
                }
            }
        }
    }
    
    /**
     * 캐시 정리
     */
    public void clearCache() {
        visibleItems.clear();
    }
    
    /**
     * 현재 로딩 상태 정보
     */
    public String getLoadingStatus() {
        return "Visible items: " + visibleItems.size();
    }
}
