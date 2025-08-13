package fr.neamar.kiss.controller;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.util.Log;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import fr.neamar.kiss.MainActivity;
import fr.neamar.kiss.R;
import fr.neamar.kiss.UIColors;

/**
 * UI 상태 및 애니메이션을 관리하는 컨트롤러
 */
public class UIController {
    
    private static final String TAG = UIController.class.getSimpleName();
    
    private final MainActivity mainActivity;
    private final SharedPreferences prefs;
    
    // UI 상태
    private boolean isDisplayingKissBar = false;
    private boolean isKeyboardVisible = false;
    private boolean isMinimalisticMode = false;
    
    // UI 리스너 인터페이스
    public interface UIStateListener {
        void onKissBarVisibilityChanged(boolean visible);
        void onKeyboardVisibilityChanged(boolean visible);
        void onThemeChanged();
        void onLayoutChanged();
    }
    
    private final List<UIStateListener> listeners = new ArrayList<>();
    
    public UIController(@NonNull MainActivity mainActivity, @NonNull SharedPreferences prefs) {
        this.mainActivity = mainActivity;
        this.prefs = prefs;
        this.isMinimalisticMode = prefs.getBoolean("minimalistic-ui", false);
    }
    
    /**
     * KISS 바를 표시합니다.
     *
     * @param animateSearch 검색 애니메이션 여부
     */
    public void showKissBar(boolean animateSearch) {
        if (isDisplayingKissBar) {
            Log.d(TAG, "KISS bar already visible");
            return;
        }
        
        Log.d(TAG, "Showing KISS bar with animation: " + animateSearch);
        isDisplayingKissBar = true;
        
        View kissBar = mainActivity.kissBar;
        if (kissBar != null) {
            kissBar.setVisibility(View.VISIBLE);
            
            if (animateSearch && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                animateKissBarReveal(kissBar);
            } else {
                // 즉시 표시
                kissBar.setAlpha(1.0f);
                onKissBarAnimationEnd();
            }
        }
        
        // UI 업데이트
        updateFavoritesBar();
        updateButtonStates();
        notifyKissBarVisibilityChanged(true);
    }
    
    /**
     * KISS 바를 숨깁니다.
     */
    public void hideKissBar() {
        if (!isDisplayingKissBar) {
            Log.d(TAG, "KISS bar already hidden");
            return;
        }
        
        Log.d(TAG, "Hiding KISS bar");
        isDisplayingKissBar = false;
        
        View kissBar = mainActivity.kissBar;
        if (kissBar != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                animateKissBarHide(kissBar);
            } else {
                // 즉시 숨김
                kissBar.setVisibility(View.GONE);
                onKissBarAnimationEnd();
            }
        }
        
