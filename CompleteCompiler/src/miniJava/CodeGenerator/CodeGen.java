package miniJava.CodeGenerator;

import mJAM.Machine;
import mJAM.Machine.Op;
import mJAM.Machine.Prim;
import mJAM.Machine.Reg;
import miniJava.AbstractSyntaxTrees.*;
import miniJava.AbstractSyntaxTrees.Package;
import miniJava.SyntacticAnalyzer.TokenKind;

import java.util.HashMap;

public class CodeGen implements Visitor<Object, Object> {

	// [HashMap] containing all entities that need to be patched.
	HashMap<Declaration, RuntimeEntity> unknownEntities = new HashMap<Declaration, RuntimeEntity>();
	
	// Methods and field numbers.
	MethodDecl currentMethod;
	public int fieldNum = 0;
	
	// Main method and its addresses.
	MethodDecl mainMethod;
	int patchAddr_Call_main;
	int codeAddr_main = -1;

	// LB pointer.
	int LB = 0;

	// OB pointer.
	int OB = 0;

	// CB pointer.
	int CB = 0;
	
	// SB pointer.
	int SB = 0;

	// 'Hack' used during variable manipulation.
	boolean declaring;
	boolean assigning;
	
	// Constructor.
	public CodeGen(AST ast) {
		ast.visit(this, null);
	}

	@Override
	public Object visitPackage(Package prog, Object arg) {
		for (ClassDecl cd : prog.classDeclList) {
			// Using [fieldNum] to keep track of how many fields have been seen.
			fieldNum = 0;

			// Pre-define the fields.
			for (FieldDecl fd : cd.fieldDeclList) {
				fd.visit(this, null);
			}
		}

		// Start the machine.
		Machine.initCodeGen();

		// Preamble: generate call to main.
		Machine.emit(Op.LOADL, 0); // array length 0
		Machine.emit(Prim.newarr); // empty String array argument
		patchAddr_Call_main = Machine.nextInstrAddr(); // record instr addr where main is called
		Machine.emit(Op.CALL, Reg.CB, -1); // static call main (address to be patched)
		Machine.emit(Op.HALT, 0, 0, 0);
		
		// Visiting all classes, saving the one with the main method last.
		for (ClassDecl c : prog.classDeclList) {
			c.visit(this, null);
		}
		
		// Visit the main method last after everything else has been defined.
		// Guaranteed to exist null due to the identification stage.
		currentMethod = mainMethod;
		mainMethod.visit(this, null);

		// Postamble: patch jumps and calls to unknown code addresses.
		// supply correct address of "main" to generated call in preamble.
		Machine.patch(patchAddr_Call_main, codeAddr_main);

		return null;
	}

	@Override
	public Object visitClassDecl(ClassDecl cd, Object arg) {
		// Visiting all methods. Fields have already been seen earlier.
		for (MethodDecl md : cd.methodDeclList) {
			// Find the main method and set its address.
			if (mainMethod == null && verifyMainMethod(md)) {
				mainMethod = md;
			} else {
				currentMethod = md;
				md.visit(this, null);
			}
		}
		return null;
	}

	@Override
	public Object visitFieldDecl(FieldDecl fd, Object arg) {
		// Set the field's known address.
		if (fd.isStatic) {
			fd.entity = new RuntimeEntity(SB++);
		} else {
			fd.entity = new RuntimeEntity(fieldNum++);
		}
		fd.type.visit(this, null);
		return null;
	}

