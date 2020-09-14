package la.serendipity.jackson_kotlin_inline_class_module

import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Test


@EnableInlineClassDeserialize
inline class MyStringId(
        @get:JsonValue
        val value: String
)

@EnableInlineClassDeserialize
inline class MyLongId(
        @get:JsonValue
        val value: Long
)

internal class KotlinInlineClassModuleTest {
    companion object {
        private val log = mu.KotlinLogging.logger {}
    }

    private val mapper = ObjectMapper().apply {
        registerModule(KotlinInlineClassModule())
    }

    @Test
    fun testInlineSerialize() {
        val ret = mapper.writeValueAsString(MyStringId("test"))

        log.info { ret }

        val readValue = mapper.readValue(ret, MyStringId::class.java)
        val longValue = mapper.readValue("123", MyLongId::class.java)
        val mapValue = mapper.readValue<Map<MyStringId, MyLongId>>("""{"key": 1}""")
        val listValue = mapper.readValue<List<MyStringId>>("""["1","2"]""")

        log.info { readValue }
        log.info { longValue }
        log.info { mapValue }
        log.info { listValue }
    }
}