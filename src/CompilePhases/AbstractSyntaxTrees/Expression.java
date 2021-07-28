/**
 * miniJava Abstract Syntax Tree classes
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.SourcePosition;

public abstract class Expression extends AST {
	private TypeKind type;

	public Expression(SourcePosition posn) {
		super(posn);
	}
	
	public void setType(TypeKind t) {
		this.type = t;
	}
	
	public TypeKind getType() {
		return type;
	}
}
