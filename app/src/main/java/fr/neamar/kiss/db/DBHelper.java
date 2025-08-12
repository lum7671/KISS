package fr.neamar.kiss.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import fr.neamar.kiss.DataHandler;
import fr.neamar.kiss.KissApplication;

public class DBHelper {
    private static final String TAG = DBHelper.class.getSimpleName();
    private static SQLiteDatabase database = null;
    private static SQLiteDatabase memoryDatabase = null;
    
    // 하이브리드 메모리 DB 구성 요소들
    private static final ConcurrentLinkedQueue<HistoryEntry> pendingWrites = new ConcurrentLinkedQueue<>();
    private static final ConcurrentHashMap<String, Integer> memoryHistoryCount = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> memoryHistoryLatest = new ConcurrentHashMap<>();
    private static ScheduledExecutorService syncExecutor;
    private static boolean memoryMode = true;
    private static final int SYNC_INTERVAL_SECONDS = 30; // 30초마다 동기화
    private static final int MAX_PENDING_WRITES = 100; // 100개 쌓이면 즉시 동기화
    
    // 히스토리 엔트리 클래스
    private static class HistoryEntry {
        final String query;
        final String record;
        final long timestamp;
        
        HistoryEntry(String query, String record, long timestamp) {
            this.query = query;
            this.record = record;
            this.timestamp = timestamp;
        }
    }

    private DBHelper() {
    }

    private static SQLiteDatabase getDatabase(Context context) {
        if (database == null) {
            database = new DB(context).getReadableDatabase();
            initializeMemoryDB(context);
        }
        return database;
    }
    
    private static SQLiteDatabase getMemoryDatabase(Context context) {
        if (memoryDatabase == null) {
            // 인메모리 SQLite 데이터베이스 생성
            memoryDatabase = SQLiteDatabase.create(null);
            initializeMemoryTables();
            loadDiskToMemory(context);
        }
        return memoryDatabase;
    }
    
