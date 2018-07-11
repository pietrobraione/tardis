package symbols;

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