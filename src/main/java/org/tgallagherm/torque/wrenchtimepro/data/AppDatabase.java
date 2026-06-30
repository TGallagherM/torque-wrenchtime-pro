package org.tgallagherm.torque.wrenchtimepro.data;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import android.content.Context;

/**
 * The Room database for the application.
 * Annotated with @Database to define entities and the version number.
 */
@Database(entities = {Reminder.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract ReminderDao reminderDao();

    private static volatile AppDatabase instance;

    /**
     * Gets the singleton instance of the AppDatabase.
     * Uses a double-checked locking pattern for thread safety.
     */
    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "wrenchtime_database")
                            .build();
                }
            }
        }
        return instance;
    }
}