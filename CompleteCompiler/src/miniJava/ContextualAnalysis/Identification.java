package miniJava.ContextualAnalysis;

import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.SyntacticAnalyzer.Token;
import miniJava.SyntacticAnalyzer.TokenKind;
import miniJava.ErrorReporter;

import java.util.ArrayList;
import java.util.HashMap;

public class Identification implements Visitor<Object, Object> {

	// Identification table
	public IdentificationTable table;

	// [ErrorReporter] used to determine if the program needs to be terminates.
	private ErrorReporter reporter;

	// Bookeeping variables to track the program's progress.
	public ClassDecl currentClass;
	public MethodDecl currentMethod;

	// Variables used to determine validity.
	boolean assignmentFlag;
	boolean isCalling;
	int numOfMainMethods;
	

	// Constructor
	public Identification(AST ast, ErrorReporter reporter) {
		this.reporter = reporter;
		table = new IdentificationTable(reporter);
		ast.visit(this, null);
	}

	// Visits a [Package].
	public Object visitPackage(Package prog, Object obj) {
		// Creating the predefined classes.
		ArrayList<ClassDecl> predefinedClasses = createPredefinedClasses();

		// Creating null variable.
		VarDecl nullDecl = new VarDecl(new BaseType(TypeKind.NULL, null), "null", null);

		// Opening scope 1.
		table.openScope();
		
		// Entering null into the table and visiting it.
		table.enter(nullDecl);
		nullDecl.type.visit(this, null);

		// Adding predefined classes to the table.
		for (ClassDecl cd : predefinedClasses) {
			table.enter(cd);
		}
		
		// Entering remaining classes to the table and visiting.
		for (ClassDecl cd : prog.classDeclList) {
			table.enter(cd);
		}
		for (ClassDecl cd : prog.classDeclList) {
			cd.visit(this, null);
		}
		
		// Checking if the appropriate number of main methods were found.
		 if (numOfMainMethods != 1) 
		 	identificationError("0", "Expected one main method, but got " + numOfMainMethods + ".");
		 
		
		// Closing scope 1.
		table.closeScope();
		return null;
	}

	// Visits a [ClassDecl].
	public Object visitClassDecl(ClassDecl cd, Object obj) {
		// Tracking variable.
		currentClass = cd;

		// Adding members so all fields and members are visible.
		// Opening scope 2.
		table.openScope();
		for (FieldDecl fd : cd.fieldDeclList) {
			table.enter(fd);
		}
		
		// Adding methods.
		for (MethodDecl md : cd.methodDeclList) {
			// Checking for the existence of a main method.
			if (verifyMainMethod(md))
				numOfMainMethods++;
			table.enter(md);
		}

		// Visiting all members.
		for (FieldDecl fd : cd.fieldDeclList)
			fd.visit(this, null);
		for (MethodDecl md : cd.methodDeclList) {
			md.visit(this, null);
		}

		// Closing scope 2.
		table.closeScope();
		return null;
	}

	// Visits a [FieldDecl].
	public Object visitFieldDecl(FieldDecl fd, Object obj) {
		fd.type.visit(this, null);
		return null;
	}

	// Visits a [MethodDecl].
	public Object visitMethodDecl(MethodDecl md, Object obj) {
		// Setting tracker variable.
		currentMethod = md;
		
		md.type.visit(this, null);
		// Opening scope 3.
		table.openScope();
		for (ParameterDecl pd : md.parameterDeclList) {
			pd.visit(this, null);
		}
		// Opening scope 4.
		table.openScope();

		for (Statement st : md.statementList) {
			st.visit(this, null);
		}
		
		// Check that the last statement for a non-void method is a return statement.
		if (md.type.typeKind != TypeKind.VOID) {
			if (md.statementList.size() == 0 || !(md.statementList.get(md.statementList.size() - 1) instanceof ReturnStmt)) {
				identificationError(md.posn.toString(), "Expected a return statement.");
			}
		}
		
		// Closing scope 4.
		table.closeScope();

		// Closing scope 3.
		table.closeScope();
		return null;
	}

	@Override
	public Object visitParameterDecl(ParameterDecl pd, Object arg) {
		// Validating parameter class type.
		if (pd.type instanceof ClassType) {
			// An error will be thrown from retrival if the class does not exist.
			String className = (String) (((ClassType) pd.type).className.spelling);
			table.retrieveFrom(1, className, false, pd.posn);
		}
		pd.type.visit(this, null);
		table.enter(pd);
		return null;
	}

	@Override
	public Object visitVarDecl(VarDecl decl, Object arg) {
		// Validating variable class type.
		if (decl.type instanceof ClassType) {
			// An error will be thrown from retrival if the class does not exist.
			String className = (String) (((ClassType) decl.type).className.spelling);
			table.retrieveFrom(1, className, false, decl.posn);
		}
		decl.type.visit(this, null);
		table.replace(decl);
		return null;
	}

