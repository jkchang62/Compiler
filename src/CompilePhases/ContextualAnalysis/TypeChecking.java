package miniJava.ContextualAnalysis;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.SyntacticAnalyzer.TokenKind;

public class TypeChecking implements Visitor<Object, Object> {
	
	private ErrorReporter reporter;	
	private MethodDecl currentMethod;
	private ClassDecl  currentClass;
	
	public TypeChecking(AST ast, ErrorReporter reporter) {
		this.reporter = reporter;
		ast.visit(this, null);
	}

	@Override
	public Object visitPackage(Package prog, Object obj) {			
		// Visiting all classes.
		for (ClassDecl cd : prog.classDeclList) {
			cd.visit(this, null);
		}
		return null;
	}

	@Override
	public Object visitClassDecl(ClassDecl cd, Object obj) {
		// Saving current class for future use.
		currentClass = cd;
		
		// Visiting all fields and members.
		for (FieldDecl fd : cd.fieldDeclList)
			fd.visit(this, null);
		for (MethodDecl md : cd.methodDeclList) {
			currentMethod = md;
			md.visit(this, null);
		}
		return null;
	}

	@Override
	public Object visitFieldDecl(FieldDecl fd, Object obj) {
		// Visiting a field.
		fd.type.visit(this, null);
		return null;
	}

	@Override
	public Object visitMethodDecl(MethodDecl md, Object obj) {
		// Saving the current method for future use.
		currentMethod = md;
		
		// Visiting all parameters and statements of a method.
		for (ParameterDecl pd : md.parameterDeclList) {
			pd.visit(this, null);
		}
		for (Statement st : md.statementList) {
			st.visit(this, null);
		}
		return null;
	}

	@Override
	public Object visitParameterDecl(ParameterDecl pd, Object arg) {		
		// Returns parameter type.
		return pd.type.visit(this, arg);
	}

	@Override
	public Object visitVarDecl(VarDecl decl, Object arg) {
		// Returns var type.
		return decl.type.visit(this, arg);
	}

	@Override
	public Object visitBaseType(BaseType type, Object arg) {
		// Returns base type.
		return type.typeKind;
	}

	@Override
	public Object visitClassType(ClassType type, Object arg) {
		// Returns a class's name if [arg] is of TypeKind.CLASS.
		// Otherwise, it returns TypeKind.CLASS.
		if (arg == TypeKind.CLASS) {
			return type.className.spelling;
		}
		return type.typeKind;
	}

	@Override
	public Object visitArrayType(ArrayType type, Object arg) {
		// Returns an array's element's type if arg is of TypeKind.ARRAY.
		// Otherwise, it returns TypeKind.ARRAY.
		if (arg == TypeKind.ARRAY) {
			return type.eltType.typeKind;
		}
		return type.typeKind;
	}

	@Override
	public Object visitBlockStmt(BlockStmt stmt, Object arg) {
		// Visiting all statements in the block.
		for (Statement s : stmt.sl) {
			s.visit(this, arg);
		}
		return null;
	}