	@Override
	public Object visitMethodDecl(MethodDecl md, Object arg) {
		// Set the method's known address.
		// If the method is the main method, set [codeAddr_main].
		if (md.equals(mainMethod)) {
			codeAddr_main = Machine.nextInstrAddr();
			md.entity = new RuntimeEntity(codeAddr_main);
		} else {
			// Patching an old method and removing it from [unknownEntities].
			if (unknownEntities.get(md) != null) {
				Machine.patch(md.entity.address, Machine.nextInstrAddr());
				unknownEntities.remove(md);
			}
			md.entity = new RuntimeEntity(Machine.nextInstrAddr());
		}

		// Set the method's LB to 3 due to linkage.
		setLB(3);
		
		// Finding the negatively displaced address.
		int address = (md.parameterDeclList.size()) * -1;
		for (ParameterDecl pd : md.parameterDeclList) {
			// Assign [pd]'s entity here.
			pd.entity = new RuntimeEntity(address++);
			pd.visit(this, null);
		}

		// Visiting the statements.
		for (Statement st : md.statementList) {
			st.visit(this, null);
		}

		// Synthesizing a VOID return statement for a VOID return type method and visiting it
		// to pop possible parameters off the stack.
		if (md.type.typeKind == TypeKind.VOID) {
			ReturnStmt voidReturnStmt = new ReturnStmt(null, null);
			voidReturnStmt.visit(this, null);
		}
		return null;
	}

	@Override
	public Object visitParameterDecl(ParameterDecl pd, Object arg) {	
		// Nothing to do here - [pd]'s entity is already set beforehand,
		// and visiting types is for the type checking stage.	
		return null;
	}

	@Override
	public Object visitVarDecl(VarDecl decl, Object arg) {
		// Assign a [RuntimeEntity] entity to [decl] if it has not already been declared.
		if (decl.entity == null) {
			decl.entity = new RuntimeEntity(LB);
		}
		return decl.entity;
	}

	@Override
	public Object visitBaseType(BaseType type, Object arg) {
		return type.typeKind;
	}

	@Override
	public Object visitClassType(ClassType type, Object arg) {
		return type.className.visit(this, null);
	}

	@Override
	public Object visitArrayType(ArrayType type, Object arg) {
		// Nothing to do here - visiting types is for the type checking stage.
		return null;
	}

	@Override
	public Object visitBlockStmt(BlockStmt stmt, Object arg) {
		// Remember the number of variables defined before entering the block statement.
		int preBlockSize = LB;
		
		// [additionalStorage] tracks variables that may have been pushed during the block stmt.
		int additionalStorage = 0;
		for (Statement s : stmt.sl) {
			// Check if a method was called and a return value was pushed.
			if (s instanceof CallStmt && ((MethodDecl) ((CallStmt) s).methodRef.getDecl()).type.typeKind != TypeKind.VOID) {
				additionalStorage++;
			}
			s.visit(this, null);
		}
		// Pop the difference and adjust [LB] accordingly.
		int difference = LB - preBlockSize;
		Machine.emit(Op.POP, difference + additionalStorage);
		LB -= difference;
		return null;
	}

	@Override
	public Object visitVardeclStmt(VarDeclStmt stmt, Object arg) {
		// Evaluating the expression.
		// Load the value at the [RuntimeEntity]'s address if it's not null.
		// If null, then the value must be some literal expression.
		// RuntimeEntity initExprRET = (RuntimeEntity) stmt.initExp.visit(this, null);
		// if (!(stmt.initExp instanceof NewObjectExpr)) {
		// 	if (initExprRET != null) {
		// 		Machine.emit(Op.LOAD, Reg.LB, initExprRET.address);
		// 	}
		// }

		stmt.initExp.visit(this, null);

		// Visiting [stmt.varDecl] to initialize its entity.
		declaring = true; 
		stmt.varDecl.visit(this, null);
		declaring = false; 

		// LB should be incremented by one to prepare for the next [VarDecl].
		LB++;

		return null;
	}

