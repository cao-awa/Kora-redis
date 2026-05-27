package com.github.cao.awa.com.github.cao.awa.kora.redis.config

import com.github.cao.awa.cason.obj.JSONObject
import com.github.cao.awa.kora.config.KoraConfig
import java.io.File

open class KoraRedisClientConfig: KoraConfig() {
    companion object {
        fun createConfig(file: File): KoraRedisClientConfig {
            return createConfig(file) {
                val config = KoraRedisClientConfig()

                ifString("host") {
                    config.host(this)
                }

                ifInt("port") {
                    config.port = this
                }

                ifInt("reconnect_time") {
                    config.reconnectTime = this
                }

                config
            }
        }
    }

    private var host: String = "127.0.0.1"
    private var port: Int = 6379
    private var reconnectTime: Int = 5000

    fun host(): String {
        return this.host
    }

    open fun host(host: String): KoraRedisClientConfig {
        this.host = host
        return this
    }

    fun port(): Int {
        return this.port
    }

    open fun port(port: Int): KoraRedisClientConfig {
        this.port = port
        return this
    }

    fun reconnectTime(): Int {
        return this.reconnectTime
    }

    open fun reconnectTime(time: Int): KoraRedisClientConfig {
        this.reconnectTime = time
        return this
    }

    override fun toJSON(): JSONObject {
        return JSONObject {
            "host" set host
            "port" set port
            "reconnect_time" set reconnectTime
        }
    }
}