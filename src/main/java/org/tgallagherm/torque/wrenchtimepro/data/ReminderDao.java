package org.tgallagherm.torque.wrenchtimepro.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

/**
 * Data Access Object for the Reminder entity.
 * Defines the database interactions such as fetching, inserting, and deleting reminders.
 */
@Dao
public interface ReminderDao {
    @Query("SELECT * FROM reminders")
    List<Reminder> getAll();

    @Insert
    void insert(Reminder reminder);

    @Update
    void update(Reminder reminder);

    @Delete
    void delete(Reminder reminder);
}