	@Override
	public Object visitBaseType(BaseType type, Object arg) {
		return null;
	}

	@Override
	public Object visitClassType(ClassType type, Object arg) {
		type.className.visit(this, null);
		return null;
	}

	@Override
	public Object visitArrayType(ArrayType type, Object arg) {
		type.eltType.visit(this, null);
		return null;
	}

	@Override
	public Object visitBlockStmt(BlockStmt stmt, Object arg) {
		table.openScope();
		for (Statement s : stmt.sl) {
			s.visit(this, null);
		}
		table.closeScope();
		return null;
	}

	@Override
	public Object visitVardeclStmt(VarDeclStmt stmt, Object arg) {
		// Reserve the variable name.
		table.enter(new VarDecl(new BaseType(TypeKind.RESERVE, stmt.posn), stmt.varDecl.name, stmt.posn));
		stmt.initExp.visit(this, null);
		stmt.varDecl.visit(this, null);

		// Check if the variable is being set to a class.
		if (stmt.initExp instanceof RefExpr && ((RefExpr) stmt.initExp).ref.getDecl() instanceof ClassDecl) {
			identificationError(stmt.posn.toString(), "VarDeclStmt can't be set to a class name");
		}
		return null;
	}

	@Override
	public Object visitAssignStmt(AssignStmt stmt, Object arg) {
		assignmentFlag = true;
		stmt.ref.visit(this, null);
		assignmentFlag = false;
		stmt.val.visit(this, null);

		// Check for incorrect assignments.
		if (stmt.val instanceof RefExpr && !(((RefExpr) stmt.val).ref instanceof ThisRef)) {
			Declaration refDecl = ((RefExpr) stmt.val).ref.getDecl();
			if (refDecl instanceof MethodDecl || refDecl instanceof ClassDecl) {
				identificationError(stmt.posn.toString(), "AssignStmt can't be set directly to a method or class name.");
			}
		}
		return null;
	}

