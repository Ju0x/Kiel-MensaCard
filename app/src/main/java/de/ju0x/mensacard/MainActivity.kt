package de.ju0x.mensacard

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Color
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.ju0x.mensacard.nfc.BalanceReader
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var nfcAdapter: NfcAdapter? = null

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
            ?: run {
                Toast.makeText(this, "NFC nicht unterstützt", Toast.LENGTH_LONG).show()
                finish()
                return
            }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        observeCardState()
    }

    override fun onResume() {
        super.onResume()

        updateNfcBanner()

        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, arrayOf(arrayOf(IsoDep::class.java.name)))
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action != NfcAdapter.ACTION_TECH_DISCOVERED) return

        val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }

        tag?.let { readDesfireTag(it) }
    }

    private fun readDesfireTag(tag: Tag) {
        val isoDep = IsoDep.get(tag) ?: return

        Thread {
            val cardData = BalanceReader().read(isoDep)

            if (cardData != null) {
                runOnUiThread {
                    viewModel.onCardScanned(cardData.balanceEuros)
                }
            }
        }.start()
    }

    private fun updateNfcBanner() {
        if (nfcAdapter?.isEnabled == false) {
            viewModel.onNfcDisabled()
            findViewById<Button>(R.id.nfc_settings_button).setOnClickListener {
                startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            }
        } else {
            viewModel.onNfcEnabled()
        }
    }

    private fun observeCardState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.cardState.collect { state -> renderState(state) }
            }
        }
    }

    private fun renderState(state: CardUiState) {
        val stateNfcDisabled = findViewById<LinearLayout>(R.id.state_nfc_disabled)
        val stateWaiting     = findViewById<TextView>(R.id.state_waiting)
        val stateScanned     = findViewById<LinearLayout>(R.id.state_scanned)
        val card             = findViewById<com.google.android.material.card.MaterialCardView>(R.id.card)
        val stonks = findViewById<ImageView>(R.id.emote)

        stateNfcDisabled.visibility = View.GONE
        stateWaiting.visibility     = View.GONE
        stateScanned.visibility     = View.GONE
        card.strokeWidth = 0

        when (state) {
            is CardUiState.NfcDisabled -> {
                card.setCardBackgroundColor(Color.DKGRAY)
                stateNfcDisabled.visibility = View.VISIBLE
            }
            is CardUiState.WaitingForScan -> {
                card.setCardBackgroundColor(Color.DKGRAY)
                stateWaiting.visibility = View.VISIBLE
            }
            is CardUiState.ScannedBalance -> {
                card.setCardBackgroundColor(
                    ContextCompat.getColor(this, R.color.card_blue)
                )
                findViewById<TextView>(R.id.balance).text = formatBalance(state.balance)
                stateScanned.visibility = View.VISIBLE
                stonks.setImageResource(R.drawable.stonks)
            }
        }
    }

    private fun formatBalance(amount: Double): String =
        "%,.2f€".format(amount)
}