	@Override
	public Object visitAssignStmt(AssignStmt stmt, Object arg) {
		// Visiting the reference.
		assigning = true;
		RuntimeEntity stmtRefRET = (RuntimeEntity) stmt.ref.visit(this, null);
		assigning = false;
		
		// Visiting the value.
		stmt.val.visit(this, null);

		// Storing based on [Declaration] type.
		// Storing and updating a field.
		if (stmt.ref.getDecl() instanceof FieldDecl) {
			if (stmt.ref instanceof QualRef) {
				Machine.emit(Prim.fieldupd);
			} else {
				if (((FieldDecl) stmt.ref.getDecl()).isStatic) {
					Machine.emit(Op.STORE, Reg.SB, stmtRefRET.address);
				} else {
					Machine.emit(Op.STORE, Reg.OB, stmtRefRET.address);
				}
			}
		
		// Storing a local variable.
		} else if (stmt.ref.getDecl() instanceof VarDecl) {
			Machine.emit(Op.STORE, Reg.LB, stmtRefRET.address);
		
		// Storing on the stack.
		} else {
			Machine.emit(Op.STORE, Reg.SB, stmtRefRET.address);
		}

		return null;
	}

	@Override
	public Object visitIxAssignStmt(IxAssignStmt stmt, Object arg) {
		// Pushing the address of the array.
		stmt.ref.visit(this, null);

		// Pushing the the index value. Known to be an int due to the type checking stage.
		stmt.ix.visit(this, null);

		// Evaluating the expression and pushing a value.
		stmt.exp.visit(this, null);

		// Updating the array.
		Machine.emit(Prim.arrayupd);

		return null;
	}

	@Override
	public Object visitCallStmt(CallStmt stmt, Object arg) {
		// Place arguments on the stack.
		for (Expression e : stmt.argList) {
			e.visit(this, null);
		}

		// Check if it's a System.out.println().
		// !!! Fix this later for the fake one.
		if (isSystemCall(stmt)) {
			Machine.emit(Prim.putintnl);
//			Machine.emit(Op.RETURN, 0, 0, 1);
			return null;
		}
		
		// Pushing the address of the object instance up.
		if (!(stmt.methodRef instanceof QualRef)) {
			Machine.emit(Op.LOADA, Reg.OB, 0);
		}
		
		// Finds the address of the method being called.
		RuntimeEntity methodAddr = (RuntimeEntity) stmt.methodRef.visit(this, null);

		if (((MethodDecl) stmt.methodRef.getDecl()).isStatic) {
			// Use CALL here.
			// Commented statement below. Not sure where I read that -1 had to be pushed first.
			// Machine.emit(Op.LOADL, -1);
			System.out.println("Wow our first static method.");
			Machine.emit(Op.CALL, Reg.CB, methodAddr.address);

		} else {
			// Call the method.
			Machine.emit(Op.CALLI, Reg.CB, methodAddr.address);
		}
		
		return null;
	}

	@Override
	public Object visitReturnStmt(ReturnStmt stmt, Object arg) {
		// Determine the number of callers to pop off the stack.
		int numOfCallers = currentMethod.parameterDeclList.size();

		// Return with one value to return at the caller stack top.
		if (stmt.returnExpr != null) {
			stmt.returnExpr.visit(this, null);
			Machine.emit(Op.RETURN, 1, 0, numOfCallers);

		// Return with no value to return at the caller stack top.
		} else {
			Machine.emit(Op.RETURN, 0, 0, numOfCallers);
		}
		return null;
	}

	@Override
	public Object visitIfStmt(IfStmt stmt, Object arg) {
		// Visiting the conditional statement.
		stmt.cond.visit(this, null);

		// Setting up the first backpatching label.
		int cond = Machine.nextInstrAddr();
		Machine.emit(Op.JUMPIF, 0, Reg.CB, 0);
		
		// Visit and check for special cases.
		if (stmt.thenStmt != null) {
			stmt.thenStmt.visit(this, null);
		}

		// Setting up the second backpatching label.
		int jump = Machine.nextInstrAddr();
		Machine.emit(Op.JUMP, 0);
		Machine.patch(cond, Machine.nextInstrAddr());

		if (stmt.elseStmt != null) {
			stmt.elseStmt.visit(this, null);
		}

		// Setting up the third backpatching label.
		Machine.patch(jump, Machine.nextInstrAddr());
		return null;
	}

