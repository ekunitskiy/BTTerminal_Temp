/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.sash0k.bluetooth_terminal.bluetooth

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import ru.sash0k.bluetooth_terminal.R

/**
 * This Activity appears as a dialog. It lists already paired devices,
 * and it can scan for devices nearby. When the user selects a device,
 * its MAC address is returned to the caller as the result of this activity.
 */
class DeviceListActivity : Activity() {

    private var mBtAdapter: BluetoothAdapter? = null
    private var scanButton: Button? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.device_list)
    }

    @SuppressLint("MissingPermission")
    override fun onResume() {
        super.onResume()

        // Initialize the button to perform device discovery
        scanButton = findViewById<View>(R.id.button_scan) as Button
        scanButton!!.setOnClickListener { pairNewDevice() }
        val pairedDevicesAdapter = ArrayAdapter<String>(this, R.layout.device_name)
        val pairedListView = findViewById<View>(R.id.paired_devices) as ListView
        pairedListView.adapter = pairedDevicesAdapter
        pairedListView.onItemClickListener = mDeviceClickListener
        mBtAdapter = BluetoothAdapter.getDefaultAdapter()
        val pairedDevices = mBtAdapter?.getBondedDevices()
        pairedDevices?.let {
            if (pairedDevices.isNotEmpty()) {
                pairedListView.isEnabled = true
                for (device in pairedDevices) {
                    val address = device.address
                    pairedDevicesAdapter.add(
                        """
                    ${device.name}
                    $address
                    """.trimIndent())
                }
            } else {
                pairedListView.isEnabled = false
                val noDevices = resources.getText(R.string.none_paired).toString()
                pairedDevicesAdapter.add(noDevices)
            }
        }
    }

    /**
     * Start device discover with the system settings UI
     */
    private fun pairNewDevice() {
        startActivity(Intent().setAction(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    private val mDeviceClickListener = OnItemClickListener { av, v, arg2, arg3 ->
        // Get the device MAC address, which is the last 17 chars in the View
        val info = (v as TextView).text
        if (info != null) {
            // TODO this is not so cool...
            val address: CharSequence = info.toString().substring(info.length - 17)
            val intent = Intent()
            intent.putExtra(EXTRA_DEVICE_ADDRESS, address)
            setResult(RESULT_OK, intent)
            finish()
        }
    }

    companion object {

        const val EXTRA_DEVICE_ADDRESS = "device_address"
    }
}
