/**
 *  Scans the a single line of input
 * 
 * Tokens:
 * ID    ::= Letter( Letter | Digit | '_' )*
 * NUM   ::= Digit (Digit)*
 * Digit ::= '0' | ... | '9'
 * Binop ::= '+' | '-' | '*' | '/' | '&&' | '||' | '!' | '>' | '<' | 
 * 			'==' |'<=' |'>=' |'!='
 * Unop  ::= '!' | '-'
 * 
 */

package miniJava.SyntacticAnalyzer;
import miniJava.ErrorReporter;
import java.io.*;

public class Scanner {

	private InputStream inputStream;
	private ErrorReporter reporter;

	private char currentChar;
	private StringBuilder currentSpelling;
	private int currentLineNumber;
	
	// True when end of a line is found.
	private boolean eot = false;

	public Scanner(InputStream inputStream, ErrorReporter reporter) {
		this.inputStream = inputStream;
		this.reporter = reporter;
		currentLineNumber = 1;
		
		// Initializing the scanner.
		readChar();
	}

	// Skipping whitespace and scanning next token
	public Token scan() {

		// Skipping whitespace.
		while (!eot && currentChar == ' ' || currentChar == '\t' || currentChar == '\n' || currentChar == '\r') {
			if (currentChar == '\n' || currentChar == 'r') {
				currentLineNumber++;
			}
			skipIt();
		}
		
		// Start of a token: collect spelling and identify token kind.
		currentSpelling = new StringBuilder();
		TokenKind kind;

		// Initializing [kind].
		if (isLetter(currentChar)) {
			kind = scanId();
		} else if (isDigit(currentChar)) {
			kind = scanNum();
		} else {
			kind = scanToken();
		}

		// Returning new token.
		String spelling = currentSpelling.toString();
		return new Token(kind, spelling, new SourcePosition(currentLineNumber));
	}

	/**
	 * ID ::= Letter( Letter | Digit | '_' )*
	 */
	public TokenKind scanId() {
		if (eot)
			return(TokenKind.EOT); 
		
		while (!eot && isLetter(currentChar) || isDigit(currentChar) || currentChar == '_')
			takeIt();

		return TokenKind.ID;
	}

	/**
	 * NUM ::= Digit (Digit)*
	 */
	public TokenKind scanNum() {
		if (eot)
			return TokenKind.EOT; 
		
		while (!eot && isDigit(currentChar))
			takeIt();
		
		return TokenKind.NUM;
	}

	/**
	* Single-length Tokens:
	* Formatting ::= '{' | '}' | '(' | ')' | ';' | '.'
	* Binop ::= '+' | '-' | '*' | '/' | '&&' | '||' | '!' | '>' | '<' | 
	* 			'==' |'<=' |'>=' |'!='
	* Unop  ::= '!' | '-'
	*/
	public TokenKind scanToken() {
		if (eot)
			return TokenKind.EOT; 

		switch (currentChar) {

		// Formatting tokens.
		case '{':
			takeIt();
			return TokenKind.LBRACE;

		case '}':
			takeIt();
			return TokenKind.RBRACE;

		case '[':
			takeIt();
			return TokenKind.LBRACKET;
			
		case ']':
			takeIt();
			return TokenKind.RBRACKET;
			
		case '(': 
			takeIt();
			return TokenKind.LPAREN;

		case ')':
			takeIt();
			return TokenKind.RPAREN;

		case ',':
			takeIt();
			return TokenKind.COMMA;

		case ';':
			takeIt();
			return TokenKind.SEMI;

		case '.':
			takeIt();
			return TokenKind.DOT;

		case '+': 
			takeIt();
			return TokenKind.PLUS;
			
		case '*':
			takeIt();
			return TokenKind.TIMES;
			
		case '/':
			takeIt();
			// Parsing a multi-line comment, a single-comment line, or a divide.
			switch(currentChar) {
			case '*':
				skipIt();
				// While loop runs until terminating characters are detected and returns and error if they're not.
				while (true) {
					if (eot)
						return TokenKind.ERROR;
					
					if (currentChar == '*' && readNextChar() == '/') {
						skipIt();
						break;
					}
					
					if (currentChar != '*')
						skipIt();
				}
				return scan().kind;
			case '/':
				do {
					skipIt();
				} while (!eot && currentChar != '\n');
				return scan().kind;
			default:
				return TokenKind.DIVIDE;
			}

		case '>':
			takeIt();
			if (currentChar == '=') {
				takeIt();
				return TokenKind.GTEQUALTO;
			}
			return TokenKind.GTHAN;
			
		case '<':
			takeIt();
			if (currentChar == '=') {
				takeIt();
				return TokenKind.LTEQUALTO;
			}
			return TokenKind.LTHAN;

		case '&':
			takeIt();
			take('&');
			return TokenKind.AND;

		case '|':
			takeIt();
			take('|');
			return TokenKind.OR;

		case '=':
			takeIt();
			if (currentChar == '=') {
				takeIt();
				return TokenKind.EQUALTO;
			}
			return TokenKind.EQUAL;

		// Unary operators.
		case '!':
			takeIt();
			if (currentChar == '=') {
				takeIt();
				return TokenKind.NEQUALTO;
			}
			return TokenKind.NOT;

		case '-':
			takeIt();
			return TokenKind.MINUS;

		default:
			scanError("Unrecognized character '" + currentChar + "' in input");
			return(TokenKind.ERROR);
		}
	}

	private void take(char expectedChar) {
		if (currentChar == expectedChar) {
			takeIt();
		} else {
			scanError("Expected '" + expectedChar + "'' but receieved '" + currentChar + "''.");
		}
	}

	private void takeIt() {
		currentSpelling.append(currentChar);
		nextChar();
	}

	private void skipIt() {
		nextChar();
	}

	private boolean isLetter(char c) {
		return (c >= 'A') && (c <= 'Z') || (c >= 'a') && (c <= 'z');
	}

	private boolean isDigit(char c) {
		return (c >= '0') && (c <= '9');
	}

	private void scanError(String m) {
		reporter.reportError("Scan Error:  " + m);
	}

	/**
	 * Advances to the next char in inputstream.
	 * Detects end of file or end of line as end of input.
	 */
	private void nextChar() {
		if (!eot)
			readChar();
	}

	private void readChar() {
		try {
			int c = inputStream.read();
			currentChar = (char) c;
			if (c == -1) {
				eot = true;
			} 
				
		} catch (IOException e) {
			scanError("I/O Exception!");
			eot = true;
		}
	}
	
	private char readNextChar() {
		nextChar();
		return currentChar;
	}
	
	public int getLineNumber() {
		return currentLineNumber;
	}
}