package miniJava.SyntacticAnalyzer;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;

public class Parser {

	private Scanner scanner;
	private ErrorReporter reporter;
	private Token token;
	private boolean trace = false;

	public Parser(Scanner scanner, ErrorReporter reporter) {
		this.scanner = scanner;
		this.reporter = reporter;
	}

	// SyntaxError is used to unwind parse stack when parse fails
	class SyntaxError extends Error {
		private static final long serialVersionUID = 1L;	
	}

	// Parse input, catch possible parse error
	public AST parse() {
		token = scanner.scan();
		ClassDeclList cdl;
		try {
			cdl = parseProgram();
		}
		catch (SyntaxError e) { 
			System.out.println("Could not parse correctly.");
			return null;
		}
		return new Package(cdl, new SourcePosition(scanner.getLineNumber()));
	}

	// Program ::= (ClassDeclaration)* eot
	private ClassDeclList parseProgram() throws SyntaxError {
		ClassDeclList classDeclList = new ClassDeclList();
		
		while (token.kind == TokenKind.CLASS)
			classDeclList.add(parseClassDeclaration());
		
		accept(TokenKind.EOT);
		return classDeclList;
	}

	// ClassDeclaration ::= class id { (FieldDeclaration (; | MethodDeclaration))* }
	private ClassDecl parseClassDeclaration() throws SyntaxError {
		accept(TokenKind.CLASS);
		
		FieldDeclList  fdl = new FieldDeclList();
		MethodDeclList mdl = new MethodDeclList();
		String cn = token.spelling;
		
		accept(TokenKind.ID);
		accept(TokenKind.LBRACE);

		while (isVisibilityStarter(token.kind) || isAccessStarter(token.kind) || token.kind == TokenKind.VOID || isTypeStarter(token.kind)) {
			FieldDecl fd = parseFieldDeclaration();
			if (token.kind == TokenKind.SEMI) {
				if (fd.type.typeKind == TypeKind.VOID)
					parseError("Expected '(', but recieved " + token.kind + ".");
				acceptIt();
				fdl.add(fd);
			} else {
				mdl.add(parseMethodDeclaration(fd));
			}
		}
		
		accept(TokenKind.RBRACE);
		return new ClassDecl(cn, fdl, mdl, new SourcePosition(scanner.getLineNumber()));
	}
	
	// FieldDeclaration  ::= Visibility Access Type id
	// NOTE: [parseFieldDeclaration] takes VOID as a type for a possible error flag.
	private FieldDecl parseFieldDeclaration() {
		boolean isPrivate = parseVisibility();
		boolean isStatic  = parseAccess();
		TypeDenoter t     = null;
		
		switch(token.kind) {
		case ID: case INT: case BOOLEAN:
			t = parseType();
			break;
		case VOID:
			t = new BaseType(TypeKind.VOID, new SourcePosition(scanner.getLineNumber()));
			acceptIt();
			break;
		default:
			parseError("Expected [Type] or [VOID], but recieved " + token.kind + ".");
		}
		
		String fmName = token.spelling;
		accept(TokenKind.ID);
		return new FieldDecl(isPrivate, isStatic, t, fmName, new SourcePosition(scanner.getLineNumber()));
	}
	
	// MethodDeclaration ::= (ParameterList?) { Statement* }
	private MethodDecl parseMethodDeclaration(FieldDecl fd) {
		ParameterDeclList pdl = new ParameterDeclList();
		StatementList      sl = new StatementList();
		
		accept(TokenKind.LPAREN);
		if (isTypeStarter(token.kind)) 
			pdl = parseParameterList();
		accept(TokenKind.RPAREN);
		accept(TokenKind.LBRACE);
		
		while (isStatementStarter(token.kind))
			sl.add(parseStatement());
		
		accept(TokenKind.RBRACE);
		return new MethodDecl(fd, pdl, sl, new SourcePosition(scanner.getLineNumber()));
	}

	// Visibility ::= ( public | private )?
	// Additionally, it returns true if it reads a token of PRIVATE.
	private boolean parseVisibility() throws SyntaxError {
		if (isVisibilityStarter(token.kind)) {
			boolean isPrivate = (token.kind == TokenKind.PRIVATE);
			acceptIt();
			return isPrivate;
		}
		return false;
	}

