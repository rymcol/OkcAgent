package org.ddosolitary.okcagent

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import org.openintents.openpgp.OpenPgpError
import org.openintents.openpgp.util.OpenPgpApi.*
import org.openintents.ssh.authentication.request.KeySelectionRequest
import org.openintents.ssh.authentication.response.KeySelectionResponse

private const val REQUEST_SELECT_SSH_KEY = 1
private const val REQUEST_SELECT_GPG_KEY = 2

class MainActivity : Activity() {
	private val pref by lazy {
		getSharedPreferences(getString(R.string.pref_main), Context.MODE_PRIVATE)
	}
	private var sshApi: SshApi? = null
	private var gpgApi: GpgApi? = null

	private fun selectSshKeyCallback(intent: Intent) {
		val res = KeySelectionResponse(intent)
		when (res.resultCode) {
			RESULT_CODE_SUCCESS -> updateSshKeyId(res.keyId)
			RESULT_CODE_ERROR -> showError(
				this@MainActivity,
				res.error?.message ?: getString(R.string.error_api)
			)
			RESULT_CODE_USER_INTERACTION_REQUIRED -> startIntentSenderForResult(
				res.pendingIntent.intentSender, REQUEST_SELECT_SSH_KEY,
				null, 0, 0, 0
			)
		}
	}

	private fun selectGpgKeyCallback(intent: Intent) {
		when (intent.getIntExtra(RESULT_CODE, -1)) {
			RESULT_CODE_SUCCESS -> updateGpgKeyId(intent.getLongExtra(EXTRA_SIGN_KEY_ID, -1))
			RESULT_CODE_ERROR -> showError(
				this@MainActivity,
				intent.getParcelableExtra<OpenPgpError>(RESULT_ERROR)?.message
					?: getString(R.string.error_api)
			)
			RESULT_CODE_USER_INTERACTION_REQUIRED -> startIntentSenderForResult(
				intent.getParcelableExtra<PendingIntent>(RESULT_INTENT)!!.intentSender,
				REQUEST_SELECT_GPG_KEY,
				null,
				0,
				0,
				0
			)
		}
	}

	private fun updateSshKeyId(keyId: String) {
		pref.edit().apply {
			putString(getString(R.string.key_ssh_key), keyId)
			apply()
		}
		findViewById<TextView>(R.id.text_ssh_key).text = getString(R.string.text_has_ssh_key)
	}

	private fun updateGpgKeyId(keyId: Long) {
		pref.edit().apply {
			putLong(getString(R.string.key_gpg_key), keyId)
			apply()
		}
		findViewById<TextView>(R.id.text_gpg_key).text = getString(R.string.text_has_gpg_key)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		sshApi = SshApi(this) {
			if (!it) {
				close()
				showError(this@MainActivity, R.string.error_connect)
			}
		}.also { it.connect() }
		gpgApi = GpgApi(this) {
			if (!it) {
				close()
				showError(this@MainActivity, R.string.error_connect)
			}
		}.also { it.connect() }
		findViewById<TextView>(R.id.text_ssh_key).setText(
			if (pref.getString(getString(R.string.key_ssh_key), null) == null) {
				R.string.text_no_ssh_key
			} else {
				R.string.text_has_ssh_key
			}
		)
		findViewById<TextView>(R.id.text_gpg_key).setText(
			if (pref.getLong(getString(R.string.key_gpg_key), -1) == -1L) {
				R.string.text_no_gpg_key
			} else {
				R.string.text_has_gpg_key
			}
		)
	}

	override fun onDestroy() {
		sshApi?.close()
		gpgApi?.close()
		super.onDestroy()
	}


	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		if (resultCode == RESULT_OK) {
			when (requestCode) {
				REQUEST_SELECT_SSH_KEY -> selectSshKeyCallback(sshApi?.executeApi(data!!) ?: return)
				REQUEST_SELECT_GPG_KEY -> selectGpgKeyCallback(
					gpgApi?.executeApi(data!!, null, null) ?: return
				)
			}
		}
	}

	fun selectSshKey(@Suppress("UNUSED_PARAMETER") view: View) {
		selectSshKeyCallback(sshApi?.executeApi(KeySelectionRequest().toIntent()) ?: return)
	}

	fun selectGpgKey(@Suppress("UNUSED_PARAMETER") view: View) {
		selectGpgKeyCallback(gpgApi?.executeApi(Intent().apply {
			action = ACTION_GET_SIGN_KEY_ID
		}, null, null) ?: return)
	}
}
