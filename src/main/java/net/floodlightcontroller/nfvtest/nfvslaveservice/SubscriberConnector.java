package net.floodlightcontroller.nfvtest.nfvslaveservice;

import java.util.concurrent.LinkedBlockingQueue;

import net.floodlightcontroller.nfvtest.message.Message;
import net.floodlightcontroller.nfvtest.message.MessageProcessor;
import net.floodlightcontroller.nfvtest.message.ConcreteMessage.KillSelfRequest;

public class SubscriberConnector extends MessageProcessor{
	
	public SubscriberConnector(String id){
		this.id = id;
		this.queue = new LinkedBlockingQueue<Message>();
	}
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		LinkedBlockingQueue<Message> q = (LinkedBlockingQueue<Message>)this.queue;
		while(true){
			try{
				Message m = q.take();
				if(m instanceof KillSelfRequest){
					break;
				}
				else{
					onReceive(m);
				}
			}
			catch (Exception e){
				e.printStackTrace();
			}			
		}
	}

	@Override
	protected void onReceive(Message m) {
		// TODO Auto-generated method stub
		
	}
	
}