	// Access ::= static ?
	private boolean parseAccess() throws SyntaxError {
		if (token.kind == TokenKind.STATIC) {
			acceptIt();
			return true;
		} else {
			return false;
		}
	}

	// Type ::= int | boolean | id | ( int | id ) []
	private TypeDenoter parseType() throws SyntaxError {
		TypeDenoter td;
		switch(token.kind) {
		case BOOLEAN:
			acceptIt();
			return new BaseType(TypeKind.BOOLEAN, new SourcePosition(scanner.getLineNumber()));
			
		case ID: 
			td = new ClassType(new Identifier(token), new SourcePosition(scanner.getLineNumber()));
			acceptIt();
			break;
		
		case INT:
			td = new BaseType(TypeKind.INT, new SourcePosition(scanner.getLineNumber()));
			acceptIt();
			break;
				
		default: 
			parseError("expecting starter for [Type], but found " + token.kind + ".");
			return null;
		}
		
		if (token.kind == TokenKind.LBRACKET) {
			acceptIt();
			accept(TokenKind.RBRACKET);
			td = new ArrayType(td, new SourcePosition(scanner.getLineNumber()));
		}	
		return td;
	}

	// ParameterList ::= Type id ( , Type id )*
	private ParameterDeclList parseParameterList() throws SyntaxError {
		ParameterDeclList pdl = new ParameterDeclList();
		TypeDenoter         t = parseType();
		String             cn = token.spelling;
		accept(TokenKind.ID);
		pdl.add(new ParameterDecl(t, cn, new SourcePosition(scanner.getLineNumber())));
		
		while (token.kind == TokenKind.COMMA) {
			acceptIt();
			t = parseType();
			cn = token.spelling;
			accept(TokenKind.ID);
			pdl.add(new ParameterDecl(t, cn, new SourcePosition(scanner.getLineNumber())));
		}
		return pdl;
	}

	// ArgumentList ::= Expression ( , Expression )*
	private ExprList parseArgumentList() throws SyntaxError {
		ExprList el = new ExprList();
		el.add(parseExpression());
		
		while (token.kind == TokenKind.COMMA) {
			acceptIt();
			el.add(parseExpression());
		}
		return el;
	}

	// Reference ::= (id | this) (. id)*
	private Reference parseReference() throws SyntaxError {
		Reference r;
		switch(token.kind) {
		case ID: 
			r = new IdRef(new Identifier(token), new SourcePosition(scanner.getLineNumber()));
			acceptIt();
			break;
			
		case THIS:
			r = new ThisRef(new SourcePosition(scanner.getLineNumber()));
			acceptIt();
			break;

		default:
			parseError("expecting starter for [Reference], but found " + token.kind + ".");
			return null;
		}

		while (token.kind == TokenKind.DOT) {
			acceptIt();
			r = new QualRef(r, new Identifier(token), new SourcePosition(scanner.getLineNumber()));
			accept(TokenKind.ID);
		}
		
		return r;
	}

