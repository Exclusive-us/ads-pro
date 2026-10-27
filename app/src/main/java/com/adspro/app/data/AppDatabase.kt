package com.adspro.app.data
import android.content.Context
import androidx.room.*
@Entity(tableName = "sms_outbox", indices = [Index(value = ["sender", "receivedAt"], unique = true)])
data class SmsOutbox(@PrimaryKey(autoGenerate = true) val id: Long = 0, val sender: String, val body: String, val receivedAt: Long)
@Entity(tableName = "notification_outbox")
data class NotificationOutbox(@PrimaryKey(autoGenerate = true) val id: Long = 0, val packageName: String, val key: String, val title: String?, val body: String?, val postedAt: Long)
@Dao interface SmsOutboxDao {
    @Query("SELECT * FROM sms_outbox ORDER BY receivedAt ASC") suspend fun all(): List<SmsOutbox>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(item: SmsOutbox): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertAll(items: List<SmsOutbox>)
    @Delete suspend fun delete(item: SmsOutbox)
}
@Dao interface NotificationOutboxDao {
    @Query("SELECT * FROM notification_outbox ORDER BY postedAt ASC") suspend fun all(): List<NotificationOutbox>
    @Insert suspend fun insert(item: NotificationOutbox): Long
    @Delete suspend fun delete(item: NotificationOutbox)
}
@Database(entities = [SmsOutbox::class, NotificationOutbox::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun smsOutboxDao(): SmsOutboxDao
    abstract fun notificationOutboxDao(): NotificationOutboxDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) { INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "adspro.db").build().also { INSTANCE = it } }
        }
    }
}