	@Override
	public Object visitWhileStmt(WhileStmt stmt, Object arg) {
		// Setting the first backpatching label.
		int cond = Machine.nextInstrAddr();
		stmt.cond.visit(this, null);

		// Setting the second backpatching label.
		int start = Machine.nextInstrAddr();
		Machine.emit(Op.JUMPIF, Reg.CB, 0);
		stmt.body.visit(this, null);

		// Emitting the jump command and patching labels.
		Machine.emit(Op.JUMP, Reg.CB, cond);
		Machine.patch(start, Machine.nextInstrAddr());
		return null;
	}

	@Override
	public Object visitUnaryExpr(UnaryExpr expr, Object arg) {
		// Place the operation on the stack and apply the operation on it.
		expr.expr.visit(this, null);

		// Determining the operator to use.
		// Could call `expr.operator.visit(this, null)` here as well.
		switch(expr.operator.kind) {
		case MINUS:
			Machine.emit(Prim.neg);
			break;
		case NOT:
			Machine.emit(Prim.not);
			break;
		default:
			break;
		}
		return null;
	}

	@Override
	public Object visitBinaryExpr(BinaryExpr expr, Object arg) {
		// Visiting the left expression.
		expr.left.visit(this, null);
		
		// Can cut the program early if it's an [and] statement and the left is already known to be [false].
		if (expr.operator.kind == TokenKind.AND && Machine.code[Machine.CT - 1].d == Machine.falseRep) {
			return null;
			
		// Can cut the program early if it's an [or] statement and the left is already known to be [true].
		} else if (expr.operator.kind == TokenKind.OR & Machine.code[Machine.CT - 1].d == Machine.trueRep) {
			return null;
		}
		
		// Visiting the right.
		expr.right.visit(this, null);

		// Perform the operation on the two values on the top of the stack.
		expr.operator.visit(this, null);
		return null;
	}

	@Override
	public Object visitRefExpr(RefExpr expr, Object arg) {
		// Straight-forward visit call.
		return expr.ref.visit(this, null);
	}

	@Override
	public Object visitIxExpr(IxExpr expr, Object arg) {
		// Pushing the array address.
		expr.ref.visit(this, null);
		
		// Pushing the array index.
		expr.ixExpr.visit(this, null);

		// Retrieving the value.
		Machine.emit(Prim.arrayref);

		return null;
	}

	@Override
	public Object visitCallExpr(CallExpr expr, Object arg) {
		// RuntimeEntity functionRET = (RuntimeEntity) expr.functionRef.visit(this, null);
		for (Expression e : expr.argList) {
			e.visit(this, null);
		}
		
		// Pushing the address of the object instance up.
		if (!(expr.functionRef instanceof QualRef)) {
			Machine.emit(Op.LOADA, Reg.OB, 0);
		}

		// Retrieving the [RuntimeEntity].
		RuntimeEntity functionRED = (RuntimeEntity) expr.functionRef.visit(this, null);

		// Calling depending on whether it's a static or non-static method.
		if (((MethodDecl) expr.functionRef.getDecl()).isStatic) {
			// Using CALL here.
		} else {
			// Using CALLI here.
			Machine.emit(Op.CALLI, Reg.CB, functionRED.address);
		}
		return null;
	}

	@Override
	public Object visitLiteralExpr(LiteralExpr expr, Object arg) {
		// Simple visit.
		expr.lit.visit(this, null);
		return null;
	}

