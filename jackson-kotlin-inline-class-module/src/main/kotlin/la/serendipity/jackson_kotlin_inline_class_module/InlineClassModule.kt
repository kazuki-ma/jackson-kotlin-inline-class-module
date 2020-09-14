package la.serendipity.jackson_kotlin_inline_class_module


import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.Version
import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.DeserializationConfig
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.KeyDeserializer
import com.fasterxml.jackson.databind.Module
import com.fasterxml.jackson.databind.deser.Deserializers
import com.fasterxml.jackson.databind.deser.KeyDeserializers
import kotlin.reflect.KFunction
import kotlin.reflect.full.createType
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

annotation class EnableInlineClassDeserialize

private fun Class<*>.getAccessiblePrimaryConstructor(): KFunction<Any> {
    return checkNotNull(kotlin.primaryConstructor.apply { this!!.isAccessible = true }) {
        "There are no primary constructor in this class"
    }
}


private val JavaType.isInlineClass: Boolean
    get() = rawClass.isAnnotationPresent(EnableInlineClassDeserialize::class.java)

private class InlineClassDeserializer(
    rawClass: Class<*>
) : JsonDeserializer<Any>() {
    private val const = rawClass.getAccessiblePrimaryConstructor()
    private val getter: (JsonParser) -> Any = when (const.parameters[0].type) {
        Boolean::class.createType() -> JsonParser::getValueAsBoolean
        Int::class.createType() -> JsonParser::getValueAsInt
        Long::class.createType() -> JsonParser::getValueAsLong
        Float::class.createType() -> JsonParser::getFloatValue
        Double::class.createType() -> JsonParser::getValueAsDouble
        else -> TODO("Unsupported type")
    }

    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Any {
        return const.call(getter(p))
    }
}

private class InlineClassKeyDeserializer(
    rawClass: Class<*>
) : KeyDeserializer() {
    private val const = rawClass.getAccessiblePrimaryConstructor()
    override fun deserializeKey(key: String?, ctxt: DeserializationContext?): Any {
        return const.call(key)
    }
}

class KotlinInlineClassModule : Module() {
    override fun version() = Version.unknownVersion()
    override fun getModuleName() = javaClass.name

    override fun setupModule(context: SetupContext) {
        context.addKeyDeserializers(KeyDeserializers { type, _, _ ->
            if (type.isInlineClass) {
                return@KeyDeserializers InlineClassKeyDeserializer(type.rawClass)
            }
            null
        })
        context.addDeserializers(object : Deserializers.Base() {
            override fun findBeanDeserializer(
                type: JavaType,
                config: DeserializationConfig?,
                beanDesc: BeanDescription?
            ): JsonDeserializer<*>? {
                if (type.isInlineClass) {
                    return InlineClassDeserializer(type.rawClass)
                }
                return null
            }
        })
    }
}

