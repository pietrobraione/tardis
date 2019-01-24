package symbols;


public class Prova2 {
	private double b1, b2;
	public boolean m(double a1, double a2) {
		
		b1 = Math.sqrt(5.0);
		b2 = Math.sqrt(a1);
		if(a2 > b1) {
			return true;
		}
		if (a2 > b2) {
			return true;
		}
		return false;
	}
	
}
