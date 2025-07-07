package com.example.currencyexchange.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject
import com.example.currencyexchange.data.repository.*
import com.example.currencyexchange.data.dao.PartnerSummary

@HiltViewModel
class MainViewModel @Inject constructor(
    private val partnerRepository: PartnerRepository,
    private val transactionRepository: TransactionRepository,
    private val exchangeRateRepository: ExchangeRateRepository
) : ViewModel() {

    val partners = partnerRepository.getAllActivePartners()

    private val _selectedPartnerId = MutableStateFlow(0L)
    val selectedPartnerId = _selectedPartnerId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    val filteredPartners = combine(partners, searchQuery) { partnerList, query ->
        if (query.isBlank()) {
            partnerList
        } else {
            partnerList.filter { it.name.contains(query, ignoreCase = true) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun selectPartner(partnerId: Long) {
        _selectedPartnerId.value = partnerId
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addPartner(name: String) {
        viewModelScope.launch {
            val partnerId = partnerRepository.addPartner(name)
            _selectedPartnerId.value = partnerId
        }
    }

    fun deletePartner(partnerId: Long) {
        viewModelScope.launch {
            partnerRepository.deletePartner(partnerId)
            if (_selectedPartnerId.value == partnerId) {
                _selectedPartnerId.value = 0L
            }
        }
    }

    suspend fun getPartnerSummaryByDateRange(
        partnerId: Long,
        startDate: Date,
        endDate: Date
    ) = transactionRepository.getPartnerSummaryByDateRange(partnerId, startDate, endDate)

    suspend fun getDefaultExchangeRates() = exchangeRateRepository.getAllDefaultRates()

    suspend fun getCumulativeNetPositions(): PartnerSummary {
        val currentPartners = partners.first() // Get current partners list

        if (currentPartners.isEmpty()) {
            return PartnerSummary(
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

        // Sum up all partner summaries
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

        return PartnerSummary(
            totalNetTzs = cumulativeTzs,
            totalNetCny = cumulativeCny,
            totalNetUsdt = cumulativeUsdt,
            transactionCount = cumulativeTransactions
        )
    }
}