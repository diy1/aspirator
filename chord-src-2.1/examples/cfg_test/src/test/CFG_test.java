package test;
import test.Logger;

class Dummy_Socket {
	public int return_status;

	public Dummy_Socket() {
		return_status = 1;
	}

	public void send (int sock, String message) {
		System.out.println("socket send: socket number: " + sock
				    + ", message: " + message);
	}

	public int test_dataflow(int a, int b, int c) {
		int l1, l2;
		l1 = a + b;
		l2 = c + 1;
		l1 += l2;
		l1 += return_status;
		return l1;
	}
}

public class CFG_test {
	private static Logger log;
	private static Dummy_Socket socket;

	public static void main(String[] args) {
		// global_int = a + 1;
		log = new Logger(5);
		socket = new Dummy_Socket();
		
		for (int i = 0; i < 10; i++) { 
			socket.send(i, "test string" + socket.test_dataflow(i, 1, 1));
			if (socket.return_status != 0) {
				log.info();
			}
		}
		// log.debug();
		// System.out.println("Hello, " + a);
	}
}