	/* Statement ::=
	* { Statement* }
	* | Type id = Expression ;
	* | Reference = Expression ;
	* | Reference [ Expression ] = Expression ;
	* | Reference ( ArgumentList? ) ;
	* | return Expression? ;
	* | if ( Expression ) Statement (else Statement)?
	* | while ( Expression ) Statement
	*/
	private Statement parseStatement() throws SyntaxError {
		StatementList sl = new StatementList();
		Statement  s1 = null;
		Statement  s2 = null;
		Expression  e = null;
		TypeDenoter t = null;
		VarDecl     v = null;
		
		switch(token.kind) {
		case LBRACE:
			acceptIt();
			while (isStatementStarter(token.kind)) {
				sl.add(parseStatement());
			}
			accept(TokenKind.RBRACE);
			return new BlockStmt(sl, new SourcePosition(scanner.getLineNumber()));
			
		case INT: case BOOLEAN:
			t = parseType();
			v = new VarDecl(t, token.spelling, new SourcePosition(t.posn.getLineNumber()));
			accept(TokenKind.ID);
			accept(TokenKind.EQUAL);
			e = parseExpression();
			accept(TokenKind.SEMI);
			return new VarDeclStmt(v, e, new SourcePosition(v.posn.getLineNumber()));

		case THIS: case ID:
			t = new ClassType(new Identifier(token), new SourcePosition(scanner.getLineNumber()));
			Reference r = parseReference();
			Expression ixExpr = null;
			
			switch(token.kind) {
			case ID: case LBRACKET:
				if (token.kind == TokenKind.LBRACKET) {
					acceptIt();
					t = new ArrayType(t, new SourcePosition(scanner.getLineNumber()));
					if (isExpressionStarter(token.kind)) {
						ixExpr = parseExpression();
					}
					accept(TokenKind.RBRACKET);
				}
				
				if (ixExpr == null) {
					v = new VarDecl(t, token.spelling, new SourcePosition(scanner.getLineNumber()));
					accept(TokenKind.ID);
				}
			
			case EQUAL:
					accept(TokenKind.EQUAL);
					e = parseExpression();
					accept(TokenKind.SEMI);
					
					if (ixExpr != null) {
						return new IxAssignStmt(r, ixExpr, e, new SourcePosition(scanner.getLineNumber()));
					} else if (v != null) {
						return new VarDeclStmt(v, e, new SourcePosition(v.posn.getLineNumber()));
					} else {
						return new AssignStmt(r, e, new SourcePosition(r.posn.getLineNumber()));
					}

				case LPAREN:
					acceptIt();
					ExprList el = new ExprList();
					if (isExpressionStarter(token.kind)) {
						el = parseArgumentList();
					}
					accept(TokenKind.RPAREN);
					accept(TokenKind.SEMI);
					return new CallStmt(r, el, new SourcePosition(scanner.getLineNumber()));

				default:
					parseError("expecting '=', '[', or '(', but found " + token.kind + ".");
			}
			break;

		case RETURN:
			acceptIt();
			if (isExpressionStarter(token.kind)) {
				e = parseExpression();
			}
			accept(TokenKind.SEMI);
			return new ReturnStmt(e, new SourcePosition(scanner.getLineNumber()));

		case IF:
			acceptIt();
			accept(TokenKind.LPAREN);
			e = parseExpression();
			accept(TokenKind.RPAREN);
			s1 = parseStatement();
			s2 = null;
			if (token.kind == TokenKind.ELSE) {
				acceptIt();
				s2 = parseStatement();
			}
			return new IfStmt(e, s1, s2, new SourcePosition(e.posn.getLineNumber()));

		case WHILE:
			acceptIt();
			accept(TokenKind.LPAREN);
			e = parseExpression();
			accept(TokenKind.RPAREN);
			s1 = parseStatement();
			return new WhileStmt(e, s1, new SourcePosition(e.posn.getLineNumber()));
		
		default:
			parseError("expecting starter for [Statement], but found " + token.kind + ".");
		}
		return null;
	}
	
	/* Precedence rules.
	    Expression = D$
		D = C ( || C)*
		C = E (&& E)*
		E = R ( (== | !=) R)*
		R = A ( (<= | < | > | >=) A)*
		A = M ((+ | -) M)*
		M = U ( (* | / ) U)*
		U = -U | !U | | ( D ) | T
		T = Reference (, [ Expression ], ( ArgumentList? ) )
	        | num | true | false
	        | new ( id () | int [ Expression ] | id [ Expression ]
	        | null )
	 */
	private Expression parseExpression() {
		return parseD();
	}
	
	private Expression parseD() {
		Expression e1 = parseC();
		Expression e2 = null;
		Operator o;
		
		while (token.kind == TokenKind.OR) {
			o = new Operator(token);
			acceptIt();
			e2 = parseC();
			e1 = new BinaryExpr(o, e1, e2, new SourcePosition(scanner.getLineNumber()));
		}
		return e1;
	}
	
