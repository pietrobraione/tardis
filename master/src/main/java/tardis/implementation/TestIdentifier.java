package tardis.implementation;

public class TestIdentifier {
	private int testCount;
	
	public TestIdentifier(int start) {
		this.testCount = start;
	}
	
	public void testCountAdd(int amount) {
		this.testCount += amount;
	}
	public int getTestCount() {
		return this.testCount;
	}
}
