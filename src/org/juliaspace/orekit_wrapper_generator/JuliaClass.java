package org.juliaspace.orekit_wrapper_generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class JuliaClass implements Comparable<JuliaClass> {

    private final String name;
    private final Set<JuliaMethod> methods = new TreeSet<>();
    private final Set<String> imports = new TreeSet<>();
    private final Set<String> exports = new TreeSet<>();

    public JuliaClass(Class<?> cls) {
        name = Namer.name(cls);
        methods.addAll(Arrays.stream(cls.getConstructors()).filter(JuliaMethod::isWrappable)
                .map(JuliaMethod::new)
                .collect(Collectors.toList()));
        methods.addAll(Arrays.stream(cls.getMethods()).filter(JuliaMethod::isWrappable)
                .map(JuliaMethod::new)
                .collect(Collectors.toList()));
        methods.stream()
                .map(JuliaMethod::getImports)
                .forEach(imports::addAll);
        methods.stream()
                .map(JuliaMethod::getName)
                .forEach(exports::add);
        exports.add(name);
    }

    public static boolean isWrappable(Class<?> cls) {
        return !(cls.isAnnotation() ||
                cls.isAnnotationPresent(Deprecated.class) ||
                Exception.class.isAssignableFrom(cls)
        );
    }

    public Set<String> getImports() {
        return imports;
    }

    public Set<String> getExports() {
        return exports;
    }

    public String write(Path path, Set<String> previouslyProcessed) throws IOException {
        String fileName = Namer.fileName(name);
        StringBuilder sb = new StringBuilder();
        Set<String> newlyProcessed = new HashSet<>();
        for (JuliaMethod method : methods) {
            String body = method.getBody();
            if (!previouslyProcessed.contains(body)) {
                sb.append(body);
                newlyProcessed.add(body);
            }
        }
        previouslyProcessed.addAll(newlyProcessed);
        Files.write(path.resolve(fileName), sb.toString().getBytes());
        return fileName;
    }

    @Override
    public String toString() {
        return "JuliaClass{" +
                "name='" + name + '\'' +
                '}';
    }

    @Override
    public int compareTo(JuliaClass cls) {
        return name.compareTo(cls.name);
    }
}
