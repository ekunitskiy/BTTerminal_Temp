package com.example.btterminal_for_ai.activity

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.example.btterminal_for_ai.R
import com.example.btterminal_for_ai.Utils
import ru.sash0k.bluetooth_terminal.R
import ru.sash0k.bluetooth_terminal.Utils

/**
 * Общий базовый класс. Инициализация BT-адаптера
 * Created by sash0k on 09.12.13.
 */
abstract class BaseActivity : Activity() {
    var btAdapter: BluetoothAdapter? = null

    // do not resend request to enable Bluetooth
    // if there is a request already in progress
    // See: https://code.google.com/p/android/issues/detail?id=24931#c1
    var pendingRequestEnableBt = false

    // ==========================================================================
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)

        actionBar?.setHomeButtonEnabled(false)
        checkBluetoothPermission(REQUEST_ENABLE_BT)
        if (state != null) {
            pendingRequestEnableBt = state.getBoolean(SAVED_PENDING_REQUEST_ENABLE_BT)
        }
        btAdapter = BluetoothAdapter.getDefaultAdapter()
        if (btAdapter == null) {
            val no_bluetooth = getString(R.string.no_bt_support)
            showAlertDialog(no_bluetooth)
            Utils.log(no_bluetooth)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        btAdapter = null
    }

    // ==========================================================================
    protected fun checkBluetoothPermission(requestCode: Int): Boolean {
        val permission: String
        permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), requestCode)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val grantResult: Int
        grantResult = if (grantResults.size > 0) grantResults[0] else PackageManager.PERMISSION_DENIED
        if (requestCode == REQUEST_ENABLE_BT && grantResult != PackageManager.PERMISSION_GRANTED) {
            val message = getString(R.string.no_permission)
            showAlertDialog(message)
            Utils.log(message)
        }
    }

    public override fun onStart() {
        super.onStart()
        if (btAdapter == null) return
        if (!btAdapter!!.isEnabled && !pendingRequestEnableBt) {
            pendingRequestEnableBt = true
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (checkBluetoothPermission(REQUEST_ENABLE_BT)) startActivityForResult(enableIntent, REQUEST_ENABLE_BT)
        }
    }

    // ==========================================================================
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SAVED_PENDING_REQUEST_ENABLE_BT, pendingRequestEnableBt)
    }

    // ==========================================================================
    val isAdapterReady: Boolean
        /**
         * Проверка адаптера
         *
         * @return - true, если готов к работе
         */
        get() = btAdapter != null && btAdapter!!.isEnabled
    // ==========================================================================
    /**
     * Показывает диалоговое окно с предупреждением.
     * TODO: При переконфигурациях будет теряться
     *
     * @param message - сообщение
     */
    fun showAlertDialog(message: String?) {
        val alertDialogBuilder = AlertDialog.Builder(this)
                .setTitle(getString(R.string.app_name))
                .setMessage(message)
        val alertDialog = alertDialogBuilder.create()
        alertDialog.setCancelable(false)
        alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, getString(android.R.string.ok)) { dialogInterface, i -> finish() }
        alertDialog.show()
    } // ==========================================================================

    companion object {
        // Intent request codes
        const val REQUEST_CONNECT_DEVICE = 1
        const val REQUEST_ENABLE_BT = 2

        // Message types sent from the DeviceConnector Handler
        const val MESSAGE_STATE_CHANGE = 1
        const val MESSAGE_READ = 2
        const val MESSAGE_WRITE = 3
        const val MESSAGE_DEVICE_NAME = 4
        const val MESSAGE_TOAST = 5
        private const val SAVED_PENDING_REQUEST_ENABLE_BT = "PENDING_REQUEST_ENABLE_BT"
    }
}
