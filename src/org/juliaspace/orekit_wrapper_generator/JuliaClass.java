package org.juliaspace.orekit_wrapper_generator;

public class JuliaClass implements Comparable {
    public String juliaName;
    public String fileName;

    public JuliaClass(String name, String path) {
        juliaName = name;
        fileName = path;
    }

    @Override
    public int compareTo(Object o) {
        JuliaClass other = (JuliaClass) o;
        return juliaName.compareTo(other.juliaName);
    }
}
