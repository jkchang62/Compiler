package miniJava.SyntacticAnalyzer;

/**
 *   TokenKind is a simple enumeration of the different kinds of tokens
 *   
 */ 
public enum TokenKind {
    // Program keywords.
    EOT,
    EOL,

    // Class keywords.
    CLASS,
    THIS,
    ID,

    // Visibility keywords.
    PUBLIC,
    PRIVATE,

    // Access keywords.
    STATIC,

    // Type keywords.
    INT,
    BOOLEAN, 
    VOID,
    NUM,

    // Boolean keywords.
    TRUE,
    FALSE,

    // Formatting keywords.
    LBRACE,
    RBRACE,
    LBRACKET,
    RBRACKET,
    LPAREN,
    RPAREN,
    COMMA,

    // Loop keywords.
    WHILE,
    
    // Statement keywords.
    RETURN,
    SEMI,
    EQUAL,
    
    // Reference keywords.
    DOT,

    // Conditional keywords.
    IF,
    ELSE,

    // Expression keywords.
    NEW,

    // Mathematical operators.
    OR,
    AND,
    NOT,
    EQUALTO,
    NEQUALTO,
    LTEQUALTO,
    GTEQUALTO,
    GTHAN,
    LTHAN,
    PLUS,
    MINUS,
    TIMES,
    DIVIDE,
    
    // Other.
    NULL,
    
    // Error keyword.
    ERROR, 
 
}
