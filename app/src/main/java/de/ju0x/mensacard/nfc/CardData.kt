package de.ju0x.mensacard.nfc

data class CardData(
    val balanceCents: Int,
    val currency: String = "EUR"
) {
    // Dividing by 1000 because the value is in mill not cents
    val balanceEuros: Double get() = balanceCents / 1000.0
    val balanceFormatted: String get() = "%.2f %s".format(balanceEuros, currency)
}