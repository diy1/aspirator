package test;
import test.Logger;

class Dummy_Socket {
	public int return_status;

	public Dummy_Socket() {
		return_status = 1;
	}

	public int send (int sock, String message) {
		System.out.println("socket send: socket number: " + sock
				    + ", message: " + message);
		return sock;	
	}
}

/* The goal of this example is to be able to write an analysis to track
   the data-flow of the variable "result". Assume the input:
 
   var-name@scope
   output: last modification point. 
 
 */
public class DFlow_test {
	private static Logger log;
	private static Dummy_Socket socket;

	public static void main(String[] args) {
		int result = 0;
		log = new Logger(5);
		socket = new Dummy_Socket();
		
		for (int i = 0; i < 5; i++) {
			result = socket.send(i, "test string");
			if (result != 0) {
				log.info(result);
			}
		}
	}
}
