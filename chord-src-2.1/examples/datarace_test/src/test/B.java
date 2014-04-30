package test;

public class B {
	private A bf;
	public B() {
		A a = new A();
		this.bf = a;
	}
	public int get() {
		A a = this.bf;
		return a.get();
	}
	public void set(int i) {
		A a = this.bf;
		a.set(i);
	}
}
