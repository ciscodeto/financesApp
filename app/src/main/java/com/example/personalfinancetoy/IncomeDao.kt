package com.example.personalfinancetoy

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface IncomeDao {
    @Query("SELECT * FROM income")
    fun getAll(): List<Income>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(income: Income): Long

    @Update
    fun update(income: Income): Int

    @Delete
    fun delete(income: Income): Int
}

// TODO: Wire this DAO and its Expense counterpart into repositories once architecture cleanup happens.
