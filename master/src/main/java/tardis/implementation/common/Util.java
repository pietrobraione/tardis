package tardis.implementation.common;

import static jbse.common.Type.className;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import tardis.Options;
import tardis.Visibility;
import tardis.implementation.jbse.ClauseAssumeExpandsSubtypes;
import tardis.implementation.jbse.JBSEResult;

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
     * @param pathCondition a {@link Collection}{@code <}{@link Clause}{@code >}.
     * @return {@code pathCondition} filtered, where the filter drops all the clauses
     *         that are {@code instanceof }{@link ClauseAssumeClassInitialized}
     *         or {@link ClauseAssumeClassNotInitialized}.
     */
    public static List<Clause> shorten(Collection<Clause> pathCondition) {
        return pathCondition.stream().filter(x -> !(x instanceof ClauseAssumeClassInitialized || x instanceof ClauseAssumeClassNotInitialized)).collect(Collectors.toList());
    }

    /**
     * Checks whether a bytecode is a load bytecode
     * (only for strings).
     * 
     * @param bytecode a {@code byte}.
     * @return {@code true} iff {@code bytecode} is a load bytecode
     *         that may be used to load a reference to a string.
     */
    public static boolean isBytecodeLoad(byte bytecode) {
        return (bytecode == Opcodes.OP_LDC ||
        bytecode == Opcodes.OP_LDC_W ||
        bytecode == Opcodes.OP_LDC2_W || 
        bytecode == Opcodes.OP_ALOAD || 
        bytecode == Opcodes.OP_ALOAD_0 || 
        bytecode == Opcodes.OP_ALOAD_1 || 
        bytecode == Opcodes.OP_ALOAD_2 || 
        bytecode == Opcodes.OP_ALOAD_3 || 
        bytecode == Opcodes.OP_AALOAD || 
        bytecode == Opcodes.OP_GETSTATIC ||
        bytecode == Opcodes.OP_GETFIELD);
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
     * @throws ClassNotFoundException if the target class is not in {@code o.}{@link Options#getClassesPath() getClassesPath()}.
     * @throws SecurityException if a security violation arises.
     * @throws MalformedURLException if some path in {@code o.}{@link Options#getClassesPath() getClassesPath()} is malformed.
     */
    public static List<List<String>> getTargets(Options o) 
    throws ClassNotFoundException, MalformedURLException, SecurityException {
        final List<List<String>> retVal = new ArrayList<>();
        final String className = o.getTargetClass();
        if (className == null) {
            retVal.add(o.getTargetMethod());
        } else {
            final boolean onlyPublic = (o.getVisibility() == Visibility.PUBLIC);
            ensureInternalClassLoader(o.getClassesPath());
            final ClassLoader ic = getInternalClassLoader(); 
            final Class<?> clazz = ic.loadClass(className.replace('/', '.'));
            final List<Executable> targets = Stream.concat(Arrays.stream(clazz.getDeclaredMethods()), Arrays.stream(clazz.getDeclaredConstructors())).collect(Collectors.toList());
            for (Executable target : targets) {
            	addTargetIfNotExcluded(retVal, target, onlyPublic, className);
            }
        }
        return retVal;
    }
    
    private static void addTargetIfNotExcluded(List<List<String>> retVal, Executable target, boolean onlyPublic, String className) {
    	final boolean visible = (onlyPublic && (target.getModifiers() & Modifier.PUBLIC) != 0) || (target.getModifiers() & Modifier.PRIVATE) == 0;
    	if (!EXCLUDED.contains(target.getName()) && visible && !target.isSynthetic()) {
    		final List<String> targetSignature = new ArrayList<>(3);
    		targetSignature.add(className);
    		final String descriptorParams = "(" + Arrays.stream(target.getParameterTypes())
    		.map(c -> c.getName())
    		.map(s -> s.replace('.', '/'))
    		.map(Util::convertPrimitiveTypes)
    		.map(Util::addReferenceMark)
    		.collect(Collectors.joining()) + ")";
    		final String descriptorReturn = ((target instanceof Method) ? addReferenceMark(convertPrimitiveTypes(((Method) target).getReturnType().getName().replace('.', '/'))) : "V");
    		targetSignature.add(descriptorParams + descriptorReturn);
    		targetSignature.add((target instanceof Method) ? target.getName() : "<init>");
    		retVal.add(targetSignature);
    	}
    }
    
    /**
     * Creates a classloader to load the classes on a classpath.
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
    static ClassLoader internalClassLoader = null;
    public static void ensureInternalClassLoader(List<Path> classpath) throws MalformedURLException, SecurityException {
        if (internalClassLoader == null) {
            if (classpath == null || classpath.size() == 0) {
                internalClassLoader = ClassLoader.getSystemClassLoader();
            } else {
            	internalClassLoader = makeURLClassLoader(classpath);
            }
        }
    }
    
    private static ClassLoader makeURLClassLoader(List<Path> classpath) throws MalformedURLException {
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
        final ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        if (systemClassLoader instanceof URLClassLoader) {
            urls.addAll(Arrays.asList(((URLClassLoader) systemClassLoader).getURLs()));
        }

        for (File newPath : paths) {
            urls.add(newPath.toURI().toURL());
        }
        return new URLClassLoader(urls.toArray(new URL[0]), Util.class.getClassLoader());
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
    public static ClassLoader getInternalClassLoader() {
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
     * @param pathCondition a {@link List}{@code <}{@link Clause}{@code >}.
     * @param generated a {@code boolean}; if {@code true}, the path 
     *        condition was generated 
     * @return a {@link String} representation of {@code pathCondition}.
     * @throws NullPointerException if {@code pathCondition == null}.
     */
    private static final String stringifyPathCondition(List<Clause> pathCondition, boolean generated) {
        final StringBuilder retVal = new StringBuilder();
        for (int i = 0; i < pathCondition.size(); ++i) {
            if (i > 0) {
                retVal.append(" && ");
            }
            final Clause c = pathCondition.get(i);
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
                if (generated && i == pathCondition.size() - 1) {
                    retVal.append("subclass of ");
                    retVal.append(((ClauseAssumeExpands) c).getReference().getStaticType());
                } else {
                	retVal.append(((ClauseAssumeExpands) c).getObjekt().getType().getClassName());
                }
            } else if (c instanceof ClauseAssumeExpandsSubtypes) {
                retVal.append(((ClauseAssumeExpandsSubtypes) c).getReference().asOriginString());
                retVal.append(" fresh subclass of ");
                retVal.append(className(((ClauseAssumeExpandsSubtypes) c).getReference().getStaticType()));
                if (!((ClauseAssumeExpandsSubtypes) c).forbiddenExpansions().isEmpty()) {
                    retVal.append(" excluded ");
                    retVal.append(((ClauseAssumeExpandsSubtypes) c).forbiddenExpansions().stream().collect(Collectors.joining(", ")));
                }
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
    
    public static String stringifyTestPathCondition(List<Clause> pathCondition) {
    	return stringifyPathCondition(shorten(pathCondition), false);
    }

    public static String stringifyPostFrontierPathCondition(List<Clause> pathCondition) {
    	return stringifyPathCondition(shorten(pathCondition), true);
    }

	public static String stringifyPostFrontierPathCondition(JBSEResult item) {
	    final List<Clause> pathCondition = item.getPathConditionGenerated();
	    final String retVal = (pathCondition == null ? "true" : stringifyPostFrontierPathCondition(pathCondition));
	    return retVal;
	}
        
    public static Set<String> filterOnPattern(Set<String> toFilter, String pattern) {
        final Pattern p = Pattern.compile(pattern); 
        final HashSet<String> retVal = toFilter.stream().filter(s -> { final Matcher m = p.matcher(s); return m.matches(); }).collect(Collectors.toCollection(HashSet::new));
        return retVal;
    }
    
    /**
     * Do not instantiate!
     */
    private Util() { }
}