	@Override
	public Object visitVardeclStmt(VarDeclStmt stmt, Object arg) {		
		// Type retrieval.
		TypeKind stmtDeclType = (TypeKind) stmt.initExp.visit(this, arg);
		TypeKind stmtInitType = (TypeKind) stmt.varDecl.visit(this, arg);
		 
		// Special case between a class and null.
		if (stmtDeclType == TypeKind.CLASS && stmtInitType == TypeKind.NULL 
				|| stmtDeclType == TypeKind.NULL && stmtInitType == TypeKind.CLASS 
				|| stmtDeclType == TypeKind.ARRAY && stmtInitType == TypeKind.NULL 
				|| stmtDeclType == TypeKind.NULL && stmtInitType == TypeKind.ARRAY) {
			return null;
		}
		
		// Arrays must be of the same type.
		if (stmtDeclType == TypeKind.ARRAY && stmtInitType == TypeKind.ARRAY) {
			TypeKind arrayDeclType = (TypeKind) stmt.initExp.visit(this, TypeKind.ARRAY);
			TypeKind arrayInitType = (TypeKind) stmt.varDecl.visit(this, TypeKind.ARRAY);
			if (arrayDeclType != arrayInitType) {
				typeError(stmt.posn.toString(), stmt.toString(), arrayInitType, arrayDeclType, null);
			}
		}
		// If only one is of type array, interpret the value from the index.
		else if (stmtDeclType == TypeKind.ARRAY) {
			stmtDeclType = (TypeKind) stmt.varDecl.visit(this, TypeKind.ARRAY);
		} else if (stmtInitType == TypeKind.ARRAY) {
			stmtInitType = (TypeKind) stmt.initExp.visit(this, TypeKind.ARRAY);
		}
		
		// Classes must be equivalent in name.
		if (stmtDeclType == TypeKind.CLASS && stmtInitType == TypeKind.CLASS) {
			// Interpreting possible IxExpr.
			String classDeclName = "";
			if (stmt.initExp instanceof IxExpr) {
				classDeclName = ((ClassType) ((ArrayType) ((IxExpr) stmt.initExp).ref.getDecl().type).eltType).className.spelling;
			} else if (stmt.initExp instanceof CallExpr) { 
				classDeclName = ((ClassType) ((MethodDecl) ((CallExpr) stmt.initExp).functionRef.getDecl()).type).className.spelling;
			} else {
				classDeclName = (String) stmt.initExp.visit(this, TypeKind.CLASS);
			}
			String classInitName = (String) stmt.varDecl.visit(this, TypeKind.CLASS);
			if (!(classDeclName.equals(classInitName))) {
				customTypeError(stmt.posn.toString(), stmt.toString(), "expected same class name, but got " + classDeclName + " and " + classInitName + " instead.", null);
			}
		}
		
		// Throwing an error if the types on the left and right are not equivalent.
		if (stmtInitType != stmtDeclType && (stmtInitType != TypeKind.ERROR || stmtDeclType != TypeKind.ERROR)) {
			typeError(stmt.posn.toString(), stmt.toString(), stmtInitType, stmtDeclType, null);
		}
		return null;
	}

	@Override
	public Object visitAssignStmt(AssignStmt stmt, Object arg) {
		// Type retrieval.
		TypeKind stmtRefType  = (TypeKind) stmt.ref.visit(this, arg);
		TypeKind stmtDeclType = (TypeKind) stmt.val.visit(this, arg);
		
		// Special case between a class and an array and null.
		if (stmtDeclType == TypeKind.CLASS && stmtRefType == TypeKind.NULL 
				|| stmtDeclType == TypeKind.NULL && stmtRefType == TypeKind.CLASS
				|| stmtDeclType == TypeKind.ARRAY && stmtRefType == TypeKind.NULL 
				|| stmtDeclType == TypeKind.NULL && stmtRefType == TypeKind.ARRAY) {
			return null;
		}

		// Class names must be equivalent.
		// Check for name equivalence if it's a class.
		if (stmtRefType == TypeKind.CLASS && stmtDeclType == TypeKind.CLASS) {
			String declCN = "";
			if (stmt.val instanceof IxExpr) {
				declCN = ((ClassType) ((ArrayType) ((IxExpr) stmt.val).ref.getDecl().type).eltType).className.spelling;
			} else if (stmt.val instanceof CallExpr) { 
				declCN = ((ClassType) ((MethodDecl) ((CallExpr) stmt.val).functionRef.getDecl()).type).className.spelling;
			} else {
				declCN = (String) stmt.val.visit(this, TypeKind.CLASS);
			}
			String initCN = (String) stmt.ref.visit(this, TypeKind.CLASS);
			if (!(initCN.equals(declCN))) {
				customTypeError(stmt.posn.toString(), stmt.toString(), "expected class " + initCN + ", but got class " + declCN + " instead.", null);
			}
		}
		
		// Types on the left and right must be equivalent.
		if (stmtRefType != stmtDeclType) {
			typeError(stmt.posn.toString(), stmt.toString(), stmtRefType, stmtDeclType, null);
		}
		return null;
	}

