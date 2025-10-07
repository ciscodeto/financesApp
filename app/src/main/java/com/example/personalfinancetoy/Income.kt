package com.example.personalfinancetoy

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "income")
data class Income(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val description: String? = null,
    val amount: Double
) : Serializable {
    init {
        require(amount > 0) { "Income amount must be positive" }
    }
}

// TODO: Introduce an Expense entity and table mirroring Income so the balance can take real spending into account.
