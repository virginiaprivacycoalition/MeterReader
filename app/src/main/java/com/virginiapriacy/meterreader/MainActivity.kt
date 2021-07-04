package com.virginiapriacy.meterreader

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import com.virginiapriacy.androidusb.AndroidUsbInterface
import com.virginiapriacy.smartmeterplugin.SmartMeterPlugin
import com.virginiaprivacy.drivers.sdr.RTLDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.jetbrains.kotlinx.multik.api.EngineType
import org.jetbrains.kotlinx.multik.api.JvmEngineType
import org.jetbrains.kotlinx.multik.api.NativeEngineType
import org.jetbrains.kotlinx.multik.api.mk

@ExperimentalUnsignedTypes
@ExperimentalCoroutinesApi
@ExperimentalStdlibApi
class MainActivity : AppCompatActivity() {

    val manager: UsbManager by lazy {
        getSystemService(UsbManager::class.java) as UsbManager
    }
    private val usbPermissionIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(this, 0,
            Intent(ACTION_USB_PERMISSION), 0)
    }

    private fun hasUsbDevicePermission(): Boolean {
        return baseContext.checkSelfPermission(ACTION_USB_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    fun readMeters(device: UsbDevice) {
        val androidDevice = AndroidUsbInterface(context, device)
        val dev = RTLDevice.getDevice(androidDevice)
        val plugin = SmartMeterPlugin(dev)
        dev.runPlugin(plugin)
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let {
                            readMeters(it)
                        }
                    } else {
                        getUsbDevicePermission()
                        Log.d("USB", "permission denied for device $device")
                    }
                }
            }
        }
    }

    private fun getUsbDevicePermission() {
        if (manager.deviceList.isEmpty()) {
            Toast.makeText(baseContext, "no devices", Toast.LENGTH_SHORT)
                .show()
        } else {
            manager.requestPermission(
                manager.deviceList.values.first(),
                usbPermissionIntent
            )
        }
    }

    private val filter by lazy {
        IntentFilter(ACTION_USB_PERMISSION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        mk.setEngine(JvmEngineType)
        super.onCreate(savedInstanceState)
        context = this
        registerReceiver(usbReceiver, filter)
        getUsbDevicePermission()
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.button).setOnClickListener {
            getUsbDevicePermission()
            readMeters(manager.deviceList.values.first())
        }
    }

    companion object {
        const val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"
        lateinit var context: Context
    }
}