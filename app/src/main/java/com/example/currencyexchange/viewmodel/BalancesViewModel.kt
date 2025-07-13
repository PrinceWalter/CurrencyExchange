package com.example.currencyexchange.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import com.example.currencyexchange.data.repository.BalancesRepository
import com.example.currencyexchange.data.repository.BalancesState
import com.example.currencyexchange.data.repository.PersistentBalanceItem
import com.example.currencyexchange.data.repository.PartnerRepository
import com.example.currencyexchange.data.repository.TransactionRepository
import com.example.currencyexchange.data.repository.ExchangeRateRepository

// Data class for UI balance items (same as in BalancesPage)
data class BalanceItem(
    val id: String = UUID.randomUUID().toString(),
    var description: String = "",
    var amount: String = "",
    var currency: String = "TZS",
    var rate: String = "",
    val isNetPosition: Boolean = false,
    val isFixedType: Boolean = false
)

@HiltViewModel
class BalancesViewModel @Inject constructor(
    private val balancesRepository: BalancesRepository,
    private val partnerRepository: PartnerRepository,
    private val transactionRepository: TransactionRepository,
    private val exchangeRateRepository: ExchangeRateRepository
) : ViewModel() {

    private val _balanceItems = MutableStateFlow<List<BalanceItem>>(emptyList())
    val balanceItems = _balanceItems.asStateFlow()

    private val _defaultCnyRate = MutableStateFlow("376")
    val defaultCnyRate = _defaultCnyRate.asStateFlow()

    private val _defaultUsdtRate = MutableStateFlow("2380")
    val defaultUsdtRate = _defaultUsdtRate.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _netPositionTzs = MutableStateFlow(0.0)
    val netPositionTzs = _netPositionTzs.asStateFlow()

    init {
        loadSavedState()
        loadNetPosition()
    }

    private fun loadSavedState() {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                // Load default rates first
                val (savedCnyRate, savedUsdtRate) = balancesRepository.loadDefaultRates()
                _defaultCnyRate.value = savedCnyRate
                _defaultUsdtRate.value = savedUsdtRate

                // Load saved balances state
                val savedState = balancesRepository.loadBalancesState()

                if (savedState != null) {
                    // Convert PersistentBalanceItem to BalanceItem
                    val balanceItems = savedState.balanceItems.map { persistentItem ->
                        BalanceItem(
                            id = persistentItem.id,
                            description = persistentItem.description,
                            amount = persistentItem.amount,
                            currency = persistentItem.currency,
                            rate = persistentItem.rate,
                            isNetPosition = persistentItem.isNetPosition,
                            isFixedType = persistentItem.isFixedType
                        )
                    }
                    _balanceItems.value = balanceItems

                    // Update rates if they were saved in the state
                    _defaultCnyRate.value = savedState.defaultCnyRate
                    _defaultUsdtRate.value = savedState.defaultUsdtRate
                } else {
                    // Initialize with default items if no saved state
                    initializeDefaultItems()
                }
            } catch (e: Exception) {
                // If loading fails, initialize with defaults
                initializeDefaultItems()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadNetPosition() {
        viewModelScope.launch {
            try {
                val summary = getCumulativeNetPositions()
                _netPositionTzs.value = summary.totalNetTzs

                // Update the net position item if it exists
                updateNetPositionItem(summary.totalNetTzs)
            } catch (e: Exception) {
                _netPositionTzs.value = 0.0
            }
        }
    }

    private fun updateNetPositionItem(netPositionValue: Double) {
        val currentItems = _balanceItems.value.toMutableList()
        val netPositionIndex = currentItems.indexOfFirst { it.isNetPosition }

        if (netPositionIndex != -1) {
            currentItems[netPositionIndex] = currentItems[netPositionIndex].copy(
                amount = formatNumberWithCommas(netPositionValue)
            )
            _balanceItems.value = currentItems
            saveState() // Save the updated state
        }
    }

    private fun initializeDefaultItems() {
        val defaultItems = listOf(
            BalanceItem(
                description = "Overall Net Position",
                amount = formatNumberWithCommas(_netPositionTzs.value),
                currency = "TZS",
                rate = "",
                isNetPosition = true,
                isFixedType = true
            ),
            BalanceItem(
                description = "ALIPAY",
                amount = "",
                currency = "CNY",
                rate = "",
                isFixedType = true
            ),
            BalanceItem(
                description = "USDT",
                amount = "",
                currency = "USDT",
                rate = "",
                isFixedType = true
            ),
            BalanceItem() // Empty TZS row
        )
        _balanceItems.value = defaultItems
        saveState()
    }

    fun updateBalanceItem(itemId: String, updatedItem: BalanceItem) {
        val currentItems = _balanceItems.value.toMutableList()
        val index = currentItems.indexOfFirst { it.id == itemId }
        if (index != -1) {
            currentItems[index] = updatedItem
            _balanceItems.value = currentItems
            saveState()
        }
    }

    fun addNewBalanceItem() {
        val currentItems = _balanceItems.value.toMutableList()
        currentItems.add(BalanceItem())
        _balanceItems.value = currentItems
        saveState()
    }

    fun removeBalanceItem(itemId: String) {
        val currentItems = _balanceItems.value.toMutableList()
        val item = currentItems.find { it.id == itemId }

        // Don't allow removing the net position item, fixed items, or if only essential items remain
        if (item != null && !item.isNetPosition && !item.isFixedType && currentItems.size > 4) {
            currentItems.removeAll { it.id == itemId }
            _balanceItems.value = currentItems
            saveState()
        }
    }

    fun updateDefaultCnyRate(rate: String) {
        _defaultCnyRate.value = rate
        saveState()

        // Also save to main exchange rate repository
        viewModelScope.launch {
            try {
                val rateValue = rate.toDoubleOrNull()
                if (rateValue != null && rateValue > 0) {
                    exchangeRateRepository.setDefaultRate("CNY", rateValue)
                }
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }

    fun updateDefaultUsdtRate(rate: String) {
        _defaultUsdtRate.value = rate
        saveState()

        // Also save to main exchange rate repository
        viewModelScope.launch {
            try {
                val rateValue = rate.toDoubleOrNull()
                if (rateValue != null && rateValue > 0) {
                    exchangeRateRepository.setDefaultRate("USDT", rateValue)
                }
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }

    private fun saveState() {
        viewModelScope.launch {
            try {
                // Convert BalanceItem to PersistentBalanceItem
                val persistentItems = _balanceItems.value.map { item ->
                    PersistentBalanceItem(
                        id = item.id,
                        description = item.description,
                        amount = item.amount,
                        currency = item.currency,
                        rate = item.rate,
                        isNetPosition = item.isNetPosition,
                        isFixedType = item.isFixedType
                    )
                }

                val balancesState = BalancesState(
                    balanceItems = persistentItems,
                    defaultCnyRate = _defaultCnyRate.value,
                    defaultUsdtRate = _defaultUsdtRate.value
                )

                balancesRepository.saveBalancesState(balancesState)
            } catch (e: Exception) {
                // Handle save error
                e.printStackTrace()
            }
        }
    }

    fun refreshNetPosition() {
        loadNetPosition()
    }

    private suspend fun getCumulativeNetPositions(): com.example.currencyexchange.data.dao.PartnerSummary {
        val currentPartners = partnerRepository.getAllActivePartners().first()

        if (currentPartners.isEmpty()) {
            return com.example.currencyexchange.data.dao.PartnerSummary(
                totalNetTzs = 0.0,
                totalNetCny = 0.0,
                totalNetUsdt = 0.0,
                transactionCount = 0
            )
        }

        var cumulativeTzs = 0.0
        var cumulativeCny = 0.0
        var cumulativeUsdt = 0.0
        var cumulativeTransactions = 0

        currentPartners.forEach { partner ->
            try {
                val summary = partnerRepository.getPartnerSummary(partner.id)
                cumulativeTzs += summary.totalNetTzs
                cumulativeCny += summary.totalNetCny
                cumulativeUsdt += summary.totalNetUsdt
                cumulativeTransactions += summary.transactionCount
            } catch (e: Exception) {
                // Skip partners with no transactions or errors
            }
        }

        return com.example.currencyexchange.data.dao.PartnerSummary(
            totalNetTzs = cumulativeTzs,
            totalNetCny = cumulativeCny,
            totalNetUsdt = cumulativeUsdt,
            transactionCount = cumulativeTransactions
        )
    }

    fun clearSavedState() {
        viewModelScope.launch {
            balancesRepository.clearBalancesState()
            initializeDefaultItems()
        }
    }

    private fun formatNumberWithCommas(number: Double): String {
        val formatted = String.format("%.2f", number)
        val parts = formatted.split(".")
        val integerPart = parts[0]
        val decimalPart = if (parts.size > 1) parts[1] else "00"

        val isNegative = integerPart.startsWith("-")
        val absoluteIntegerPart = if (isNegative) integerPart.substring(1) else integerPart

        val formattedInteger = if (absoluteIntegerPart.length > 3) {
            absoluteIntegerPart.reversed().chunked(3).joinToString(",").reversed()
        } else {
            absoluteIntegerPart
        }

        val sign = if (isNegative) "-" else ""
        return "$sign$formattedInteger.$decimalPart"
    }
}