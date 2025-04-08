package slang.visualizer

import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker
import org.fife.ui.rsyntaxtextarea.Token
import org.fife.ui.rsyntaxtextarea.TokenMap
import javax.swing.text.Segment


class SlangTokenMaker() : AbstractTokenMaker() {

    private var currentTokenType : Int = Token.NULL

    override fun getWordsToHighlight(): TokenMap {
        val tokenMap = TokenMap()
        tokenMap.put("let", Token.RESERVED_WORD);
        tokenMap.put("fun", Token.RESERVED_WORD);
        tokenMap.put("while", Token.RESERVED_WORD);
        tokenMap.put("for", Token.RESERVED_WORD);
        tokenMap.put("print", Token.RESERVED_WORD);
        tokenMap.put("if", Token.RESERVED_WORD);
        tokenMap.put("else", Token.RESERVED_WORD);
        tokenMap.put("then", Token.RESERVED_WORD);
        tokenMap.put("return", Token.RESERVED_WORD);
        tokenMap.put("do", Token.RESERVED_WORD);
        tokenMap.put("readInput", Token.RESERVED_WORD);
        tokenMap.put("ref", Token.RESERVED_WORD);
        tokenMap.put("deref", Token.RESERVED_WORD);
        tokenMap.put("true", Token.RESERVED_WORD);
        tokenMap.put("false", Token.RESERVED_WORD);
        tokenMap.put("None", Token.RESERVED_WORD);

        return tokenMap
    }

    override fun addToken(segment: Segment?, start: Int, end: Int, tokenType: Int, startOffset: Int) {
        var tt = tokenType
        if (tt == Token.IDENTIFIER) {
            val value = wordsToHighlight[segment, start, end]
            if (value != -1) {
                tt = value
            }
        }
        super.addToken(segment, start, end, tt, startOffset)
    }

    override fun getTokenList(text: Segment, startTokenType: Int, startOffset: Int): Token {
        resetTokenList()

        val array = text.array
        val offset = text.offset
        val count = text.count
        val end = offset + count

        val newStartOffset = startOffset - offset

        var currentTokenStart = offset

        if (currentTokenType != Token.COMMENT_MULTILINE) {
            currentTokenType = startTokenType
        }

        var i = offset
        while (i < end) {
            val c = array[i]

            when (currentTokenType) {
                Token.NULL -> {
                    currentTokenStart = i // Starting a new token here.

                    currentTokenType = when {
                        c.isWhitespace() -> Token.WHITESPACE
                        c == '"' -> Token.LITERAL_STRING_DOUBLE_QUOTE
                        c == '#' -> Token.COMMENT_EOL
                        c == '(' && i + 1 < end && array[i + 1] == '*' -> {
                            i++
                            Token.COMMENT_MULTILINE
                        }
                        c.isDigit() -> Token.LITERAL_NUMBER_DECIMAL_INT
                        c.isLetter() || c == '/' || c == '_' -> Token.IDENTIFIER
                        else -> Token.IDENTIFIER
                    }
                }

                Token.WHITESPACE -> if (!c.isWhitespace()) {
                    addToken(text, currentTokenStart, i - 1, Token.WHITESPACE, newStartOffset + currentTokenStart)
                    currentTokenStart = i
                    currentTokenType = Token.NULL
                    continue
                }

                Token.IDENTIFIER -> if (!c.isLetterOrDigit() && c != '/' && c != '_') {
                    addToken(text, currentTokenStart, i - 1, Token.IDENTIFIER, newStartOffset + currentTokenStart)
                    currentTokenStart = i
                    currentTokenType = Token.NULL
                    continue
                }

                Token.LITERAL_NUMBER_DECIMAL_INT -> if (!c.isDigit() && c != '.') {
                    addToken(text, currentTokenStart, i - 1, Token.LITERAL_NUMBER_DECIMAL_INT, newStartOffset + currentTokenStart)
                    currentTokenStart = i
                    currentTokenType = Token.NULL
                    continue
                }

                Token.COMMENT_EOL -> {
                    i = end - 1
                    addToken(text, currentTokenStart, i, currentTokenType, newStartOffset + currentTokenStart)
                    currentTokenType = Token.NULL
                }

                Token.LITERAL_STRING_DOUBLE_QUOTE -> if (c == '"') {
                    addToken(text, currentTokenStart, i, Token.LITERAL_STRING_DOUBLE_QUOTE, newStartOffset + currentTokenStart)
                    currentTokenType = Token.NULL
                }

                Token.COMMENT_MULTILINE -> if (c == '*' && i + 1 < end && array[i + 1] == ')') {
                    i++
                    addToken(text, currentTokenStart, i, Token.COMMENT_MULTILINE, newStartOffset + currentTokenStart)
                    currentTokenType = Token.NULL
                }
            }
            i++
        }

        when (currentTokenType) {
            Token.LITERAL_STRING_DOUBLE_QUOTE -> addToken(text, currentTokenStart, end - 1, currentTokenType, newStartOffset + currentTokenStart)
            Token.NULL -> addNullToken()
            else -> {
                addToken(text, currentTokenStart, end - 1, currentTokenType, newStartOffset + currentTokenStart)
                addNullToken()
            }
        }
        return firstToken
    }
}