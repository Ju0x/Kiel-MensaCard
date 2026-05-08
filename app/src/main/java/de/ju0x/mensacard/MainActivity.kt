package de.ju0x.mensacard

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import de.ju0x.mensacard.nfc.BalanceReader
class MainActivity : AppCompatActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private val TAG = "MainActivity";

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // window.decorView.setBackgroundColor("#FF5733".toColorInt())
        setContentView(R.layout.activity_main)

        val yourBalance = findViewById<TextView>(R.id.title);

        if (yourBalance != null) {
            yourBalance.text = "Halte deine Karte an dein Smartphone"
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
            ?: run {
                Toast.makeText(this, "NFC wird auf diesem Gerät nicht unterstützt!", Toast.LENGTH_LONG).show()
                finish()
                return
            }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
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
        runOnUiThread {
            findViewById<TextView>(R.id.title).text = "Bitte warten..."
        }

        val isoDep = IsoDep.get(tag) ?: return
        val balanceText: TextView = findViewById(R.id.balance)

        Thread {
            val cardData = BalanceReader().read(isoDep)

            if (cardData != null) {
                Log.d(TAG, "Balance: ${cardData.balanceFormatted}")
                runOnUiThread {
                    findViewById<TextView>(R.id.title).text = "Dein Guthaben:"
                    balanceText.text = "%.2f€".format(cardData.balanceEuros)
                }
            } else {
                Log.w(TAG, "Error: Card cannot be read")
            }
        }.start()
    }

    private fun updateNfcBanner() {
        val banner = findViewById<LinearLayout>(R.id.nfc_banner)

        if (nfcAdapter?.isEnabled == false) {
            banner.visibility = View.VISIBLE
            findViewById<Button>(R.id.nfc_settings_button).setOnClickListener {
                startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            }
        } else {
            banner.visibility = View.GONE
        }
    }
}