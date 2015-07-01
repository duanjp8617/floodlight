package net.floodlightcontroller.nfvtest;

import java.lang.String;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class SlaveServiceChain{
	private String id;
	private ConcurrentLinkedQueue<Message> messageQueue;
	private MasterServiceChain master;
	
	abstract public void processMessagesSentToMaster(ConcurrentLinkedQueue<Message> queue);
	abstract protected void processMessagesReceivedFromMaster();
	
	public void setMaster(MasterServiceChain master){
		master = this.master;
	}
	
	public void insertMessageToSlave(Message message){
		messageQueue.add(message);
	}
	
	public String getId(){
		return id;
	}
	
	@SuppressWarnings("unused")
	private void insertMessageToMaster(Message message){
		ConcurrentLinkedQueue<Message> masterMessageQueue = this.master.getMessageQueueForSlave(this.id);
		masterMessageQueue.add(message);
	}
}