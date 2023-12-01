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
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Bundle
import android.os.Handler
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import ru.sash0k.bluetooth_terminal.DeviceData
import ru.sash0k.bluetooth_terminal.activity.BaseActivity.Companion.MESSAGE_DEVICE_NAME
import ru.sash0k.bluetooth_terminal.activity.BaseActivity.Companion.MESSAGE_READ
import ru.sash0k.bluetooth_terminal.activity.BaseActivity.Companion.MESSAGE_STATE_CHANGE
import ru.sash0k.bluetooth_terminal.activity.BaseActivity.Companion.MESSAGE_TOAST
import ru.sash0k.bluetooth_terminal.activity.BaseActivity.Companion.MESSAGE_WRITE
import ru.sash0k.bluetooth_terminal.activity.DeviceControlActivity
import ru.sash0k.bluetooth_terminal.bluetooth.BluetoothUtils.createRfcommSocket

class DeviceConnector(
    deviceData: DeviceData,
    private val handler: Handler
) {

    private var mState: Int
    private val btAdapter: BluetoothAdapter
    private val connectedDevice: BluetoothDevice
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null
    private val deviceName: String

    // ==========================================================================
    init {
        btAdapter = BluetoothAdapter.getDefaultAdapter()
        connectedDevice = btAdapter.getRemoteDevice(deviceData.address)
        deviceName = if (deviceData.name == null) deviceData.address else deviceData.name
        mState = STATE_NONE
    }
    // ==========================================================================
    /**
     * Запрос на соединение с устойством
     */
    @Synchronized
    fun connect() {
        if (D) Log.d(TAG, "connect to: $connectedDevice")
        if (mState == STATE_CONNECTING) {
            if (connectThread != null) {
                if (D) Log.d(TAG, "cancel mConnectThread")
                connectThread!!.cancel()
                connectThread = null
            }
        }
        if (connectedThread != null) {
            if (D) Log.d(TAG, "cancel mConnectedThread")
            connectedThread!!.cancel()
            connectedThread = null
        }

        // Start the thread to connect with the given device
        connectThread = ConnectThread(connectedDevice)
        connectThread!!.start()
        state = STATE_CONNECTING
    }
    // ==========================================================================
    /**
     * Завершение соединения
     */
    @Synchronized
    fun stop() {
        if (D) Log.d(TAG, "stop")
        if (connectThread != null) {
            if (D) Log.d(TAG, "cancel mConnectThread")
            connectThread!!.cancel()
            connectThread = null
        }
        if (connectedThread != null) {
            if (D) Log.d(TAG, "cancel mConnectedThread")
            connectedThread!!.cancel()
            connectedThread = null
        }
        state = STATE_NONE
    }

    // ==========================================================================
    // ==========================================================================
    @get:Synchronized
    @set:Synchronized
    var state: Int
        /**
         * Получение состояния устройства
         */
        get() = mState
        /**
         * Установка внутреннего состояния устройства
         *
         * @param state - состояние
         */
        private set(state) {
            if (D) Log.d(TAG, "setState() $mState -> $state")
            mState = state
            handler.obtainMessage(MESSAGE_STATE_CHANGE, state, -1).sendToTarget()
        }

    // ==========================================================================
    @Synchronized
    fun connected(socket: BluetoothSocket) {
        if (D) Log.d(TAG, "connected")

        // Cancel the thread that completed the connection
        if (connectThread != null) {
            if (D) Log.d(TAG, "cancel mConnectThread")
            connectThread!!.cancel()
            connectThread = null
        }
        if (connectedThread != null) {
            if (D) Log.d(TAG, "cancel mConnectedThread")
            connectedThread!!.cancel()
            connectedThread = null
        }
        state = STATE_CONNECTED

        // Send the name of the connected device back to the UI Activity
        val msg = handler.obtainMessage(MESSAGE_DEVICE_NAME, deviceName)
        handler.sendMessage(msg)

        // Start the thread to manage the connection and perform transmissions
        connectedThread = ConnectedThread(socket)
        connectedThread!!.start()
    }

    // ==========================================================================
    fun write(data: ByteArray) {
        var r: ConnectedThread?
        // Synchronize a copy of the ConnectedThread
        synchronized(this) {
            if (mState != STATE_CONNECTED) return
            r = connectedThread
        }

        // Perform the write unsynchronized
        if (data.size == 1) r!!.write(data[0]) else r!!.writeData(data)
    }

    // ==========================================================================
    private fun connectionFailed() {
        if (D) Log.d(TAG, "connectionFailed")

        // Send a failure message back to the Activity
        val msg = handler.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle()
        msg.data = bundle
        handler.sendMessage(msg)
        state = STATE_NONE
    }

    // ==========================================================================
    private fun connectionLost() {
        // Send a failure message back to the Activity
        val msg = handler.obtainMessage(MESSAGE_TOAST)
        val bundle = Bundle()
        msg.data = bundle
        handler.sendMessage(msg)
        state = STATE_NONE
    }
    // ==========================================================================
    /**
     * Класс потока для соединения с BT-устройством
     */
    // ==========================================================================
    private inner class ConnectThread(device: BluetoothDevice) : Thread() {

        private val mmSocket: BluetoothSocket?
        private val mmDevice: BluetoothDevice

        private val TAG = "ConnectThread"
        private val D = false

        init {
            if (Companion.D) Log.d(Companion.TAG, "create ConnectThread")
            mmDevice = device
            mmSocket = createRfcommSocket(mmDevice)
        }
        // ==========================================================================
        /**
         * Основной рабочий метод для соединения с устройством.
         * При успешном соединении передаёт управление другому потоку
         */
        @SuppressLint("MissingPermission")
        override fun run() {
            if (Companion.D) Log.d(Companion.TAG, "ConnectThread run")
            if (mmSocket == null) {
                if (Companion.D) Log.d(Companion.TAG, "unable to connect to device, socket isn't created")
                connectionFailed()
                return
            }

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                mmSocket.connect()
            } catch (e: IOException) {
                // Close the socket
                try {
                    mmSocket.close()
                } catch (e2: IOException) {
                    if (Companion.D) Log.e(Companion.TAG, "unable to close() socket during connection failure", e2)
                }
                connectionFailed()
                return
            }

            // Reset the ConnectThread because we're done
            synchronized(this@DeviceConnector) { connectThread = null }

            // Start the connected thread
            connected(mmSocket)
        }
        // ==========================================================================
        /**
         * Отмена соединения
         */
        fun cancel() {
            if (D) Log.d(TAG, "ConnectThread cancel")
            if (mmSocket == null) {
                if (D) Log.d(TAG, "unable to close null socket")
                return
            }
            try {
                mmSocket.close()
            } catch (e: IOException) {
                if (D) Log.e(TAG, "close() of connect socket failed", e)
            }
        } // ==========================================================================
    }
    // ==========================================================================
    /**
     * Класс потока для обмена данными с BT-устройством
     */
    // ==========================================================================
    private inner class ConnectedThread(socket: BluetoothSocket) : Thread() {

        private val mmSocket: BluetoothSocket
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?

        private val TAG = "ConnectedThread"
        private val D = false

        init {
            if (Companion.D) Log.d(Companion.TAG, "create ConnectedThread")
            mmSocket = socket
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.inputStream
                tmpOut = socket.outputStream
            } catch (e: IOException) {
                if (Companion.D) Log.e(Companion.TAG, "temp sockets not created", e)
            }
            mmInStream = tmpIn
            mmOutStream = tmpOut
        }
        // ==========================================================================
        /**
         * Основной рабочий метод - ждёт входящих команд от потока
         */
        override fun run() {
            if (D) Log.i(TAG, "ConnectedThread run")
            val buffer = ByteArray(512)
            var bytes: Int
            val readMessage = StringBuilder()
            while (true) {
                try {
                    // считываю входящие данные из потока и собираю в строку ответа
                    bytes = mmInStream!!.read(buffer)
                    val readed = String(buffer, 0, bytes)
                    readMessage.append(readed)

                    // маркер конца команды - вернуть ответ в главный поток
                    if (readed.contains("\n")) {
                        handler.obtainMessage(MESSAGE_READ, bytes, -1, readMessage.toString())
                            .sendToTarget()
                        readMessage.setLength(0)
                    }
                } catch (e: IOException) {
                    if (D) Log.e(TAG, "disconnected", e)
                    connectionLost()
                    break
                }
            }
        }
        // ==========================================================================
        /**
         * Записать кусок данных в устройство
         */
        fun writeData(chunk: ByteArray?) {
            try {
                mmOutStream!!.write(chunk)
                mmOutStream.flush()
                // Share the sent message back to the UI Activity
                handler.obtainMessage(MESSAGE_WRITE, -1, -1, chunk).sendToTarget()
            } catch (e: IOException) {
                if (Companion.D) Log.e(Companion.TAG, "Exception during write", e)
            }
        }
        // ==========================================================================
        /**
         * Записать байт
         */
        fun write(command: Byte) {
            val buffer = ByteArray(1)
            buffer[0] = command
            try {
                mmOutStream!!.write(buffer)

                // Share the sent message back to the UI Activity
                handler.obtainMessage(MESSAGE_WRITE, -1, -1, buffer).sendToTarget()
            } catch (e: IOException) {
                if (Companion.D) Log.e(Companion.TAG, "Exception during write", e)
            }
        }
        // ==========================================================================
        /**
         * Отмена - закрытие сокета
         */
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                if (Companion.D) Log.e(Companion.TAG, "close() of connect socket failed", e)
            }
        } // ==========================================================================
    } // ==========================================================================

    companion object {

        private const val TAG = "DeviceConnector"
        private const val D = false

        // Constants that indicate the current connection state
        const val STATE_NONE = 0 // we're doing nothing
        const val STATE_CONNECTING = 1 // now initiating an outgoing connection
        const val STATE_CONNECTED = 2 // now connected to a remote device
    }
}
