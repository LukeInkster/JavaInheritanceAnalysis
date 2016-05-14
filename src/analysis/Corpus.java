package analysis;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Corpus {
	private List<Project> projects;
	
	public Corpus(String location, int start, int end){
		System.out.println("ping");
		List<Path> paths = Arrays
			.stream(new File(location).listFiles())
			.filter(file -> file.isDirectory())
			.map(directory -> directory.toPath())
			.filter(path -> containsJavaFiles(path)).skip(700).limit(100).collect(Collectors.toList());
		System.out.println("pong");
		projects = paths.stream()
//			.parallel()
			.map(path -> Project.from(path))
			.filter(project -> project != null)
			.collect(Collectors.toList());
	}
	
	public int size(){
		return projects.size();
	}
	
	public long countExtends(){
		return projects
			.stream()
			.mapToLong(project -> project.extendsClassCount())
			.sum();
	}
	
	public long countExtended(){
		return projects
			.stream()
			.mapToLong(project -> project.extendedClassCount())
			.sum();
	}

	public int countFiles() {
		return projects
			.stream()
			.mapToInt(project -> project.units.size())
			.sum();
	}

	public int countClasses() {
		return projects
			.stream()
			.flatMap(project -> project.units.stream())
			.mapToInt(unit -> unit.classCount)
			.sum();
	}

	public long countClassesWithFailures() {
		return projects
			.stream()
			.mapToLong(project -> project.failures().filter(failure -> !failure.failures.isEmpty()).count())
			.sum();
	}
	
	public List<FailureSet> failures(){
		return projects
			.stream()
			.flatMap(project -> project.failures())
			.collect(Collectors.toList());
	}

	public long countClassesWithFailure(FailureType failureType) {
		return failures()
			.stream()
			.filter(failureSet -> failureSet
				.failures
				.stream()
				.anyMatch(x -> x.failureType == failureType))
			.count();
	}

	public long countExtendedClassesWithFailure(FailureType failureType) {
		return projects
			.stream()
			.mapToLong(project -> project.countExtendedClassesWithFailure(failureType))
			.sum();
	}
	
	private Stream<Unit> classesWithForwarding(){
		return projects.stream().flatMap(p -> p.units.stream()).filter(u -> u.hasForwarding);
	}
	
	public long countClassesWithForwarding(){
		return classesWithForwarding().count();
	}
	
	public long countClassesWithForwardingThatExtend(){
		return classesWithForwarding().filter(c -> c.superClassName != null).count();
	}
	
	public long countClassesWithDelegation(){
		return projects
			.stream()
			.flatMap(project -> project.units.stream())
			.filter(unit -> !unit.delegationStatements.isEmpty())
			.count();
	}
	
	private static boolean containsJavaFiles(Path path) {
		try {
			Stream<Path> files = Files.walk(path);
			long found = files
				.filter(f -> f.toString().endsWith(".java"))
				.count();
			files.close();
//			System.out.println(found);
			return found > 0;
		} catch (IOException e) {
			return false;
		}
	}
}
