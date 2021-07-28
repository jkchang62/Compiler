/**
 * miniJava Abstract Syntax Tree classes
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.SourcePosition;

public abstract class Reference extends AST {
	Declaration decl;
	
	public Reference(SourcePosition posn) {
		super(posn);
	}
	
	public void setDecl(Declaration decl) {
		if (this.decl != null) {
			System.out.println("This [Reference] has an existing [decl].");
			return;
		}
		this.decl = decl;
	}
	
	public Declaration getDecl() {
		return decl;
	}
}
