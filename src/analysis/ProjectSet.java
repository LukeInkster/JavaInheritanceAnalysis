package analysis;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ProjectSet {
	private List<Project> projects;
	
	public ProjectSet(String location, int limit){
		projects = Arrays
			.stream(new File(location).listFiles())
			.filter(file -> file.isDirectory())
			.map(directory -> directory.toPath())
			.map(path -> Project.from(path))
			.filter(project -> project != null)
			.limit(limit)
			.collect(Collectors.toList());
	}
	
	public int size(){
		return projects.size();
	}
	
	public int countExtends(){
		return projects
			.stream()
			.mapToInt(project -> project.extendsClasses.size())
			.sum();
	}
	
	public int countExtended(){
		return projects
			.stream()
			.mapToInt(project -> project.extendedClasses.size())
			.sum();
	}

	public int countFiles() {
		return projects
			.stream()
			.mapToInt(project -> project.fileCount)
			.sum();
	}

	public int countClasses() {
		return projects
			.stream()
			.mapToInt(project -> project.classCount)
			.sum();
	}

	public int countFailures() {
		return projects
			.stream()
			.mapToInt(project -> project.failures.size())
			.sum();
	}
	
	public List<FailureSet> failures(){
		return projects
			.stream()
			.flatMap(project -> project.failures.stream())
			.collect(Collectors.toList());
	}
}
