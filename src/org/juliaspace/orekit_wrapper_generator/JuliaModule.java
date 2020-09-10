package org.juliaspace.orekit_wrapper_generator;

import com.google.common.base.Joiner;
import com.google.common.reflect.ClassPath;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class JuliaModule {
    private final Map<String, JuliaModule> submodules = new TreeMap<>();
    private final Set<JuliaClass> classes = new TreeSet<>();

    public String getName() {
        return name;
    }

    private final String name;

    public JuliaModule(ClassPath cp, String pkg, String customName) {
        String[] packages = pkg.split("\\.");
        name = customName != null ? customName : Namer.namePkg(packages[packages.length - 1]);
        for (ClassPath.ClassInfo info : cp.getTopLevelClassesRecursive(pkg)) {
            if (!JuliaClass.isWrappable(info.load())) continue;

            if (info.getPackageName().equals(pkg)) {
                classes.add(new JuliaClass(info.load()));
            } else {
                List<String> classPackages = Arrays.asList(info.getPackageName().split("\\."));
                String submodule = classPackages.get(packages.length);
                if (submodules.containsKey(submodule)) continue;
                if (submodule.equals("exception")) continue;
                Joiner joiner = Joiner.on(".");
                String newPkg = joiner.join(classPackages.subList(0, packages.length + 1));
                submodules.put(submodule, new JuliaModule(cp, newPkg));
            }
        }
    }
    public JuliaModule(ClassPath cp, String pkg) {
        this(cp, pkg, null);
    }

    public String write(Path root) throws IOException {
        String fileName = name + ".jl";
        Path path = root.resolve(name);
        Files.createDirectories(path);
        StringBuilder sb = new StringBuilder();
        Set<String> imports = new TreeSet<>();
        classes.stream().map(JuliaClass::getImports).forEach(imports::addAll);
        Set<String> exports = new TreeSet<>();
        classes.stream().map(JuliaClass::getExports).forEach(exports::addAll);
        sb.append("module ")
                .append(name)
                .append("\n\n")
                .append("using JavaCall\n\n");
        if (!imports.isEmpty()) {
            imports.forEach(x -> sb.append(x).append("\n"));
            sb.append("\n");
        }
        if (!exports.isEmpty()) {
            exports.forEach(x -> sb.append("export ").append(x).append("\n"));
            sb.append("\n");
        }
        Set<String> includes = new TreeSet<>();
        Set<String> processed = new HashSet<>();
        for (JuliaClass juliaClass : classes) {
            includes.add(juliaClass.write(path, processed));
        }
        for (JuliaModule mod : submodules.values()) {
            String modFileName = mod.write(path);
            Path p = Paths.get(mod.getName()).resolve(modFileName);
            includes.add(p.toString());
        }
        includes.forEach(x->sb.append("include(\"").append(x).append("\")\n"));
        sb.append("\nend\n");
        Files.write(path.resolve(fileName), sb.toString().getBytes());
        return fileName;
    }

}