	@Override
	public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg) {
		// Initializing all relevant types.
		TypeKind initEltType = (TypeKind) stmt.ref.visit(this, TypeKind.ARRAY);
		TypeKind declEltType = (TypeKind) stmt.exp.visit(this, TypeKind.ARRAY);	
		TypeKind refType   = (TypeKind) stmt.ref.visit(this, arg);
		TypeKind indexType = (TypeKind) stmt.ix.visit(this,  arg);
		
		// Index must be type integer.
		if (indexType != TypeKind.INT) {
			typeError(stmt.posn.toString(), stmt.toString(), TypeKind.INT, indexType, null);
			
		// Reference must be an array.
		} else if (refType != TypeKind.ARRAY) {
			typeError(stmt.posn.toString(), stmt.toString(), TypeKind.ARRAY, refType, null);
			
		// Array's element type and declared element type must be equivalent.
		} else if (declEltType != initEltType) {
			typeError(stmt.posn.toString(), stmt.toString(), initEltType, declEltType, null);
		}
		
		// Check for name equivalence if it's a class.
		if (initEltType == TypeKind.CLASS) {
			String initCN = (String) stmt.ref.visit(this, TypeKind.CLASS);
			String declCN = (String) stmt.exp.visit(this, TypeKind.CLASS);
			if (!(initCN.equals(declCN))) {
				customTypeError(stmt.posn.toString(), stmt.toString(), "expected class " + initCN + ", but got class " + declCN + " instead.", null);
			}
		}
		return null;
	}

	@Override
	public Object visitCallStmt(CallStmt stmt, Object arg) {
		// Visiting all arguments in a call.
		for (Expression e : stmt.argList) {
			e.visit(this, arg);
		}
		stmt.methodRef.visit(this, arg);
		return null;
	}

	@Override
	public Object visitReturnStmt(ReturnStmt stmt, Object arg) {
		TypeKind methodReturnType = (TypeKind) currentMethod.type.visit(this, null);

		// No expression is expected for a null return type.
		if (methodReturnType != TypeKind.VOID && stmt.returnExpr == null) {
			typeError(stmt.posn.toString(), stmt.toString(), methodReturnType, TypeKind.VOID, null);
			return null;
		} else if (methodReturnType == TypeKind.VOID && stmt.returnExpr == null) {
			return null;
		}

		// Return types must be equivalent.
		TypeKind returnType = (TypeKind) stmt.returnExpr.visit(this, null);
		if (methodReturnType != returnType) {
			typeError(stmt.posn.toString(), stmt.toString(), methodReturnType, returnType, null);
			return null;
		}
		
		// Class names must be equivalent.
		if (methodReturnType == TypeKind.CLASS) {
			String methodReturnCN = (String) currentMethod.type.visit(this, TypeKind.CLASS);
			String returnCN = (String) stmt.returnExpr.visit(this, TypeKind.CLASS);
			
			if (!methodReturnCN.equals(returnCN)) {
				customTypeError(stmt.posn.toString(), stmt.toString(),
						"expected class " + methodReturnCN + ", but got class " + returnCN + " instead.", null);
			}
		}

		// Arrays' element type must be equivalent.
		if (methodReturnType == TypeKind.ARRAY) {
			TypeKind methodEltType = (TypeKind) currentMethod.type.visit(this, TypeKind.ARRAY);
			TypeKind returnEltType = (TypeKind) stmt.returnExpr.visit(this, TypeKind.ARRAY);
			if (methodEltType != returnEltType) {
				customTypeError(stmt.posn.toString(), stmt.toString(), "expected an array of type " + methodEltType + ", but got array of type " + returnEltType  + " instead.", null);
			}
		}
		return null;
	}

	@Override
	public Object visitIfStmt(IfStmt stmt, Object arg) {
		// Conditional must be type boolean.
		TypeKind conditionType = (TypeKind) stmt.cond.visit(this, arg);
		if (conditionType != TypeKind.BOOLEAN) {
			typeError(stmt.posn.toString(), stmt.toString(), TypeKind.BOOLEAN, conditionType, null);
		}
		
		// Visiting possible statements.
		if (stmt.elseStmt != null)
			stmt.elseStmt.visit(this, arg);
		if (stmt.thenStmt != null)
			stmt.thenStmt.visit(this, arg);
		
		return null;
	}

	@Override
	public Object visitWhileStmt(WhileStmt stmt, Object arg) {
		// Conditional must be type boolean.
		TypeKind conditionType = (TypeKind) stmt.cond.visit(this, arg);
		if (conditionType != TypeKind.BOOLEAN) {
			typeError(stmt.posn.toString(), stmt.toString(), TypeKind.BOOLEAN, conditionType, null);
		}
		
		// Visiting the body.
		stmt.body.visit(this, arg);
		return null;
	}

	@Override
	public Object visitUnaryExpr(UnaryExpr expr, Object arg) {
		// Operand and operator type retrieval.
		TypeKind  exprType = (TypeKind) expr.expr.visit(this, arg);
		TokenKind operator = (TokenKind) expr.operator.visit(this, arg);
		expr.setType(exprType);
		
		// Operand can only be of type boolean or int.
		if (exprType != TypeKind.BOOLEAN && exprType != TypeKind.INT) {
			customTypeError(expr.posn.toString(), expr.toString(), "expected either type BOOLEAN or INT, but got type " + exprType + " instead.", expr);
		}

		// Checking for proper operand and operation type combination.
		if (operator == TokenKind.MINUS && exprType == TypeKind.BOOLEAN
				|| operator == TokenKind.NOT && exprType == TypeKind.INT) {
			customTypeError(expr.posn.toString(), expr.toString(), "operand " + operator + " cannot be applied to type " + exprType + ".", expr);
		}
		return expr.getType();
	}

	@Override
	public Object visitBinaryExpr(BinaryExpr expr, Object arg) {
		// Get types of the left and right operands.
		TypeKind leftType  = (TypeKind) expr.left.visit(this, arg);
		TypeKind rightType = (TypeKind) expr.right.visit(this, arg);
		
		// Checking that both types are the same.
		if (leftType != rightType) {
			// Check special case of null and an object.
			if (leftType != TypeKind.NULL && rightType != TypeKind.CLASS 
					&& leftType != TypeKind.CLASS && rightType != TypeKind.NULL) {
				customTypeError(expr.posn.toString(), expr.toString(), "incompatible types, " + leftType + " and " + rightType + ".", expr);
				return expr.getType();
			}
		}
		
		// Checking if class names are equivalent as well.
		if (leftType == TypeKind.CLASS && rightType == TypeKind.CLASS) {
			String leftCN = (String) expr.left.visit(this, TypeKind.CLASS);
			String rightCN = (String) expr.right.visit(this, TypeKind.CLASS);
			if (!(leftCN.equals(rightCN))) {
				customTypeError(expr.posn.toString(), expr.toString(), "incompatible types, " + leftCN + " and " + rightCN + ".", expr);
				return expr.getType();
			}
		}
		
		// Checking if the operator is valid for the types.
		TokenKind operator = (TokenKind) expr.operator.visit(this, arg);
		switch(operator) {
		// Only type boolean can be operated by '||' and '&&'.
		case OR: case AND:
			if (leftType != TypeKind.BOOLEAN) {
				customTypeError(expr.posn.toString(), expr.toString(), "bad operand type " + operator + " for types " + leftType + ".", expr);
			} else {
				expr.setType(TypeKind.BOOLEAN);
			}
			break;
			
		// Only type int can be operated by '+', '-', '*', '/', '<', '<=', '>', and '>='.
		case PLUS: case MINUS: case TIMES: case DIVIDE:
			if (leftType != TypeKind.INT) {
				customTypeError(expr.posn.toString(), expr.toString(), "bad operand type " + operator + " for types " + leftType + ".", expr);
			} else {
				expr.setType(TypeKind.INT);
			}
			break;
		
		// Only type int can be operated by '<', '<=', '>', and '>='.
		case LTHAN: case LTEQUALTO: case GTHAN: case GTEQUALTO:
			if (leftType != TypeKind.INT) {
				customTypeError(expr.posn.toString(), expr.toString(), "bad operand type " + operator + " for types " + leftType + ".", expr);
			} else {
				expr.setType(TypeKind.BOOLEAN);
			}
			break;
			
		// Remaining operators '==' and '!=' can be operated on either int, boolean, null/class and will return type boolean.
		default:
			expr.setType(TypeKind.BOOLEAN);
		}
		return expr.getType();
	}

	@Override
	public Object visitRefExpr(RefExpr expr, Object arg) {
		return expr.ref.visit(this, arg);
	}

	@Override
	public Object visitIxExpr(IxExpr expr, Object arg) {
		TypeKind indexType   = (TypeKind) expr.ixExpr.visit(this, null);
		TypeKind refType     = (TypeKind) expr.ref.visit(this, null);
		TypeKind refEltType  = (TypeKind) expr.ref.visit(this, TypeKind.ARRAY);
		TypeKind declEltType = (TypeKind) expr.ref.visit(this, TypeKind.ARRAY);
		
		// Index must be type int.
		if (indexType != TypeKind.INT) {
			typeError(expr.posn.toString(), expr.toString(), TypeKind.INT, indexType, null);
			
		// Reference must be an array.
		} else if (refType != TypeKind.ARRAY) {
			customTypeError(expr.posn.toString(), expr.toString(), "cannot access an index of type " + refType + ".", expr);
			
		// Element types must match.
		} else if (refEltType != declEltType) {
			typeError(expr.posn.toString(), expr.toString(), refEltType, declEltType, expr);
		}
		
		expr.setType(declEltType);
		return expr.getType();
	}

	@Override
	public Object visitCallExpr(CallExpr expr, Object arg) {
		Declaration refDecl = expr.functionRef.getDecl();

		// Validate call expressions for methods.
		if (refDecl instanceof MethodDecl) {
			TypeKind functionType = (TypeKind) expr.functionRef.visit(this, null);
			ParameterDeclList pList = ((MethodDecl) refDecl).parameterDeclList;
			ExprList argList = expr.argList;

			// Type checking the arguments with the parameters.
			try {
				for (int i = 0; i < argList.size() || i < pList.size(); i++) {
					TypeKind argType   = (TypeKind) argList.get(i).visit(this, null);
					TypeKind paramType = (TypeKind) pList.get(i).type.typeKind;
					if (argType != paramType) {
						typeError(expr.posn.toString(), expr.toString(), paramType, argType, expr);
					}
				}
				expr.setType(functionType);
				
			// Incorrect number of arguments.
			} catch (IndexOutOfBoundsException e) {
				customTypeError(expr.posn.toString(), expr.toString(), "expected " + pList.size() + " arguments, but got " + argList.size() + " instead.", expr);
			}

		// Invalid to use a call expression on any other declaration.
		} else {
			customTypeError(expr.posn.toString(), expr.toString(), "call expression cannot be made on declaration " + refDecl + ".", expr);
		}
		return expr.getType();
	}

	@Override
	public Object visitLiteralExpr(LiteralExpr expr, Object arg) {
		// Matching a TokenKind to an appropriate TypeKind.
		switch(expr.lit.kind) {
		case CLASS: case THIS:
			expr.setType(TypeKind.CLASS);
			break;
		case INT: case NUM:
			expr.setType(TypeKind.INT);
			break;
		case BOOLEAN: case TRUE: case FALSE:
			expr.setType(TypeKind.BOOLEAN);
			break;
		case VOID:
			expr.setType(TypeKind.VOID);
			break;
		default:
			expr.setType(TypeKind.UNSUPPORTED);
		}
		return expr.getType();
	}

	@Override
	public Object visitNewObjectExpr(NewObjectExpr expr, Object arg) {
		return expr.classtype.visit(this, arg);
	}

	@Override
	public Object visitNewArrayExpr(NewArrayExpr expr, Object arg) {
		// Type retrival.
		TypeKind eltType   = (TypeKind) expr.eltType.visit(this, TypeKind.ARRAY);
		TypeKind sizeType  = (TypeKind) expr.sizeExpr.visit(this, null);
		
		// Setting expression type.
		expr.sizeExpr.setType(sizeType);
		
		// Size must be of type int.
		if (sizeType != TypeKind.INT) {
			typeError(expr.posn.toString(), expr.toString(), TypeKind.INT, sizeType, expr);
		} else {
			expr.setType(TypeKind.ARRAY);
		}
		
		// Returning the element type if specified, otherwise return TypeKind.ARRAY.
		if (arg == TypeKind.ARRAY) {
			return eltType;
		}
		return expr.getType();
	}

	@Override
	public Object visitThisRef(ThisRef ref, Object arg) {
		// Return the class' name.
		if (arg == TypeKind.CLASS) { 
			return currentClass.name;
		}
		return TypeKind.CLASS;
	}

	@Override
	public Object visitIdRef(IdRef ref, Object arg) {
		return ref.id.visit(this, arg);
	}

	@Override
	public Object visitQRef(QualRef ref, Object arg) {
		// Check if ref is properly formatted.
		Declaration refDecl = ref.getDecl();
		if (refDecl == null) {
			return TypeKind.ERROR;
		}
		
		// Returns a class' name.
		if (arg == TypeKind.CLASS) { 
			if (refDecl instanceof FieldDecl)
				return ((ClassType) ((FieldDecl) refDecl).type).className.spelling;
			if (refDecl instanceof MethodDecl) 
				return ((ClassType) ((MethodDecl) refDecl).type).className.spelling;
		} else if (arg == TypeKind.ARRAY) {
			if (refDecl instanceof FieldDecl) {
				return ((ArrayType) ((FieldDecl) refDecl).type).eltType.typeKind;
			} else if (refDecl instanceof VarDecl) {
				// Uhhh...
			}
		}
		
		// Return type.
		return refDecl.type.typeKind;
	}

	@Override
	public Object visitIdentifier(Identifier id, Object arg) {
		TypeDenoter type = id.getDecl().type;
		Declaration decl = id.getDecl();
		
		// Return an array's element type.
		if (arg == TypeKind.ARRAY) {
			if (type instanceof BaseType) {
				return ((BaseType) type).typeKind;
			} else if (type instanceof ClassType) {
				return ((ClassType) type).typeKind;
			}
			TypeKind arrType = ((ArrayType) type).eltType.typeKind;
			if (arrType == TypeKind.CLASS && ((ClassType) ((ArrayType) type).eltType).className.spelling.equals("String")) {
				customTypeError(type.posn.toString(), "visitIdentifier", "Unsupported type [String] given.", null);
				return TypeKind.UNSUPPORTED;
				
			}
			return arrType;
			
		// Return a class' name.
		} else if (arg == TypeKind.CLASS) {
			String className = "";
			if (decl instanceof ClassDecl) {
				className = ((ClassDecl) decl).name;
			} else if (decl instanceof VarDecl || decl instanceof FieldDecl) {
				if (type instanceof ArrayType) {
					className = ((ClassType) (((ArrayType) type).eltType)).className.spelling;
				} else {
					className = ((ClassType) decl.type).className.spelling;
				}
			} else if (decl instanceof ParameterDecl) {
				className = ((ClassType) ((ParameterDecl) decl).type).className.spelling;
			}
			if (className.equals("String")) {
				customTypeError(type.posn.toString(), "visitIdentifier", "Unsupported type [String] given.", null);
				return TypeKind.UNSUPPORTED;
			}
			return className;
			
		// Return a generic type.
		} else {
			return type.typeKind;
		}
	}

	@Override
	public Object visitOperator(Operator op, Object arg) {
		// Returns operator type.
		return op.kind;
	}

	@Override
	public Object visitIntLiteral(IntLiteral num, Object arg) {
		// Returns number type.
		return num.kind;
	}

	@Override
	public Object visitBooleanLiteral(BooleanLiteral bool, Object arg) {
		// Returns boolean type.
		return bool.kind;
	}
	
	// Reports a standard-format type error.
	private void typeError(String lineNumber, String typeLocation, TypeKind type1, TypeKind type2, Expression e) throws TypeError {
		if (e != null) 
			e.setType(TypeKind.ERROR);
		reporter.reportError("*** line " + lineNumber + ": Type error in " + typeLocation + ": expected type " + type1 + ", but got type " + type2 + " instead.");
	}
	
	// Reports a custom-form type error.
	private void customTypeError(String lineNumber, String typeLocation, String s, Expression e) throws TypeError {
		if (e != null) 
			e.setType(TypeKind.ERROR);
		reporter.reportError("*** line " + lineNumber + ": Type error in " + typeLocation + ": " + s);
	}
}