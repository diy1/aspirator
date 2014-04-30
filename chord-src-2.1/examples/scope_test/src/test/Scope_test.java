package test;
import test.Logger;

class Dummy_c {
	private int a;
	private Dummy_c(int b) {
		a = b;
		final Logger log = Logger.newLogger(5);
		log.info(b);
	}
	static Dummy_c newDummy(int b) {
		final Dummy_c dummy = new Dummy_c(b);
		return dummy;
	}
}

/* The goal of this example is to be able to write an analysis to track
   the data-flow of the variable "result". Assume the input:
 
   var-name@scope
   output: last modification point. 
 
 */
public class Scope_test {
	public static void main(String[] args) {
		Dummy_c.newDummy(1);	
	}
}
