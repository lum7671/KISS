package fr.neamar.kiss.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.preference.SwitchPreference;
import android.util.AttributeSet;

import fr.neamar.kiss.KissApplication;
import fr.neamar.kiss.R;

/**
 * Shizuku 모드 설정을 위한 스위치 컴포넌트
 */
public class ShizukuModeSwitch extends SwitchPreference {
    
    public ShizukuModeSwitch(Context context) {
        this(context, null);
    }

    public ShizukuModeSwitch(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.switchPreferenceStyle);
    }

    public ShizukuModeSwitch(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onClick() {
        if (!isChecked()) {
            // Shizuku 모드를 켜려고 할 때
            if (!KissApplication.getApplication(getContext()).getRootHandler().isShizukuAvailable()) {
                // Shizuku가 사용 불가능한 경우 오류 다이얼로그 표시
                new AlertDialog.Builder(getContext())
                    .setMessage(R.string.shizuku_mode_error)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // 아무것도 하지 않음
                        }
                    }).show();
                return;
            } else if (!KissApplication.getApplication(getContext()).getRootHandler().hasShizukuPermission()) {
                // Shizuku 권한이 없는 경우 권한 요청
                KissApplication.getApplication(getContext()).getRootHandler().requestShizukuPermission();
                
                new AlertDialog.Builder(getContext())
                    .setMessage(R.string.shizuku_permission_request)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // 권한 요청 후 다시 시도
                            if (KissApplication.getApplication(getContext()).getRootHandler().hasShizukuPermission()) {
                                ShizukuModeSwitch.super.onClick();
                            }
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
                return;
            }
        }
        
        super.onClick();

        try {
            KissApplication.getApplication(getContext()).resetRootHandler(getContext());
        } catch (NullPointerException e) {
            // 초기화되지 않은 핸들러
        }
    }
}
