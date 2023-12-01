package ru.sash0k.bluetooth_terminal.activity

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.preference.PreferenceManager
import android.text.Html
import android.text.InputFilter
import android.text.InputType
import android.text.method.ScrollingMovementMethod
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import ru.sash0k.bluetooth_terminal.DeviceData
import ru.sash0k.bluetooth_terminal.R
import ru.sash0k.bluetooth_terminal.Utils
import ru.sash0k.bluetooth_terminal.Utils.InputFilterHex
import ru.sash0k.bluetooth_terminal.bluetooth.DeviceConnector
import ru.sash0k.bluetooth_terminal.bluetooth.DeviceListActivity
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DeviceControlActivity : BaseActivity() {
    private var handler: BluetoothResponseHandler? = null

    private var logHtml: StringBuilder? = null
    private var logTextView: TextView? = null
    private var commandEditText: EditText? = null

    // Настройки приложения
    private var logLimitSize = 0
    private var hexMode = false
    private var checkSum = false
    private var needClean = false
    private var logLimit = false
    private var show_timings = false
    private var show_direction = false
    private var command_ending: String? = null
    private var deviceName: String? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PreferenceManager.setDefaultValues(this, R.xml.settings_activity, false)
        if (handler == null) handler = BluetoothResponseHandler(this) else handler!!.setTarget(this)
        MSG_NOT_CONNECTED = getString(R.string.msg_not_connected)
        MSG_CONNECTING = getString(R.string.msg_connecting)
        MSG_CONNECTED = getString(R.string.msg_connected)
        setContentView(R.layout.activity_terminal)
        if (isConnected && savedInstanceState != null) {
            setDeviceName(savedInstanceState.getString(DEVICE_NAME))
        } else actionBar!!.subtitle = MSG_NOT_CONNECTED
        logHtml = StringBuilder()
        if (savedInstanceState != null) logHtml!!.append(savedInstanceState.getString(LOG))
        logTextView = findViewById<View>(R.id.log_textview) as TextView
        logTextView!!.movementMethod = ScrollingMovementMethod()
        logTextView!!.text = Html.fromHtml(logHtml.toString())
        commandEditText = findViewById<View>(R.id.command_edittext) as EditText
        // soft-keyboard send button
        commandEditText!!.setOnEditorActionListener(OnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCommand(null)
                return@OnEditorActionListener true
            }
            false
        })
        // hardware Enter button
        commandEditText!!.setOnKeyListener(View.OnKeyListener { v, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_ENTER -> {
                        sendCommand(null)
                        return@OnKeyListener true
                    }

                    else -> {}
                }
            }
            false
        })
        val intent = intent
        val action = intent.action
        val type = intent.type
        if (Intent.ACTION_SEND == action && type != null) {
            if ("text/plain" == type) {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (sharedText != null) {
                    val commandEditText = findViewById<View>(R.id.command_edittext) as EditText
                    commandEditText.setText(sharedText)
                }
            }
        }
    }

    // ==========================================================================
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(DEVICE_NAME, deviceName)
        if (logTextView != null) {
            outState.putString(LOG, logHtml.toString())
        }
    }

    // ============================================================================
    private val isConnected: Boolean
        /**
         * Проверка готовности соединения
         */
        private get() = connector != null && connector!!.state == DeviceConnector.STATE_CONNECTED
    // ==========================================================================
    /**
     * Разорвать соединение
     */
    private fun stopConnection() {
        if (connector != null) {
            connector!!.stop()
            connector = null
            deviceName = null
        }
    }
    // ==========================================================================
    /**
     * Список устройств для подключения
     */
    private fun startDeviceListActivity() {
        stopConnection()
        val serverIntent = Intent(this, DeviceListActivity::class.java)
        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE)
    }
    // ============================================================================
    /**
     * Обработка аппаратной кнопки "Поиск"
     *
     * @return
     */
    override fun onSearchRequested(): Boolean {
        if (super.isAdapterReady) startDeviceListActivity()
        return false
    }

    // ==========================================================================
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.device_control_activity, menu)
        val bluetooth = menu.findItem(R.id.menu_search)
        bluetooth?.setIcon(if (isConnected) R.drawable.ic_action_device_bluetooth_connected else R.drawable.ic_action_device_bluetooth)
        return true
    }

    // ============================================================================
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_search -> {
                if (super.isAdapterReady) {
                    if (isConnected) stopConnection() else startDeviceListActivity()
                } else {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    if (checkBluetoothPermission(REQUEST_ENABLE_BT)) startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                }
                true
            }

            R.id.menu_clear -> {
                if (logTextView != null) logTextView!!.text = ""
                true
            }

            R.id.menu_send -> {
                if (logTextView != null) {
                    val msg = logTextView!!.text.toString()
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.type = "text/plain"
                    intent.putExtra(Intent.EXTRA_TEXT, msg)
                    startActivity(Intent.createChooser(intent, getString(R.string.menu_send)))
                }
                true
            }

            R.id.menu_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    // ============================================================================
    override fun onStart() {
        super.onStart()

        // hex mode
        val mode = Utils.getPrefence(this, getString(R.string.pref_commands_mode))
        hexMode = "HEX" == mode
        if (hexMode) {
            commandEditText!!.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            commandEditText!!.filters = arrayOf<InputFilter>(InputFilterHex())
        } else {
            commandEditText!!.inputType = InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            commandEditText!!.filters = arrayOf()
        }

        // checksum
        val checkSum = Utils.getPrefence(this, getString(R.string.pref_checksum_mode))
        this.checkSum = "Modulo 256" == checkSum

        // Окончание строки
        command_ending = commandEnding

        // Формат отображения лога команд
        show_timings = Utils.getBooleanPrefence(this, getString(R.string.pref_log_timing))
        show_direction = Utils.getBooleanPrefence(this, getString(R.string.pref_log_direction))
        needClean = Utils.getBooleanPrefence(this, getString(R.string.pref_need_clean))
        logLimit = Utils.getBooleanPrefence(this, getString(R.string.pref_log_limit))
        logLimitSize = Utils.formatNumber(Utils.getPrefence(this, getString(R.string.pref_log_limit_size)))
    }

    // ============================================================================
    private val commandEnding: String
        /**
         * Получить из настроек признак окончания команды
         */
        private get() {
            var result = Utils.getPrefence(this, getString(R.string.pref_commands_ending))
            result = if (result == "\\r\\n") "\r\n" else if (result == "\\n") "\n" else if (result == "\\r") "\r" else ""
            return result
        }

    // ============================================================================
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CONNECT_DEVICE ->                 // When DeviceListActivity returns with a device to connect
                if (resultCode == RESULT_OK) {
                    val address = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS)
                    val device = btAdapter?.getRemoteDevice(address)
                    device?.let {
                        if (super.isAdapterReady && connector == null) setupConnector(device)
                    }
                }

            REQUEST_ENABLE_BT -> {
                // When the request to enable Bluetooth returns
                super.pendingRequestEnableBt = false
                if (resultCode != RESULT_OK) {
                    Utils.log("BT not enabled")
                }
            }
        }
    }
    // ==========================================================================
    /**
     * Установка соединения с устройством
     */
    private fun setupConnector(connectedDevice: BluetoothDevice) {
        stopConnection()
        try {
            val emptyName = getString(R.string.empty_device_name)
            val data = DeviceData(connectedDevice, emptyName)
            handler?.let {
                connector = DeviceConnector(data, it)
                connector!!.connect()
            }
        } catch (e: IllegalArgumentException) {
            Utils.log("setupConnector failed: " + e.message)
        }
    }

    // ==========================================================================
    /**
     * Отправка команды устройству
     */
    fun sendCommand(view: View?) {
        if (commandEditText != null) {
            var commandString = commandEditText!!.text.toString()
            if (commandString.isEmpty()) return

            // Дополнение команд в hex
            if (hexMode && commandString.length % 2 == 1) {
                commandString = "0$commandString"
                commandEditText!!.setText(commandString)
            }

            // checksum
            if (checkSum) {
                commandString += Utils.calcModulo256(commandString)
            }
            var command = if (hexMode) Utils.toHex(commandString) else commandString.toByteArray()
            if (command_ending != null) command = Utils.concat(command, command_ending!!.toByteArray())
            if (isConnected) {
                connector!!.write(command)
                appendLog(commandString, hexMode, true, needClean)
            }
        }
    }
    // ==========================================================================
    /**
     * Добавление ответа в лог
     *
     * @param message  - текст для отображения
     * @param outgoing - направление передачи
     * @param clean - удалять команду из поля ввода после отправки
     */
    fun appendLog(message: String, hexMode: Boolean, outgoing: Boolean, clean: Boolean) {

        // если установлено ограничение на логи, проверить и почистить
        var message = message
        if (logLimit && logLimitSize > 0 && logTextView!!.lineCount > logLimitSize) {
            logTextView!!.text = ""
        }
        val msg = StringBuilder()
        if (show_timings) msg.append("[").append(timeformat.format(Date())).append("]")
        if (show_direction) {
            val arrow = if (outgoing) " << " else " >> "
            msg.append(arrow)
        } else msg.append(" ")

        // Убрать символы переноса строки \r\n
        message = message.replace("\r", "").replace("\n", "")

        // Проверка контрольной суммы ответа
        var crc = ""
        var crcOk = false
        if (checkSum) {
            val crcPos = message.length - 2
            crc = message.substring(crcPos)
            message = message.substring(0, crcPos)
            crcOk = outgoing || crc == Utils.calcModulo256(message).uppercase(Locale.getDefault())
            if (hexMode) crc = Utils.printHex(crc.uppercase(Locale.getDefault()))
        }

        // Лог в html
        msg.append("<b>")
                .append(if (hexMode) Utils.printHex(message) else message)
                .append(if (checkSum) Utils.mark(crc, if (crcOk) CRC_OK else CRC_BAD) else "")
                .append("</b>")
                .append("<br>")
        logHtml!!.append(msg)
        logTextView!!.append(Html.fromHtml(msg.toString()))
        val scrollAmount = logTextView!!.layout.getLineTop(logTextView!!.lineCount) - logTextView!!.height
        if (scrollAmount > 0) logTextView!!.scrollTo(0, scrollAmount) else logTextView!!.scrollTo(0, 0)
        if (clean) commandEditText!!.setText("")
    }

    // =========================================================================
    fun setDeviceName(deviceName: String?) {
        this.deviceName = deviceName
        actionBar!!.subtitle = deviceName
    }
    // ==========================================================================
    /**
     * Обработчик приёма данных от bluetooth-потока
     */
    private class BluetoothResponseHandler(activity: DeviceControlActivity) : Handler() {
        private var mActivity: WeakReference<DeviceControlActivity>

        init {
            mActivity = WeakReference(activity)
        }

        fun setTarget(target: DeviceControlActivity) {
            mActivity.clear()
            mActivity = WeakReference(target)
        }

        override fun handleMessage(msg: Message) {
            val activity = mActivity.get()
            if (activity != null) {
                when (msg.what) {
                    MESSAGE_STATE_CHANGE -> {
                        Utils.log("MESSAGE_STATE_CHANGE: " + msg.arg1)
                        val bar = activity.actionBar
                        when (msg.arg1) {
                            DeviceConnector.STATE_CONNECTED -> bar!!.subtitle = MSG_CONNECTED
                            DeviceConnector.STATE_CONNECTING -> bar!!.subtitle = MSG_CONNECTING
                            DeviceConnector.STATE_NONE -> bar!!.subtitle = MSG_NOT_CONNECTED
                        }
                        activity.invalidateOptionsMenu()
                    }

                    MESSAGE_READ -> {
                        val readMessage = msg.obj as String
                        if (readMessage != null) {
                            activity.appendLog(readMessage, false, false, false)
                        }
                    }

                    MESSAGE_DEVICE_NAME -> activity.setDeviceName(msg.obj as String)
                    MESSAGE_WRITE -> {}
                    MESSAGE_TOAST -> {}
                }
            }
        }
    } // ==========================================================================

    companion object {
        private const val DEVICE_NAME = "DEVICE_NAME"
        private const val LOG = "LOG"

        // Подсветка crc
        private const val CRC_OK = "#FFFF00"
        private const val CRC_BAD = "#FF0000"
        private val timeformat = SimpleDateFormat("HH:mm:ss.SSS")
        private var MSG_NOT_CONNECTED: String? = null
        private var MSG_CONNECTING: String? = null
        private var MSG_CONNECTED: String? = null
        private var connector: DeviceConnector? = null
    }
}