	@Override
	public Object visitNewObjectExpr(NewObjectExpr expr, Object arg) {
		// Loading the address of the new object.
		Machine.emit(Op.LOADL, -1);

		// Calculating the # of fields in the class to allocate on the stack.
		int numOfFields = ((ClassDecl) expr.classtype.className.getDecl()).fieldDeclList.size();
		// for (FieldDecl fd : ((ClassDecl) expr.classtype.className.getDecl()).fieldDeclList) {
		// 	// If the field is a class, then its fields needs to be added as well.
		// 	if (fd.type.typeKind == TypeKind.CLASS) {
		// 		numOfFields += ((ClassDecl) ((ClassType) fd.type).className.getDecl()).fieldDeclList.size();
		// 	} 
		// 	numOfFields++;
		// }
		Machine.emit(Op.LOADL, numOfFields);

		// Creating the new instance.
		Machine.emit(Prim.newobj);

		return null;
	}

	@Override
	public Object visitNewArrayExpr(NewArrayExpr expr, Object arg) {
		// The expression is KNOWN to be an integer due to identification.
		// Visiting [sizeExpr] will push the size onto the stack.
		expr.sizeExpr.visit(this, null);
		
		// Create the new array.
		Machine.emit(Prim.newarr);
		return null;
	}

	@Override
	public Object visitThisRef(ThisRef ref, Object arg) {
		// [This] is simply stores in 0[OB].
		Machine.emit(Op.LOADA, Reg.OB, 0);
		return null;
	}

	@Override
	public Object visitIdRef(IdRef ref, Object arg) {
		return ref.id.visit(this, null);
	}

	@Override
	public Object visitQRef(QualRef ref, Object arg) {
		// Placing the address of the variable on the stack depending on its declaration.
		// Need to turn switch the [assigning] gate first.
		boolean assigningOGVal = assigning;
		assigning = false;
		ref.ref.visit(this, "DEPTH");
		assigning = assigningOGVal;

		// Determining if it's trying to access a [FieldDecl].
		if (ref.id.getDecl() instanceof FieldDecl) {
			// Determining if [length] is being accessed. Specifically, we know that it's the Array.length
			// if its entity is null because that is never assigned.
			if (ref.id.spelling.equals("length") && ref.id.getDecl().entity == null) {
				Machine.emit(Prim.arraylen);
			} else {
				// Placing the field index on the stack.
				Machine.emit(Op.LOADL, ref.id.getDecl().entity.address);

				// Retrieving a field's value.
				// The conditionals are explained in the following two ways:
				// - arg != null : an argument is given when the [QRef] is currently being traveresed and lays in the middle i.e) f.n.p; [at n].
				// - !assigning : a field's value should not be retrieved if it is being assigned i.e.) f.n = 5;
				if (arg != null || !assigning) {
					Machine.emit(Prim.fieldref);
				}
			}

		// Determining if it's trying to access a [MethodDecl].
		} else if (ref.id.getDecl() instanceof MethodDecl) {
			// Return the [RuntimeEntity] associated with it and delegate the calling task to [CallStmt].
			return ref.id.visit(this, null);
		} else {
			System.out.println("QRef trying to access something else? Not sure what else it could be.");
		}

		return ref.id.entity;
	}

