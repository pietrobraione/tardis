package tardis.implementation;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import jbse.bc.Opcodes;
import jbse.mem.Clause;
import jbse.mem.ClauseAssumeClassInitialized;
import jbse.mem.ClauseAssumeClassNotInitialized;
import sushi.configure.Visibility;
import tardis.Options;

public class Util {
    /**
     * Returns the name of this application, as resulting
     * from the containing jar file.
     * 
     * @return a {@link String} or {@code null} if this 
     *         class is not packaged in a jar file.
     */
    public static String getName() {
        return Util.class.getPackage().getImplementationTitle();
    }

    /**
     * Returns the vendor of this application, as resulting
     * from the containing jar file.
     * 
     * @return a {@link String} or {@code null} if this 
     *         class is not packaged in a jar file.
     */
    public static String getVendor() {
        return Util.class.getPackage().getImplementationVendor();
    }

    /**
     * Returns the version of this application, as resulting
     * from the containing jar file.
     * 
     * @return a {@link String} or {@code null} if this 
     *         class is not packaged in a jar file.
     */
    public static String getVersion() {
        return Util.class.getPackage().getImplementationVersion();
    }

    /**
     * Converts an iterable to a stream.
     * See <a href="https://stackoverflow.com/a/23177907/450589">https://stackoverflow.com/a/23177907/450589</a>.
     * @param it an {@link Iterable}{@code <T>}.
     * @return a {@link Stream}{@code <T>} for {@code it}.
     */
    public static <T> Stream<T> stream(Iterable<T> it) {
        return StreamSupport.stream(it.spliterator(), false);
    }

    /**
     * Shortens a sequence of {@link Clause}s by dropping all the clauses
     * about class initialization.
     * 
     * @param pc a {@link Collection}{@code <}{@link Clause}{@code >}.
     * @return {@code pc} filtered, where the filter drops all the clauses
     *         that are {@code instanceof }{@link ClauseAssumeClassInitialized}
     *         or {@link ClauseAssumeClassNotInitialized}.
     */
    static Collection<Clause> shorten(Collection<Clause> pc) {
        return pc.stream().filter(x -> !(x instanceof ClauseAssumeClassInitialized || x instanceof ClauseAssumeClassNotInitialized)).collect(Collectors.toList());
    }

    /**
     * Checks whether a bytecode is a jump bytecode.
     * 
     * @param currentBytecode a {@code byte}.
     * @return {@code true} iff {@code currentBytecode} jumps.
     */
    static boolean bytecodeJump(byte currentBytecode) {
        return (currentBytecode == Opcodes.OP_IF_ACMPEQ ||
        currentBytecode == Opcodes.OP_IF_ACMPNE ||	
        currentBytecode == Opcodes.OP_IFNONNULL ||	
        currentBytecode == Opcodes.OP_IFNULL ||	
        currentBytecode == Opcodes.OP_IFEQ ||
        currentBytecode == Opcodes.OP_IFGE ||	
        currentBytecode == Opcodes.OP_IFGT ||	
        currentBytecode == Opcodes.OP_IFLE ||	
        currentBytecode == Opcodes.OP_IFLT ||	
        currentBytecode == Opcodes.OP_IFNE ||	
        currentBytecode == Opcodes.OP_IF_ICMPEQ ||	
        currentBytecode == Opcodes.OP_IF_ICMPGE ||	
        currentBytecode == Opcodes.OP_IF_ICMPGT ||	
        currentBytecode == Opcodes.OP_IF_ICMPLE ||	
        currentBytecode == Opcodes.OP_IF_ICMPLT ||	
        currentBytecode == Opcodes.OP_IF_ICMPNE ||	
        currentBytecode == Opcodes.OP_LOOKUPSWITCH ||	
        currentBytecode == Opcodes.OP_TABLESWITCH);

    }

    static boolean bytecodeInvoke(byte currentBytecode) {
        return (currentBytecode == Opcodes.OP_INVOKEVIRTUAL ||
        currentBytecode == Opcodes.OP_INVOKESTATIC ||
        currentBytecode == Opcodes.OP_INVOKEINTERFACE ||
        currentBytecode == Opcodes.OP_INVOKESPECIAL ||
        currentBytecode == Opcodes.OP_INVOKEDYNAMIC ||
        currentBytecode == Opcodes.OP_INVOKEHANDLE);
    }

