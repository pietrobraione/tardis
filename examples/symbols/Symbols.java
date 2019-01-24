/*package symbols;

import java.util.LinkedList;

public class Symbols {
	public String m(String s, double b1) {
		if (b1 >= 0.0) {
			return "ciao";
		}
		if (s.equals("prova")) {
			return "prova";
		}
		return null;
	}

	public String m(LinkedList<String> l, double b1) {
		if (b1 >= 0.0) {
			return "ciao";
		}
		if (l.toString().equals("prova")) {
			return "prova";
		}
		return null;
	}
}
*/
package symbols;

import java.util.LinkedList;

public class Symbols {

	public String m(String a, String b, double b1) {

	
			if (b1 >= 0.0) {
				return "ciao";
			} else {
			//	Object c = new Object();
				// String z=c.toString();
				String z = b.toString();
				if (z.equals(a)) {
					double a1 = 5 + b1;

					if (a1 + 6 < 2) {
						return "a";
					} else
						return "b";
				}

				else {
					if (a.equals(b)) {
						double a3 = 3 - b1;
						if (a3 - 3 <= 4) {
							return "b";
						} else {
							return "f";
						}
					} else {
						double a4 = 1 + b1;
						if (a4 + 2 <= 1) {
							return "c";
						} else {
							return "d";
						}

					}
				}
			}

	}
}