	private Expression parseC() {
		Expression e1 = parseE();
		Expression e2;
		Operator o;

		while (token.kind == TokenKind.AND) {
			o = new Operator(token);
			acceptIt();
			e2 = parseE();
			e1 = new BinaryExpr(o, e1, e2, new SourcePosition(scanner.getLineNumber()));
		}
		return e1;
	}
	
	private Expression parseE() {
		Expression e1 = parseR();
		Expression e2;
		Operator o;
		
		while (token.kind == TokenKind.EQUALTO || token.kind == TokenKind.NEQUALTO) {
			o = new Operator(token);
			acceptIt();
			e2 = parseR();
			e1 = new BinaryExpr(o, e1, e2, new SourcePosition(scanner.getLineNumber()));
		}
		return e1;
	}
	
	private Expression parseR() {
		Expression e1 = parseA();
		Expression e2;
		Operator o;
	
		while (token.kind == TokenKind.LTEQUALTO || token.kind == TokenKind.LTHAN || 
				token.kind == TokenKind.GTHAN || token.kind == TokenKind.GTEQUALTO) {
			o = new Operator(token);
			acceptIt();
			e2 = parseA();
			e1 = new BinaryExpr(o, e1, e2, new SourcePosition(scanner.getLineNumber()));
		}
		return e1;
	}
	
	private Expression parseA() {
		Expression e1 = parseM();
		Expression e2;
		Operator o;
		
		while (token.kind == TokenKind.PLUS || token.kind == TokenKind.MINUS) {
			o = new Operator(token);
			acceptIt();
			e2 = parseM();
			e1 = new BinaryExpr(o, e1, e2, new SourcePosition(scanner.getLineNumber()));
		}
		return e1;
	}

	private Expression parseM() {
		Expression e1 = parseU();
		Expression e2;
		Operator o;
		
		while (token.kind == TokenKind.TIMES || token.kind == TokenKind.DIVIDE) {
			o = new Operator(token);
			acceptIt();
			e2 = parseU();
			e1 = new BinaryExpr(o, e1, e2, new SourcePosition(scanner.getLineNumber()));
		}
		return e1;
	}

	private Expression parseU() {
		Operator o = null;
		Expression e;
		
		switch(token.kind) {
		case MINUS:
			o = new Operator(token);
			acceptIt();
			e = parseU();
			return new UnaryExpr(o, e, new SourcePosition(scanner.getLineNumber()));
			
		case NOT:
			o = new Operator(token);
			acceptIt();
			e = parseU();
			return new UnaryExpr(o, e, new SourcePosition(scanner.getLineNumber()));
			
		case LPAREN:
			acceptIt();
			e = parseD();
			accept(TokenKind.RPAREN);
			return e;
			
		default: 
			return parseT();
		}
	}
	
	private Expression parseT() throws SyntaxError {		
		Expression e = null;
		Reference  r = null;
		
		switch(token.kind) {
		case NULL:
			Identifier nullId = new Identifier(token);
			return new LiteralExpr(nullId, new SourcePosition(scanner.getLineNumber()));
		
		case THIS: case ID:
			r = parseReference();
			switch(token.kind) {
				case LBRACKET:
					acceptIt();
					e = parseExpression();
					accept(TokenKind.RBRACKET);
					return new IxExpr(r, e, new SourcePosition(scanner.getLineNumber()));
				case LPAREN:
					acceptIt();
					ExprList el = new ExprList();
					if (isExpressionStarter(token.kind)) {
						el = parseArgumentList();
					}
					accept(TokenKind.RPAREN);
					return new CallExpr(r, el, new SourcePosition(scanner.getLineNumber()));
				default:
					return new RefExpr(r, new SourcePosition(scanner.getLineNumber()));
			}
			
		case NUM: 
			IntLiteral il = new IntLiteral(token);
			acceptIt();
			return new LiteralExpr(il, new SourcePosition(scanner.getLineNumber()));
			
		case TRUE: case FALSE:
			BooleanLiteral bl = new BooleanLiteral(token);
			acceptIt();
			return new LiteralExpr(bl, new SourcePosition(scanner.getLineNumber()));

		case NEW:
			acceptIt();
			Identifier id = new Identifier(token);
			switch(token.kind) {
			case ID:
				ClassType td = new ClassType(id, new SourcePosition(scanner.getLineNumber()));
				acceptIt();
				if (token.kind == TokenKind.LPAREN) {
					acceptIt();
					accept(TokenKind.RPAREN);
					return new NewObjectExpr(td, new SourcePosition(scanner.getLineNumber()));
					
				} else if (token.kind == TokenKind.LBRACKET) {
					acceptIt();
					e = parseExpression();
					accept(TokenKind.RBRACKET);
					return new NewArrayExpr(td, e, new SourcePosition(scanner.getLineNumber()));
					
				} else {
					parseError("Expected '(' or '[', but got " + token.kind + ".");
					return null;
				}
				
			case INT:
				BaseType bt = new BaseType(TypeKind.INT, new SourcePosition(scanner.getLineNumber()));
				acceptIt();
				accept(TokenKind.LBRACKET);
				e = parseExpression();
				accept(TokenKind.RBRACKET);
				return new NewArrayExpr(bt, e, new SourcePosition(scanner.getLineNumber()));
				
			default:
				parseError("Expected ID or INT, but got " + token.kind + ".");
			}
			break;
			
		default:
			parseError("expecting starter for [Expression], but found " + token.kind + ".");
			return null;
		}
		
		return e;
	}
	

