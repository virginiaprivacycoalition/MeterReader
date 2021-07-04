package com.virginiapriacy.smartmeterplugin

import com.virginiaprivacy.drivers.sdr.Plugin
import com.virginiaprivacy.drivers.sdr.RTLDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.api.toNDArray
import org.jetbrains.kotlinx.multik.ndarray.data.*
import org.jetbrains.kotlinx.multik.ndarray.operations.*
import java.util.*
import kotlin.math.roundToInt

@ExperimentalCoroutinesApi
@OptIn(ExperimentalUnsignedTypes::class)
@ExperimentalStdlibApi
class SmartMeterPlugin(override val device: RTLDevice) : Plugin {
    override fun getForEachBuffer(bytes: ByteArray) {
        val mag = when ((bytes.size - 1) % 2) {
            0 -> magnitude(bytes.toUByteArray())
            else -> magnitude(bytes.copyOf(bytes.size - 1).toUByteArray())
        }
        val ndarray = mk.ndarray(mag.toShortArray())
        val size = SYMBOL_LENGTH / 8
        var r = csum(ndarray, size)
        var s = csum(r, (MESSAGE_LENGTH * 0.5).roundToInt())
        r = (r.asD1Array()[Slice(0, s.size, 1)] - s)
        val bits = r.map { when (it > 0) {
            true -> 1
            false -> 0
        } }
       // val packedLength =
       // println("bits: ${r.size}, bytes: ${r.size / 8}")
      //  val decimate = bits.slice(Slice(0, bits.size, REDUCED_PREAMBLE_SIZE))
        var i = 0
        while (i + preamble.size <= bits.size) {
            val subList = bits.toList().subList(i, i + preamble.size)
            val correlation = PearsonsCorrelation()
                .correlation(
                    subList.toIntArray().map { it.toDouble() }.toDoubleArray(),
                    preamble.map { it.toDouble() }.toDoubleArray()
                )
            if (correlation == 0.0) {
                println(bits.toList().subList(i, bits.size))
            }
            i++

        }


//        println(mk.math.cumSum(ndarray).toList())
//        val cumSum = cumsum(ndarray, (SYMBOL_LENGTH / 8))
//        println(cumSum.asD1Array().toList())
//        val movingAverage = mag.asSequence()
//            .windowed(size)
//            .map { it.sum() / it.size.toUInt() }
//        val rm = movingAverage
//            .windowed((MESSAGE_LENGTH * 0.5).toInt())
//            .map { it.sum() / it.size.toUInt() }
//            .toList()
//        val filter = movingAverage.map { rm[it.toInt()] - it }
//            .filter { it == 0u || it == 1u }
//            .toList()
//        filter
//       // println(filter.count())
//            for (i in 0..filter.count()) {
//                if (preamble.size + i >= filter.count()) {
//                    break
//                }
//                val darray = filter.subList(i, i + (REDUCED_W * 4)).map { it.toDouble() }.toDoubleArray()
//                //println(darray.toList())
//            }
//        println(
//
//            .toList()
//
////            .windowed(MESSAGE_LENGTH / 2)
////            .map { it.sum() / it.size.toUInt() }
//           )
    }

    private fun csum(
        ndarray: D1Array<Short>,
        size: Int
    ): NDArray<Short, D1> {
       // val windowed = ndarray.windowed(size.toInt(), 1)
        val cumSum = mk.math.cumSum(ndarray)
        return ((cumSum[Slice(size.toInt(), cumSum.size, 1)] - cumSum[Slice(0, (cumSum.size - size.toInt()), 1)]) / size.toInt().toShort())
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
            setSampleRate(SAMPLE_RATE)
            setBiasTee(true)
            resetBuffer()
        }
    }

    fun <T : Number, D : Dimension> cumsum(ndArray: NDArray<T, D>, width: Int): NDArray<T, D1> {
        val result = mk.math.cumSum(ndArray)
        return (result[width..1..1] - result[1..result.size - width..1]) / (width as T)
    }

    fun <T : Number, D : Dimension> movingAverage(ndArray: NDArray<T, D>, width: Int): NDArray<T, D1> {
        return cumsum(ndArray, width)
    }

    fun magnitude(bytes: UByteArray): UShortArray {
        val dubs = UShortArray(bytes.size / 2)
        var i = 0
        for (i2 in dubs.indices) {
            dubs[i2] = ((lutSquares[bytes[i].toInt()] + lutSquares[bytes[i + 1].toInt()]).toUShort())
            i++
        }
        return dubs
    }

    companion object {
        const val R900_CENTER_FREQ = 912600000
        const val SAMPLE_RATE = 2359000

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
        const val SYMBOL_LENGTH = 72

        const val MESSAGE_LENGTH = 2 * 12 * 72

        const val DECIMATION = 24

        const val REDUCED_W = SYMBOL_LENGTH / DECIMATION

        val preambleSearch = preamble.take(16).plus(preamble.take(16)).plus(preamble.take(16))

        val lutSquares = (0..256).map {
            val i = it.abs8()
            (i * i).toUShort()
        }

        val mkSquares = (0..256).map {
            val i = it.abs8()
            (i * i)
        }.toNDArray()




        val REDUCED_PREAMBLE_SIZE = REDUCED_W * preamble.size
    }
}

private operator fun <E> List<E>.times(multiple: Int): MutableList<E> {
    var a = this.toMutableList()
    for (i in 1..multiple) {
        a = a.plus(this) as MutableList<E>
    }
    return a

}


@ExperimentalCoroutinesApi
@ExperimentalStdlibApi
fun main() {
    println(SmartMeterPlugin.lutSquares)
}