# Kora-redis
A Redis client plugin for Kora webserver.

## Usage
Add dependencies firsy: 
```groovy
repositories {
    maven {
        url 'https://jitpack.io'
    }
}

dependencies {
    implementation 'com.github.cao-awa:Kora-redis:{version}'
}
```

For the versions, see [JitPack](https://jitpack.io/#cao-awa/Kora-redis).

And use redis client in your code:
```kotlin
import com.github.cao.awa.kora.redis.client.KoraRedisClient

object Test {
    @JvmStatic
    fun entry() {
        val redisClient = KoraRedisClient.INSTANCE
        // Set data to redis.
        redisClient["test-key"] = "test"
        // Get data from redis.
        println(redisClient["test-key"])
    }
}
```

In produce environment, you need put the ``kora-redis`` jar to ``libs/`` directory and declare entrypoint:
```json
{
    "entrypoint": [
        "kora-redis-client",
        "com.yourservice.xxx.ServiceEntrypoint#entry"
    ]
}
```

For entrypoint, please see [Kora's document](https://github.com/cao-awa/Kora/tree/main/docs/entrypoint)/