package exec;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jbse.bc.Opcodes;

class Util {
	/**
	 * Checks whether a bytecode is a jump bytecode.
	 * 
	 * @param currentBytecode a {@code byte}.
	 * @return {@code true} iff {@code currentBytecode} jumps.
	 */
	static boolean atJump(byte currentBytecode) {
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
	 * @param onlyPublic {@code true} to restrict the list to the public methods of the class.
	 * @return a {@link List}{@code <}{@link List}{@code <}{@link String}{@code >>} of the methods
	 *         of the class {@code o.}{@link Options#getTargetClass() getTargetClass()} that are not private, nor synthetic, nor one of the 
	 *         {@code equals}, {@code hashCode}, {@code toString}, {@code clone}, {@code immutableEnumSet}.
	 *         If {@code onlyPublic == true} only the public methods are returned. Each {@link List}{@code <}{@link String}{@code >}
	 *         has three elements and is a method signature.
	 * @throws ClassNotFoundException if the class is not in {@code o.}{@link Options#getClassesPath() getClassesPath()}.
	 * @throws SecurityException 
	 * @throws MalformedURLException if some path in {@code o.}{@link Options#getClassesPath() getClassesPath()} does not exist.
	 */
	static List<List<String>> getVisibleTargetMethods(Options o, boolean onlyPublic) 
	throws ClassNotFoundException, MalformedURLException, SecurityException {
		final String className = o.getTargetClass();
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
