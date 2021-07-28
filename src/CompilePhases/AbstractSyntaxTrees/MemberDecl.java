/**
 * miniJava Abstract Syntax Tree classes
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.SourcePosition;

abstract public class MemberDecl extends Declaration {

    public MemberDecl(boolean isPrivate, boolean isStatic, TypeDenoter mt, String name, SourcePosition posn) {
        super(name, mt, posn);
        this.isPrivate = isPrivate;
        this.isStatic = isStatic;

        if (mt instanceof ClassType) {
            cn = ((ClassType) mt).className.spelling;
        }
    }
    
    public MemberDecl(MemberDecl md, SourcePosition posn){
    	super(md.name, md.type, posn);
    	this.isPrivate = md.isPrivate;
    	this.isStatic = md.isStatic;
    }
    
    public boolean isPrivate;
    public boolean isStatic;
    public String cn;
}
