package fr.neamar.kiss.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.preference.PreferenceViewHolder;

/**
 * AndroidX 기반 SwitchPreference
 * 기존 SwitchPreference.java와 동일한 기능 제공
 * - 요약 텍스트 최대 10줄 제한
 * 
 * 배경:
 * - Android KitKat 이하에서 요약 텍스트 줄 수가 일관되지 않은 문제 해결
 * - 참고: https://code.google.com/p/android/issues/detail?id=26194
 * 
 * 마이그레이션 노트:
 * - 기존 android.preference.SwitchPreference에서 androidx.preference.SwitchPreferenceCompat으로 전환
 * - Phase 6 Step 1: 베이스 클래스로 생성
 * - 기존 SwitchPreference.java와 병렬 존재 (마이그레이션 완료 후 구버전 제거 예정)
 * 
 * @see fr.neamar.kiss.preference.SwitchPreference (구버전, android.preference 기반)
 */
public class SwitchPreferenceCompat extends androidx.preference.SwitchPreferenceCompat {

    public SwitchPreferenceCompat(Context context) {
        this(context, null);
    }

    public SwitchPreferenceCompat(Context context, AttributeSet attrs) {
        this(context, attrs, androidx.preference.R.attr.switchPreferenceCompatStyle);
    }

    public SwitchPreferenceCompat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public SwitchPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        // 요약 텍스트 최대 10줄로 제한
        // 기존 SwitchPreference.java와 동일한 동작
        View summary = holder.findViewById(android.R.id.summary);
        if (summary instanceof TextView) {
            ((TextView) summary).setMaxLines(10);
        }
    }
}
