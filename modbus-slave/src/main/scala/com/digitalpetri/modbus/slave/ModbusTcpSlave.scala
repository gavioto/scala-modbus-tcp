/*
 * Copyright 2014 Kevin Herron
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.digitalpetri.modbus.slave

import java.net.SocketAddress
import java.util
import java.util.concurrent.atomic.AtomicReference

import com.codahale.metrics.{Counter, Metric, MetricRegistry, MetricSet}
import com.digitalpetri.modbus.layers.{ModbusTcpDecoder, ModbusTcpEncoder}
import com.digitalpetri.modbus.serialization.{ModbusRequestDecoder, ModbusResponseEncoder}
import com.digitalpetri.modbus.slave.ServiceRequest._
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel._
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.logging.{LogLevel, LoggingHandler}
import org.slf4j.LoggerFactory

import scala.collection.concurrent.TrieMap
import scala.concurrent.{Future, Promise}

class ModbusTcpSlave(config: ModbusTcpSlaveConfig) {

  private val logger = config.instanceId match {
    case Some(instanceId) => LoggerFactory.getLogger(s"${getClass.getName}.$instanceId")
    case None => LoggerFactory.getLogger(getClass)
  }

  private val decodingErrorCount  = new Counter()
  private val unsupportedPduCount = new Counter()
  private val channelCount        = new Counter()

  private val metrics = Map[String, Metric](
    metricName("decoding-error-count")  -> decodingErrorCount,
    metricName("unsupported-pdu-count") -> unsupportedPduCount,
    metricName("channel-count")         -> channelCount)

  private val serverChannels = new TrieMap[SocketAddress, Channel]()
  private val requestHandler = new AtomicReference[ServiceRequestHandler](IllegalFunctionHandler)

  def bind(host: String, port: Int): Future[SocketAddress] = {
    val bootstrap = new ServerBootstrap

    val initializer = new ChannelInitializer[SocketChannel] {
      def initChannel(channel: SocketChannel) {
        channel.pipeline.addLast(new LoggingHandler(LogLevel.TRACE))
        channel.pipeline.addLast(new ModbusTcpDecoder(new ModbusRequestDecoder, config.instanceId, decodingErrorCount, unsupportedPduCount))
        channel.pipeline.addLast(new ModbusTcpEncoder(new ModbusResponseEncoder))
        channel.pipeline.addLast(new ModbusTcpServiceDispatcher(new ServiceHandler, config.executionContext))

        channelCount.inc()
        logger.info(s"channel initialized: $channel")

        channel.closeFuture().addListener(new ChannelFutureListener {
          def operationComplete(future: ChannelFuture): Unit = channelCount.dec()
        })
      }
    }

    bootstrap.group(config.eventLoop)
      .channel(classOf[NioServerSocketChannel])
      .handler(new LoggingHandler(LogLevel.DEBUG))
      .childHandler(initializer)
      .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)

    val bindPromise = Promise[SocketAddress]()

    bootstrap.bind(host, port).addListener(new ChannelFutureListener {
      def operationComplete(f: ChannelFuture): Unit = {
        if (f.isSuccess) {
          val channel = f.channel()
          serverChannels += (channel.localAddress() -> channel)
          bindPromise.success(channel.localAddress())
        } else {
          bindPromise.failure(f.cause())
        }
      }
    })

    bindPromise.future
  }

  def setRequestHandler(requestHandler: ServiceRequestHandler) {
    this.requestHandler.set(requestHandler)
  }

  def getMetricSet: MetricSet = new MetricSet {

    import scala.collection.JavaConversions._

    def getMetrics: util.Map[String, Metric] = metrics
  }

  def shutdown() {
    serverChannels.values.foreach(_.close())
    serverChannels.clear()
    requestHandler.set(IllegalFunctionHandler)
  }

  private def metricName(name: String) =
    MetricRegistry.name(classOf[ModbusTcpSlave], config.instanceId.getOrElse(""), name)

  class ServiceHandler extends ServiceRequestHandler {

    def onReadDiscreteInputs(service: ReadDiscreteInputsService): Unit =
      requestHandler.get().onReadDiscreteInputs(service)

    def onReadCoils(service: ReadCoilsService): Unit =
      requestHandler.get().onReadCoils(service)

    def onReadInputRegisters(service: ReadInputRegistersService): Unit =
      requestHandler.get().onReadInputRegisters(service)

    def onReadHoldingRegisters(service: ReadHoldingRegistersService): Unit =
      requestHandler.get().onReadHoldingRegisters(service)

    def onMaskWriteRegister(service: MaskWriteRegisterService): Unit =
      requestHandler.get().onMaskWriteRegister(service)

    def onWriteMultipleRegisters(service: WriteMultipleRegistersService): Unit =
      requestHandler.get().onWriteMultipleRegisters(service)

    def onWriteMultipleCoils(service: WriteMultipleCoilsService): Unit =
      requestHandler.get().onWriteMultipleCoils(service)

    def onWriteSingleRegister(service: WriteSingleRegisterService): Unit =
      requestHandler.get().onWriteSingleRegister(service)

    def onWriteSingleCoil(service: WriteSingleCoilService): Unit =
      requestHandler.get().onWriteSingleCoil(service)

  }

}

