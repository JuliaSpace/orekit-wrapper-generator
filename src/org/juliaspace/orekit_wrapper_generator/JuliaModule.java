package org.juliaspace.orekit_wrapper_generator;

import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

public class JuliaModule {
    public Path path;
    public Set<Path> submodules = new TreeSet<>();
    public Set<JuliaClass> classes = new TreeSet<>();

    public JuliaModule(Path path) {
        this.path = path;
    }
}
