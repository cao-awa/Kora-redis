package com.github.cao.awa.kora.redis.client

import com.github.cao.awa.com.github.cao.awa.kora.redis.config.KoraRedisClientConfig
import com.github.cao.awa.kora.plugin.registerCleaner
import com.github.cao.awa.kora.status.KoraStatus
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.Socket
import java.util.Random

class KoraRedisClient(private val config: KoraRedisClientConfig) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger("KoraRedisClient")
        private var REAL_INSTANCE: KoraRedisClient? = null
        val INSTANCE: KoraRedisClient
            get() = REAL_INSTANCE!!
        private lateinit var daemonThread: Thread

        fun init(config: KoraRedisClientConfig) {
            REAL_INSTANCE = KoraRedisClient(config)

            try {
                INSTANCE.connect()

                KoraStatus.registerLifecycle("Kora-redis", INSTANCE)

                KoraStatus.registerReloadListener {
                    INSTANCE.close()
                    this.daemonThread.interrupt()
                    KoraStatus.completedLifecycle(INSTANCE)
                }

                KoraStatus.registerStopListener {
                    INSTANCE.close()
                    this.daemonThread.interrupt()
                    KoraStatus.completedLifecycle(INSTANCE)
                }

                registerCleaner("kora-redis-instance") {
                    REAL_INSTANCE = null
                    LOGGER.info("Kora Redis client lifecycle ending")
                }

                val random = Random()
                this.daemonThread = Thread.startVirtualThread {
                    try {
                        while (INSTANCE.isRunning) {
                            Thread.sleep(1000)
                            try {
                                INSTANCE["${random.nextInt()}"]
                            } catch (e: Exception) {
                                LOGGER.warn("Redis server closed")
                                INSTANCE.close()
                                while (!INSTANCE.isRunning) {
                                    Thread.sleep(config.reconnectTime().toLong())
                                    LOGGER.info("Trying to reconnect to Redis server")
                                    try {
                                        INSTANCE.connect()
                                        LOGGER.info("Connected to Redis server on ${config.host()}:${config.port()}")
                                    } catch (e: ConnectException) {
                                        LOGGER.warn("Failed to reconnect to Redis server, try again after {} ms", config.reconnectTime())
                                    }
                                }
                            }
                        }
                    } catch (e: InterruptedException) {
                        LOGGER.info("Kora Redis client daemon thread exited")
                    }
                }

                LOGGER.info("Initialized Redis client, connected to ${config.host()}:${config.port()}")
            } catch (e: Exception) {
                throw IllegalStateException("Cannot initialize Redis client", e)
            }
        }
    }

    private var isRunning = false
    private lateinit var socket: Socket
    private lateinit var reader: BufferedReader
    private lateinit var writer: BufferedWriter

    fun connect() {
        val socket = Socket(this.config.host(), this.config.port())
        socket.tcpNoDelay = true
        this.socket = socket
        this.reader = BufferedReader(InputStreamReader(this.socket.getInputStream()))
        this.writer = BufferedWriter(OutputStreamWriter(this.socket.getOutputStream()))
        this.isRunning = true
    }

    fun isClosed(): Boolean {
        return this.socket.isClosed
    }

    operator fun set(key: String, value: String) {
        sendCommand("SET", key, value)
        val response = readResponse()
        if (response != "OK") {
            throw RuntimeException("SET failed: $response")
        }
    }

    operator fun get(key: String): String? {
        sendCommand("GET", key)
        return readResponse() as? String
    }

    private fun sendCommand(vararg args: String) {
        this.writer.write("*${args.size}\r\n")
        for (arg in args) {
            this.writer.write("$${arg.length}\r\n$arg\r\n")
        }
        this.writer.flush()
    }

    private fun readResponse(): Any? {
        val line = this.reader.readLine() ?: throw IllegalStateException("Connection closed")
        return when (val type = line[0]) {
            '+' -> line.substring(1)
            '-' -> throw RuntimeException("Redis error: ${line.substring(1)}")
            ':' -> line.substring(1).toLong()
            '$' -> {
                val length = line.substring(1).toInt()
                when {
                    length == -1 -> null
                    else -> {
                        val data = CharArray(length)
                        this.reader.read(data, 0, length)
                        this.reader.read()
                        this.reader.read()
                        String(data)
                    }
                }
            }

            '*' -> {
                val count = line.substring(1).toInt()
                if (count == -1) {
                    null
                }
                else {
                    List(count) { readResponse() }
                }
            }

            else -> throw IllegalArgumentException("Unknown response type: $type")
        }
    }

    fun close() {
        this.reader.close()
        this.writer.close()
        this.socket.close()
        this.isRunning = false
    }
}