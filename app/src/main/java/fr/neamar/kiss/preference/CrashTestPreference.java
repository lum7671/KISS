package fr.neamar.kiss.preference;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.content.DialogInterface;
import android.util.Log;

/**
 * 크래시 리포팅 시스템 테스트용 Preference
 * 개발자 전용 - 프로덕션에서는 제거 필요
 */
@SuppressWarnings("deprecation")
public class CrashTestPreference extends DialogPreference {

    public CrashTestPreference(Context context) {
        super(context, null);
    }

    public CrashTestPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        super.onClick(dialog, which);
        if (which == DialogInterface.BUTTON_POSITIVE) {
            // 의도적으로 크래시 발생시켜 이메일 전송 테스트
            Log.i("KISS_CRASH_TEST", "Intentionally triggering crash for email testing");
            
            // 다양한 크래시 시나리오 테스트
            triggerTestCrash();
        }
    }
    
    private void triggerTestCrash() {
        // NullPointerException 발생
        String testString = null;
        int length = testString.length(); // 이 줄에서 크래시 발생
    }
}
