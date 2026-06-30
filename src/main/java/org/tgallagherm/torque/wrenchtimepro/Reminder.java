package org.tgallagherm.torque.wrenchtimepro;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Data model representing a single maintenance reminder.
 * Stores the name of the service (e.g., "Oil Change") and the specific
 * mileage at which it should be performed.
 */
@Entity(tableName = "reminders")
public class Reminder {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String name;
    public String miles;

    public Reminder(String name, String miles) {
        this.name = name;
        this.miles = miles;
    }
}