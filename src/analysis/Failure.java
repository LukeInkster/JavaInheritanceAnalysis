package analysis;

import antlr.JavaParser.ExpressionContext;

public class Failure {
	public final ExpressionContext expr;
	public final FailureType failureType;
	
	public Failure(ExpressionContext expr, FailureType failureType){
		this.expr = expr;
		this.failureType = failureType;
	}

	public String text() {
		return expr.getText();
	}
}
