package io.github.lucasbaizer.kjc;

import java.util.Scanner;

public class Main {
	public static void main(String[] args) {
		Scanner scanner = new Scanner(System.in);

		System.out.print("Enter the lobby code: ");
		int lobby = Integer.parseInt(scanner.nextLine());

		System.out.print("Enter your username: ");
		String name = scanner.nextLine();

		try {
			new KahootClient(lobby, name);
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
		} finally {
			scanner.close();
		}
	}
}
