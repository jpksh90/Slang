package slast.visualizer

import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker
import org.fife.ui.rsyntaxtextarea.RSyntaxUtilities
import org.fife.ui.rsyntaxtextarea.Token
import org.fife.ui.rsyntaxtextarea.TokenMap
import javax.swing.text.Segment


class SimpleLangTokenMaker() : AbstractTokenMaker() {

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
        // This assumes all keywords, etc. were parsed as "identifiers."
        var tokenType = tokenType
        if (tokenType == Token.IDENTIFIER) {
            val value = wordsToHighlight[segment, start, end]
            if (value != -1) {
                tokenType = value
            }
        }
        super.addToken(segment, start, end, tokenType, startOffset)
    }

    /**
     * Returns a list of tokens representing the given text.
     *
     * @param text The text to break into tokens.
     * @param startTokenType The token with which to start tokenizing.
     * @param startOffset The offset at which the line of tokens begins.
     * @return A linked list of tokens representing `text`.
     */
    override fun getTokenList(text: Segment, startTokenType: Int, startOffset: Int): Token {
        resetTokenList()

        val array = text.array
        val offset = text.offset
        val count = text.count
        val end = offset + count

        val newStartOffset = startOffset - offset

        var currentTokenStart = offset
        var currentTokenType = startTokenType

        var i = offset
        while (i < end) {
            val c = array[i]

            when (currentTokenType) {
                Token.NULL -> {
                    currentTokenStart = i // Starting a new token here.

                    when (c) {
                        ' ', '\t' -> currentTokenType = Token.WHITESPACE
                        '"' -> currentTokenType = Token.LITERAL_STRING_DOUBLE_QUOTE
                        '#' -> currentTokenType = Token.COMMENT_EOL
                        else -> {
                            if (RSyntaxUtilities.isDigit(c)) {
                                currentTokenType = Token.LITERAL_NUMBER_DECIMAL_INT
                                break
                            } else if (RSyntaxUtilities.isLetter(c) || c == '/' || c == '_') {
                                currentTokenType = Token.IDENTIFIER
                                break
                            }


                            // Anything not currently handled - mark as an identifier
                            currentTokenType = Token.IDENTIFIER
                        }
                    }
                }

                Token.WHITESPACE -> when (c) {
                    ' ', '\t' -> {}
                    '"' -> {
                        addToken(text, currentTokenStart, i - 1, Token.WHITESPACE, newStartOffset + currentTokenStart)
                        currentTokenStart = i
                        currentTokenType = Token.LITERAL_STRING_DOUBLE_QUOTE
                    }

                    '#' -> {
                        addToken(text, currentTokenStart, i - 1, Token.WHITESPACE, newStartOffset + currentTokenStart)
                        currentTokenStart = i
                        currentTokenType = Token.COMMENT_EOL
                    }

                    else -> {
                        addToken(text, currentTokenStart, i - 1, Token.WHITESPACE, newStartOffset + currentTokenStart)
                        currentTokenStart = i

                        if (RSyntaxUtilities.isDigit(c)) {
                            currentTokenType = Token.LITERAL_NUMBER_DECIMAL_INT
                            break
                        } else if (RSyntaxUtilities.isLetter(c) || c == '/' || c == '_') {
                            currentTokenType = Token.IDENTIFIER
                            break
                        }

                        // Anything not currently handled - mark as identifier
                        currentTokenType = Token.IDENTIFIER
                    }
                }

                Token.IDENTIFIER -> when (c) {
                    ' ', '\t' -> {
                        addToken(text, currentTokenStart, i - 1, Token.IDENTIFIER, newStartOffset + currentTokenStart)
                        currentTokenStart = i
                        currentTokenType = Token.WHITESPACE
                    }

                    '"' -> {
                        addToken(text, currentTokenStart, i - 1, Token.IDENTIFIER, newStartOffset + currentTokenStart)
                        currentTokenStart = i
                        currentTokenType = Token.LITERAL_STRING_DOUBLE_QUOTE
                    }

                    else -> if (RSyntaxUtilities.isLetterOrDigit(c) || c == '/' || c == '_') {
                        break // Still an identifier of some type.
                    }

                }

                Token.LITERAL_NUMBER_DECIMAL_INT -> when (c) {
                    ' ', '\t' -> {
                        addToken(
                            text,
                            currentTokenStart,
                            i - 1,
                            Token.LITERAL_NUMBER_DECIMAL_INT,
                            newStartOffset + currentTokenStart
                        )
                        currentTokenStart = i
                        currentTokenType = Token.WHITESPACE
                    }

                    '"' -> {
                        addToken(
                            text,
                            currentTokenStart,
                            i - 1,
                            Token.LITERAL_NUMBER_DECIMAL_INT,
                            newStartOffset + currentTokenStart
                        )
                        currentTokenStart = i
                        currentTokenType = Token.LITERAL_STRING_DOUBLE_QUOTE
                    }

                    else -> {
                        if (RSyntaxUtilities.isDigit(c)) {
                            break // Still a literal number.
                        }

                        // Otherwise, remember this was a number and start over.
                        addToken(
                            text,
                            currentTokenStart,
                            i - 1,
                            Token.LITERAL_NUMBER_DECIMAL_INT,
                            newStartOffset + currentTokenStart
                        )
                        i--
                        currentTokenType = Token.NULL
                    }
                }

                Token.COMMENT_EOL -> {
                    i = end - 1
                    addToken(text, currentTokenStart, i, currentTokenType, newStartOffset + currentTokenStart)
                    // We need to set token type to null so at the bottom we don't add one more token.
                    currentTokenType = Token.NULL
                }

                Token.LITERAL_STRING_DOUBLE_QUOTE -> if (c == '"') {
                    addToken(
                        text,
                        currentTokenStart,
                        i,
                        Token.LITERAL_STRING_DOUBLE_QUOTE,
                        newStartOffset + currentTokenStart
                    )
                    currentTokenType = Token.NULL
                }

                else -> when (c) {
                    ' ', '\t' -> {
                        addToken(text, currentTokenStart, i - 1, Token.IDENTIFIER, newStartOffset + currentTokenStart)
                        currentTokenStart = i
                        currentTokenType = Token.WHITESPACE
                    }

                    '"' -> {
                        addToken(text, currentTokenStart, i - 1, Token.IDENTIFIER, newStartOffset + currentTokenStart)
                        currentTokenStart = i
                        currentTokenType = Token.LITERAL_STRING_DOUBLE_QUOTE
                    }

                    else -> if (RSyntaxUtilities.isLetterOrDigit(c) || c == '/' || c == '_') {
                        break
                    }

                }
            }
            i++
        }

        when (currentTokenType) {
            Token.LITERAL_STRING_DOUBLE_QUOTE -> addToken(
                text,
                currentTokenStart,
                end - 1,
                currentTokenType,
                newStartOffset + currentTokenStart
            )

            Token.NULL -> addNullToken()
            else -> {
                addToken(text, currentTokenStart, end - 1, currentTokenType, newStartOffset + currentTokenStart)
                addNullToken()
            }
        }
        // Return the first token in our linked list.
        return firstToken
    }
}