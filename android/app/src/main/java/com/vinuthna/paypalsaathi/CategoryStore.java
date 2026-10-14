package com.vinuthna.paypalsaathi;

import android.content.Context;
import android.content.SharedPreferences;

import com.vinuthna.paypalsaathi.detection.ScreenCategories;

/**
 * Persists the last detected screen category so guidance can be shown
 * after the user returns from PayPal (receiver may have been paused).
 * Stores only the category ID — never raw screen text.
 */
public final class CategoryStore {
    private static final String PREFS = "saathi_screen_state";
    private static final String KEY_CATEGORY = "last_category";
    private static final String KEY_EVENT = "last_event";
    private static final String KEY_UPDATED_AT = "updated_at";

    private CategoryStore() {}

    public static void save(Context context, String category, String eventType) {
        String safeCategory = category == null || category.trim().isEmpty()
                ? ScreenCategories.UNKNOWN
                : category.trim();

        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CATEGORY, safeCategory)
                .putString(KEY_EVENT, eventType == null ? "UI event" : eventType)
                .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
                .apply();
    }

    public static void clear(Context context) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_CATEGORY)
                .remove(KEY_EVENT)
                .remove(KEY_UPDATED_AT)
                .apply();
    }

    public static String lastCategory(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_CATEGORY, ScreenCategories.UNKNOWN);
    }

    public static String lastEvent(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_EVENT, "");
    }

    public static long lastUpdatedAt(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(KEY_UPDATED_AT, 0L);
    }
}
