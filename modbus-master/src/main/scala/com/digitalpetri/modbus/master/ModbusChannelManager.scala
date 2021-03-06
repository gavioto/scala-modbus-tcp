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

package com.digitalpetri.modbus.master

import com.digitalpetri.modbus.layers.{ModbusTcpDecoder, ModbusTcpEncoder}
import com.digitalpetri.modbus.serialization.{ModbusRequestEncoder, ModbusResponseDecoder}
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel._
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.logging.{LogLevel, LoggingHandler}

import scala.concurrent.{ExecutionContext, Promise}

class ModbusChannelManager(master: ModbusTcpMaster, config: ModbusTcpMasterConfig) extends AbstractChannelManager {

  protected implicit val executionContext = ExecutionContext.fromExecutor(config.executor)

  def connect(channelPromise: Promise[Channel]): Unit = {
    val bootstrap = new Bootstrap

    val initializer = new ChannelInitializer[SocketChannel] {
      def initChannel(channel: SocketChannel) {
        def loggerName(handler: String) = config.instanceId match {
          case Some(instanceId) => s"${classOf[ModbusTcpMaster]}.$instanceId.$handler"
          case None => s"${classOf[ModbusTcpMaster]}.$handler"
        }

        channel.pipeline.addLast(new LoggingHandler(loggerName("ByteLogger"), LogLevel.TRACE))
        channel.pipeline.addLast(new ModbusTcpEncoder(new ModbusRequestEncoder))
        channel.pipeline.addLast(new ModbusTcpDecoder(new ModbusResponseDecoder, config.instanceId, master.decodingErrorCount, master.unsupportedPduCount))
        channel.pipeline.addLast(new LoggingHandler(loggerName("MessageLogger"), LogLevel.TRACE))
        channel.pipeline.addLast(new ModbusTcpResponseDispatcher(master, executionContext))
      }
    }

    bootstrap.group(config.eventLoop)
      .channel(classOf[NioSocketChannel])
      .handler(initializer)
      .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Int.box(config.timeout.toMillis.toInt))
      .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)

    bootstrap.connect(config.host, config.port).addListener(new ChannelFutureListener {
      def operationComplete(future: ChannelFuture): Unit = {
        if (future.isSuccess) {
          channelPromise.success(future.channel())
        } else {
          channelPromise.failure(future.cause())
        }
      }
    })
  }

}
