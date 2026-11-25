package com.bahar.linkguard

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.IDN
import java.net.URI
import java.util.Locale
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import android.text.InputType

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("linkguard", MODE_PRIVATE) }
    private val blue = 0xFF2563EB.toInt()
    private val ink = 0xFF0F172A.toInt()
    private val muted = 0xFF64748B.toInt()
    private val red = 0xFFB91C1C.toInt()
    private val amber = 0xFF92400E.toInt()
    private val green = 0xFF166534.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incoming = intent?.dataString
        if (intent?.action == Intent.ACTION_VIEW && !incoming.isNullOrBlank()) {
            showConfirmation(incoming)
        } else {
            showHome()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val incoming = intent.dataString
        if (intent.action == Intent.ACTION_VIEW && !incoming.isNullOrBlank()) showConfirmation(incoming) else showHome()
    }

    private fun baseLayout(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(24))
            setBackgroundColor(0xFFF8FAFC.toInt())
        }
    }

    private fun setScrollable(content: LinearLayout) {
        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
    }

    private fun showHome() {
        val root = baseLayout()
        root.addView(text("LINKGUARD", 13, blue, true))
        root.addView(text("Take a second look before opening links.", 26, ink, true), margin(0, 10, 0, 10))
        root.addView(text("LinkGuard receives web links, checks common warning signs on your device, and asks before sending them to Chrome.", 15, muted))
        root.addView(section("PIN SECURITY"))
        root.addView(text(if (hasPin()) "A PIN is configured. Every intercepted link requires it before Chrome can open." else "No PIN is configured yet. Set a PIN before using link protection." , 14, if (hasPin()) green else amber))
        root.addView(button(if (hasPin()) "Change PIN" else "Set up PIN", true) { showPinSetup() }, margin(0, 10, 0, 0))
        root.addView(section("SETUP"))
        root.addView(text("1. Open Android Settings → Apps → Default apps → Browser app.\n\n2. Choose LinkGuard as the default browser.\n\n3. When a supported link is tapped, LinkGuard will show its review screen.\n\n4. Tap Open in Chrome only if you trust the destination.", 15, ink))
        root.addView(section("IMPORTANT"))
        root.addView(text("This first version performs on-device heuristic checks only. It does not query a live threat-intelligence service, and it cannot intercept links opened inside an app's own built-in browser. A clean result does not guarantee a website is safe.", 14, amber))
        root.addView(section("RECENT LINK HISTORY"))
        val history = prefs.getString("history", "").orEmpty()
        root.addView(text(if (history.isBlank()) "No links checked yet." else history, 13, muted))
        val clear = button("Clear history", false) { prefs.edit().remove("history").apply(); showHome() }
        root.addView(clear, margin(0, 18, 0, 0))
        setScrollable(root)
    }

    private fun showConfirmation(raw: String) {
        val result = LinkInspector.inspect(raw)
        addHistory(raw, result.level)
        val root = baseLayout()
        root.addView(text("LINKGUARD  /  LINK REVIEW", 12, blue, true))
        root.addView(text("Before you open this link", 25, ink, true), margin(0, 12, 0, 8))
        root.addView(text("Destination", 12, muted, true))
        root.addView(text(result.displayHost, 20, ink, true), margin(0, 4, 0, 4))
        root.addView(text(raw, 13, muted), margin(0, 0, 0, 16))

        val statusColor = when (result.level) { RiskLevel.HIGH -> red; RiskLevel.CAUTION -> amber; RiskLevel.LOW -> green }
        val statusTitle = when (result.level) {
            RiskLevel.HIGH -> "High-risk signs found"
            RiskLevel.CAUTION -> "Review carefully"
            RiskLevel.LOW -> "No common warning signs found"
        }
        root.addView(text(statusTitle, 18, statusColor, true))
        root.addView(text("On-device check only. This is not a live reputation verdict.", 12, muted), margin(0, 4, 0, 10))
        if (result.warnings.isEmpty()) {
            root.addView(text("No obvious URL patterns from LinkGuard's local checks were detected. Unknown or newly created scam sites may still look normal.", 14, ink))
        } else {
            result.warnings.forEach { warning -> root.addView(text("•  $warning", 14, if (result.level == RiskLevel.HIGH) red else amber), margin(0, 5, 0, 0)) }
        }
        root.addView(text("FULL URL", 11, muted, true), margin(0, 22, 0, 5))
        root.addView(text(raw, 12, ink))

        if (!hasPin()) {
            root.addView(section("PIN REQUIRED"))
            root.addView(text("Set up a LinkGuard PIN first. This link will remain closed until a PIN is configured.", 14, amber))
            root.addView(button("Set up PIN", true) { showPinSetup() }, margin(0, 18, 0, 10))
            root.addView(button("Cancel", false) { finish() })
        } else {
            root.addView(section("ENTER YOUR 6-DIGIT PIN"))
            val pinInput = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                hint = "6-digit PIN"
                filters = arrayOf(android.text.InputFilter.LengthFilter(6))
                textSize = 20f
            }
            root.addView(pinInput, margin(0, 4, 0, 4))
            val status = text("PIN is required for every link, including links with no warning signs.", 12, muted)
            root.addView(status, margin(0, 4, 0, 0))
            root.addView(button("Verify PIN & Open in Chrome", true) {
                val pin = pinInput.text?.toString().orEmpty()
                val remaining = lockoutRemainingMs()
                when {
                    remaining > 0 -> status.text = "Too many attempts. Try again in ${((remaining + 999) / 1000)} seconds."
                    pin.length != 6 -> status.text = "Enter all 6 digits."
                    verifyPin(pin) -> { prefs.edit().putInt("pin_attempts", 0).apply(); openChrome(raw) }
                    else -> {
                        val attempts = prefs.getInt("pin_attempts", 0) + 1
                        prefs.edit().putInt("pin_attempts", attempts).apply()
                        if (attempts >= 5) {
                            prefs.edit().putLong("pin_lock_until", System.currentTimeMillis() + 30_000L).putInt("pin_attempts", 0).apply()
                            status.text = "Too many incorrect PINs. Link opening is locked for 30 seconds."
                        } else status.text = "Incorrect PIN. ${5 - attempts} attempts remaining."
                        pinInput.text?.clear()
                    }
                }
            }, margin(0, 18, 0, 10))
            root.addView(button("Cancel", false) { finish() })
        }
        root.addView(text("Never enter passwords, OTPs, card details or mobile-banking PINs on a page opened from an unexpected message.", 13, muted), margin(0, 20, 0, 0))
        setScrollable(root)
    }

    private fun showPinSetup() {
        val root = baseLayout()
        root.addView(text("LINKGUARD / PIN SETUP", 12, blue, true))
        root.addView(text(if (hasPin()) "Change your PIN" else "Create your 6-digit PIN", 25, ink, true), margin(0, 12, 0, 8))
        root.addView(text("This PIN is required every time LinkGuard opens a link. Choose a PIN you do not use for banking or your phone lock.", 14, muted))
        val first = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; hint = "New 6-digit PIN"; filters = arrayOf(android.text.InputFilter.LengthFilter(6)) }
        val second = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; hint = "Confirm 6-digit PIN"; filters = arrayOf(android.text.InputFilter.LengthFilter(6)) }
        root.addView(first, margin(0, 22, 0, 8))
        root.addView(second, margin(0, 4, 0, 8))
        val status = text("PIN is stored as a salted PBKDF2 hash, not as plain text.", 12, muted)
        root.addView(status, margin(0, 8, 0, 0))
        root.addView(button("Save PIN", true) {
            val a = first.text?.toString().orEmpty(); val b = second.text?.toString().orEmpty()
            when {
                a.length != 6 || !a.all { it.isDigit() } -> status.text = "Choose exactly 6 digits."
                a != b -> status.text = "PINs do not match. Try again."
                else -> { savePin(a); Toast.makeText(this, "LinkGuard PIN saved.", Toast.LENGTH_LONG).show(); showHome() }
            }
        }, margin(0, 20, 0, 10))
        root.addView(button("Back", false) { showHome() })
        setScrollable(root)
    }

    private fun hasPin(): Boolean = !prefs.getString("pin_hash", null).isNullOrBlank() && !prefs.getString("pin_salt", null).isNullOrBlank()

    private fun savePin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pinHash(pin, salt)
        prefs.edit().putString("pin_salt", android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
            .putString("pin_hash", android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP))
            .putInt("pin_attempts", 0).putLong("pin_lock_until", 0L).apply()
    }

    private fun verifyPin(pin: String): Boolean {
        return try {
            val salt = android.util.Base64.decode(prefs.getString("pin_salt", ""), android.util.Base64.NO_WRAP)
            val expected = android.util.Base64.decode(prefs.getString("pin_hash", ""), android.util.Base64.NO_WRAP)
            MessageDigest.isEqual(expected, pinHash(pin, salt))
        } catch (_: Exception) { false }
    }

    private fun pinHash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }

    private fun lockoutRemainingMs(): Long = (prefs.getLong("pin_lock_until", 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    private fun openChrome(url: String) {
        val uri = Uri.parse(url)
        try {
            val chrome = Intent(Intent.ACTION_VIEW, uri).apply { setPackage("com.android.chrome") }
            startActivity(chrome)
        } catch (_: Exception) {
            try {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), "Open link with"))
            } catch (_: Exception) {
                Toast.makeText(this, "No browser is available to open this link.", Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }

    private fun addHistory(url: String, level: RiskLevel) {
        val old = prefs.getString("history", "").orEmpty().lines().filter { it.isNotBlank() }
        val line = "${level.name}: ${url.take(180)}"
        prefs.edit().putString("history", (listOf(line) + old).take(30).joinToString("\n")).apply()
    }

    private fun section(label: String): TextView = text(label, 11, muted, true).apply { setPadding(0, dp(24), 0, dp(9)) }
    private fun text(value: String, size: Int, color: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun button(label: String, primary: Boolean, action: () -> Unit): Button = Button(this).apply {
        text = label; isAllCaps = false; textSize = 15f
        if (primary) { setBackgroundColor(blue); setTextColor(0xFFFFFFFF.toInt()) }
        else { setBackgroundColor(0xFFE2E8F0.toInt()); setTextColor(ink) }
        setOnClickListener { action() }
    }
    private fun margin(l: Int, t: Int, r: Int, b: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, -2).apply {
        setMargins(dp(l), dp(t), dp(r), dp(b))
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

enum class RiskLevel { LOW, CAUTION, HIGH }
data class Inspection(val displayHost: String, val level: RiskLevel, val warnings: List<String>)

object LinkInspector {
    private val shorteners = setOf("bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "buff.ly", "cutt.ly", "rb.gy", "shorturl.at")
    private val suspiciousTerms = listOf("verify", "login", "password", "secure", "account", "wallet", "payment", "otp", "bonus", "prize", "claim", "urgent")

    fun inspect(raw: String): Inspection {
        val warnings = mutableListOf<String>()
        var host = "Unknown host"
        try {
            val uri = URI(raw)
            val rawHost = uri.host ?: ""
            host = if (rawHost.isNotBlank()) IDN.toUnicode(rawHost).lowercase(Locale.ROOT) else "Unknown host"
            if (!raw.startsWith("https://", true)) warnings.add("This link does not use HTTPS encryption.")
            if (uri.userInfo != null || raw.substringAfter("://", "").substringBefore('/').contains('@')) warnings.add("The URL contains user-info syntax that can disguise the real destination.")
            if (rawHost.isBlank()) warnings.add("The destination host could not be read. Do not continue unless you understand why.")
            if (rawHost.contains("xn--", true)) warnings.add("The domain uses internationalized/punycode characters. Check for look-alike letters.")
            if (rawHost.matches(Regex("^[0-9.]+$")) || rawHost.contains(":")) warnings.add("The destination appears to use an IP address instead of a normal domain name.")
            if (shorteners.any { rawHost.equals(it, true) || rawHost.endsWith(".$it", true) }) warnings.add("This is a URL-shortening service, so the final destination is hidden.")
            if (uri.port != -1 && uri.port !in listOf(80, 443)) warnings.add("The link uses an unusual network port.")
            val lower = raw.lowercase(Locale.ROOT)
            if (suspiciousTerms.any { lower.contains(it) }) warnings.add("The URL contains words commonly used in account, payment or urgency lures. This alone does not prove fraud.")
            if (raw.length > 180) warnings.add("The URL is unusually long. Check the destination carefully.")
        } catch (_: Exception) {
            warnings.add("The URL could not be parsed reliably.")
        }
        val high = warnings.any { it.contains("user-info") || it.contains("IP address") || it.contains("could not be parsed") || it.contains("host could not") }
        val level = when { high -> RiskLevel.HIGH; warnings.isNotEmpty() -> RiskLevel.CAUTION; else -> RiskLevel.LOW }
        return Inspection(host, level, warnings.distinct())
    }
}
