package one.wabbit.lang.json

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import one.wabbit.parsing.*

@Serializable sealed interface JsonNode<Span> {
    val span: Span

    @Serializable data class Null<Span>(override val span: Span) : JsonNode<Span>

    @Serializable data class Boolean<Span>(val value: kotlin.Boolean, override val span: Span) : JsonNode<Span>

    @Serializable sealed interface StringLike<Span> : JsonNode<Span> {
        val value: kotlin.String
    }

    // Note: Numbers are represented as strings to avoid precision loss. JSON spec allows for arbitrary precision and
    //       arbitrary range, so we can't represent them as Kotlin numbers easily.
    @Serializable data class Number<Span>(override val value: kotlin.String, override val span: Span) : StringLike<Span>

    @Serializable data class String<Span>(override val value: kotlin.String, override val span: Span) : StringLike<Span>

    @Serializable data class Field<Span>(val key: String<Span>, val value: JsonNode<Span>)
    @Serializable data class Object<Span>(val fields: List<Field<Span>>, override val span: Span) : JsonNode<Span> {
        operator fun get(key: kotlin.String): JsonNode<Span>? = fields.find { it.key.value == key }?.value
    }

    @Serializable data class Array<Span>(val elements: List<JsonNode<Span>>, override val span: Span) : JsonNode<Span> {
        operator fun get(index: Int): JsonNode<Span>? = elements.getOrNull(index)
    }

    fun toJsonElement(): JsonElement = when (this) {
        is Null    -> JsonNull
        is Boolean -> JsonPrimitive(value)
        is Number  -> JsonPrimitive(value)
        is String  -> JsonPrimitive(value)
        is Object  -> JsonObject(fields.associate { it.key.value to it.value.toJsonElement() })
        is Array   -> JsonArray(elements.map { it.toJsonElement() })
    }

    enum class Type(val jsonName: kotlin.String) {
        Null("null"),
        Boolean("boolean"),
        Number("number"),
        String("string"),
        Array("array"),
        Object("object")
    }

    val type: Type get() = when (this) {
        is Null    -> Type.Null
        is Boolean -> Type.Boolean
        is Number  -> Type.Number
        is String  -> Type.String
        is Array   -> Type.Array
        is Object  -> Type.Object
    }