        // UI 업데이트
        updateFavoritesBar();
        updateButtonStates();
        notifyKissBarVisibilityChanged(false);
    }
    
    /**
     * 키보드 표시 상태를 설정합니다.
     *
     * @param visible 키보드 표시 여부
     */
    public void setKeyboardVisible(boolean visible) {
        if (isKeyboardVisible != visible) {
            isKeyboardVisible = visible;
            Log.d(TAG, "Keyboard visibility changed: " + visible);
            notifyKeyboardVisibilityChanged(visible);
        }
    }
    
    /**
     * 테마를 업데이트합니다.
     */
    public void updateTheme() {
        Log.d(TAG, "Updating theme");
        
        // 색상 업데이트
        updateColorScheme();
        
        // 아이콘 틴트 업데이트
        updateIconTints();
        
        // 배경 업데이트
        updateBackgrounds();
        
        notifyThemeChanged();
    }
    
    /**
     * 레이아웃을 업데이트합니다.
     */
    public void updateLayout() {
        Log.d(TAG, "Updating layout");
        
        // 미니멀리스틱 모드 확인
        boolean newMinimalisticMode = prefs.getBoolean("minimalistic-ui", false);
        if (isMinimalisticMode != newMinimalisticMode) {
            isMinimalisticMode = newMinimalisticMode;
            Log.d(TAG, "Minimalistic mode changed: " + isMinimalisticMode);
        }
        
        // 버튼 레이아웃 업데이트
        updateButtonLayout();
        
        // 검색바 레이아웃 업데이트
        updateSearchBarLayout();
        
        notifyLayoutChanged();
    }
    
    // 애니메이션 메서드들
    private void animateKissBarReveal(@NonNull View kissBar) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                int cx = kissBar.getWidth() / 2;
                int cy = kissBar.getHeight() / 2;
                float finalRadius = (float) Math.hypot(cx, cy);
                
                Animator anim = ViewAnimationUtils.createCircularReveal(kissBar, cx, cy, 0, finalRadius);
                anim.setDuration(300);
                anim.setInterpolator(new AccelerateDecelerateInterpolator());
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        onKissBarAnimationEnd();
                    }
                });
                anim.start();
            } catch (Exception e) {
                Log.w(TAG, "Error in reveal animation", e);
                kissBar.setAlpha(1.0f);
                onKissBarAnimationEnd();
            }
        }
    }
    
    private void animateKissBarHide(@NonNull View kissBar) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            try {
                int cx = kissBar.getWidth() / 2;
                int cy = kissBar.getHeight() / 2;
                float initialRadius = (float) Math.hypot(cx, cy);
                
                Animator anim = ViewAnimationUtils.createCircularReveal(kissBar, cx, cy, initialRadius, 0);
                anim.setDuration(300);
                anim.setInterpolator(new AccelerateDecelerateInterpolator());
                anim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        kissBar.setVisibility(View.GONE);
                        onKissBarAnimationEnd();
                    }
                });
                anim.start();
            } catch (Exception e) {
                Log.w(TAG, "Error in hide animation", e);
                kissBar.setVisibility(View.GONE);
                onKissBarAnimationEnd();
            }
        }
    }
    
    private void onKissBarAnimationEnd() {
        // 애니메이션 완료 후 처리
        Log.d(TAG, "KISS bar animation completed");
    }
    
    // UI 업데이트 메서드들
    private void updateColorScheme() {
        int primaryColor = UIColors.getPrimaryColor(mainActivity);
        if (primaryColor != UIColors.COLOR_DEFAULT) {
            // 런처 버튼 색상 업데이트
            ImageView launcherButton = mainActivity.findViewById(R.id.launcherButton);
            if (launcherButton != null) {
                launcherButton.setColorFilter(primaryColor);
            }
            
            // KISS 바 배경 색상 업데이트
            if (mainActivity.kissBar != null) {
                mainActivity.kissBar.getBackground().mutate().setColorFilter(primaryColor, 
                    android.graphics.PorterDuff.Mode.SRC_IN);
            }
        }
    }
    
    private void updateIconTints() {
        // 아이콘 틴트 업데이트 로직
        Log.d(TAG, "Updating icon tints");
    }
    
    private void updateBackgrounds() {
        // 배경 업데이트 로직
        Log.d(TAG, "Updating backgrounds");
    }
    
    private void updateButtonLayout() {
        // 버튼 레이아웃 업데이트 로직
        Log.d(TAG, "Updating button layout");
    }
    
    private void updateSearchBarLayout() {
        // 검색바 레이아웃 업데이트 로직
        Log.d(TAG, "Updating search bar layout");
    }
    
    private void updateFavoritesBar() {
        // 즐겨찾기 바 업데이트 로직
        Log.d(TAG, "Updating favorites bar");
    }
    
    private void updateButtonStates() {
        // 버튼 상태 업데이트 로직
        Log.d(TAG, "Updating button states");
    }
    
    // Getter 메서드들
    public boolean isDisplayingKissBar() {
        return isDisplayingKissBar;
    }
    
    public boolean isKeyboardVisible() {
        return isKeyboardVisible;
    }
    
    public boolean isMinimalisticMode() {
        return isMinimalisticMode;
    }
    
    // 리스너 관리
    public void addUIStateListener(@NonNull UIStateListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    public void removeUIStateListener(@NonNull UIStateListener listener) {
        listeners.remove(listener);
    }
    
    public void clearUIStateListeners() {
        listeners.clear();
    }
    
    // 알림 메서드들
    private void notifyKissBarVisibilityChanged(boolean visible) {
        for (UIStateListener listener : listeners) {
            try {
                listener.onKissBarVisibilityChanged(visible);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying KISS bar visibility change", e);
            }
        }
    }
    
    private void notifyKeyboardVisibilityChanged(boolean visible) {
        for (UIStateListener listener : listeners) {
            try {
                listener.onKeyboardVisibilityChanged(visible);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying keyboard visibility change", e);
            }
        }
    }
    
    private void notifyThemeChanged() {
        for (UIStateListener listener : listeners) {
            try {
                listener.onThemeChanged();
            } catch (Exception e) {
                Log.e(TAG, "Error notifying theme change", e);
            }
        }
    }
    
    private void notifyLayoutChanged() {
        for (UIStateListener listener : listeners) {
            try {
                listener.onLayoutChanged();
            } catch (Exception e) {
                Log.e(TAG, "Error notifying layout change", e);
            }
        }
    }
    
    /**
     * 리소스 정리
     */
    public void cleanup() {
        clearUIStateListeners();
        Log.d(TAG, "UIController cleaned up");
    }
}
