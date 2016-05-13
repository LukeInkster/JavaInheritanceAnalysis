package analysis;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FailureSet {
	public final String filename;
	public final List<Failure> failures;
	
	public FailureSet(Path file, Stream<Failure> failures) {
		this.filename = file.toString();
		this.failures = failures.collect(Collectors.toList());
	}
	
	public FailureSet(Path file) {
		this.filename = file.toString();
		this.failures = new ArrayList<Failure>();
	}
	
	public void add(Failure failure){
		this.failures.add(failure);
	}
	
	public void add(Stream<Failure> failures){
		this.failures.addAll(failures.collect(Collectors.toList()));
	}
	
	public String toString(){
		StringBuilder sb = new StringBuilder(filename);
		this.failures.forEach(f -> sb.append("\n  " + f.text() + "\t\t" + f.failureType));
		return sb.toString();
	}
}