    static boolean bytecodeBranch(byte currentBytecode) {
        return (bytecodeJump(currentBytecode) ||
        bytecodeInvoke(currentBytecode) ||
        currentBytecode == Opcodes.OP_ALOAD ||
        currentBytecode == Opcodes.OP_ALOAD_0 ||
        currentBytecode == Opcodes.OP_ALOAD_1 ||
        currentBytecode == Opcodes.OP_ALOAD_2 ||
        currentBytecode == Opcodes.OP_ALOAD_3 ||
        currentBytecode == Opcodes.OP_IALOAD ||
        currentBytecode == Opcodes.OP_LALOAD ||
        currentBytecode == Opcodes.OP_FALOAD ||
        currentBytecode == Opcodes.OP_DALOAD ||
        currentBytecode == Opcodes.OP_AALOAD ||
        currentBytecode == Opcodes.OP_BALOAD ||
        currentBytecode == Opcodes.OP_CALOAD ||
        currentBytecode == Opcodes.OP_SALOAD ||
        currentBytecode == Opcodes.OP_IASTORE ||
        currentBytecode == Opcodes.OP_LASTORE ||
        currentBytecode == Opcodes.OP_FASTORE ||
        currentBytecode == Opcodes.OP_DASTORE ||
        currentBytecode == Opcodes.OP_AASTORE ||
        currentBytecode == Opcodes.OP_BASTORE ||
        currentBytecode == Opcodes.OP_CASTORE ||
        currentBytecode == Opcodes.OP_LCMP ||
        currentBytecode == Opcodes.OP_FCMPL ||
        currentBytecode == Opcodes.OP_FCMPG ||
        currentBytecode == Opcodes.OP_DCMPL ||
        currentBytecode == Opcodes.OP_DCMPG ||
        currentBytecode == Opcodes.OP_GETSTATIC ||
        currentBytecode == Opcodes.OP_GETFIELD ||
        currentBytecode == Opcodes.OP_NEWARRAY ||
        currentBytecode == Opcodes.OP_ANEWARRAY ||
        currentBytecode == Opcodes.OP_MULTIANEWARRAY);
    }

    static boolean bytecodeLoadConstant(byte currentBytecode) {
        return (currentBytecode == Opcodes.OP_LDC ||
        currentBytecode == Opcodes.OP_LDC_W ||
        currentBytecode == Opcodes.OP_LDC2_W);
    }

    private final static Set<String> EXCLUDED;

    static {
        EXCLUDED = new HashSet<String>();
        EXCLUDED.add("equals");
        EXCLUDED.add("hashCode");
        EXCLUDED.add("toString");
        EXCLUDED.add("clone");
        EXCLUDED.add("immutableEnumSet");
    }	

    /**
     * Returns the externally callable methods of the target class.
     * 
     * @param o an {@link Options} object.
     * @return a {@link List}{@code <}{@link List}{@code <}{@link String}{@code >>} of the methods
     *         of the class {@code o.}{@link Options#getTargetClass() getTargetClass()} that are not private, nor synthetic, nor one of the 
     *         {@code equals}, {@code hashCode}, {@code toString}, {@code clone}, {@code immutableEnumSet}.
     *         If {@code onlyPublic == true} only the public methods are returned. Each {@link List}{@code <}{@link String}{@code >}
     *         has three elements and is a method signature.
     * @throws ClassNotFoundException if the class is not in {@code o.}{@link Options#getClassesPath() getClassesPath()}.
     * @throws SecurityException 
     * @throws MalformedURLException if some path in {@code o.}{@link Options#getClassesPath() getClassesPath()} does not exist.
     */
    public static List<List<String>> getVisibleTargetMethods(Options o) 
    throws ClassNotFoundException, MalformedURLException, SecurityException {
        final String className = o.getTargetClass();
        final boolean onlyPublic = (o.getVisibility() == Visibility.PUBLIC);
        final ClassLoader ic = getInternalClassloader(o.getClassesPath());
        final Class<?> clazz = ic.loadClass(className.replace('/', '.'));
        final List<List<String>> methods = new ArrayList<>();
        for (Method m : clazz.getDeclaredMethods()) {
            if (!EXCLUDED.contains(m.getName()) &&
            ((onlyPublic && (m.getModifiers() & Modifier.PUBLIC) != 0) || (m.getModifiers() & Modifier.PRIVATE) == 0) &&
            !m.isSynthetic()) {
                final List<String> methodSignature = new ArrayList<>(3);
                methodSignature.add(className);
                methodSignature.add("(" +
                Arrays.stream(m.getParameterTypes())
                .map(c -> c.getName())
                .map(s -> s.replace('.', '/'))
                .map(Util::convertPrimitiveTypes)
                .map(Util::addReferenceMark)
                .collect(Collectors.joining()) +
                ")" + addReferenceMark(convertPrimitiveTypes(m.getReturnType().getName().replace('.', '/'))));
                methodSignature.add(m.getName());
                methods.add(methodSignature);
            }
        }
        return methods;
    }

