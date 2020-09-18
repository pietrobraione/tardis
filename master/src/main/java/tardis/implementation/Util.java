package tardis.implementation;

import java.io.File;
import java.lang.reflect.Executable;
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
import jbse.mem.ClauseAssume;
import jbse.mem.ClauseAssumeAliases;
import jbse.mem.ClauseAssumeClassInitialized;
import jbse.mem.ClauseAssumeClassNotInitialized;
import jbse.mem.ClauseAssumeExpands;
import jbse.mem.ClauseAssumeNull;
import jbse.val.Primitive;
import jbse.val.Symbolic;
import sushi.configure.Visibility;
import tardis.Options;

/**
 * Utility class gathering a number of common
 * static definitions.
 * 
 * @author Pietro Braione.
 */
public final class Util {
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

    /**
     * Checks whether a bytecode is an invoke* bytecode.
     * 
     * @param currentBytecode a {@code byte}.
     * @return {@code true} iff {@code currentBytecode} is an invoke*.
     */
    static boolean bytecodeInvoke(byte currentBytecode) {
        return (currentBytecode == Opcodes.OP_INVOKEVIRTUAL ||
        currentBytecode == Opcodes.OP_INVOKESTATIC ||
        currentBytecode == Opcodes.OP_INVOKEINTERFACE ||
        currentBytecode == Opcodes.OP_INVOKESPECIAL ||
        currentBytecode == Opcodes.OP_INVOKEDYNAMIC ||
        currentBytecode == Opcodes.OP_INVOKEHANDLE);
    }

    /**
     * Checks whether a bytecode is branching.
     * 
     * @param currentBytecode a {@code byte}.
     * @return {@code true} iff {@code currentBytecode} is branching.
     */
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

    /**
     * Checks whether a bytecode is a load constant bytecode.
     * 
     * @param currentBytecode a {@code byte}.
     * @return {@code true} iff {@code currentBytecode} is a load constant bytecode.
     */
    static boolean bytecodeLoadConstant(byte currentBytecode) {
        return (currentBytecode == Opcodes.OP_LDC ||
        currentBytecode == Opcodes.OP_LDC_W ||
        currentBytecode == Opcodes.OP_LDC2_W);
    }