	@Override
	public Object visitIdentifier(Identifier id, Object arg) {
		// Retrieving the id's declaration type.
		Declaration idDecl = id.getDecl();

		// If the id is in the list of those that need to be patched, patch it.
		if (unknownEntities.get(idDecl) != null) {
			// Retrieving the [RuntimeEntity] that needs to be patched.
			RuntimeEntity re = unknownEntities.get(idDecl);

			// Patching the address in the machine.
			Machine.patch(re.address, Machine.nextInstrAddr());

			// Patching the [RuntimeEntity]'s address to match.
			re.patchAddr(Machine.nextInstrAddr());

			// Setting the newly patched [RuntimeEntity] and removing it from the hashmap.
			idDecl.entity = unknownEntities.remove(idDecl);
		}

		// If the entity is null, set it [RuntimeEntity] with a known address and 
		// store it in a hashmap to patch later.
		else if (idDecl.entity == null) {
			// Set [idDecl]'s [RuntimeEntity] and place it in the hashmap for later patching. 
			RuntimeEntity unknownRE = new RuntimeEntity(Machine.nextInstrAddr());
			idDecl.entity = unknownRE;
			unknownEntities.put(idDecl, unknownRE);
		}

		// !!! This can, and should be moved to the [FieldDecl] case.
		// Checking possible special case of length.
		if (id.spelling.equals("length")) {
			Machine.emit(Prim.arraylen);
		}

		if (!declaring && !assigning) {
			// Address of the variable.
			int idAddr = idDecl.entity.address;

			// [FieldDecl]: Load the field's address and emit primitive.
			if (idDecl instanceof FieldDecl) {
				if (((FieldDecl) idDecl).isStatic) {
					Machine.emit(Op.LOAD, Reg.SB, idAddr);
				} else {
					Machine.emit(Op.LOAD, Reg.OB, idAddr);
				}

			// [MethodDecl]: Load the method's address.
			} else if (idDecl instanceof MethodDecl) { 
				// !!! Pushing the address of the method up. Uhh, I don't know honestly.
				// Machine.emit(Op.LOAD, Reg.CB, idAddr);

			// [VarDecl]: Load the variable's address.
			} else if (idDecl instanceof VarDecl) {
				Machine.emit(Op.LOAD, Reg.LB, idAddr);

			// [ClassDecl]: Load the class's address.
			} else if (idDecl instanceof ClassDecl) {
				Machine.emit(Op.LOAD, Reg.LB, idAddr);
			} else if (idDecl instanceof ParameterDecl) {
				Machine.emit(Op.LOAD, Reg.LB, idAddr);
			}
		}

		return idDecl.entity;
	}

	@Override
	public Object visitOperator(Operator op, Object arg) {
		// Emit the proper primit according to [op].
		switch(op.kind) {
		case OR:
			Machine.emit(Prim.or);
			break;
		case AND:
			Machine.emit(Prim.and);
			break;
		case NOT:
			Machine.emit(Prim.not);
			break;
		case EQUALTO:
			Machine.emit(Prim.eq);
			break;
		case NEQUALTO:
			Machine.emit(Prim.ne);
			break;
		case LTEQUALTO:
			Machine.emit(Prim.le);
			break;
		case GTEQUALTO:
			Machine.emit(Prim.ge);
			break;
		case GTHAN:
			Machine.emit(Prim.gt);
			break;
		case LTHAN:
			Machine.emit(Prim.lt);
			break;
		case PLUS:
			Machine.emit(Prim.add);
			break;
		case MINUS:
			Machine.emit(Prim.sub);
			break;
		case TIMES:
			Machine.emit(Prim.mult);
			break;
		case DIVIDE:
			Machine.emit(Prim.div);
			break;
		default:
			break;
		}
		return null;
	}

	@Override
	public Object visitIntLiteral(IntLiteral num, Object arg) {
		// Loads an integer literal.
		Machine.emit(Op.LOADL, Integer.parseInt(num.spelling));
		return null;
	}

	@Override
	public Object visitBooleanLiteral(BooleanLiteral bool, Object arg) {
		// Loads a boolean literal.
		int boolInt = bool.spelling.equals("true") ? Machine.trueRep : Machine.falseRep;
		Machine.emit(Op.LOADL, boolInt);
		return null;
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

	public boolean isSystemCall(CallStmt stmt) {
		try {
			return (stmt.methodRef instanceof QualRef
				&& ((QualRef) stmt.methodRef).id.spelling.equals("println")
				&& (((QualRef) ((QualRef) stmt.methodRef).ref).id.spelling.equals("out"))
				&& ((IdRef) (((QualRef) ((QualRef) stmt.methodRef).ref).ref)).id.spelling.equals("System"));
		} catch (ClassCastException e) {
			return false;
		}
	}

	// Sets to LB to the given integer.
	public void setLB(int newLB) {
		LB = newLB;
	}

}