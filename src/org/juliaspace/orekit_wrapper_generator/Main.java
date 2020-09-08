package org.juliaspace.orekit_wrapper_generator;

import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;

import java.io.IOException;
import java.lang.reflect.*;
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

    public static String julianize(String name) {
        Pattern p = Pattern.compile("([a-z][A-Z])([A-Z]+)");
        Matcher m = p.matcher(name);

        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            sb.append(name, last, m.start());
            sb.append(m.group(1));
            String caps = m.group(2);
            String lows = caps.substring(0, caps.length()-1).toLowerCase();
            sb.append(lows);
            sb.append(caps, caps.length()-1, caps.length());
            last = m.end();
        }
        sb.append(name.substring(last));
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, sb.toString());
    }

    public static void writeClassFile(Class<?> cls, Path path) throws IOException {
        String className = cls.getSimpleName();
        Map<String, String> imports = new TreeMap<>();

        StringBuilder body = new StringBuilder();
        Constructor<?>[] constructors = cls.getConstructors();
        for (Constructor<?> constructor : constructors) {
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
            if (Modifier.isPublic(method.getModifiers())) {
                if (method.isBridge()) continue;
                String name = method.getName();
//                String juliaName = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, name);
//                juliaName = juliaName.replaceAll("_([a-z])_", "_$1");
                String juliaName = julianize(name);
                String objName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, className);
                ParameterHandler handler = new ParameterHandler(method.getParameters(), imports);
                imports.putAll(handler.getNewImports());
                Class<?> type = method.getReturnType();
                boolean imported = imports.containsKey(type.getSimpleName());
                if (!imported && ParameterHandler.isNotPrimitive(type)) {
                    imports.put(type.getSimpleName(), type.getName());
                }
                String signature = handler.getSignature();
                String types = handler.getTypes();
                String args = handler.getArgs();
                String returnType = type.getSimpleName();
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

    }

    public static void scanJars(List<URL> fileUrls) throws IOException {
        URLClassLoader cl = new URLClassLoader(fileUrls.toArray(URL[]::new));
        ClassPath cp = ClassPath.from(cl);
        ImmutableSet<ClassPath.ClassInfo> topLevelClasses = cp.getTopLevelClassesRecursive("org.orekit");
        Set<String> processed = new HashSet<>();
        for (ClassPath.ClassInfo info : topLevelClasses) {
            Class<?> cls = info.load();
            if (processed.contains(cls.getCanonicalName())) {
                continue;
            }
            processed.add(cls.getCanonicalName());
            //FIXME: Delete branch
            if (cls.getSimpleName().startsWith("AbsoluteDate")) {
                writeClassFile(cls, Paths.get(cls.getSimpleName() + ".jl"));
            }
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
        scanJars(jars);
    }
}
