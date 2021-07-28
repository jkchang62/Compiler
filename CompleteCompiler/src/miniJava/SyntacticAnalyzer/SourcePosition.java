package miniJava.SyntacticAnalyzer;

public class SourcePosition {
	private int lineNumber;
	
	SourcePosition(int lineNumber) {
		this.lineNumber = lineNumber;
	}
	
	public String toString() {
		return String.valueOf(lineNumber);
	}
	
	public int getLineNumber() {
		return lineNumber;
	}
}