    /**
     * Methods that are excluded from being test generation targets.
     */
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
     * Returns the target methods (and constructors).
     * 
     * @param o an {@link Options} object.
     * @return a {@link List}{@code <}{@link List}{@code <}{@link String}{@code >>}, where each 
     *         {@link List}{@code <}{@link String}{@code >} that is a member of the return value
     *         has three elements and is a method signature. If 
     *         {@code o.}{@link Options#getTargetClass() getTargetClass}{@code () == null}, 
     *         the return value contains as its only element {@code o.}{@link Options#getTargetMethod()}, otherwise
     *         the return value contains the methods
     *         of {@code o.}{@link Options#getTargetClass() getTargetClass()} that are not private, nor synthetic, 
     *         nor {@code equals}, or {@code hashCode}, or {@code toString}, or {@code clone}, or {@code immutableEnumSet}
     *         methods (if {@code o.}{@link Options#getVisibility() getVisibility}{@code () == }{@link Visibility#PUBLIC}, 
     *         only the signatures public methods are returned, otherwise all the signatures of the methods with 
     *         nonprivate visibility are returned). 
     * @throws ClassNotFoundException if the class is not in {@code o.}{@link Options#getClassesPath() getClassesPath()}.
     * @throws SecurityException if a security violation arises.
     * @throws MalformedURLException if some path in {@code o.}{@link Options#getClassesPath() getClassesPath()} does not exist.
     */
    static List<List<String>> getTargets(Options o) 
    throws ClassNotFoundException, MalformedURLException, SecurityException {
        final String className = o.getTargetClass();
        final List<List<String>> retVal = new ArrayList<>();
        if (className == null) {
            retVal.add(o.getTargetMethod());
        } else {
            final boolean onlyPublic = (o.getVisibility() == Visibility.PUBLIC);
            final ClassLoader ic = getInternalClassloader(o.getClassesPath());
            final Class<?> clazz = ic.loadClass(className.replace('/', '.'));
            final List<Executable> targets = Stream.concat(Arrays.stream(clazz.getDeclaredMethods()), Arrays.stream(clazz.getDeclaredConstructors())).collect(Collectors.toList());
            for (Executable t : targets) {
                if (!EXCLUDED.contains(t.getName()) &&
                    ((onlyPublic && (t.getModifiers() & Modifier.PUBLIC) != 0) || (t.getModifiers() & Modifier.PRIVATE) == 0) &&
                    !t.isSynthetic()) {
                    final List<String> targetSignature = new ArrayList<>(3);
                    targetSignature.add(className);
                    targetSignature.add("(" + Arrays.stream(t.getParameterTypes())
                                                    .map(c -> c.getName())
                                                    .map(s -> s.replace('.', '/'))
                                                    .map(Util::convertPrimitiveTypes)
                                                    .map(Util::addReferenceMark)
                                                    .collect(Collectors.joining()) +
                                        ")" + ((t instanceof Method) ? addReferenceMark(convertPrimitiveTypes(((Method) t).getReturnType().getName().replace('.', '/'))) : "V"));
                    targetSignature.add((t instanceof Method) ? t.getName() : "<init>");
                    retVal.add(targetSignature);
                }
            }
        }
        return retVal;
    }
    
    /**
     * Returns a classloader to load the classes on a classpath, 
     * creating it if it does not exist.
     * 
     * @param classpath A {@link List}{@code <}{@link Path}{@code >}.
     * @return a {@link ClassLoader} that is able to load the classes
     *         found in {@code classpath}. If the method is invoked 
     *         twice, the second time the classloader is not recreated, 
     *         the {@code classpath} parameter is ignored, and the
     *         classoader created at the first invocation is returned 
     * @throws MalformedURLException if some path in {@code classpath} 
     *         is malformed.
     * @throws SecurityException if a security violation arises.
     */
    static ClassLoader internalClassLoader = null; //lazily initialized
    private static ClassLoader getInternalClassloader(List<Path> classpath) throws MalformedURLException, SecurityException {
        if (internalClassLoader == null) {
            final ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
            if (classpath == null || classpath.size() == 0) {
                internalClassLoader = systemClassLoader;
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
                internalClassLoader = new URLClassLoader(urls.toArray(new URL[0]), Util.class.getClassLoader());
            }
        }
        return internalClassLoader;
    }
    
    /**
     * Returns a classloader to load the classes on a classpath.
     * 
     * @return a {@link ClassLoader} that is able to load the classes
     *         found in the classpath given by the first previous call to
     *         {@link #getTargets(Options)}. If the method is invoked 
     *         before {@link #getTargets(Options)} is invoked, it returns
     *         {@code null}. 
     * @throws SecurityException if a security violation arises.
     */
    static ClassLoader getInternalClassloader() {
        return internalClassLoader;
    }

    /**
     * Converts a canonical primitive type name to the
     * corresponding internal primitive type name.
     * 
     * @param s a {@link String}.
     * @return If {@code s} is a canonical primitive type
     *         name, returns the corresponding internal primitive
     *         type name; otherwise, returns {@code s}.
     * @throws NullPointerException if {@code s == null}.
     */
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

    /**
     * Adds a reference mark to its parameter
     * 
     * @param s a {@link String}.
     * @return If {@code s} is an internal primitive type
     *         name, or {@code s}
     *         starts by {@code '['}, returns {@code s}; 
     *         otherwise, returns {@code "L" + s + ";"}. 
     * @throws NullPointerException if {@code s == null}.
     */
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

    /**
     * Converts a path condition to a {@link String}.
     * 
     * @param pathCondition a sequence (more precisely, an {@link Iterable})
     *        of {@link Clause}s.
     * @return a {@link String} representation of {@code pathCondition}.
     * @throws NullPointerException if {@code pathCondition == null}.
     */
    static final String stringifyPathCondition(Iterable<Clause> pathCondition) {
        final StringBuilder retVal = new StringBuilder();
        boolean first = true;
        for (Clause c : pathCondition) {
            if (first) {
                first = false;
            } else {
                retVal.append(" && ");
            }
            if (c instanceof ClauseAssume) {
                final Primitive p = ((ClauseAssume) c).getCondition();
                if (p instanceof Symbolic) {
                    retVal.append(((Symbolic) p).asOriginString());
                } else {
                    retVal.append(p.toString());
                }
            } else if (c instanceof ClauseAssumeExpands) {
                retVal.append(((ClauseAssumeExpands) c).getReference().asOriginString());
                retVal.append(" fresh ");
                retVal.append(((ClauseAssumeExpands) c).getObjekt().getType().getClassName());
            } else if (c instanceof ClauseAssumeAliases) {
                retVal.append(((ClauseAssumeAliases) c).getReference().asOriginString());
                retVal.append(" aliases ");
                retVal.append(((ClauseAssumeAliases) c).getObjekt().getOrigin().asOriginString());
            } else if (c instanceof ClauseAssumeNull) {
                retVal.append(((ClauseAssumeNull) c).getReference().asOriginString());
                retVal.append(" null");
            } else {
                retVal.append(c.toString());
            }
        }
        return retVal.toString();
    }
    
    /**
     * Do not instantiate!
     */
    private Util() { }
}