    /**
     * Returns the target method of the target class.
     * 
     * @param o an {@link Options} object. It must be {@code o.}{@link Options#getTargetMethod() getTargeMethod()}{@code  != null}.
     * @return a {@link List}{@code <}{@link List}{@code <}{@link String}{@code >>} containing exactly one element,
     *         derived from {@code o.}{@link Options#getTargeMethod() getTargeMethod()}. The element (a {@link List}{@code <}{@link String}{@code >})
     *         has three elements and is a method signature.
     * @throws NullPointerException if {@code o.}{@link Options#getTargetMethod() getTargeMethod()}{@code  == null}.
     */
    public static List<List<String>> getUniqueTargetMethod(Options o) {
        final ArrayList<String> targetMethod = new ArrayList<>();
        targetMethod.add(o.getTargetMethod().get(0));
        targetMethod.add(o.getTargetMethod().get(1));
        targetMethod.add(o.getTargetMethod().get(2));
        final List<List<String>> methods = new ArrayList<>();
        methods.add(targetMethod);
        return methods;
    }

    public static ClassLoader getInternalClassloader(List<Path> classpath) throws MalformedURLException, SecurityException {
        final ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        final ClassLoader classLoader;
        if (classpath == null || classpath.size() == 0) {
            classLoader = systemClassLoader;
        } else {
            final List<File> paths = new ArrayList<File>();
            for (Path path : classpath) {
                final File newPath = path.toFile();
                if (!newPath.exists()) {
                    throw new MalformedURLException("The new path " + newPath + " does not exist");
                } else {
                    paths.add(newPath);
                }
            }

            final List<URL> urls = new ArrayList<URL>();
            if (systemClassLoader instanceof URLClassLoader) {
                urls.addAll(Arrays.asList(((URLClassLoader) systemClassLoader).getURLs()));
            }

            for (File newPath : paths) {
                urls.add(newPath.toURI().toURL());
            }
            classLoader = new URLClassLoader(urls.toArray(new URL[0]), Util.class.getClassLoader());
        }
        return classLoader;
    }

    private static final String convertPrimitiveTypes(String s) {
        if (s.equals("boolean")) {
            return "Z";
        } else if (s.equals("byte")) {
            return "B";
        } else if (s.equals("short")) {
            return "S";
        } else if (s.equals("int")) {
            return "I";
        } else if (s.equals("long")) {
            return "J";
        } else if (s.equals("char")) {
            return "C";
        } else if (s.equals("float")) {
            return "F";
        } else if (s.equals("double")) {
            return "D";
        } else if (s.equals("void")) {
            return "V";
        } else {
            return s;
        }
    }

    private static final String addReferenceMark(String s) {
        if (s.equals("Z") ||
        s.equals("B") ||
        s.equals("S") ||
        s.equals("I") ||
        s.equals("J") ||
        s.equals("C") ||
        s.equals("F") ||
        s.equals("D") ||
        s.equals("V") ||
        s.charAt(0) == '[') {
            return s;
        } else {
            return "L" + s + ";";
        }
    }
}
