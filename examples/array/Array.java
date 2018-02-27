package array;

public class Array {
	public boolean getBool(int[] array, int a, int b, int c) {
		int k = 0;

		if (a == 0) {
			if (b == 0) {
				if (c == 0) {
					k = 1;
				} else {
					k = 0;
				}
			} else {
				k = 0;
			}
		} else {
			k = 0;
		}

		for (int i = 0; i < array.length; i++) {
			if (array[i] < 0) {
				k = 0;
			}
		}

		if (k == 1) {
			return true;
		} else {
			return false;
		}
	}
}
