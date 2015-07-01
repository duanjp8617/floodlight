package net.floodlightcontroller.nfvtest.nfvcorestructure;

import java.lang.RuntimeException;

public class NFVException extends RuntimeException{
	
	private static final long serialVersionUID = 495151789400585135L;

	public NFVException(){
		super();
	}
	
	public NFVException(String msg){
		super(msg);
	}
	
	public NFVException(String s, Throwable t){
		super(s, t);
	}
	
	public NFVException(Throwable t){
		super(t);
	}
}
