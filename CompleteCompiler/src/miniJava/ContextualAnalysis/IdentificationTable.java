package miniJava.ContextualAnalysis;

import java.util.HashMap;
import java.util.ListIterator;
import java.util.Stack;

import miniJava.ErrorReporter;
import miniJava.AbstractSyntaxTrees.Declaration;
import miniJava.SyntacticAnalyzer.SourcePosition;

public class IdentificationTable {

	// [IdentificationTable]'s design is a scoped, identification table.
	/**
	 *     Scope 1				  Scope 2				  Scope 3
	 *  _______________        ________________        ______________
	 * |__classname1___|      |_method1/field1_|      |____param1____|
	 * |__classname2___|      |_method2/field2_|      |____param2____|
	 * |__classname3___| -->  |_method3/field3_|  --> |____param3____|
	 * |__classname4___|      |_method4/field4_|      |____param4____|
	 * |__..........___|      |_.............._|      |____......____|
	 * 
	 */

	Stack<HashMap<String, Declaration>> table;
	ErrorReporter reporter;
	int level;
	
	// Constructor.
	public IdentificationTable(ErrorReporter reporter) {
		this.reporter = reporter;
		table = new Stack<HashMap<String, Declaration>>();
		level = 1;
	}
	
	// Enters a [Declaration] into the table.
	public void enter(Declaration decl) {
		// Checking if the entry is available and it does not hide declarations at levels 3 or higher.
		if (table.peek().get(decl.name) != null) {
			identificationError(decl.posn.toString(), "Identifier " + decl.name + " has already been declared.");
		} else if (retrieveTo(3, decl.name) != null) {
			identificationError(decl.posn.toString(), "Identifier " + decl.name + " cannot hide declaration from level 3+.");
		} else {
			table.peek().put(decl.name, decl);
		}
	}
	
	// Replaces a [Declaration] of the table.
	public void replace(Declaration decl) {
		table.peek().replace(decl.name, decl);
	}
	
	// Retrieves a [Declaration] from [table] from any level.
	public Declaration retrieve(String id, SourcePosition posn) {
		Declaration retrievedDecl = retrieveTo(0, id);
		
		// Checking if it has yet to be declared.
		if (retrievedDecl == null) {
			identificationError(posn.toString(), "Reference used before declaration.");
		}
		return retrievedDecl;
	}
	
	// Retrieves a [Declaration] from [table] till level 2. Throws no error if it's unable to find a declaration.
	public Declaration noErrorRetrieve(String id, SourcePosition posn) {
		Declaration retrievedDecl = retrieveTo(3, id);
		return retrievedDecl;
	}
	
	// Retrieves a [Declaration] from the top to the given [min] level, exclusive.
	public Declaration retrieveTo(int min, String id) {
		ListIterator<HashMap<String, Declaration>> iterator = table.listIterator(table.size());
		int currentLevel = level;
		while (iterator.hasPrevious() && currentLevel > min) {
			Declaration d = iterator.previous().get(id);
			if (d != null) {
				return d;
			}
			currentLevel--;
		}
		return null;
	}
	
	// Retrieves a [Declaration] from a certain scope level, [givenLevel].
	public Declaration retrieveFrom(int givenLevel, String id, boolean noError, SourcePosition posn) {
		ListIterator<HashMap<String, Declaration>> iterator = table.listIterator(table.size());
		int currentLevel = level;
		while (iterator.hasPrevious() && currentLevel != givenLevel + 1) {
			iterator.previous();
			currentLevel--;
		}
		
		Declaration returnedDecl = iterator.previous().get(id);
		if (returnedDecl == null && !noError) {
			identificationError(posn.toString(), "Reference used before declaration.");
		}
		return returnedDecl;
	}
	
	// Pushes a new [HashMap] onto the stack and increases the level by one.
	public void openScope() {
		table.push(new HashMap<String, Declaration>());
		level++;
	}
	
	// Pops a new [HashMap] off the stack and decreases the level by one.
	public void closeScope() {
		table.pop();
		level--;
	}
	
	// Reports an identification error.
	private void identificationError(String lineNumber, String e) throws IdentificationError {
		reporter.reportError("*** line " + lineNumber + ": " + e);
		System.exit(4);
	}
	
	// Returns the current scope level.
	public int getLevel() {
		return level;
	}
}
