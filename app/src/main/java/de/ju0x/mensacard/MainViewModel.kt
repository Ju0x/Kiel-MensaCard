package de.ju0x.mensacard

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel : ViewModel() {

    private val _cardState = MutableStateFlow<CardUiState>(CardUiState.WaitingForScan)
    val cardState: StateFlow<CardUiState> = _cardState.asStateFlow()

    fun onNfcDisabled() {
        _cardState.value = CardUiState.NfcDisabled
    }

    fun onNfcEnabled() {
        _cardState.value = CardUiState.WaitingForScan
    }

    fun onCardScanned(balance: Double) {
        _cardState.value = CardUiState.ScannedBalance(balance)
    }
}

sealed class CardUiState {
    object NfcDisabled : CardUiState()
    object WaitingForScan : CardUiState()
    data class ScannedBalance(val balance: Double) : CardUiState()
}