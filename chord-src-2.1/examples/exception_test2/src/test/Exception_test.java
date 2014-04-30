package test;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;

class Dummy_Socket {
	public int return_status;

	public Dummy_Socket() {
		return_status = 1;
	}
	
	int send_internal() throws IOException {
		throw new EOFException ("EOF exception thrown");
	}

	public int send () {
		try {
			send_internal();
		} catch  (IOException e) {
			// TODO
			System.out.println("send caught socket timeout exception" + e);
		}
		return return_status;
	}
}

/* 
 * This example is to test how Chord models the exception control-flow. 
 */
public class Exception_test {
	private static Dummy_Socket socket;

	public static void main(String[] args) {
		int result = 0;
		socket = new Dummy_Socket();
		
		for (int i = 0; i < 10; i++) {	
			result = socket.send();
		}  		
		return;
	}
}
