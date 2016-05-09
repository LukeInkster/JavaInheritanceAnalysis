package analysis;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import antlr.JavaParser.ExpressionContext;

public class FailureSet {
	public final String filename;
	public final List<ExpressionContext> failures;
	
	public FailureSet(Path file, Stream<ExpressionContext> failures) {
		this.filename = file.toString();
		this.failures = failures.collect(Collectors.toList());
	}
	
	public String toString(){
		StringBuilder sb = new StringBuilder(filename);
		failures.forEach(f -> sb.append("\n  " + f.getText()));
		return sb.toString();
	}
}
