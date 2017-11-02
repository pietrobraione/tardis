package exec;

public class TestIdentifier {
	private int testCount;
	
	public TestIdentifier() {
		this.testCount = 0;
	}
	
	public void testCountAdd(int amount) {
		this.testCount += amount;
	}
	public int getTestCount() {
		return this.testCount;
	}
}
