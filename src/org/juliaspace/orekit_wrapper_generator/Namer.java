package org.juliaspace.orekit_wrapper_generator;

import com.google.common.base.CaseFormat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Namer {
    private static final Set<String> RESERVED_NAMES = new HashSet<>();

    static {
        RESERVED_NAMES.add("Complex");
        RESERVED_NAMES.add("Iterator");
        RESERVED_NAMES.add("Vector");
        RESERVED_NAMES.add("String");
        RESERVED_NAMES.add("end");
    }

    public static boolean isReserved(String in) {
        return RESERVED_NAMES.contains(in);
    }

    public static String mangle(String in) {
        if (isReserved(in)) return "J" + in;
        return in;
    }

    public static String fileName(String name) {
        if (name.matches("^[A-Z]+$")) return name.toLowerCase();
        Pattern p = Pattern.compile("([A-Z]{3,})");
        Matcher m = p.matcher(name);

        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            sb.append(name, last, m.start());
            String caps = m.group(1);
            String start = caps.substring(0, 1);
            String lows = caps.substring(1, caps.length() - 1).toLowerCase();
            String end = caps.substring(caps.length() - 1);
            sb.append(start);
            sb.append(lows);
            sb.append(end);
            last = m.end();
        }
        sb.append(name.substring(last));
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, sb.toString()) + ".jl";
    }

    public static String name(Class<?> cls) {
        if (cls.getEnclosingClass() != null) {
            String parentName = translateName(cls.getEnclosingClass());
            return parentName + "_" + translateName(cls.getSimpleName());
        }
        return nameClass(cls.getName());
    }

    public static String nameClass(String clsName) {
        String[] parts = clsName.split("\\.");
        return mangle(parts[parts.length - 1]);
    }

    public static String name(Executable exe) {
        if (exe instanceof Method) {
            Method method = (Method) exe;
            return name(method);
        } else if (exe instanceof Constructor) {
            return nameClass(exe.getName());
        }
        return null;
    }

    public static String namePkg(String pkg) {
        String name = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, pkg);
        return name + "Wrapper";
    }

    public static String mangleMethod(String in) {
        if (RESERVED_NAMES.contains(in)) return "_" + in;
        return in;
    }

    public static String name(Method method) {
        String javaName = method.getName();
        Pattern p = Pattern.compile("([a-z][A-Z0-9])([A-Z0-9]+)([A-Z0-9]|$)");
        Matcher m = p.matcher(javaName);

        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            sb.append(javaName, last, m.start());
            sb.append(m.group(1));
            sb.append(m.group(2).toLowerCase());
            sb.append(m.group(3));
            last = m.end();
        }
        sb.append(javaName.substring(last));
        String name = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, sb.toString());
        return mangleMethod(name);
    }

    public static String translateName(Class<?> in) {
        return translateName(in.getSimpleName());
    }

    public static String translateName(String in) {
        return switch (in) {
            case "byte" -> "jbyte";
            case "boolean" -> "jboolean";
            case "char" -> "jchar";
            case "int" -> "jint";
            case "long" -> "jlong";
            case "float" -> "jfloat";
            case "double" -> "jdouble";
            case "short" -> "jshort";
            case "String" -> "JString";
            default -> mangle(in);
        };
    }

    public static String translateArrayName(Class<?> in) {
        return translateArrayName(in.getSimpleName());
    }

    public static String translateArrayName(String in) {
        boolean isMatrix = in.endsWith("[][]");
        String type = in.replaceAll("[\\[\\]]", "");
        String jType = translateName(type);
        StringBuilder sb = new StringBuilder();
        if (isMatrix) {
            sb.append("Vector{Vector{").append(jType).append("}}");
        } else {
            sb.append("Vector{").append(jType).append("}");
        }
        return sb.toString();
    }
}
