package com.example.personalfinancetoy

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Income::class],
    version = 1,
    exportSchema = false
)
abstract class PersonalFinanceDatabase : RoomDatabase() {
    abstract fun incomeDao(): IncomeDao

    companion object {
        const val NAME = "personal_finance.db"

        @Volatile
        private var INSTANCE: PersonalFinanceDatabase? = null

        fun getInstance(context: Context): PersonalFinanceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PersonalFinanceDatabase::class.java,
                    NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
