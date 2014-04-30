package test;

public class Logger {
	private int global_int;

	private Logger(int a) {
		global_int = a;
		System.out.println("Logger constructor with a = " + a);
	}
	
	static Logger newLogger(int a) {
		final Logger logger = new Logger(a);
		return logger;
	}

	public void info(int result) {
		global_int++; 
		System.out.println("info " + result);
		return;
	}
}
