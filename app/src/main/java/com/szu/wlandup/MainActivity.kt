package com.szu.wlandup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.szu.wlandup.core.ConnectLoop
import com.szu.wlandup.core.ConnectLoopExit
import com.szu.wlandup.core.ConnectResult
import com.szu.wlandup.core.ConnectSession
import com.szu.wlandup.core.CredentialStore
import com.szu.wlandup.core.Credentials
import com.szu.wlandup.core.SuccessMessage
import com.szu.wlandup.databinding.ActivityMainBinding
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var credentialStore: CredentialStore
    private val worker = Executors.newSingleThreadExecutor()
    private val running = AtomicBoolean(false)
    /** Latched true on delete/destroy until the worker observes it and exits. */
    private val stopRequested = AtomicBoolean(false)
    @Volatile
    private var activeWifi: WifiConnector? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        credentialStore = CredentialStore(File(filesDir, "credentials.dat"))
        requestNeededPermissions()
        renderCredentialGate()

        binding.btnSaveConnect.setOnClickListener { saveAndStart() }
        binding.btnStart.setOnClickListener { startLoop() }
        binding.btnDeleteCreds.setOnClickListener { deleteCredentials() }
    }

    override fun onDestroy() {
        stopRequested.set(true)
        activeWifi?.disconnect()
        activeWifi = null
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun renderCredentialGate() {
        val saved = credentialStore.load()
        if (saved == null) {
            binding.credentialCard.visibility = View.VISIBLE
            binding.savedActions.visibility = View.GONE
        } else {
            binding.credentialCard.visibility = View.GONE
            binding.savedActions.visibility = View.VISIBLE
            binding.statusText.text = "已保存账号：${saved.userAccount}"
            startLoop()
        }
    }

    private fun saveAndStart() {
        val account = binding.inputAccount.text?.toString()?.trim().orEmpty()
        val password = binding.inputPassword.text?.toString().orEmpty()
        if (account.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入卡号和密码", Toast.LENGTH_SHORT).show()
            return
        }
        credentialStore.save(Credentials(account, password))
        renderCredentialGate()
    }

    private fun deleteCredentials() {
        // Keep stop latched until the worker exits — do not clear here.
        stopRequested.set(true)
        activeWifi?.disconnect()
        activeWifi = null
        credentialStore.delete()
        binding.logView.text = ""
        binding.statusText.text = getString(R.string.status_idle)
        binding.credentialCard.visibility = View.VISIBLE
        binding.savedActions.visibility = View.GONE
    }

    private fun startLoop() {
        if (credentialStore.load() == null) return
        if (!running.compareAndSet(false, true)) return
        // Clear stop only when intentionally starting a fresh loop.
        stopRequested.set(false)
        binding.statusText.text = "正在联网…"

        worker.execute {
            val wifi = WifiConnector(this)
            activeWifi = wifi
            try {
                val session = ConnectSession(
                    wifi = wifi,
                    portal = HttpPortalClient(this@MainActivity),
                    probe = HttpBaiduProbe(),
                )
                val loop = ConnectLoop(
                    session = session,
                    loadCredentials = { credentialStore.load() },
                    shouldStop = { stopRequested.get() },
                )
                val exit = loop.run { result ->
                    when (result) {
                        is ConnectResult.Success -> publishLogs(emptyList())
                        is ConnectResult.Failure -> {
                            publishLogs(session.logs())
                            runOnUiThread {
                                binding.statusText.text =
                                    "失败(${result.attemptCount})：${result.reason}，重试中…"
                            }
                        }
                    }
                }
                // Keep the authenticated association after success; tear down otherwise.
                if (exit.shouldTearDownWifi()) {
                    wifi.disconnect()
                    if (activeWifi === wifi) activeWifi = null
                }
                when (exit) {
                    is ConnectLoopExit.Succeeded -> runOnUiThread {
                        binding.statusText.text = "联网成功"
                        showSuccessDialog(exit.result.attemptsUsed)
                    }
                    ConnectLoopExit.CredentialsGone, ConnectLoopExit.Stopped -> runOnUiThread {
                        if (credentialStore.load() == null) {
                            binding.statusText.text = getString(R.string.status_idle)
                        }
                    }
                }
            } finally {
                running.set(false)
            }
        }
    }

    private fun publishLogs(lines: List<String>) {
        runOnUiThread {
            binding.logView.text = lines.joinToString("\n")
            binding.logScroll.post {
                binding.logScroll.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun showSuccessDialog(attempts: Int) {
        val exact = SuccessMessage.format(attempts)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_success, null)
        val countView = view.findViewById<TextView>(R.id.successCount)
        countView.text = attempts.toString()
        view.contentDescription = exact

        MaterialAlertDialogBuilder(this)
            .setView(view)
            .setPositiveButton(R.string.success_ok, null)
            .show()
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.NEARBY_WIFI_DEVICES
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.ACCESS_FINE_LOCATION
            }
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1001)
        }
    }
}