	/**
	 * accept current token and advance to next token
	 */
	private void acceptIt() throws SyntaxError {
		accept(token.kind);
	}

	/**
	 * verify that current token in input matches expected token and advance to next token
	 * @param expectedToken
	 * @throws SyntaxError  if match fails
	 */
	private void accept(TokenKind expectedTokenKind) throws SyntaxError {
		if (token.kind == expectedTokenKind) {
			if (trace)
				pTrace();
			token = scanner.scan();
		}
		else
			parseError("expecting '" + expectedTokenKind +
					"' but found '" + token.kind + "'");
	}

	/*
	 * Starters for each production rule.
	 * 
	 * Methods only created for rules that contain >1 starter.
	 */
	private boolean isVisibilityStarter(TokenKind givenTokenKind) {
		switch(givenTokenKind) {
		case PUBLIC: case PRIVATE:
			return true;
		default:
			return false;
		}
	}
	
	private boolean isAccessStarter(TokenKind givenTokenKind) {
		return givenTokenKind == TokenKind.STATIC;
	}
	
	private boolean isTypeStarter(TokenKind givenTokenKind) {
		switch(givenTokenKind) {
			case ID: case INT: case BOOLEAN:
				return true;
			default:
				return false;
		}
	}
	
	private boolean isReferenceStarter(TokenKind givenTokenKind) {
		switch(givenTokenKind) {
			case THIS: case ID:
				return true;
			default:
				return false;
		}
	}
	
	private boolean isStatementStarter(TokenKind givenTokenKind) {
		switch(givenTokenKind) {
		case RETURN: case IF: case WHILE: case LBRACE:
			return true;
		default:
			return isTypeStarter(givenTokenKind) || isReferenceStarter(givenTokenKind);
		}
	}
	
	private boolean isExpressionStarter(TokenKind givenTokenKind) {
		switch(givenTokenKind) {
		case MINUS: case NOT: case LPAREN: case NUM: case TRUE: case FALSE: case NEW:
			return true;
		default: 
			return isReferenceStarter(givenTokenKind);
		}
	}
	
	/**
	 * report parse error and unwind call stack to start of parse
	 * @param e string with error detail
	 * 		  
	 * @throws SyntaxError
	 */
	private void parseError(String e) throws SyntaxError {
		reporter.reportError("Parse error: " + e);
		throw new SyntaxError();
	}

	// show parse stack whenever terminal is  accepted
	private void pTrace() {
		StackTraceElement [] stl = Thread.currentThread().getStackTrace();
		for (int i = stl.length - 1; i > 0 ; i--) {
			if(stl[i].toString().contains("parse"))
				System.out.println(stl[i]);
		}
		System.out.println("accepting: " + token.kind + " (\"" + token.spelling + "\")");
	}
}