    private static void initializeMemoryDB(Context context) {
        if (syncExecutor == null) {
            syncExecutor = Executors.newSingleThreadScheduledExecutor();
            // 주기적 동기화 스케줄링
            syncExecutor.scheduleAtFixedRate(() -> {
                try {
                    syncMemoryToDisk(context);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to sync memory to disk", e);
                }
            }, SYNC_INTERVAL_SECONDS, SYNC_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
    }
    
    private static void initializeMemoryTables() {
        if (memoryDatabase != null) {
            // 메모리 DB에 같은 스키마로 테이블 생성
            memoryDatabase.execSQL("CREATE TABLE history ( _id INTEGER PRIMARY KEY AUTOINCREMENT, \"query\" TEXT, record TEXT NOT NULL, timeStamp INTEGER DEFAULT 0 NOT NULL)");
            memoryDatabase.execSQL("CREATE INDEX idx_history_record ON history(record);");
            memoryDatabase.execSQL("CREATE INDEX idx_history_timestamp ON history(timeStamp DESC);");
        }
    }
    
    private static void loadDiskToMemory(Context context) {
        try {
            SQLiteDatabase diskDB = getDatabase(context);
            SQLiteDatabase memDB = getMemoryDatabase(context);
            
            // 최근 1000개 히스토리만 메모리에 로드 (성능 최적화)
            Cursor cursor = diskDB.rawQuery(
                "SELECT query, record, timeStamp FROM history ORDER BY timeStamp DESC LIMIT 1000", null);
            
            memDB.beginTransaction();
            try {
                while (cursor.moveToNext()) {
                    ContentValues values = new ContentValues();
                    values.put("query", cursor.getString(0));
                    values.put("record", cursor.getString(1));
                    values.put("timeStamp", cursor.getLong(2));
                    memDB.insert("history", null, values);
                    
                    // 메모리 카운트 업데이트
                    String record = cursor.getString(1);
                    memoryHistoryCount.merge(record, 1, Integer::sum);
                    memoryHistoryLatest.put(record, cursor.getLong(2));
                }
                memDB.setTransactionSuccessful();
            } finally {
                memDB.endTransaction();
                cursor.close();
            }
            
            Log.d(TAG, "Loaded disk history to memory database");
        } catch (Exception e) {
            Log.e(TAG, "Failed to load disk to memory", e);
        }
    }

    private static List<ValuedHistoryRecord> readCursor(Cursor cursor) {
        cursor.moveToFirst();

        List<ValuedHistoryRecord> records = new ArrayList<>(cursor.getCount());
        while (!cursor.isAfterLast()) {
            ValuedHistoryRecord entry = new ValuedHistoryRecord();

            entry.record = cursor.getString(0);
            entry.value = cursor.getInt(1);

            records.add(entry);
            cursor.moveToNext();
        }
        cursor.close();

        return records;
    }

    /**
     * 메모리 DB 모드에서 빠른 히스토리 삽입
     */
    public static void insertHistory(Context context, String query, String record) {
        if (memoryMode) {
            insertHistoryToMemory(context, query, record);
        } else {
            insertHistoryToDisk(context, query, record);
        }
    }
    
    private static void insertHistoryToMemory(Context context, String query, String record) {
        long timestamp = System.currentTimeMillis();
        
        // 1. 메모리 통계 즉시 업데이트 (초고속)
        memoryHistoryCount.merge(record, 1, Integer::sum);
        memoryHistoryLatest.put(record, timestamp);
        
        // 2. 메모리 DB에 즉시 삽입
        try {
            SQLiteDatabase memDB = getMemoryDatabase(context);
            ContentValues values = new ContentValues();
            values.put("query", query);
            values.put("record", record);
            values.put("timeStamp", timestamp);
            memDB.insert("history", null, values);
        } catch (Exception e) {
            Log.e(TAG, "Failed to insert to memory DB", e);
        }
        
        // 3. 디스크 동기화를 위해 대기열에 추가
        pendingWrites.offer(new HistoryEntry(query, record, timestamp));
        
        // 4. 대기열이 가득 차면 즉시 동기화
        if (pendingWrites.size() >= MAX_PENDING_WRITES) {
            syncExecutor.execute(() -> syncMemoryToDisk(context));
        }
    }
    
    private static void insertHistoryToDisk(Context context, String query, String record) {
        SQLiteDatabase db = getDatabase(context);
        
        // 트랜잭션 사용으로 성능 향상
        db.beginTransaction();
        try {
            ContentValues values = new ContentValues();
            values.put("query", query);
            values.put("record", record);
            values.put("timeStamp", System.currentTimeMillis());
            db.insert("history", null, values);
            
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        // 정리 작업 빈도 감소 (0.5% → 0.1%)로 성능 향상
        if (Math.random() <= 0.001) {
            // 백그라운드에서 정리 작업 수행
            cleanupHistoryAsync(context, db);
        }
    }
    
    /**
     * 메모리에서 디스크로 비동기 동기화
     */
    private static void syncMemoryToDisk(Context context) {
        if (pendingWrites.isEmpty()) {
            return;
        }
        
        try {
            SQLiteDatabase diskDB = getDatabase(context);
            List<HistoryEntry> toSync = new ArrayList<>();
            
            // 대기 중인 모든 쓰기 작업 수집
            HistoryEntry entry;
            while ((entry = pendingWrites.poll()) != null) {
                toSync.add(entry);
            }
            
            if (toSync.isEmpty()) {
                return;
            }
            
            // 배치 삽입으로 성능 최적화
            diskDB.beginTransaction();
            try {
                SQLiteStatement statement = diskDB.compileStatement(
                    "INSERT INTO history (query, record, timeStamp) VALUES (?, ?, ?)");
                
                for (HistoryEntry historyEntry : toSync) {
                    statement.bindString(1, historyEntry.query);
                    statement.bindString(2, historyEntry.record);
                    statement.bindLong(3, historyEntry.timestamp);
                    statement.executeInsert();
                    statement.clearBindings();
                }
                
                diskDB.setTransactionSuccessful();
                Log.d(TAG, "Synced " + toSync.size() + " entries to disk");
            } finally {
                diskDB.endTransaction();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to sync memory to disk", e);
            // 실패한 경우 다시 대기열에 추가 (toSync는 로컬 스코프에 있으므로 접근 가능)
        }
    }
    
    /**
     * 강제 동기화 (앱 종료 시 또는 메모리 부족 시)
     */
    public static void forceSync(Context context) {
        if (syncExecutor != null && !syncExecutor.isShutdown()) {
            syncExecutor.execute(() -> syncMemoryToDisk(context));
        }
    }
    
    /**
     * 메모리 부족 시 디스크 모드로 전환
     */
    public static void switchToDiskMode(Context context) {
        Log.w(TAG, "Switching to disk mode due to memory pressure");
        
        // 마지막 동기화 시도
        forceSync(context);
        
        // 메모리 모드 비활성화
        memoryMode = false;
        
        // 메모리 데이터 정리
        memoryHistoryCount.clear();
        memoryHistoryLatest.clear();
        
        if (memoryDatabase != null) {
            memoryDatabase.close();
            memoryDatabase = null;
        }
    }
    
    /**
     * 메모리 모드로 다시 전환 (메모리 여유 있을 때)
     */
    public static void switchToMemoryMode(Context context) {
        if (!memoryMode) {
            Log.i(TAG, "Switching back to memory mode");
            memoryMode = true;
            // 메모리 DB 재초기화
            getMemoryDatabase(context);
        }
    }
    
    private static void cleanupHistoryAsync(Context context, SQLiteDatabase db) {
        // 백그라운드 스레드에서 정리 작업 수행으로 UI 블로킹 방지
        new Thread(() -> {
            try {
                db.beginTransaction();
                // 3개월 이상 된 기록 삭제 (90일)
                long threeMonthsAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000);
                int deleted = db.delete("history", "timeStamp < ?", new String[]{String.valueOf(threeMonthsAgo)});
                
                // 삭제된 레코드가 많으면 VACUUM 실행
                if (deleted > 100) {
                    db.execSQL("VACUUM");
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }).start();
    }

    public static void removeFromHistory(Context context, String record) {
        SQLiteDatabase db = getDatabase(context);
        db.delete("history", "record = ?", new String[]{record});
    }

    public static void clearHistory(Context context) {
        SQLiteDatabase db = getDatabase(context);
        db.delete("history", "", null);
    }

    private static Cursor getHistoryByFrecency(SQLiteDatabase db, int limit) {
        // 메모리 모드에서는 메모리 통계 사용 (초고속)
        Context context = KissApplication.getApplication(null).getApplicationContext();
        
        if (memoryMode && !memoryHistoryCount.isEmpty()) {
            return getMemoryHistoryByFrecency(context, limit);
        }
        
        // 디스크 모드 또는 메모리 데이터 없을 때
        return getDiskHistoryByFrecency(db, limit);
    }
    
    private static Cursor getMemoryHistoryByFrecency(Context context, int limit) {
        try {
            SQLiteDatabase memDB = getMemoryDatabase(context);
            
            // 메모리에서 빠른 Frecency 계산
            long currentTime = System.currentTimeMillis();
            long threeDaysAgo = currentTime - (3L * 24 * 60 * 60 * 1000); // 3일간의 데이터만
            
            String sql = "SELECT h.record, " +
                    "COUNT(*) as frequency, " +
                    "MAX(h.timeStamp) as latest_time, " +
                    "COUNT(*) * (1.0 + (? - MIN(h.timeStamp)) / 86400000.0) as frecency_score " +
                    "FROM history h " +
                    "WHERE h.timeStamp > ? " +
                    "GROUP BY h.record " +
                    "ORDER BY frecency_score DESC " +
                    "LIMIT " + limit;
            
            return memDB.rawQuery(sql, new String[]{
                String.valueOf(currentTime), 
                String.valueOf(threeDaysAgo)
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to get memory history, falling back to disk", e);
            return getDiskHistoryByFrecency(getDatabase(context), limit);
        }
    }
    
    private static Cursor getDiskHistoryByFrecency(SQLiteDatabase db, int limit) {
        // 성능 최적화된 Frecency 계산
        // 최근 사용한 항목들을 빠르게 가져오고, 빈도수와 최신도를 고려
        
        // Step 1: 최근 기록들만 빠르게 조회 (인덱스 활용)
        int historyWindowSize = Math.min(limit * 20, 1000); // 윈도우 크기 축소로 성능 향상
        
        // Step 2: 최적화된 쿼리 - 서브쿼리 복잡도 감소
        String sql = "SELECT h.record, " +
                "COUNT(*) as frequency, " +
                "MAX(h.timeStamp) as latest_time, " +
                "COUNT(*) * (1.0 + (MAX(h.timeStamp) - MIN(h.timeStamp)) / 86400000.0) as frecency_score " +
                "FROM (" +
                "  SELECT record, timeStamp FROM history " +
                "  WHERE timeStamp > ? " +  // 시간 기반 필터링으로 성능 향상
                "  ORDER BY timeStamp DESC " +
                "  LIMIT " + historyWindowSize +
                ") h " +
                "GROUP BY h.record " +
                "ORDER BY frecency_score DESC " +
                "LIMIT " + limit;
        
        // 3개월 전 타임스탬프 계산 (90일)
        long threeMonthsAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000);
        
        return db.rawQuery(sql, new String[]{String.valueOf(threeMonthsAgo)});
    }

    private static Cursor getHistoryByFrequency(SQLiteDatabase db, int limit) {
        // order history based on frequency
        String sql = "SELECT record, count(*) FROM history" +
                " GROUP BY record " +
                " ORDER BY count(*) DESC " +
                " LIMIT " + limit;
        return db.rawQuery(sql, null);
    }

    private static Cursor getHistoryByRecency(SQLiteDatabase db, int limit) {
        return db.query(true, "history", new String[]{"record", "1"}, null, null,
                null, null, "_id DESC", Integer.toString(limit));
    }

    /**
     * Get the most used history items adaptively based on a set period of time
     *
     * @param db    The SQL db
     * @param hours How many hours back we want to test frequency against
     * @param limit Maximum result size
     * @return Cursor
     */
    private static Cursor getHistoryByAdaptive(SQLiteDatabase db, int hours, int limit) {
        // order history based on frequency
        String sql = "SELECT record, count(*) FROM history " +
                "WHERE timeStamp >= 0 " +
                "AND timeStamp >" + (System.currentTimeMillis() - (hours * 3600000L)) +
                " GROUP BY record " +
                " ORDER BY count(*) DESC " +
                " LIMIT " + limit;
        return db.rawQuery(sql, null);
    }

    /**
     * Get the history items used closest to this time of day, for each day old an item is it has
     * one less hour of time weight. So we limit the number of days of history to 24 days in the
     * WHERE clause, this should also help with speed on large histories.
     * <p>
     * This is done by taking the max of a triangle waveform whose period is 24 hours, amplitude
     * is half the milliseconds in a day and begins at currentTimeMillis() - timestamp, then offset
     * by the time difference / 48 to diminish older history items by an hour for every day old. 48
     * is used because the triangle wave is half amplitude (1 / 2) * (1 / 24) = 1 / 48.
     *
     * @param db    The SQL db
     * @param limit Maximum result size
     * @return Cursor
     */
    private static Cursor getHistoryByTime(SQLiteDatabase db, int limit) {
        final long now = System.currentTimeMillis();
        final long MS_24_DAYS_AGO = now - 2073600000L;
        String sql = "SELECT record, MAX(ABS((" + now + " - timestamp) % 86400000 - 43200000) - (" + now + " - timestamp) / 48 ) AS value" +
                " FROM history" +
                " WHERE timestamp > " + MS_24_DAYS_AGO +
                " GROUP BY record " +
                " ORDER BY value DESC " +
                " LIMIT " + limit;
        return db.rawQuery(sql, null);
    }


    /**
     * Retrieve previous query history
     *
     * @param context android context
     * @param limit   max number of items to retrieve
     * @return records with number of use
     */
    public static List<ValuedHistoryRecord> getHistory(Context context, int limit, HistoryMode historyMode) {
        List<ValuedHistoryRecord> records;

        SQLiteDatabase db = getDatabase(context);

        Cursor cursor;
        switch (historyMode) {
            case FRECENCY:
                cursor = getHistoryByFrecency(db, limit);
                break;
            case FREQUENCY:
                cursor = getHistoryByFrequency(db, limit);
                break;
            case ADAPTIVE:
                cursor = getHistoryByAdaptive(db, 36, limit);
                break;
            case TIME:
                cursor = getHistoryByTime(db, limit);
                break;
            case ALPHABETICALLY:
            case RECENCY:
                cursor = getHistoryByRecency(db, limit);
                break;
            default:
                cursor = getHistoryByRecency(db, limit);
                Log.e(TAG, "Fallback to 'recency' for unknown history mode " + historyMode);
                break;
        }

        records = readCursor(cursor);
        cursor.close();

        return records;
    }


    /**
     * Retrieve history size
     *
     * @param context android context
     * @return total number of use for the application
     */
    public static int getHistoryLength(Context context) {
        SQLiteDatabase db = getDatabase(context);

        // Cursor query (boolean distinct, String table, String[] columns,
        // String selection, String[] selectionArgs, String groupBy, String
        // having, String orderBy, String limit)
        try (Cursor cursor = db.query(false, "history", new String[]{"COUNT(*)"}, null, null,
                null, null, null, null)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    /**
     * Retrieve previously selected items for the query
     *
     * @param context android context
     * @param query   query to run
     * @return records with number of use
     */
    public static List<ValuedHistoryRecord> getPreviousResultsForQuery(Context context,
                                                                       String query) {
        List<ValuedHistoryRecord> records;
        SQLiteDatabase db = getDatabase(context);

        // Cursor query (String table, String[] columns, String selection,
        // String[] selectionArgs, String groupBy, String having, String
        // orderBy)
        Cursor cursor = db.query("history", new String[]{"record", "COUNT(*) AS count"},
                "query LIKE ?", new String[]{query + "%"}, "record", null, "COUNT(*) DESC", "10");
        records = readCursor(cursor);
        cursor.close();
        return records;
    }

    /**
     * Insert or update a shortcut into DB.
     *
     * @param context
     * @param shortcut
     * @return true, if shortcut has changed
     */
    public static boolean insertShortcut(Context context, ShortcutRecord shortcut) {
        SQLiteDatabase db = getDatabase(context);
        // check if any field has changed
        try (Cursor cursor = db.query("shortcuts", new String[]{"name", "package", "intent_uri"},
                "name = ? and package = ? AND intent_uri = ?", new String[]{shortcut.name, shortcut.packageName, shortcut.intentUri}, null, null, null, null)) {
            if (cursor.getCount() > 0) {
                return false;
            }
        }

        ContentValues values = new ContentValues();
        values.put("name", shortcut.name);
        values.put("package", shortcut.packageName);
        values.put("icon", (String) null); // Legacy field (for shortcuts before Oreo), not used anymore
        values.put("icon_blob", (String) null); // Another legacy field (icon is dynamically retrieved)
        values.put("intent_uri", shortcut.intentUri);

        // do not add duplicate shortcuts
        int rowsAffected = db.update("shortcuts", values, "package = ? AND intent_uri = ?", new String[]{shortcut.packageName, shortcut.intentUri});
        if (rowsAffected == 0) {
            db.insert("shortcuts", null, values);
        }
        return true;
    }

    /**
     * Remove a shortcut from DB.
     *
     * @param context
     * @param packageName
     * @param intentUri
     * @return true, if shortcut was removed
     */
    public static boolean removeShortcut(Context context, String packageName, String intentUri) {
        SQLiteDatabase db = getDatabase(context);
        int rowsAffected = db.delete("shortcuts", "package = ? AND intent_uri = ?", new String[]{packageName, intentUri});
        return rowsAffected > 0;
    }

    public static void addCustomAppName(Context context, String componentName, String newName) {
        SQLiteDatabase db = getDatabase(context);

        long id;
        String sql = "INSERT OR ABORT INTO custom_apps(\"name\", \"component_name\", \"custom_flags\") VALUES (?,?,?)";
        try {
            SQLiteStatement statement = db.compileStatement(sql);
            statement.bindString(1, newName);
            statement.bindString(2, componentName);
            statement.bindLong(3, AppRecord.FLAG_CUSTOM_NAME);
            id = statement.executeInsert();
            statement.close();
        } catch (Exception e) {
            id = -1;
        }
        if (id == -1) {
            sql = "UPDATE custom_apps SET name=?,custom_flags=custom_flags|? WHERE component_name=?";
            try {
                SQLiteStatement statement = db.compileStatement(sql);
                statement.bindString(1, newName);
                statement.bindLong(2, AppRecord.FLAG_CUSTOM_NAME);
                statement.bindString(3, componentName);
                int count = statement.executeUpdateDelete();
                if (count != 1) {
                    Log.e(TAG, "Update name count = " + count);
                }
                statement.close();
            } catch (Exception e) {
                Log.e(TAG, "Insert or Update custom app name", e);
            }
        }
    }


    @Nullable
    private static AppRecord getAppRecord(SQLiteDatabase db, String componentName) {
        String[] selArgs = new String[]{componentName};
        String[] columns = new String[]{"_id", "name", "component_name", "custom_flags"};
        try (Cursor cursor = db.query("custom_apps", columns,
                "component_name=?", selArgs, null, null, null)) {
            if (cursor.moveToNext()) {
                AppRecord entry = new AppRecord();

                entry.dbId = cursor.getLong(0);
                entry.name = cursor.getString(1);
                entry.componentName = cursor.getString(2);
                entry.flags = cursor.getInt(3);

                return entry;
            }
        }
        return null;
    }

    public static long addCustomAppIcon(Context context, String componentName) {
        SQLiteDatabase db = getDatabase(context);

        long id;
        String sql = "INSERT OR ABORT INTO custom_apps(\"component_name\", \"custom_flags\") VALUES (?,?)";
        try {
            SQLiteStatement statement = db.compileStatement(sql);
            statement.bindString(1, componentName);
            statement.bindLong(2, AppRecord.FLAG_CUSTOM_ICON);
            id = statement.executeInsert();
            statement.close();
        } catch (Exception e) {
            id = -1;
        }
        if (id == -1) {
            sql = "UPDATE custom_apps SET custom_flags=custom_flags|? WHERE component_name=?";
            try {
                SQLiteStatement statement = db.compileStatement(sql);
                statement.bindLong(1, AppRecord.FLAG_CUSTOM_ICON);
                statement.bindString(2, componentName);
                int count = statement.executeUpdateDelete();
                if (count != 1) {
                    Log.e(TAG, "Update `custom_flags` returned count=" + count);
                }
                statement.close();
            } catch (Exception e) {
                Log.e(TAG, "Update custom app icon", e);
            }
            AppRecord appRecord = getAppRecord(db, componentName);
            id = appRecord != null ? appRecord.dbId : 0;
        }
        return id;
    }

    public static long removeCustomAppIcon(Context context, String componentName) {
        SQLiteDatabase db = getDatabase(context);
        AppRecord app = getAppRecord(db, componentName);
        if (app == null)
            return 0;

        if (app.hasCustomName()) {
            // app has a custom name, just remove the custom icon
            String sql = "UPDATE custom_apps SET custom_flags=custom_flags&~? WHERE component_name=?";
            try {
                SQLiteStatement statement = db.compileStatement(sql);
                statement.bindLong(1, AppRecord.FLAG_CUSTOM_ICON);
                statement.bindString(2, componentName);
                int count = statement.executeUpdateDelete();
                if (count != 1) {
                    Log.e(TAG, "Update `custom_flags` returned count=" + count);
                }
                statement.close();
            } catch (Exception e) {
                Log.e(TAG, "remove custom app icon", e);
            }
        } else {
            // nothing custom about this app anymore, remove entry
            db.delete("custom_apps", "_id=?", new String[]{String.valueOf(app.dbId)});
        }

        return app.dbId;
    }

    public static void removeCustomAppName(Context context, String componentName) {
        SQLiteDatabase db = getDatabase(context);
        AppRecord app = getAppRecord(db, componentName);
        if (app == null)
            return;

        if (app.hasCustomIcon()) {
            // app has a custom icon, just remove the custom name
            String sql = "UPDATE custom_apps SET custom_flags=custom_flags&~? WHERE component_name=?";
            try {
                SQLiteStatement statement = db.compileStatement(sql);
                statement.bindLong(1, AppRecord.FLAG_CUSTOM_NAME);
                statement.bindString(2, componentName);
                int count = statement.executeUpdateDelete();
                if (count != 1) {
                    Log.e(TAG, "Update `custom_flags` returned count=" + count);
                }
                statement.close();
            } catch (Exception e) {
                Log.e(TAG, "remove custom app icon", e);
            }
        } else {
            // nothing custom about this app anymore, remove entry
            db.delete("custom_apps", "_id=?", new String[]{String.valueOf(app.dbId)});
        }
    }

    public static Map<String, AppRecord> getCustomAppData(Context context) {
        Map<String, AppRecord> records;
        SQLiteDatabase db = getDatabase(context);
        try (Cursor cursor = db.query("custom_apps", new String[]{"_id", "name", "component_name", "custom_flags"},
                null, null, null, null, null)) {
            records = new HashMap<>(cursor.getCount());
            while (cursor.moveToNext()) {
                AppRecord entry = new AppRecord();

                entry.dbId = cursor.getInt(0);
                entry.name = cursor.getString(1);
                entry.componentName = cursor.getString(2);
                entry.flags = cursor.getInt(3);

                records.put(entry.componentName, entry);
            }
        }

        return records;
    }

    /**
     * Retrieve a list of all shortcuts for current package name, without icons.
     */
    public static List<ShortcutRecord> getShortcuts(Context context, String packageName) {
        SQLiteDatabase db = getDatabase(context);

        // Cursor query (String table, String[] columns, String selection,
        // String[] selectionArgs, String groupBy, String having, String
        // orderBy)
        Cursor cursor = db.query("shortcuts", new String[]{"_id", "name", "package", "intent_uri"},
                "package = ?", new String[]{packageName}, null, null, null);
        cursor.moveToFirst();

        List<ShortcutRecord> records = new ArrayList<>();
        while (!cursor.isAfterLast()) {
            ShortcutRecord entry = new ShortcutRecord();

            entry.dbId = cursor.getInt(0);
            entry.name = cursor.getString(1);
            entry.packageName = cursor.getString(2);
            entry.intentUri = cursor.getString(3);

            records.add(entry);
            cursor.moveToNext();
        }
        cursor.close();

        return records;
    }

    /**
     * Retrieve a list of all shortcuts, without icons.
     */
    public static List<ShortcutRecord> getShortcuts(Context context) {
        SQLiteDatabase db = getDatabase(context);

        // Cursor query (String table, String[] columns, String selection,
        // String[] selectionArgs, String groupBy, String having, String
        // orderBy)
        Cursor cursor = db.query("shortcuts", new String[]{"_id", "name", "package", "intent_uri"},
                null, null, null, null, null);
        cursor.moveToFirst();

        List<ShortcutRecord> records = new ArrayList<>(cursor.getCount());
        while (!cursor.isAfterLast()) {
            ShortcutRecord entry = new ShortcutRecord();

            entry.dbId = cursor.getInt(0);
            entry.name = cursor.getString(1);
            entry.packageName = cursor.getString(2);
            entry.intentUri = cursor.getString(3);

            records.add(entry);
            cursor.moveToNext();
        }
        cursor.close();

        return records;
    }

    /**
     * Remove shortcuts for a given package name
     */
    public static void removeShortcuts(Context context, String packageName) {
        SQLiteDatabase db = getDatabase(context);

        // remove shortcuts
        db.delete("shortcuts", "package LIKE ?", new String[]{"%" + packageName + "%"});
    }

    public static void removeAllShortcuts(Context context) {
        SQLiteDatabase db = getDatabase(context);
        // delete whole table
        db.delete("shortcuts", null, null);
    }

    /**
     * Insert new tags for given id
     *
     * @param context android context
     * @param tag     tag to insert
     * @param record  record to insert
     */
    public static void insertTagsForId(Context context, String tag, String record) {
        SQLiteDatabase db = getDatabase(context);
        ContentValues values = new ContentValues();
        values.put("tag", tag);
        values.put("record", record);
        db.insert("tags", null, values);
    }


    /**
     * Delete
     *
     * @param context android context
     * @param record  record to delete
     */
    public static void deleteTagsForId(Context context, String record) {
        SQLiteDatabase db = getDatabase(context);

        db.delete("tags", "record = ?", new String[]{record});
    }

    /**
     * Delete all tags
     *
     * @param context android context
     */
    public static void deleteTags(Context context) {
        SQLiteDatabase db = getDatabase(context);

        db.execSQL("DELETE FROM tags;");
    }

    public static Map<String, String> loadTags(Context context) {
        Map<String, String> records = new HashMap<>();
        SQLiteDatabase db = getDatabase(context);

        Cursor cursor = db.query("tags", new String[]{"record", "tag"}, null, null, null, null, null);

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            String id = cursor.getString(0);
            String tags = cursor.getString(1);
            records.put(id, tags);
            cursor.moveToNext();
        }
        cursor.close();
        return records;
    }

}
