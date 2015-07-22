package net.floodlightcontroller.nfvtest.message;

import java.util.Queue;
import net.floodlightcontroller.nfvtest.message.Message;
import net.floodlightcontroller.nfvtest.message.MessageHub;

public abstract class MessageProcessor implements Runnable{
	protected Queue<Message> queue;
	protected MessageHub mh;
	protected String id;
	
	public String getId(){
		return id;
	}
	
	public void registerWithMessageHub(MessageHub mh){
		this.mh = mh;
		this.mh.addToHub(this);
	}
	
	public Queue<Message> obtainQueue(){
		return queue;
	}
	
	abstract protected void onReceive(Message m);
}
