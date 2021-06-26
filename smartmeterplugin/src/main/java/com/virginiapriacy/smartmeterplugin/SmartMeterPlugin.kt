package com.virginiapriacy.smartmeterplugin

import com.virginiaprivacy.drivers.sdr.Plugin
import com.virginiaprivacy.drivers.sdr.RTLDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.math.pow

@ExperimentalCoroutinesApi
@OptIn(ExperimentalUnsignedTypes::class)
@ExperimentalStdlibApi
class SmartMeterPlugin(override val device: RTLDevice) : Plugin {
    override fun getForEachBuffer(bytes: ByteArray) {
        val mag = when ((bytes.size - 1) % 2) {
            0 -> magnitude(bytes.toUByteArray())
            else -> magnitude(bytes.copyOf(bytes.size - 1).toUByteArray())
        }
        println(bytes.map { it.toUInt().shr(4) })
    }

    override fun setup() {
        device.run {
            setI2cRepeater(1)
            setGain(false)
            setI2cRepeater(0)
            setAGCMode(true)
            setI2cRepeater(1)
            setFrequency(R900_CENTER_FREQ)
            setI2cRepeater(0)
            setSampleRate(SAMLE_RATE)
            setBiasTee(true)
            resetBuffer()
        }
    }

    fun magnitude(bytes: UByteArray): UIntArray {
        val dubs = UIntArray(bytes.size / 2)
        var i = 0
        for (i2 in dubs.indices) {
            dubs[i2] = ((lutSquares[bytes[i].toInt()] + lutSquares[bytes[i + 1].toInt()]).toUInt())
            i++
        }
        return dubs
    }

    companion object {
        const val R900_CENTER_FREQ = 912600000
        const val SAMLE_RATE = 2359000

        fun Int.abs8(): Int {
            return if (this >= 127) {
                this - 127
            } else
                127 - this
        }

        val preamble = arrayOf(
            1,
            0,
            1,
            0,
            1,
            0,
            1,
            0,
            1,
            0,
            0,
            1,
            0,
            1,
            1,
            0,
            0,
            1,
            1,
            0,
            0,
            1,
            1,
            0,
            0,
            1,
            0,
            1,
            1,
            0,
            1,
            0,
            0,
            1,
            0,
            1,
            0,
            1,
            0,
            1,
            0,
            1
        )

        val lutSquares = (0..256).map {
            val i = it.abs8()
            (i * i).toUShort()

        }

    }
}



@ExperimentalCoroutinesApi
@ExperimentalStdlibApi
fun main() {
    println(SmartMeterPlugin.lutSquares)
}