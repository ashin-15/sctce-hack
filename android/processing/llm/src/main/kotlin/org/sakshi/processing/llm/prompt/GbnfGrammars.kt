package org.sakshi.processing.llm.prompt

public object GbnfGrammars {

    public fun extractionGrammar(): String {
        return """
        root ::= "{" ws "\"date\":" ws (string | "null") "," ws "\"platform\":" ws (string | "null") "," ws "\"sender\":" ws (string | "null") "," ws "\"threat_type\":" ws (string | "null") "," ws "\"quote\":" ws (string | "null") "," ws "\"source_ids\":" ws string-array "}" ws
        string-array ::= "[" ws (string ("," ws string)*)? ws "]"
        string ::= "\"" ([^"\\] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F]))* "\""
        ws ::= [ \t\n\r]*
        """.trimIndent()
    }

    public fun whyFlaggedGrammar(): String {
        return """
        root ::= "{" ws "\"explanation\":" ws string "," ws "\"supporting_quote\":" ws (string | "null") "," ws "\"source_id\":" ws (string | "null") "}" ws
        string ::= "\"" ([^"\\] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F]))* "\""
        ws ::= [ \t\n\r]*
        """.trimIndent()
    }

    public fun jsonObjectGrammar(): String {
        return """
        root ::= object
        object ::= "{" ws (string ":" ws value ("," ws string ":" ws value)*)? ws "}"
        value ::= object | array | string | number | "true" | "false" | "null"
        array ::= "[" ws (value ("," ws value)*)? ws "]"
        string ::= "\"" ([^"\\] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F]))* "\""
        number ::= "-"? [0-9]+ ("." [0-9]+)? ([eE] [-+]? [0-9]+)?
        ws ::= [ \t\n\r]*
        """.trimIndent()
    }
}
