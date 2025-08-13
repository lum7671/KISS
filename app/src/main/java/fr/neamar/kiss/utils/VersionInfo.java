package fr.neamar.kiss.utils;

import fr.neamar.kiss.BuildConfig;

/**
 * 앱 버전 정보를 관리하는 유틸리티 클래스
 */
public class VersionInfo {
    
    /**
     * 현재 최적화된 버전
     */
    public static String getOptimizedVersion() {
        return BuildConfig.VERSION_NAME;
    }
    
    /**
     * 업스트림 원저자 버전
     */
    public static String getUpstreamVersion() {
        return BuildConfig.UPSTREAM_VERSION;
    }
    
    /**
     * 업스트림 버전 코드
     */
    public static String getUpstreamVersionCode() {
        return BuildConfig.UPSTREAM_VERSION_CODE;
    }
    
    /**
     * 최적화 작업자
     */
    public static String getOptimizedBy() {
        return BuildConfig.OPTIMIZED_BY;
    }
    
    /**
     * 빌드 날짜
     */
    public static String getBuildDate() {
        return BuildConfig.BUILD_DATE;
    }
    
    /**
     * 빌드 타입 (release, debug, profile)
     */
    public static String getBuildType() {
        return BuildConfig.BUILD_TYPE;
    }
    
    /**
     * 전체 버전 정보 문자열
     */
    public static String getFullVersionInfo() {
        return String.format("KISS Optimized %s (%s)\nBased on upstream %s (versionCode: %s)\nOptimized by: %s\nBuild date: %s",
                getOptimizedVersion(),
                getBuildType().toUpperCase(),
                getUpstreamVersion(),
                getUpstreamVersionCode(),
                getOptimizedBy(),
                getBuildDate());
    }
    
    /**
     * 간단한 버전 정보 (설정 화면용)
     */
    public static String getSimpleVersionInfo() {
        return String.format("v%s (%s) (based on upstream %s)", 
                getOptimizedVersion(), 
                getBuildType().toUpperCase(),
                getUpstreamVersion());
    }
}
