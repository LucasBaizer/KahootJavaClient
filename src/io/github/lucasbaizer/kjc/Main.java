package io.github.lucasbaizer.kjc;

public class Main {
	public static void main(String[] args) {
		try {
			KahootClient client = new KahootClient(3828407, "Jacob");
		} catch (KahootException e) {
			System.out.println("Error with client: " + e.getMessage());
			if (e.getCause() != null) {
				System.out.println("Cause: ");
				e.printStackTrace(System.out);
			}
			System.exit(1);
		} catch (Exception e) {
			e.printStackTrace(System.out);
			System.exit(1);
		}
	}
}
