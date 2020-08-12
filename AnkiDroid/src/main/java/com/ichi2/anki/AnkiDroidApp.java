/****************************************************************************************
 * Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>                                   *
 * Copyright (c) 2009 Casey Link <unnamedrambler@gmail.com>                             *
 * Copyright (c) 2014 Timothy Rae <perceptualchaos2@gmail.com>                          *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package com.ichi2.anki;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Environment;
import android.os.LocaleList;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;
import android.view.ViewConfiguration;
import android.webkit.CookieManager;

import com.ichi2.anki.contextmenu.CardBrowserContextMenu;
import com.ichi2.anki.exception.ManuallyReportedException;
import com.ichi2.anki.exception.StorageAccessException;
import com.ichi2.anki.services.BootService;
import com.ichi2.anki.services.NotificationService;
import com.ichi2.compat.CompatHelper;
import com.ichi2.utils.LanguageUtil;
import com.ichi2.anki.analytics.UsageAnalytics;
import com.ichi2.utils.Permissions;
import com.ichi2.utils.WebViewDebugging;

import java.io.InputStream;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.multidex.MultiDexApplication;
import timber.log.Timber;
import static timber.log.Timber.DebugTree;

/**
 * Application class.
 */
public class AnkiDroidApp extends MultiDexApplication {

    public static final String XML_CUSTOM_NAMESPACE = "http://arbitrary.app.namespace/com.ichi2.anki";

    // ACRA constants used for stored preferences
    public static final String FEEDBACK_REPORT_KEY = "reportErrorMode";
    public static final String FEEDBACK_REPORT_ASK = "2";
    public static final String FEEDBACK_REPORT_NEVER = "1";
    public static final String FEEDBACK_REPORT_ALWAYS = "0";

    // Tag for logging messages.
    public static final String TAG = "AnkiDroid";
    // Singleton instance of this class.
    private static AnkiDroidApp sInstance;
    // Constants for gestures
    public static int sSwipeMinDistance = -1;
    public static int sSwipeThresholdVelocity = -1;
    private static int DEFAULT_SWIPE_MIN_DISTANCE;
    private static int DEFAULT_SWIPE_THRESHOLD_VELOCITY;

    /**
     * The latest package version number that included important changes to the database integrity check routine. All
     * collections being upgraded to (or after) this version must run an integrity check as it will contain fixes that
     * all collections should have.
     */
    public static final int CHECK_DB_AT_VERSION = 0;

    /**
     * The latest package version number that included changes to the preferences that requires handling. All
     * collections being upgraded to (or after) this version must update preferences.
     */
    public static final int CHECK_PREFERENCES_AT_VERSION = 0;


    @NonNull
    public static InputStream getResourceAsStream(@NonNull String name) {
        return sInstance.getApplicationContext().getClassLoader().getResourceAsStream(name);
    }

    @Override
    protected void attachBaseContext(Context base) {
        //update base context with preferred app language before attach
        //possible since API 17, only supported way since API 25
        //for API < 17 we update the configuration directly
        super.attachBaseContext(updateContextWithLanguage(base));
    }

