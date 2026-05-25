package com.github.cao.awa.kora.redis.client

import com.github.cao.awa.com.github.cao.awa.kora.redis.config.KoraRedisClientConfig
import com.github.cao.awa.kora.plugin.registerCleaner
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

class KoraRedisClient(private val config: KoraRedisClientConfig) {
    companion object {
        private val LOGGER: Logger = LogManager.getLogger("KoraRedisClient")
        private var REAL_INSTANCE: KoraRedisClient? = null
        val INSTANCE: KoraRedisClient
            get() = REAL_INSTANCE!!

        fun init(config: KoraRedisClientConfig) {
            REAL_INSTANCE = KoraRedisClient(config)

            try {
                INSTANCE.connect()

                LOGGER.info("Initialized redis client, connected to ${config.host()}:${config.port()}")
            } catch (e: Exception) {
                throw IllegalStateException("Cannot initialize redis client", e)
            }

            registerCleaner("kora-redis-instance") {
                INSTANCE.disconnect()
                REAL_INSTANCE = null
            }
        }
    }

    private lateinit var socket: Socket
    private lateinit var reader: BufferedReader
    private lateinit var writer: BufferedWriter

    fun connect() {
        this.socket = Socket(config.host(), config.port())
        this.reader = BufferedReader(InputStreamReader(this.socket.getInputStream()))
        this.writer = BufferedWriter(OutputStreamWriter(this.socket.getOutputStream()))
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

    fun disconnect() {
        this.reader.close()
        this.writer.close()
        this.socket.close()
    }
}