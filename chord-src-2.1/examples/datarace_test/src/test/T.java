package test;

public class T extends java.lang.Thread {
    public static void main(String[] a) {
        B b1 = new B();
        B b2 = new B();
        for (int i = 0; i < 10; i++) {
            T t = new T(b1, b2);
            t.start();
        }
        for (int i = 0; i < 10; i++) {
            b1.get();
            synchronized (b2) {
                b2.set(i);
            }
        }
    }
    private B f1, f2;
    public T(B b1, B b2) {
        this.f1 = b1;
        this.f2 = b2;
    }
    public void run() {
        B b1 = this.f1;
        B b2 = this.f2;
        int i;
        synchronized (b2) {
            i = b2.get();
        }
        b1.set(i);
    }
}
