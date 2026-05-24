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