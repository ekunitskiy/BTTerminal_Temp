package com.gmail.evgen622.bt_thickness_gauge

class PackageManager {

    val UByteArray.opCode
        get() = this[opCodePosition]

    val UByteArray.data
        get() = this.sliceArray(dataStartPosition..this.size - 2)

    fun generateDataPackage(opCode: UByte, data: UByteArray): UByteArray? {
        if (data.size > UShort.MAX_VALUE.toInt()) throw Throwable("Error. Data size: ${data.size}")

        var dataPackage = UByteArray(0)
        val head = generateDataPackageHead(data.size.toUShort())

        var dataForCheckSum = UByteArray(0)
        dataForCheckSum += opCode
        dataForCheckSum += data
        val checkSum = calculateChecksum(dataForCheckSum)

        dataPackage += head
        dataPackage += opCode
        dataPackage += data
        dataPackage += checkSum

        return dataPackage
    }

    fun validatePackage(byteArray: UByteArray): Boolean {
        val opCodeDataArray = ubyteArrayOf(byteArray.opCode, *byteArray.data)
        val calculatedCheckSum = calculateChecksum(opCodeDataArray)
        val inputCheckSum = byteArray[byteArray.size - 1]
        return calculatedCheckSum == inputCheckSum
    }

    fun generateDataPackageHead(size: UShort): UByteArray {
        val length = (size + opcodeSize.toUShort()).toUShort().generateLengthBytes()
        val head = UByteArray(3)
        head[0] = uartStartByte.toUByte()
        head[1] = length[0]
        head[2] = length[1]
        return head
    }

    fun UShort.generateLengthBytes(): UByteArray {
        val highByte = (this.toInt() ushr 8).toUByte()
        val lowByte = this.toUByte()
        val result = UByteArray(2)
        result[0] = highByte
        result[1] = lowByte
        return result
    }

    fun calculateChecksum(data: UByteArray): UByte =
        data.reduce { acc, uByte -> (acc + uByte).toUByte() }

    companion object {
        val uartStartByte = 0xAA.toUByte()
        val opCodePosition = 4
        val dataStartPosition = 5
        private val opcodeSize = 1.toUByte()
        private val headSize = 3.toUByte()
        private val chkSumSize = 1.toUByte()
    }
}




