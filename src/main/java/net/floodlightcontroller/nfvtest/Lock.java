package net.floodlightcontroller.nfvtest;

/*A non-reentrant lock implementation used to protect the 
 * shared service chain storage.
 */
public class Lock{
	
	private boolean isLocked = false;

	public synchronized void lock()
			throws InterruptedException{
		while(isLocked){
			wait();
	    }
	    isLocked = true;
	}

	public synchronized void unlock(){
	    isLocked = false;
	    notify();
	}
}
