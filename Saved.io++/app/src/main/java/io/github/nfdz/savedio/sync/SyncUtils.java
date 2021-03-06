/*
 * Copyright (C) 2017 Noe Fernandez
 */
package io.github.nfdz.savedio.sync;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.firebase.jobdispatcher.Constraint;
import com.firebase.jobdispatcher.Driver;
import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.Lifetime;
import com.firebase.jobdispatcher.Trigger;

import java.util.concurrent.TimeUnit;

import io.github.nfdz.savedio.Callbacks;
import io.github.nfdz.savedio.data.PreferencesUtils;
import io.github.nfdz.savedio.model.SyncResult;
import io.realm.Realm;

public class SyncUtils {

    public static final long SYNC_INTERVAL_HOURS = 24;
    public static final long SYNC_INTERVAL_SECONDS = TimeUnit.HOURS.toSeconds(SYNC_INTERVAL_HOURS);
    public static final long SYNC_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(SYNC_INTERVAL_HOURS);
    public static final long SYNC_FLEXTIME_SECONDS = SYNC_INTERVAL_SECONDS / 3;

    private static final String SYNC_TAG = "savediopp-sync";

    private static boolean sInitialized = false;

    /**
     * Initializes all application tasks related with synchronization. It will execute only once.
     * @param context
     * @param realm
     */
    synchronized public static void initialize(@NonNull final Context context, @NonNull Realm realm) {

        if (sInitialized) return;
        sInitialized = true;

        // initialize sync result object
        SyncResult result = realm.where(SyncResult.class).findFirst();
        if (result == null) {
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    realm.createObject(SyncResult.class);
                }
            });
        }

        scheduleFirebaseJobDispatcherSync(context);

        // check if it is necessary to perform synchronization now
        PreferencesUtils.retrieveLastSyncTime(context, new Callbacks.FinishCallback<Long>() {
            @Override
            public void onFinish(Long lastSync) {
                long now = System.currentTimeMillis();
                if (now - lastSync > SYNC_INTERVAL_MILLIS) {
                    String userKey = PreferencesUtils.getUserAPIKey(context);
                    if (!TextUtils.isEmpty(userKey)) {
                        SyncUtils.startImmediateSync(context);
                    }
                }
            }
        });
    }

    /**
     * Starts synchronization service immediately.
     * @param context
     */
    public static void startImmediateSync(@NonNull final Context context) {
        Intent intentToSync = new Intent(context, SyncIntentService.class);
        context.startService(intentToSync);
    }

    static void scheduleFirebaseJobDispatcherSync(@NonNull final Context context) {
        Driver driver = new GooglePlayDriver(context);
        FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(driver);

        Job syncJob = dispatcher.newJobBuilder()
                .setService(SyncFirebaseJobService.class)
                .setTag(SYNC_TAG)
                .setConstraints(Constraint.ON_ANY_NETWORK)
                .setLifetime(Lifetime.FOREVER)
                .setRecurring(true)
                .setTrigger(Trigger.executionWindow(
                        (int) SYNC_INTERVAL_SECONDS,
                        (int) SYNC_INTERVAL_SECONDS + (int) SYNC_FLEXTIME_SECONDS))
                .setReplaceCurrent(true)
                .build();

        dispatcher.schedule(syncJob);
    }
}