	@Override
	public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg) {
		stmt.exp.visit(this, null);
		stmt.ix.visit(this, null);
		stmt.ref.visit(this, null);
		return null;
	}

	@Override
	public Object visitCallStmt(CallStmt stmt, Object arg) {
		// Calls can only be made on methods.
		if (!(stmt.methodRef instanceof IdRef) && !(stmt.methodRef instanceof QualRef) ) {
			identificationError(stmt.posn.toString(), "CallStmt can only be made on methods.");
		}
		for (Expression e : stmt.argList) {
			e.visit(this, null);
		}
		
		isCalling = true;
		stmt.methodRef.visit(this, null);
		isCalling = false;
		return null;
	}

	@Override
	public Object visitReturnStmt(ReturnStmt stmt, Object arg) {
		if (stmt.returnExpr != null)
			stmt.returnExpr.visit(this, null);
		return null;
	}

	@Override
	public Object visitIfStmt(IfStmt stmt, Object arg) {
		stmt.cond.visit(this, null);
		
		// Visit and check for special cases.
		if (stmt.thenStmt != null) {
			stmt.thenStmt.visit(this, null);
			if (stmt.thenStmt instanceof VarDeclStmt) {
				identificationError(stmt.posn.toString(), "if-then statement can not contain VarDeclStmt");
			} 
		}
		if (stmt.elseStmt != null) {
			stmt.elseStmt.visit(this, null);
			if (stmt.elseStmt instanceof VarDeclStmt) {
				identificationError(stmt.posn.toString(), "else-then statement can not contain VarDeclStmt");
			}
		}
		return null;
	}

	@Override
	public Object visitWhileStmt(WhileStmt stmt, Object arg) {
		stmt.body.visit(this, null);
		stmt.cond.visit(this, null);
		if (stmt.body instanceof VarDeclStmt) {
			identificationError(stmt.posn.toString(), "while-body statement can not contain VarDeclStmt");
		}
		return null;
	}

	@Override
	public Object visitUnaryExpr(UnaryExpr expr, Object arg) {
		expr.expr.visit(this, null);
		expr.operator.visit(this, null);
		return null;
	}

	@Override
	public Object visitBinaryExpr(BinaryExpr expr, Object arg) {
		expr.left.visit(this, null);
		expr.operator.visit(this, null);
		expr.right.visit(this, null);
		return null;
	}

	@Override
	public Object visitRefExpr(RefExpr expr, Object arg) {
		expr.ref.visit(this, null);
		return null;
	}

	@Override
	public Object visitIxExpr(IxExpr expr, Object arg) {
		expr.ixExpr.visit(this, null);
		expr.ref.visit(this, null);
		return null;
	}

	@Override
	public Object visitCallExpr(CallExpr expr, Object arg) {
		isCalling = true;
		expr.functionRef.visit(this, null);
		isCalling = false;
		for (Expression e : expr.argList) {
			e.visit(this, null);
		}
		return null;
	}

	@Override
	public Object visitLiteralExpr(LiteralExpr expr, Object arg) {
		return expr.lit;
	}

	@Override
	public Object visitNewObjectExpr(NewObjectExpr expr, Object arg) {
		expr.classtype.visit(this, null);
		return null;
	}

	@Override
	public Object visitNewArrayExpr(NewArrayExpr expr, Object arg) {
		expr.eltType.visit(this, null);
		expr.sizeExpr.visit(this, null);
		return null;
	}

	@Override
	public Object visitThisRef(ThisRef ref, Object arg) {
		if (currentMethod.isStatic) {
			identificationError(ref.posn.toString(), "this keyword cannot be used in a static context.");
		}
		ref.setDecl(currentClass);
		return null;
	}

	@Override
	public Object visitIdRef(IdRef ref, Object arg) {
		Declaration d = table.retrieve(ref.id.spelling, ref.posn);
		if (d instanceof VarDecl && ((VarDecl) d).type.typeKind == TypeKind.RESERVE) {
			identificationError(ref.posn.toString(), "Cannot reference " + ref.id.spelling + " within the initializing expression.");
		} else {
			ref.setDecl(d);
			ref.id.visit(this, null);
		}
		return null;
	}

	@Override
	public Object visitQRef(QualRef ref, Object arg) {
		// QualRef = Ref.id
		ref.ref.visit(this, null);
		
		// Name of the ref.
		String refName = "";
		if (ref.ref instanceof QualRef) {
			refName = ((QualRef) ref.ref).id.spelling;
		} else if (ref.ref instanceof ThisRef) {
			refName = "this";
		} else if (ref.ref instanceof IdRef) {
			refName = ((IdRef) ref.ref).id.spelling;
		}

		if (ref.id.spelling.equals("length")) {
			// Check if the length is trying to be re-assigned.
			if (assignmentFlag) {
				identificationError(ref.posn.toString(), "Cannot re-assign length.");
			} else if (ref.ref.getDecl().type.typeKind == TypeKind.ARRAY) {
				// Length property.
				FieldDecl lengthDecl = new FieldDecl(false, false, new BaseType(TypeKind.INT, null), "length", null);
				ref.setDecl(lengthDecl);
				ref.id.setDecl(lengthDecl);
			}
			return null;
		}

		// Change [ref.ref] to its ClassDecl.
		Declaration refDecl = ref.ref.getDecl();
		try {
			if (refDecl instanceof ClassDecl) {
				refDecl = (ClassDecl) refDecl;
			} else if (refDecl instanceof FieldDecl) {
				refDecl = table.retrieveFrom(1, ((FieldDecl) refDecl).cn, false, ref.posn);
			} else if (refDecl instanceof MethodDecl) {
				refDecl = table.retrieveFrom(1, ((MethodDecl) refDecl).cn, false, ref.posn);
			} else if (refDecl instanceof VarDecl) {
				refDecl = table.retrieveFrom(1, ((VarDecl) refDecl).cn, false, ref.posn);
			} else if (refDecl instanceof ParameterDecl) {
				refDecl = table.retrieveFrom(1, ((ParameterDecl) refDecl).cn, false, ref.posn);
			}
		} catch (ClassCastException e) {
			identificationError(ref.posn.toString(), "Unknown QualRef access.");
			return null;
		}

		// Searching for field declaration associated with id.
		FieldDeclList fieldList = ((ClassDecl) refDecl).fieldDeclList;
		for (FieldDecl field : fieldList) {
			// Finding a field that matches the spelling of the current [id].
			if (field.name.equals(ref.id.spelling)) {
				
				// Throwing an error if the private field is being accessed from another class.
				if (field.isPrivate && !refDecl.equals(currentClass)) {
					identificationError(ref.posn.toString(), "Cannot access a private field of a different class.");
				} 
				
				// Throwing an error if a class name is trying to access a non-static field.
				else if (!field.isStatic && table.retrieveFrom(1, refName, true, ref.posn) != null && !(ref.ref instanceof IdRef)) {
					identificationError(ref.posn.toString(), "Cannot directly access non-static field " + field.name + " from " + refDecl.name);
				}
				
				// Throwing an error if [this] is attempting to access a static field from a non-static method.
				else if (!currentMethod.isStatic && ref.ref instanceof ThisRef && field.isStatic) {
					identificationError(ref.posn.toString(), "Cannot access static field " + field.name + " using this keyword from a non-static method.");
				}
				
				// Setting declarations.
				ref.setDecl(field);
				ref.id.setDecl(field);
				return null;
			}
		}

	// Searching for method declaration associated with id.
		MethodDeclList methodList = ((ClassDecl) refDecl).methodDeclList;
		for (MethodDecl method : methodList) {
			// Finding a method that matches the spelling of the current [id].
			if (method.name.equals(ref.id.spelling)) {
				
				// Throwing an error if the private method is being accessed from another class.
				if (method.isPrivate && !refDecl.equals(currentClass)) {
					identificationError(ref.posn.toString(), "Cannot access a private method of a different class.");
					
				// Throwing an error if a method is not static when a classname is used.
				} else if (!method.isStatic && table.retrieveFrom(1, refName, true, ref.posn) != null && !(ref.ref instanceof IdRef)) {
					identificationError(ref.posn.toString(), "Can't access " + method.name + ", a non-static method.");
				}
				
				// Throwing an error if [this] is attempting to access a static method from a non-static method.
				else if (!currentMethod.isStatic && ref.ref instanceof ThisRef && method.isStatic) {
					identificationError(ref.posn.toString(), "Cannot access static method " + method.name + " using this keyword from a non-static method.");
				}
				
				// Setting declarations.
				ref.setDecl(method);
				ref.id.setDecl(method);
				return null;
			}
		}
		identificationError(ref.posn.toString(), "QRef does not reference a variable in " + refDecl.name + ".");
		return null;
	}

	@Override
	public Object visitIdentifier(Identifier id, Object arg) {
		// Linking a declaration to the identifier.
		Declaration d = table.retrieve(id.spelling, id.posn);
		
		// Checking for a non-static field/method being used in a static method..
		if (d instanceof FieldDecl && !((FieldDecl) d).isStatic && currentMethod != null && currentMethod.isStatic) {
			identificationError(id.posn.toString(), "Cannot reference non-static symbol " + id.spelling + " in static context.");
		} else if (d instanceof MethodDecl && !((MethodDecl) d).isStatic && currentMethod != null && currentMethod.isStatic){
			identificationError(id.posn.toString(), "Cannot reference non-static method " + id.spelling + " in static context.");
		} else {
			id.setDecl(table.retrieve(id.spelling, id.posn));
		}

		return null;
	}

	@Override
	public Object visitOperator(Operator op, Object arg) {
		return null;
	}

	@Override
	public Object visitIntLiteral(IntLiteral num, Object arg) {
		return null;
	}

	@Override
	public Object visitBooleanLiteral(BooleanLiteral bool, Object arg) {
		return null;
	}
	
	// Reports an identification error and leaves.
	private void identificationError(String lineNumber, String e) throws IdentificationError {
		reporter.reportError("*** line " + lineNumber + ": " + e);
		System.exit(4);
	}

	// Verifies whether a method is the main method.
	public boolean verifyMainMethod(MethodDecl md) {
		try {
			return (!md.isPrivate && md.isStatic && md.name.equals("main") && md.type.typeKind == TypeKind.VOID
					&& ((ClassType) (((ArrayType) md.parameterDeclList.get(0).type).eltType)).className.spelling
							.equals("String")
					&& md.parameterDeclList.get(0).name.equals("args") && md.parameterDeclList.size() == 1);
		} catch (ClassCastException e) {}
		return false;
	}

	// Creates the predefined classes for Java.
	private ArrayList<ClassDecl> createPredefinedClasses() {
		// [ArrayList] containing the predefined classes.
		ArrayList<ClassDecl> predefinedClasses = new ArrayList<ClassDecl>();

		// Creating predefined classes.
		// System:
		String cn = "System";
		FieldDeclList     fdl = new FieldDeclList();
		MethodDeclList    mdl = new MethodDeclList();
		ParameterDeclList pdl = new ParameterDeclList();
		StatementList      sl = new StatementList();
		Identifier         id = new Identifier(new Token(TokenKind.CLASS, "_PrintStream", null));
		TypeDenoter        td = new ClassType(id, null);
		FieldDecl       field = new FieldDecl(false, true, td, "out", null);
		MethodDecl     method = new MethodDecl(field, pdl, sl, null);
		fdl.add(field);
		predefinedClasses.add(new ClassDecl(cn, fdl, mdl, null));
		
		// _PrintStream
		cn    = "_PrintStream";
		fdl   = new FieldDeclList();
		mdl   = new MethodDeclList();
		td    = new BaseType(TypeKind.INT, null);
		field = new FieldDecl(false, false, new BaseType(TypeKind.VOID, null), "println", null);
		ParameterDecl parameter = new ParameterDecl(td, "n", null);
		pdl.add(parameter);
		method = new MethodDecl(field, pdl, sl, null);
		mdl.add(method);
		predefinedClasses.add(new ClassDecl(cn, fdl, mdl, null));
		
		// String
		cn  = "String";
		mdl = new MethodDeclList();
		pdl = new ParameterDeclList();
		predefinedClasses.add(new ClassDecl(cn, fdl, mdl, null));

		return predefinedClasses;
	}
}
