package com.example.personalfinancetoy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import com.example.personalfinancetoy.ui.theme.PersonalFinanceToyTheme
import java.text.NumberFormat
import java.util.Locale

private const val ROUTE_HOME = "home"
private const val ROUTE_EDIT = "editIncome"
private const val ARG_INCOME = "incomeArg"

class MainActivity : ComponentActivity() {

    private lateinit var database: PersonalFinanceDatabase
    private lateinit var incomeDao: IncomeDao
    private val incomes = mutableStateListOf<Income>() // TODO: Move this state into a ViewModel-backed repository when the architecture matures.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = Room.databaseBuilder(
            applicationContext,
            PersonalFinanceDatabase::class.java,
            PersonalFinanceDatabase.NAME
        )
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries() // TODO: Replace with proper coroutine usage instead of allowing main-thread database calls.
            .build()
        incomeDao = database.incomeDao()

        refreshIncomesFromDatabase()
        seedSampleDataIfEmpty()

        setContent {
            PersonalFinanceToyTheme {
                PersonalFinanceToyApp()
            }
        }
    }

    private fun refreshIncomesFromDatabase() {
        incomes.clear()
        incomes.addAll(incomeDao.getAll())
    }

    private fun seedSampleDataIfEmpty() {
        if (incomes.isEmpty()) {
            val starterEntries = listOf(
                Income(
                    title = "Starter Paycheck",
                    description = "Sample data so the screen is not empty",
                    amount = 1200.0
                ),
                Income(
                    title = "Weekend tutoring",
                    description = "One-off gig",
                    amount = 160.0
                )
            )
            starterEntries.forEach { incomeDao.insert(it) }
            refreshIncomesFromDatabase()
        }
    }

    private fun saveIncome(income: Income): Income {
        val sanitized = income.copy(description = income.description?.takeIf { it.isNotBlank() })
        val persisted = if (sanitized.id == 0) {
            val generatedId = incomeDao.insert(sanitized).toInt()
            sanitized.copy(id = generatedId)
        } else {
            val updatedRows = incomeDao.update(sanitized)
            if (updatedRows > 0) {
                sanitized
            } else {
                // TODO: Handle conflict resolution in a less hacky way once a repository layer is introduced.
                val generatedId = incomeDao.insert(sanitized).toInt()
                sanitized.copy(id = generatedId)
            }
        }
        refreshIncomesFromDatabase()
        return persisted
    }

    private fun deleteIncome(income: Income) {
        incomeDao.delete(income)
        refreshIncomesFromDatabase()
    }

    @Composable
    private fun PersonalFinanceToyApp() {
        val navController = rememberNavController()
        val totalBalance by remember {
            derivedStateOf { incomes.sumOf { it.amount } }
        }
        var incomePendingDeletion by remember { mutableStateOf<Income?>(null) }

        NavHost(
            navController = navController,
            startDestination = ROUTE_HOME
        ) {
            composable(route = ROUTE_HOME) {
                HomeScreen(
                    incomes = incomes,
                    totalBalance = totalBalance,
                    onAddIncome = {
                        navController.currentBackStackEntry?.savedStateHandle?.set(ARG_INCOME, null)
                        navController.navigate(ROUTE_EDIT)
                    },
                    onEditIncome = { income ->
                        navController.currentBackStackEntry?.savedStateHandle?.set(ARG_INCOME, income)
                        navController.navigate(ROUTE_EDIT)
                    },
                    onRequestDeleteIncome = { income -> incomePendingDeletion = income }
                )

                incomePendingDeletion?.let { target ->
                    DeleteIncomeDialog(
                        income = target,
                        onConfirm = {
                            deleteIncome(target)
                            incomePendingDeletion = null
                        },
                        onDismiss = { incomePendingDeletion = null }
                    )
                }
            }

            composable(route = ROUTE_EDIT) {
                val incomeToEdit = navController.previousBackStackEntry?.savedStateHandle?.get<Income>(ARG_INCOME)
                EditIncomeScreen(
                    initialIncome = incomeToEdit,
                    onSaveIncome = { incoming ->
                        val prepared = incoming.copy(id = incomeToEdit?.id ?: 0)
                        saveIncome(prepared)
                        // TODO: Replace Serializable arguments with a safer, type-safe navigation contract.
                        navController.popBackStack()
                    },
                    onCancel = { navController.popBackStack() }
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun HomeScreen(
        incomes: List<Income>,
        totalBalance: Double,
        onAddIncome: () -> Unit,
        onEditIncome: (Income) -> Unit,
        onRequestDeleteIncome: (Income) -> Unit
    ) {
        val currencyFormatter = remember(Locale.getDefault()) { NumberFormat.getCurrencyInstance() }
        val balanceColor = when {
            totalBalance > 0 -> MaterialTheme.colorScheme.primary
            totalBalance < 0 -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(text = "PersonalFinanceToy") }
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = onAddIncome) {
                    Text(text = "+")
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Current balance",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatCurrency(totalBalance, currencyFormatter, prefixPlus = true),
                    style = MaterialTheme.typography.headlineMedium,
                    color = balanceColor
                )
                // TODO: Include expenses in the balance once the Expense CRUD is available.
                Divider()

                if (incomes.isEmpty()) {
                    Text(
                        text = "No incomes yet. Use the + button to add one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items = incomes, key = { it.id }) { income ->
                            IncomeRow(
                                income = income,
                                currencyFormatter = currencyFormatter,
                                onEditIncome = onEditIncome,
                                onRequestDeleteIncome = onRequestDeleteIncome
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun IncomeRow(
        income: Income,
        currencyFormatter: NumberFormat,
        onEditIncome: (Income) -> Unit,
        onRequestDeleteIncome: (Income) -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = income.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!income.description.isNullOrBlank()) {
                            Text(
                                text = income.description!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = formatCurrency(income.amount, currencyFormatter, prefixPlus = true),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = { onEditIncome(income) }) {
                        Icon(imageVector = Icons.Filled.Edit, contentDescription = "Edit income")
                    }
                    IconButton(onClick = { onRequestDeleteIncome(income) }) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete income")
                    }
                }
            }
        }
    }

    @Composable
    private fun DeleteIncomeDialog(
        income: Income,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(text = "Delete income?") },
            text = {
                Text(text = "Are you sure you want to remove \"${income.title}\"?")
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(text = "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun EditIncomeScreen(
        initialIncome: Income?,
        onSaveIncome: (Income) -> Unit,
        onCancel: () -> Unit
    ) {
        var title by rememberSaveable { mutableStateOf(initialIncome?.title.orEmpty()) }
        var description by rememberSaveable { mutableStateOf(initialIncome?.description.orEmpty()) }
        var amountInput by rememberSaveable {
            mutableStateOf(
                initialIncome?.amount?.toString() ?: ""
            )
        }
        var validationError by rememberSaveable { mutableStateOf<String?>(null) }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(text = if (initialIncome == null) "New income" else "Edit income")
                    },
                    navigationIcon = {
                        IconButton(onClick = onCancel) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        validationError = null
                    },
                    label = { Text(text = "Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(text = "Description (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = {
                        amountInput = it
                        validationError = null
                    },
                    label = { Text(text = "Amount") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                validationError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) {
                        Text(text = "Cancel")
                    }
                    Spacer(modifier = Modifier.height(0.dp))
                    Button(
                        onClick = {
                            val sanitizedTitle = title.trim()
                            val normalizedAmount = amountInput
                                .replace(" ", "")
                                .replace(',', '.')
                                .toDoubleOrNull()
                            when {
                                sanitizedTitle.isEmpty() -> validationError = "Title cannot be blank."
                                normalizedAmount == null -> validationError = "Amount must be a valid number."
                                normalizedAmount <= 0.0 -> validationError = "Amount must be greater than zero."
                                else -> {
                                    onSaveIncome(
                                        Income(
                                            id = initialIncome?.id ?: 0,
                                            title = sanitizedTitle,
                                            description = description.trim().ifBlank { null },
                                            amount = normalizedAmount
                                        )
                                    )
                                }
                            }
                        }
                    ) {
                        Text(text = if (initialIncome == null) "Create income" else "Save changes")
                    }
                }
                // TODO: Extend this screen so it can handle expense editing once that module exists.
            }
        }
    }
}

private fun formatCurrency(
    amount: Double,
    formatter: NumberFormat,
    prefixPlus: Boolean = false
): String {
    val formatted = formatter.format(amount)
    return if (prefixPlus && amount > 0) "+$formatted" else formatted
}
