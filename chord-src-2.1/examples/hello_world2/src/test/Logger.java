package test;

public class Logger {
	private int global_int;

	public Logger(int a) {
		global_int = a;
		System.out.println("Logger constructor with a = " + a);
	}

	private void print(String level) {
		System.out.println("Logger." + level + ", global_int = " + global_int);
	}

	public void info() {
		global_int++; 
		print("info");
		return;
	}

	public void debug() {
		print("debug");
		return;
	}
}
