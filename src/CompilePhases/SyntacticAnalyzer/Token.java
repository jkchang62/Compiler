package miniJava.SyntacticAnalyzer;

/**
 *  A token has a kind and a spelling
 *  In a compiler it would also have a source position 
 */
public class Token {
	public TokenKind kind;
	public String spelling;
	public SourcePosition posn;

	public Token(TokenKind kind, String spelling, SourcePosition posn) {
		this.spelling = spelling;
		this.kind = kind;
		this.posn = posn;
		// If kind is of ID and spelling matches on of the keywords,
		// change the token's kind accordingly.
		if (kind == TokenKind.ID) {
			for (int k = CLASS; k <= INT; k++) {
				if (spelling.equals(spellings[k])) {
					this.kind = kinds[k];
					break;
				}
			}
		}
	}

	// Token kinds.
	public final static byte
		CLASS   =  0, THIS  = 1,  PUBLIC =  2, PRIVATE =  3, STATIC = 4,
		BOOLEAN =  5, VOID  = 6,  WHILE  =  7, RETURN  =  8, IF     = 9, 
		ELSE    = 10, NEW   = 11, TRUE   = 12, FALSE   = 13, INT    = 14,
		NULL    = 15
	;

	// Spellings of different kinds of token corresponding to the 
	// token kinds above.
	private final static String[] spellings = {
		"class",   "this", "public", "private", "static",
		"boolean", "void", "while",  "return",  "if",
		"else",    "new",  "true",   "false",   "int",
		"null"
	};
	
	private final static TokenKind[] kinds = {
		TokenKind.CLASS,   TokenKind.THIS, TokenKind.PUBLIC, TokenKind.PRIVATE, TokenKind.STATIC,
		TokenKind.BOOLEAN, TokenKind.VOID, TokenKind.WHILE,  TokenKind.RETURN,  TokenKind.IF,
		TokenKind.ELSE,    TokenKind.NEW,  TokenKind.TRUE,   TokenKind.FALSE,   TokenKind.INT,
		TokenKind.NULL
	};
}
