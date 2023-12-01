package com.example.btterminal_for_ai

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.ParcelUuid
import com.example.btterminal_for_ai.bluetooth.BluetoothUtils.getDeviceUuids
import ru.sash0k.bluetooth_terminal.bluetooth.BluetoothUtils.getDeviceUuids

@SuppressLint("MissingPermission")
class DeviceData(device: BluetoothDevice, emptyName: String) {
    val name: String
    val address: String
    val bondState: Int
    val uuids: ArrayList<ParcelUuid>
    val deviceClass: Int
    val majorDeviceClass: Int

    init {
        // TODO Add permissions checks
        name = device.name.ifEmpty { emptyName }
        address = device.address

        // We might need BluetoothDevice.BOND_NONE in some cases
        bondState = device.bondState

        deviceClass = device.bluetoothClass.deviceClass
        majorDeviceClass = device.bluetoothClass.majorDeviceClass
        uuids = getDeviceUuids(device)
    }
}
