package test;

public class HelloWorld {
	private int global_int;

	private void bar() {
		global_int++;
		System.out.println("Hello, " + global_int);
		return;
	}

	private void foo() {
		bar();
	}

	public static void main(String[] args) {
		int a;
		a = 3;
		// global_int = a + 1;
		foo();
		// System.out.println("Hello, " + a);
	}
}
