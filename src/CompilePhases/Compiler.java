package miniJava;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import miniJava.ContextualAnalysis.Identification;
import miniJava.ContextualAnalysis.TypeChecking;
import miniJava.AbstractSyntaxTrees.AST;
import miniJava.AbstractSyntaxTrees.ASTDisplay;
import miniJava.CodeGenerator.CodeGen;
import miniJava.SyntacticAnalyzer.Parser;
import miniJava.SyntacticAnalyzer.Scanner;
import mJAM.*;

public class Compiler {
    
    public static void main(String[] args) {
        // Initializing the input stream to a file.
        InputStream inputStream = null;
        try {
			// inputStream = new FileInputStream(args[0]);
			inputStream = new FileInputStream("C:\\Users\\jkchang0\\Desktop\\Sample.txt");
        } catch(FileNotFoundException e) {
            System.out.println("Input file " + args[0] + " not found");
            System.exit(1);
        }

        // Initializing auxillary components.
		ErrorReporter errorReporter = new ErrorReporter();
		Scanner scanner = new Scanner(inputStream, errorReporter);
		Parser parser = new Parser(scanner, errorReporter);
		
        // PA1: Parsing.
		AST ast = parser.parse();
        checkErrors(errorReporter, "Syntactic analysis");

		// PA2: AST Construction.
		ASTDisplay td = new ASTDisplay();
//		td.showTree(ast);
		
		// PA3: Identification.
		Identification identification = new Identification(ast, errorReporter);
        checkErrors(errorReporter, "Identification");
        
		// PA3: Type Checking.
		TypeChecking typeChecking = new TypeChecking(ast, errorReporter);
        checkErrors(errorReporter, "Type checking");
        
        // PA4: Code Generation.
        CodeGen codeGeneration = new CodeGen(ast);
        
        String objectCodeFileName = "Sample.mJAM";
//        String objectCodeFileName = "Sample.mJAM";
        
		ObjectFile objF = new ObjectFile(objectCodeFileName);
		System.out.print("Writing object code file " + objectCodeFileName + " ... ");
		if (objF.write()) {
			System.out.println("FAILED!");
			return;
		}
		else
			System.out.println("SUCCEEDED");	

		// Creating asm file corresponding to object code using disassembler 
       String asmCodeFileName = objectCodeFileName.replace(".mJAM",".asm");
       System.out.print("Writing assembly file " + asmCodeFileName + " ... ");
       Disassembler d = new Disassembler(objectCodeFileName);
       if (d.disassemble()) {
               System.out.println("FAILED!");
               return;
       } else {
               System.out.println("SUCCEEDED");
       }
       
       System.out.println("Running code in debugger ... ");
       Interpreter.debug(objectCodeFileName, asmCodeFileName);

       System.out.println("*** mJAM execution completed");
       
		System.exit(0);
    }

	// Examines the [ErrorReporter] and prints out a description regarding its success/failure.
	// Immediately exits if a failure is detected.
	public static void checkErrors(ErrorReporter er, String stage) {
		if (er.hasErrors()) {
			System.out.println(stage + " has FAILED.");
			System.exit(4);
		} 
		System.out.println(stage + " has PASSED.");
	}
}
