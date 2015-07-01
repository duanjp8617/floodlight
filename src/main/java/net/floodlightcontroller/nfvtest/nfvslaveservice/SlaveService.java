package net.floodlightcontroller.nfvtest.nfvslaveservice;

import net.floodlightcontroller.nfvtest.nfvmasterservice.MasterService;
import net.floodlightcontroller.nfvtest.nfvutils.Message;

import java.lang.String;
import java.util.concurrent.ConcurrentLinkedQueue;

public abstract class SlaveService{
	private String id;
	private ConcurrentLinkedQueue<Message> messageQueue;
	private MasterService master;
	
	abstract public void processMessagesSentToMaster(ConcurrentLinkedQueue<Message> queue);
	abstract protected void processMessagesReceivedFromMaster();
	
	public void setMaster(MasterService master){
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