    /**
     * On application creation.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        if (sInstance != null) {
            Timber.i("onCreate() called multiple times");
            //5887 - fix crash.
            if (sInstance.getResources() == null) {
                Timber.w("Skipping re-initialisation - no resources. Maybe uninstalling app?");
                return;
            }
        }
        sInstance = this;
        // Get preferences
        SharedPreferences preferences = getSharedPrefs(this);

        if (BuildConfig.DEBUG) {
            // Enable verbose error logging and do method tracing to put the Class name as log tag
            Timber.plant(new DebugTree());
        } else {
            Timber.plant(new ProductionCrashReportingTree());
        }
        Timber.tag(TAG);

        Timber.d("Startup - Application Start");

        // analytics after ACRA, they both install UncaughtExceptionHandlers but Analytics chains while ACRA does not
        UsageAnalytics.initialize(this);
        if (BuildConfig.DEBUG) {
            UsageAnalytics.setDryRun(true);
        }

        CardBrowserContextMenu.ensureConsistentStateWithSharedPreferences(this);
        NotificationChannels.setup(getApplicationContext());

        // Configure WebView to allow file scheme pages to access cookies.
        CookieManager.setAcceptFileSchemeCookies(true);

        // Prepare Cookies to be synchronized between RAM and permanent storage.
        CompatHelper.getCompat().prepareWebViewCookies(this.getApplicationContext());

        // Set good default values for swipe detection
        final ViewConfiguration vc = ViewConfiguration.get(this);
        DEFAULT_SWIPE_MIN_DISTANCE = vc.getScaledPagingTouchSlop();
        DEFAULT_SWIPE_THRESHOLD_VELOCITY = vc.getScaledMinimumFlingVelocity();

        // Forget the last deck that was used in the CardBrowser
        CardBrowser.clearLastDeckId();

        // Create the AnkiDroid directory if missing. Send exception report if inaccessible.
        if (Permissions.hasStorageAccessPermission(this)) {
            try {
                String dir = CollectionHelper.getCurrentAnkiDroidDirectory(this);
                CollectionHelper.initializeAnkiDroidDirectory(dir);
            } catch (StorageAccessException e) {
                Timber.e(e, "Could not initialize AnkiDroid directory");
                String defaultDir = CollectionHelper.getDefaultAnkiDroidDirectory();
                if (isSdCardMounted() && CollectionHelper.getCurrentAnkiDroidDirectory(this).equals(defaultDir)) {
                    // Don't send report if the user is using a custom directory as SD cards trip up here a lot
                    sendExceptionReport(e, "AnkiDroidApp.onCreate");
                }
            }
        }

        Timber.i("AnkiDroidApp: Starting Services");
        new BootService().onReceive(this, new Intent(this, BootService.class));

        // Register BroadcastReceiver NotificationService
        NotificationService ns = new NotificationService();
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        lbm.registerReceiver(ns, new IntentFilter(NotificationService.INTENT_ACTION));
    }


    /**
     * Convenience method for accessing Shared preferences
     *
     * @param context Context to get preferences for.
     * @return A SharedPreferences object for this instance of the app.
     */
    public static SharedPreferences getSharedPrefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }


    public static AnkiDroidApp getInstance() {
        return sInstance;
    }


    public static String getCacheStorageDirectory() {
        return sInstance.getCacheDir().getAbsolutePath();
    }

    public static Resources getAppResources() {
        return sInstance.getResources();
    }


    public static boolean isSdCardMounted() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /** Used when we don't have an exception to throw, but we know something is wrong and want to diagnose it */
    public static void sendExceptionReport(@NonNull String message, String origin) {
        sendExceptionReport(new ManuallyReportedException(message), origin, null);
    }

    public static void sendExceptionReport(Throwable e, String origin) {
        sendExceptionReport(e, origin, null);
    }


    public static void sendExceptionReport(Throwable e, String origin, String additionalInfo) {
        sendExceptionReport(e, origin, additionalInfo, false);
    }


    public static void sendExceptionReport(Throwable e, String origin, String additionalInfo, boolean onlyIfSilent) {
        UsageAnalytics.sendAnalyticsException(e, false);
    }

    /**
     *  Returns a Context with the correct, saved language, to be attached using attachBase().
     *  For old APIs directly sets language using deprecated functions
     *
     * @param remoteContext The base context offered by attachBase() to be passed to super.attachBase().
     *                      Can be modified here to set correct GUI language.
     */
    @SuppressWarnings("deprecation")
    @NonNull
    public static Context updateContextWithLanguage(@NonNull Context remoteContext) {
        try {
            SharedPreferences preferences;
            //sInstance (returned by getInstance() ) set during application OnCreate()
            //if getInstance() is null, the method is called during applications attachBaseContext()
            // and preferences need mBase directly (is provided by remoteContext during attachBaseContext())
            if (getInstance() != null) {
                preferences = getSharedPrefs(getInstance().getBaseContext());
            } else {
                preferences = getSharedPrefs(remoteContext);
            }
            Configuration langConfig = getLanguageConfig(remoteContext.getResources().getConfiguration(), preferences);
            //API level >= 25: supported since API 17
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                return remoteContext.createConfigurationContext(langConfig);
            } else {
                //API level < 25:
                remoteContext.getResources().updateConfiguration(langConfig, remoteContext.getResources().getDisplayMetrics());
                return remoteContext;
            }
        } catch (Exception e) {
            Timber.e(e, "failed to update context with new language");
            //during AnkiDroidApp.attachBaseContext() ACRA is not initialized, so the exception report will not be sent
            sendExceptionReport(e,"AnkiDroidApp.updateContextWithLanguage");
            return remoteContext;
        }
    }

    /**
     *  Creates and returns a new configuration with the chosen GUI language that is saved in the preferences
     *
     * @param remoteConfig The configuration of the remote context to set the language for
     * @param prefs
     */
    @SuppressWarnings("deprecation")
    @NonNull
    private static Configuration getLanguageConfig(@NonNull Configuration remoteConfig, @NonNull SharedPreferences prefs) {
        Configuration newConfig = new Configuration(remoteConfig);
        Locale newLocale = LanguageUtil.getLocale(prefs.getString(Preferences.LANGUAGE, ""), prefs);
        Timber.d("AnkiDroidApp::getLanguageConfig - setting locale to %s", newLocale);
        //API level >=24
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            //Build list of locale strings, separated by commas: newLocale as first element
            String strLocaleList = newLocale.toLanguageTag();
            //if Anki locale from settings is no equal to system default, add system default as second item
            //LocaleList must not contain language tags twice, will crash otherwise!
            if (!strLocaleList.contains(Locale.getDefault().toLanguageTag())) {
                strLocaleList = strLocaleList + "," + Locale.getDefault().toLanguageTag();
            }

            LocaleList newLocaleList = LocaleList.forLanguageTags(strLocaleList);
            //first element of setLocales() is automatically setLocal()
            newConfig.setLocales(newLocaleList);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                //API level >=17 but <24
                newConfig.setLocale(newLocale);
            } else {
                //Legacy, API level <17
                newConfig.locale = newLocale;
            }
        }

        return newConfig;
    }


    public static boolean initiateGestures(SharedPreferences preferences) {
        boolean enabled = preferences.getBoolean("gestures", false);
        if (enabled) {
            int sensitivity = preferences.getInt("swipeSensitivity", 100);
            if (sensitivity != 100) {
                float sens = 100.0f/sensitivity;
                sSwipeMinDistance = (int) (DEFAULT_SWIPE_MIN_DISTANCE * sens + 0.5f);
                sSwipeThresholdVelocity = (int) (DEFAULT_SWIPE_THRESHOLD_VELOCITY * sens  + 0.5f);
            } else {
                sSwipeMinDistance = DEFAULT_SWIPE_MIN_DISTANCE;
                sSwipeThresholdVelocity = DEFAULT_SWIPE_THRESHOLD_VELOCITY;
            }
        }
        return enabled;
    }

    /**
     * Get the url for the feedback page
     */
    public static String getFeedbackUrl() {
        return "https://david-allison-1.github.io/ankidroid-mia/feedback";
    }

    /**
     * Get the url for the manual
     */
    public static String getManualUrl() {
        return "https://david-allison-1.github.io/ankidroid-mia/manual";
    }

    public static String getPatreonUrl() {
        return "https://www.patreon.com/DavidAllison";
    }


    public static String getBackersUrl() {
        return "https://david-allison-1.github.io/ankidroid-mia/backers";
    }

    /**
     * Check whether l is the currently set language code
     * @param l ISO2 language code
     * @return
     */
    private static boolean isCurrentLanguage(String l) {
        String pref = getSharedPrefs(sInstance).getString(Preferences.LANGUAGE, "");
        return pref.equals(l) || "".equals(pref) && Locale.getDefault().getLanguage().equals(l);
    }

    /**
     * A tree which logs necessary data for crash reporting.
     *
     * Requirements:
     * 1) ignore verbose and debug log levels
     * 2) use the fixed AnkiDroidApp.TAG log tag (ACRA filters logcat for it when reporting errors)
     * 3) dynamically discover the class name and prepend it to the message for warn and error
     */
    @SuppressLint("LogNotTimber")
    public static class ProductionCrashReportingTree extends Timber.Tree {

        // ----  BEGIN copied from Timber.DebugTree because DebugTree.getTag() is package private ----

        private static final int CALL_STACK_INDEX = 6;
        private static final Pattern ANONYMOUS_CLASS = Pattern.compile("(\\$\\d+)+$");


        /**
         * Extract the tag which should be used for the message from the {@code element}. By default
         * this will use the class name without any anonymous class suffixes (e.g., {@code Foo$1}
         * becomes {@code Foo}).
         * <p>
         * Note: This will not be called if an API with a manual tag was called with a non-null tag
         */
        @Nullable
        String createStackElementTag(@NonNull StackTraceElement element) {
            String tag = element.getClassName();
            Matcher m = ANONYMOUS_CLASS.matcher(tag);
            if (m.find()) {
                tag = m.replaceAll("");
            }
            return tag.substring(tag.lastIndexOf('.') + 1);
        }


        final String getTag() {

            // DO NOT switch this to Thread.getCurrentThread().getStackTrace(). The test will pass
            // because Robolectric runs them on the JVM but on Android the elements are different.
            StackTraceElement[] stackTrace = new Throwable().getStackTrace();
            if (stackTrace.length <= CALL_STACK_INDEX) {

                // --- this is not present in the Timber.DebugTree copy/paste ---
                // We are in production and should not crash the app for a logging failure
                return TAG + " unknown class";
                //throw new IllegalStateException(
                //        "Synthetic stacktrace didn't have enough elements: are you using proguard?");
                // --- end of alteration from upstream Timber.DebugTree.getTag ---
            }
            return createStackElementTag(stackTrace[CALL_STACK_INDEX]);
        }
        // ----  END copied from Timber.DebugTree because DebugTree.getTag() is package private ----



        @Override
        protected void log(int priority, String tag, @NonNull String message, Throwable t) {

            switch (priority) {
                case Log.VERBOSE:
                case Log.DEBUG:
                    break;

                case Log.INFO:
                    Log.i(AnkiDroidApp.TAG, message, t);
                    break;

                case Log.WARN:
                    Log.w(AnkiDroidApp.TAG, getTag() + "/ " + message, t);
                    break;

                case Log.ERROR:
                case Log.ASSERT:
                    Log.e(AnkiDroidApp.TAG, getTag() + "/ " + message, t);
                    break;
            }
        }
    }

}
