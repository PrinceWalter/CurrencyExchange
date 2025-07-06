package com.example.currencyexchange.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject
import com.example.currencyexchange.data.repository.*
import com.example.currencyexchange.data.entities.TransactionEntity
import com.example.currencyexchange.data.dao.PartnerSummary

@HiltViewModel
class PartnerViewModel @Inject constructor(
    private val partnerRepository: PartnerRepository,
    private val transactionRepository: TransactionRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val partnerId: Long = savedStateHandle.get<Long>("partnerId") ?: 0L

    private val _partner = MutableStateFlow(null as com.example.currencyexchange.data.entities.PartnerEntity?)
    val partner = _partner.asStateFlow()

    val transactions = transactionRepository.getTransactionsByPartner(partnerId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _defaultCnyRate = MutableStateFlow("376")
    val defaultCnyRate = _defaultCnyRate.asStateFlow()

    private val _defaultUsdtRate = MutableStateFlow("2380")
    val defaultUsdtRate = _defaultUsdtRate.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow("")
    val successMessage = _successMessage.asStateFlow()

    init {
        loadPartner()
        loadDefaultRates()
    }

    private fun loadPartner() {
        viewModelScope.launch {
            _partner.value = partnerRepository.getPartnerById(partnerId)
        }
    }

    private fun loadDefaultRates() {
        viewModelScope.launch {
            try {
                val rates = exchangeRateRepository.getAllDefaultRates()
                _defaultCnyRate.value = rates["CNY"]?.toString() ?: "376"
                _defaultUsdtRate.value = rates["USDT"]?.toString() ?: "2380"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load exchange rates"
            }
        }
    }

    fun updateDefaultCnyRate(rate: String) {
        _defaultCnyRate.value = rate
        viewModelScope.launch {
            try {
                rate.toDoubleOrNull()?.let {
                    exchangeRateRepository.setDefaultRate("CNY", it)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update CNY rate"
            }
        }
    }

    fun updateDefaultUsdtRate(rate: String) {
        _defaultUsdtRate.value = rate
        viewModelScope.launch {
            try {
                rate.toDoubleOrNull()?.let {
                    exchangeRateRepository.setDefaultRate("USDT", it)
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update USDT rate"
            }
        }
    }

    fun saveTransaction(
        date: Date,
        tzsReceived: Double,
        foreignGiven: Double,
        foreignCurrency: String,
        exchangeRate: Double,
        notes: String = ""
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                transactionRepository.addTransaction(
                    partnerId = partnerId,
                    date = date,
                    tzsReceived = tzsReceived,
                    foreignGiven = foreignGiven,
                    foreignCurrency = foreignCurrency,
                    exchangeRate = exchangeRate,
                    notes = notes
                )
                _successMessage.value = "Transaction saved successfully"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save transaction: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                transactionRepository.updateTransaction(transaction)
                _successMessage.value = "Transaction updated successfully"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to update transaction: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                transactionRepository.deleteTransaction(transaction)
                _successMessage.value = "Transaction deleted successfully"
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete transaction: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun getPartnerSummary() = transactionRepository.getPartnerSummary(partnerId)

    fun clearErrorMessage() {
        _errorMessage.value = ""
    }

    fun clearSuccessMessage() {
        _successMessage.value = ""
    }
}