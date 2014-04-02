package com.digitalpetri.modbus.serialization

import com.digitalpetri.modbus._
import io.netty.buffer.ByteBuf
import scala.util.Try


class ModbusResponseEncoder extends ModbusPduEncoder {

  def encode(pdu: ModbusPdu, buffer: ByteBuf): Try[Unit] = {
    pdu.asInstanceOf[ModbusResponse] match {
      case r: ReadCoilsResponse               => encodeReadCoils(r, buffer)
      case r: ReadDiscreteInputsResponse      => encodeReadDiscreteInputs(r, buffer)
      case r: ReadHoldingRegistersResponse    => encodeReadHoldingRegisters(r, buffer)
      case r: ReadInputRegistersResponse      => encodeReadInputRegisters(r, buffer)
      case r: WriteSingleCoilResponse         => encodeWriteSingleCoil(r, buffer)
      case r: WriteSingleRegisterResponse     => encodeWriteSingleRegister(r, buffer)
      case r: WriteMultipleCoilsResponse      => encodeWriteMultipleCoils(r, buffer)
      case r: WriteMultipleRegistersResponse  => encodeWriteMultipleRegisters(r, buffer)
      case r: MaskWriteRegisterResponse       => encodeMaskWriteRegister(r, buffer)
      case r: ExceptionResponse               => encodeExceptionResponse(r, buffer)
    }
  }

  def encodeReadCoils(response: ReadCoilsResponse, buffer: ByteBuf): Try[Unit] = Try {
    buffer.writeByte(response.functionCode.functionCode)

    val byteCount = (response.coils.length + 7) / 8
    buffer.writeByte(byteCount)

    response.coils.sliding(8, 8).map(bits2Int).foreach(buffer.writeByte)
  }

  def encodeReadDiscreteInputs(response: ReadDiscreteInputsResponse, buffer: ByteBuf): Try[Unit] = Try {
    buffer.writeByte(response.functionCode.functionCode)

    val byteCount = (response.inputs.length + 7) / 8
    buffer.writeByte(byteCount)

    response.inputs.sliding(8, 8).map(bits2Int).foreach(buffer.writeByte)
  }

  def encodeReadHoldingRegisters(response: ReadHoldingRegistersResponse, buffer: ByteBuf): Try[Unit] = Try {
    buffer.writeByte(response.functionCode.functionCode)

    val byteCount = response.registers.length * 2
    buffer.writeByte(byteCount)

    response.registers.foreach(s => buffer.writeShort(s))
  }

  def encodeReadInputRegisters(response: ReadInputRegistersResponse, buffer: ByteBuf): Try[Unit] = Try {
    buffer.writeByte(response.functionCode.functionCode)

    val byteCount = response.registers.length * 2
    buffer.writeByte(byteCount)

    response.registers.foreach(s => buffer.writeShort(s))
  }

  def encodeWriteMultipleCoils(response: WriteMultipleCoilsResponse, buffer: ByteBuf): Try[Unit] = Try {
    buffer.writeByte(response.functionCode.functionCode)
    buffer.writeShort(response.startingAddress)
    buffer.writeShort(response.quantity)
  }

  def encodeWriteMultipleRegisters(response: WriteMultipleRegistersResponse, buffer: ByteBuf): Try[Unit] = Try {
    buffer.writeByte(response.functionCode.functionCode)
    buffer.writeShort(response.startingAddress)
    buffer.writeShort(response.quantity)
  }

  def encodeWriteSingleCoil(response: WriteSingleCoilResponse, buffer: ByteBuf): Try[Unit] = Try {
    buffer.writeByte(response.functionCode.functionCode)
    buffer.writeShort(response.coilAddress)

    val coilStatus = if (response.coilStatus) 0xFF00 else 0x0000
    buffer.writeShort(coilStatus)
  }

  def encodeWriteSingleRegister(response: WriteSingleRegisterResponse, buffer: ByteBuf): Try[Unit] = Try {
    buffer.writeByte(response.functionCode.functionCode)
    buffer.writeShort(response.registerAddress)
    buffer.writeShort(response.registerValue)
  }

  def encodeMaskWriteRegister(response: MaskWriteRegisterResponse, buffer: ByteBuf): Try[Unit] = Try {
    buffer.writeByte(response.functionCode.functionCode)
    buffer.writeShort(response.referenceAddress)
    buffer.writeShort(response.andMask)
    buffer.writeShort(response.orMask)
  }

  def encodeExceptionResponse(response: ExceptionResponse, buffer: ByteBuf): Try[Unit] = Try {
    buffer.writeByte(response.functionCode.functionCode + 0x80)
    buffer.writeByte(response.exceptionCode.exceptionCode)
  }

}