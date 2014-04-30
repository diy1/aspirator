package test;
import test.Logger;

public class HelloWorld2 {
	private static Logger log;

	public static void main(String[] args) {
		// global_int = a + 1;
		log = new Logger(5);
		log.info();
		log.debug();
		// System.out.println("Hello, " + a);
	}
}