    companion object {
        // Loosely follows https://www.ietf.org/rfc/rfc4627.txt
        fun <Span> parse(input: CharInput<Span>): JsonNode<Span> = parseJson(input)

        fun parseWithTextAndPosSpans(input: kotlin.String): JsonNode<TextAndPosSpan> =
            parseJson(CharInput.withTextAndPosSpans(input))
        fun parseWithPosSpans(input: kotlin.String): JsonNode<PosOnlySpan> =
            parseJson(CharInput.withPosOnlySpans(input))
        fun parseWithEmptySpans(input: kotlin.String): JsonNode<EmptySpan> =
            parseJson(CharInput.withEmptySpans(input))
        fun parseWithTextSpans(input: kotlin.String): JsonNode<TextOnlySpan> =
            parseJson(CharInput.withTextOnlySpans(input))

        private fun <Span> CharInput<Span>.fail(message: kotlin.String): Nothing {
            throw Exception("$message at $this")
        }

        private fun <Span> skipSpaces(input: CharInput<Span>): Span {
            val start: CharInput.Mark = input.mark()
            while (true) {
                val char = input.current
                when {
                    char == CharInput.EOB -> return input.capture(start)
                    // NOTE: more lenient than the actual JSON spec since it allows any Unicode whitespace.
                    char.isWhitespace() -> input.advance()
                    else -> return input.capture(start)
                }
            }
        }

        private fun <Span> parseJson(input: CharInput<Span>): JsonNode<Span> {
            skipSpaces(input)
            val node = parseValue(input)
            skipSpaces(input)
            if (input.current != CharInput.EOB) {
                input.fail("Unexpected character: ${input.current}")
            }
            return node
        }

        private fun <Span> parseValue(input: CharInput<Span>): JsonNode<Span> {
            skipSpaces(input)
            return when (input.current) {
                '[' -> parseArray(input)
                '{' -> parseObject(input)
                '"' -> parseString(input, StringType.DOUBLE)
                '\'' -> parseString(input, StringType.SINGLE)
                't', 'f', 'T', 'F' -> parseBoolean(input)
                'n', 'N' -> parseNull(input)
                in '0'..'9', '-', '+' -> parseNumber(input)
                else -> input.fail("Unexpected character: ${input.current}")
            }
        }

        private fun <Span> CharInput<Span>.expect(c: kotlin.Char) {
            val ch = current
            if (ch != c) {
                this.fail("Expected '$c', got '$ch'")
            }
            advance()
        }
        private fun <Span> CharInput<Span>.expect(c: kotlin.String) {
            for (cc in c) {
                expect(cc)
            }
        }

        private fun <Span> parseNull(input: CharInput<Span>): JsonNode.Null<Span> {
            val start = input.mark()
            when (input.current) {
                'n' -> input.expect("null")
                'N' -> input.expect("None")
                else -> input.fail("Expected 'n' or 'N', got '${input.current}'")
            }
            val span = input.capture(start)
            skipSpaces(input)
            return JsonNode.Null(span)
        }

        private fun <Span> parseBoolean(input: CharInput<Span>): JsonNode.Boolean<Span> {
            val start = input.mark()
            val value = when (input.current) {
                't' -> {
                    input.expect("true")
                    true
                }
                'f' -> {
                    input.expect("false")
                    false
                }
                'T' -> {
                    input.expect("True")
                    true
                }
                'F' -> {
                    input.expect("False")
                    false
                }
                else -> input.fail("Expected 't' or 'f', got '${input.current}'")
            }

            val span = input.capture(start)
            skipSpaces(input)
            return JsonNode.Boolean(value, span)
        }

        private enum class StringType {
            SINGLE, DOUBLE
        }

        private fun <Span> parseString(input: CharInput<Span>, type: StringType): JsonNode.String<Span> {
            val start = input.mark()
            val sb = StringBuilder()

            val quoteChar = when (type) {
                StringType.SINGLE -> '\''
                StringType.DOUBLE -> '"'
            }

            input.expect(quoteChar)

            while (input.current != quoteChar) {
                if (input.current != '\\') {
                    sb.append(input.current)
                    input.advance()
                    continue
                }
                input.advance()

                val escaped = input.current

                //          char = unescaped /
                //                escape (
                //                    %x22 /          ; "    quotation mark  U+0022
                //                    %x5C /          ; \    reverse solidus U+005C
                //                    %x2F /          ; /    solidus         U+002F
                //                    %x62 /          ; b    backspace       U+0008
                //                    %x66 /          ; f    form feed       U+000C
                //                    %x6E /          ; n    line feed       U+000A
                //                    %x72 /          ; r    carriage return U+000D
                //                    %x74 /          ; t    tab             U+0009
                //                    %x75 4HEXDIG )  ; uXXXX                U+XXXX
                when (escaped) {
                    quoteChar, '\\', '/' -> {
                        sb.append(input.current)
                        input.advance()
                    }

                    'b' -> {
                        sb.append('\b')
                        input.advance()
                    }

                    'f' -> {
                        sb.append('\u000C')
                        input.advance()
                    }

                    'n' -> {
                        sb.append('\n')
                        input.advance()
                    }

                    'r' -> {
                        sb.append('\r')
                        input.advance()
                    }

                    't' -> {
                        sb.append('\t')
                        input.advance()
                    }

                    'u' -> {
                        input.advance()
                        val hex = input.take(4) ?: input.fail("Invalid escape: $escaped")
                        if (hex.length != 4 || !hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                            input.fail("Invalid escape: $escaped")
                        }
                        sb.append(hex.toInt(16).toChar())
                    }

                    // NOTE: This is a non-standard extension to the JSON spec.
                    'x' -> {
                        input.advance()
                        val hex = input.take(2) ?: input.fail("Invalid escape: $escaped")
                        if (hex.length != 2 || !hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                            input.fail("Invalid escape: $escaped")
                        }
                        sb.append(hex.toInt(16).toChar())
                    }

                    else -> input.fail("Invalid escape: $escaped")
                }
            }
            input.expect(quoteChar)
            val span = input.capture(start)
            skipSpaces(input)
            return JsonNode.String(sb.toString(), span)
        }

        private fun <Span> parseArray(input: CharInput<Span>): JsonNode.Array<Span> {
            val start = input.mark()
            input.expect('[')

            val elements = mutableListOf<JsonNode<Span>>()
            while (input.current != ']') {
                skipSpaces(input)
                if (input.current == ']') break

                elements.add(parseValue(input))

                // NOTE: This is a non-standard extension to the JSON spec: trailing (and optional) commas in arrays.
                if (input.current == ',') {
                    input.advance()
                }
            }

            input.expect(']')
            val span = input.capture(start)
            skipSpaces(input)
            return JsonNode.Array(elements, span)
        }

        private fun <Span> parseObject(input: CharInput<Span>): JsonNode.Object<Span> {
            val start = input.mark()
            input.expect('{')
            val fields = mutableListOf<JsonNode.Field<Span>>()
            while (input.current != '}') {
                skipSpaces(input)
                if (input.current == '}') break

                val key = when (input.current) {
                    '"' -> parseString(input, StringType.DOUBLE)
                    '\'' -> parseString(input, StringType.SINGLE)
                    else -> input.fail("Expected '\"' or '\'', got '${input.current}'")
                }

                input.expect(':')

                val value = parseValue(input)
                fields.add(JsonNode.Field(key, value))

                // NOTE: This is a non-standard extension to the JSON spec: trailing (and optional) commas.
                if (input.current == ',') {
                    input.advance()
                }
            }
            input.expect('}')
            val span = input.capture(start)
            skipSpaces(input)
            return JsonNode.Object(fields, span)
        }

        private fun <Span> parseNumber(input: CharInput<Span>): JsonNode.Number<Span> {
            val start = input.mark()
            val sb = StringBuilder()

            // NOTE: This is a non-standard extension to the JSON spec: leading '+' sign.
            if (input.current == '-' || input.current == '+') {
                if (input.current == '-') sb.append(input.current)
                input.advance()
            }

            // Whole part
            if (input.current == '0') {
                sb.append('0')
                input.advance()
            } else {
                if (input.current !in '1'..'9') {
                    input.fail("Invalid number: ${input.current}")
                }
                while (input.current in '0'..'9') {
                    sb.append(input.current)
                    input.advance()
                }
            }

            // Fractional part
            if (input.current == '.') {
                sb.append('.')
                input.advance()
                if (input.current !in '0'..'9') {
                    input.fail("Invalid number: ${input.current}")
                }
                while (input.current in '0'..'9') {
                    sb.append(input.current)
                    input.advance()
                }
            }

            // Exponent part
            if (input.current == 'e' || input.current == 'E') {
                sb.append('e')
                input.advance()

                if (input.current == '+' || input.current == '-') {
                    sb.append(input.current)
                    input.advance()
                }

                if (input.current !in '0'..'9') {
                    input.fail("Invalid number: ${input.current}")
                }
                while (input.current in '0'..'9') {
                    sb.append(input.current)
                    input.advance()
                }
            }

            val span = input.capture(start)
            skipSpaces(input)
            return JsonNode.Number(sb.toString(), span)
        }
    }
}
