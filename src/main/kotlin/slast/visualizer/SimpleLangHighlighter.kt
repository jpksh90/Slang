//
//import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker
//import org.fife.ui.rsyntaxtextarea.Token
//import javax.swing.text.Segment
//
//class SimpleLangTokenMaker : AbstractTokenMaker() {
//    override fun getTokenList(text: Segment, initialTokenType: Int, startOffset: Int): Token {
//        resetTokenList()
//
//        val lexer = SimpleLangLexer(CharStreams.fromString(text.toString()))
//        val tokens: MutableList<out org.antlr.v4.runtime.Token>? = lexer.allTokens
//
//        if (tokens != null) {
//            for (antlrToken in tokens) {
//                val type = mapTokenType(antlrToken.getType())
//                addToken(
//                    text,
//                    antlrToken.getStartIndex(),
//                    antlrToken.getStopIndex(),
//                    type,
//                    startOffset + antlrToken.getStartIndex()
//                )
//            }
//        }
//
//        return firstToken
//    }
//
//    private fun mapTokenType(antlrTokenType: Int): Int {
//        return when (antlrTokenType) {
//            SimpleLangLexer.LET -> Token.RESERVED_WORD
//            SimpleLangLexer.IDENTIFIER -> Token.IDENTIFIER
//            SimpleLangLexer.STRING -> Token.LITERAL_STRING_DOUBLE_QUOTE
//            SimpleLangLexer.NUMBER -> Token.LITERAL_NUMBER_DECIMAL_INT
//            SimpleLangLexer.COMMENT -> Token.COMMENT_EOL
//            else -> Token.NULL
//        }
//    }
//}