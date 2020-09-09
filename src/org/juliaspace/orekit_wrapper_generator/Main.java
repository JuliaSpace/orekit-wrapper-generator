package org.juliaspace.orekit_wrapper_generator;

import com.google.common.base.CaseFormat;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static final String[] TOP_LEVEL_PACKAGES = {"org.orekit", "org.hipparchus"};

    public static String julianize(String name) {
        Pattern p = Pattern.compile("([a-z][A-Z])([A-Z]+)");
        Matcher m = p.matcher(name);

        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            sb.append(name, last, m.start());
            sb.append(m.group(1));
            String caps = m.group(2);
            String lows = caps.substring(0, caps.length() - 1).toLowerCase();
            sb.append(lows);
            sb.append(caps, caps.length() - 1, caps.length());
            last = m.end();
        }
        sb.append(name.substring(last));
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, sb.toString());
    }

    public static String writeClassFile(Class<?> cls, Path path) throws IOException {
        String className = cls.getSimpleName();
        if (className.equals(path.getParent().getFileName().toString())) {
            className += "Cls";
        }
        Map<String, String> imports = new TreeMap<>();
        imports.put(className, cls.getName());


        StringBuilder body = new StringBuilder();
        Constructor<?>[] constructors = cls.getConstructors();
        for (Constructor<?> constructor : constructors) {
            if (constructor.isAnnotationPresent(Deprecated.class)) continue;
            if (Modifier.isPublic(constructor.getModifiers())) {
                ParameterHandler handler = new ParameterHandler(constructor.getParameters(), imports);
                imports.putAll(handler.getNewImports());
                String signature = handler.getSignature();
                String types = handler.getTypes();
                String args = handler.getArgs();

                String buf = "function " +
                        className +
                        "(" +
                        signature +
                        ")\n" +
                        "    return " +
                        className +
                        "((" +
                        types +
                        "), " +
                        args +
                        ")\nend\n\n";
                body.append(buf);
            }
        }
        Method[] methods = cls.getMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(Deprecated.class)) continue;
            if (Modifier.isPublic(method.getModifiers())) {
                if (method.isBridge()) continue;
                if (method.isSynthetic()) continue;
                String name = method.getName();
                String juliaName = julianize(name);
                String objName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, className);
                ParameterHandler handler = new ParameterHandler(method.getParameters(), imports);
                imports.putAll(handler.getNewImports());
                Class<?> type = method.getReturnType();
                String typeName = type.getSimpleName();
                boolean imported = imports.containsKey(type.getSimpleName());
                if (type.getEnclosingClass() != null && Modifier.isStatic(type.getModifiers())) {
                    String parentName = type.getEnclosingClass().getSimpleName();
                    typeName = parentName + "_" + typeName;
                    if (!imports.containsKey(typeName)) {
                        imports.put(typeName, type.getName());
                    }
                } else if (!imported && ParameterHandler.isNotPrimitive(type)) {
                    imports.put(type.getSimpleName(), type.getName());
                }
                String signature = handler.getSignature();
                String types = handler.getTypes();
                String args = handler.getArgs();
                String returnType = typeName;
                StringBuilder buf = new StringBuilder();
                buf.append("function ")
                        .append(juliaName)
                        .append("(")
                        .append(objName)
                        .append("::")
                        .append(className);
                if (!signature.isEmpty()) {
                    buf.append(", ").append(signature);
                }
                buf.append(")\n")
                        .append("    return jcall(")
                        .append(objName)
                        .append(", \"")
                        .append(name)
                        .append("\", ")
                        .append(returnType)
                        .append(", (")
                        .append(types)
                        .append("), ")
                        .append(args)
                        .append(")\nend\n\n");

                body.append(buf);
            }
        }
        StringBuilder jlFile = new StringBuilder();
        for (Map.Entry<String, String> entry : imports.entrySet()) {
            jlFile.append("const ")
                    .append(entry.getKey())
                    .append(" = @jimport ")
                    .append(entry.getValue())
                    .append("\n");
        }
        jlFile.append("\n");
        jlFile.append(body);
        Files.write(path, jlFile.toString().getBytes());

        return className;
    }

    public static Path packageToPath(String packageName) {
        List<String> parts = Arrays.asList(packageName.split("\\."));
        String root = CaseFormat.LOWER_UNDERSCORE.to(
                CaseFormat.UPPER_CAMEL, parts.get(1)
        );
        Path path = Paths.get(root + "Wrapper");
        for (String s : parts.subList(2, parts.size())) {
            String part = CaseFormat.LOWER_UNDERSCORE.to(
                    CaseFormat.UPPER_CAMEL, s
            );
            path = path.resolve(part);
        }
        return path;
    }

    public static String parentPackage(String packageName) {
        List<String> parts = Arrays.asList(packageName.split("\\."));
        Joiner joiner = Joiner.on(".");
        return joiner.join(parts.subList(0, parts.size()-1));
    }

    public static void scanJars(List<URL> fileUrls, String[] topLevelPackages) throws IOException {
        Path root = Paths.get("gen");
        FileUtils.deleteDirectory(root.toFile());
        Files.createDirectories(root);
        URLClassLoader cl = new URLClassLoader(fileUrls.toArray(URL[]::new));
        ClassPath cp = ClassPath.from(cl);
        Set<String> processed = new HashSet<>();
        Map<String, JuliaModule> modules = new TreeMap<>();
        for (String topLevelPackage : topLevelPackages) {
            ImmutableSet<ClassPath.ClassInfo> topLevelClasses = cp.getTopLevelClassesRecursive(topLevelPackage);
            for (ClassPath.ClassInfo info : topLevelClasses) {
                Class<?> cls = info.load();
                if (cls.isAnnotation()) continue;
                if (cls.isInterface()) continue;
                if (Modifier.isAbstract(cls.getModifiers())) continue;
                if (cls.isAnnotationPresent(Deprecated.class)) continue;
                String pkgName = cls.getPackageName();
                String parentPkgName = parentPackage(pkgName);
                Path pkgPath = root.resolve(packageToPath(pkgName));
                Files.createDirectories(pkgPath);
                if (processed.contains(cls.getCanonicalName())) {
                    continue;
                }
                processed.add(cls.getCanonicalName());
                String fileName = CaseFormat.UPPER_CAMEL.to(
                        CaseFormat.LOWER_UNDERSCORE, cls.getSimpleName()
                ) + ".jl";
                if (fileName.equals(pkgPath.getFileName().toString().toLowerCase()+".jl")) {
                    fileName += "_cls";
                }
                String className = writeClassFile(cls, pkgPath.resolve(fileName));
                JuliaClass jl = new JuliaClass(className, fileName);
                if (!modules.containsKey(pkgName)) {
                    modules.put(pkgName, new JuliaModule(pkgPath));
                }
                modules.get(pkgName).classes.add(jl);
                if (!modules.containsKey(parentPkgName)) {
                    modules.put(parentPkgName, new JuliaModule(pkgPath.getParent()));
                }
                modules.get(parentPkgName).submodules.add(pkgPath);
            }
        }
        for (Map.Entry<String, JuliaModule> entry : modules.entrySet()) {
            JuliaModule mod = entry.getValue();
            Path modulePath = mod.path;
            String moduleName = modulePath.getFileName().toString();
            Path moduleFile = modulePath.resolve(moduleName + ".jl");
            StringBuilder sb = new StringBuilder();
            sb.append("module ").append(moduleName).append("\n\nusing JavaCall\n\n");
            StringBuilder expSb = new StringBuilder();
            StringBuilder incSb = new StringBuilder();
            for (Path submod : mod.submodules) {
                String subModName = submod.getFileName().toString();
                String subModFile = submod.getFileName().resolve(subModName + ".jl").toString();
                expSb.append("export ").append(subModName).append("\n");
                incSb.append("include(\"").append(subModFile).append("\")\n");
            }
            for (JuliaClass jlClass : mod.classes) {
                expSb.append("export ").append(jlClass.juliaName).append("\n");
                incSb.append("include(\"").append(jlClass.fileName).append("\")\n");
            }
            sb.append(expSb.toString());
            sb.append("\n");
            sb.append(incSb.toString());
            sb.append("\nend\n");
            Files.write(moduleFile, sb.toString().getBytes());
        }
    }

    public static void main(String[] args) throws IOException {
        List<URL> jars = new ArrayList<>();
        for (String arg : args) {
            try {
                jars.add(Paths.get(arg).toUri().toURL());
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
        scanJars(jars, TOP_LEVEL_PACKAGES);
    }
}
