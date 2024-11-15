package one.wabbit.lang.json

import one.wabbit.parsing.EmptySpan
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonSpec {
    private fun p(s: String) = JsonNode.parseWithEmptySpans(s)


    @Test
    fun `parse boolean`() {
        assertEquals(JsonNode.Boolean(true, EmptySpan), p("true"))
        assertEquals(JsonNode.Boolean(true, EmptySpan), p(" true "))
        assertEquals(JsonNode.Boolean(false, EmptySpan), p("false"))
        assertEquals(JsonNode.Boolean(false, EmptySpan), p(" false "))
    }

    @Test
    fun `EXT parse python style booleans`() {
        assertEquals(JsonNode.Boolean(true, EmptySpan), p("True"))
        assertEquals(JsonNode.Boolean(true, EmptySpan), p(" True "))
        assertEquals(JsonNode.Boolean(false, EmptySpan), p("False"))
        assertEquals(JsonNode.Boolean(false, EmptySpan), p(" False "))
    }

    @Test
    fun `parse null`() {
        assertEquals(JsonNode.Null(EmptySpan), p("null"))
        assertEquals(JsonNode.Null(EmptySpan), p(" null "))
    }

    @Test
    fun `EXT parse python style null`() {
        assertEquals(JsonNode.Null(EmptySpan), p("None"))
        assertEquals(JsonNode.Null(EmptySpan), p(" None "))
    }

    @Test
    fun `parse empty object`() {
        assertEquals(JsonNode.Object(emptyList(), EmptySpan), p("{}"))
        assertEquals(JsonNode.Object(emptyList(), EmptySpan), p("{ }"))
        assertEquals(JsonNode.Object(emptyList(), EmptySpan), p("{\n}"))
        assertEquals(JsonNode.Object(emptyList(), EmptySpan), p(" {} "))
        assertEquals(JsonNode.Object(emptyList(), EmptySpan), p(" { } "))
    }

    @Test
    fun `parse empty array`() {
        assertEquals(JsonNode.Array(emptyList(), EmptySpan), p("[]"))
        assertEquals(JsonNode.Array(emptyList(), EmptySpan), p("[ ]"))
        assertEquals(JsonNode.Array(emptyList(), EmptySpan), p("[\n]"))
        assertEquals(JsonNode.Array(emptyList(), EmptySpan), p(" [] "))
        assertEquals(JsonNode.Array(emptyList(), EmptySpan), p(" [ ] "))
    }

    @Test
    fun `parse simple string`() {
        assertEquals(JsonNode.String("", EmptySpan), p("\"\""))
        assertEquals(JsonNode.String("", EmptySpan), p(" \"\" "))
        assertEquals(JsonNode.String("hello", EmptySpan), p("\"hello\""))
        assertEquals(JsonNode.String("hello'", EmptySpan), p("\"hello'\""))
    }

    @Test
    fun `EXT parse strings with single quotes`() {
        assertEquals(JsonNode.String("hello", EmptySpan), p("'hello'"))
        assertEquals(JsonNode.String("hello", EmptySpan), p(" 'hello' "))
        assertEquals(JsonNode.String("hello\"", EmptySpan), p(" 'hello\"' "))
    }

    @Test
    fun `parse string with escapes`() {
        assertEquals(JsonNode.String("hello\nworld", EmptySpan), p("\"hello\\nworld\""))
        assertEquals(JsonNode.String("hello\nworld", EmptySpan), p(" \"hello\\nworld\" "))
        assertEquals(JsonNode.String("hello\\world", EmptySpan), p("\"hello\\\\world\""))
        assertEquals(JsonNode.String("hello\\world", EmptySpan), p(" \"hello\\\\world\" "))
        assertEquals(JsonNode.String("hello\"world", EmptySpan), p("\"hello\\\"world\""))
        assertEquals(JsonNode.String("hello\"world", EmptySpan), p(" \"hello\\\"world\" "))
        assertEquals(JsonNode.String("hello\\\"world", EmptySpan), p("\"hello\\\\\\\"world\""))
        assertEquals(JsonNode.String("helloXworld", EmptySpan), p(" \"hello\u0058world\" "))
    }

    @Test
    fun `EXT parse strings with single quotes and escapes`() {
        assertEquals(JsonNode.String("hello\nworld", EmptySpan), p("'hello\\nworld'"))
        assertEquals(JsonNode.String("hello\nworld", EmptySpan), p(" 'hello\\nworld' "))
        assertEquals(JsonNode.String("hello\\world", EmptySpan), p("'hello\\\\world'"))
        assertEquals(JsonNode.String("hello\\world", EmptySpan), p(" 'hello\\\\world' "))
        assertEquals(JsonNode.String("hello\'world", EmptySpan), p("'hello\\\'world'"))
        assertEquals(JsonNode.String("hello\'world", EmptySpan), p(" 'hello\\\'world' "))
        assertEquals(JsonNode.String("hello\\\'world", EmptySpan), p("'hello\\\\\\\'world'"))
        assertEquals(JsonNode.String("helloXworld", EmptySpan), p(" 'hello\u0058world' "))
    }

    @Test
    fun `EXT parse string with x escapes`() {
        assertEquals(JsonNode.String("helloXworld", EmptySpan), p("\"hello\\x58world\""))
        assertEquals(JsonNode.String("helloXworld", EmptySpan), p(" \"hello\\x58world\" "))
    }

    @Test
    fun `parse simple number`() {
        assertEquals(JsonNode.Number("0", EmptySpan), p("0"))
        assertEquals(JsonNode.Number("0", EmptySpan), p(" 0 "))
        assertEquals(JsonNode.Number("123", EmptySpan), p("123"))
        assertEquals(JsonNode.Number("-123", EmptySpan), p("-123"))
        assertEquals(JsonNode.Number("123", EmptySpan), p(" 123 "))
        assertEquals(JsonNode.Number("123.456", EmptySpan), p("123.456"))
        assertEquals(JsonNode.Number("123.456", EmptySpan), p(" 123.456 "))
        assertEquals(JsonNode.Number("123.456e789", EmptySpan), p("123.456e789"))
        assertEquals(JsonNode.Number("123.456e789", EmptySpan), p(" 123.456e789 "))
        assertEquals(JsonNode.Number("123.456e+789", EmptySpan), p("123.456e+789"))
        assertEquals(JsonNode.Number("123.456e+789", EmptySpan), p(" 123.456e+789 "))
        assertEquals(JsonNode.Number("123.456e-789", EmptySpan), p("123.456e-789"))
        assertEquals(JsonNode.Number("123.456e-789", EmptySpan), p(" 123.456e-789 "))
    }

    @Test
    fun `EXT parse numbers with plus sign`() {
        assertEquals(JsonNode.Number("123", EmptySpan), p("+123"))
    }

    @Test
    fun `parse simple object`() {
        assertEquals(JsonNode.Object(listOf(
            JsonNode.Field(JsonNode.String("a", EmptySpan), JsonNode.Number("1", EmptySpan)),
            JsonNode.Field(JsonNode.String("b", EmptySpan), JsonNode.Number("2", EmptySpan)),
            JsonNode.Field(JsonNode.String("c", EmptySpan), JsonNode.Number("3", EmptySpan))
        ), EmptySpan), p("{ \"a\" : 1 , \"b\" : 2 , \"c\" : 3 }"))
    }

    @Test
    fun `EXT parse objects with single quote keys`() {
        assertEquals(JsonNode.Object(listOf(
            JsonNode.Field(JsonNode.String("a", EmptySpan), JsonNode.Number("1", EmptySpan)),
            JsonNode.Field(JsonNode.String("b", EmptySpan), JsonNode.Number("2", EmptySpan)),
            JsonNode.Field(JsonNode.String("c", EmptySpan), JsonNode.Number("3", EmptySpan))
        ), EmptySpan), p("{ 'a' : 1 , 'b' : 2 , 'c' : 3 }"))
    }

    @Test
    fun `EXT parse object with trailing commas`() {
        assertEquals(JsonNode.Object(listOf(
            JsonNode.Field(JsonNode.String("a", EmptySpan), JsonNode.Number("1", EmptySpan)),
            JsonNode.Field(JsonNode.String("b", EmptySpan), JsonNode.Number("2", EmptySpan)),
            JsonNode.Field(JsonNode.String("c", EmptySpan), JsonNode.Number("3", EmptySpan))
        ), EmptySpan), p("{ \"a\" : 1 , \"b\" : 2 , \"c\" : 3 , }"))
    }

    @Test
    fun `EXT parse object with missing commas`() {
        assertEquals(JsonNode.Object(listOf(
            JsonNode.Field(JsonNode.String("a", EmptySpan), JsonNode.Number("1", EmptySpan)),
            JsonNode.Field(JsonNode.String("b", EmptySpan), JsonNode.Number("2", EmptySpan)),
            JsonNode.Field(JsonNode.String("c", EmptySpan), JsonNode.Number("3", EmptySpan))
        ), EmptySpan), p("{ \"a\" : 1 , \"b\" : 2 \"c\" : 3 , }"))
    }

    @Test
    fun `parse array`() {
        assertEquals(JsonNode.Array(listOf(
            JsonNode.Number("1", EmptySpan),
            JsonNode.Number("2", EmptySpan),
            JsonNode.Number("3", EmptySpan)
        ), EmptySpan), p("[ 1 , 2 , 3 ]"))
    }

    @Test
    fun `EXT parse array with trailing commas`() {
        assertEquals(JsonNode.Array(listOf(
            JsonNode.Number("1", EmptySpan),
            JsonNode.Number("2", EmptySpan),
            JsonNode.Number("3", EmptySpan)
        ), EmptySpan), p("[1,2,3,]"))
    }

    @Test
    fun `EXT parse array with missing commas`() {
        assertEquals(JsonNode.Array(listOf(
            JsonNode.Number("1", EmptySpan),
            JsonNode.Number("2", EmptySpan),
            JsonNode.Number("3", EmptySpan)
        ), EmptySpan), p("[1,2 3,]"))
    }
}
