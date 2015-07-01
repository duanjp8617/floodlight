package net.floodlightcontroller.nfvtest;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Map;

public class MasterServiceChain implements Runnable{
	private final Map<String, ConcurrentLinkedQueue<Message>> messageQueueMap;
	private final Map<String, SlaveServiceChain> slaveMap;
	private final NFVServiceChainStorage serviceChainStorage;
	
	private boolean exitFlag;
	
	MasterServiceChain(NFVServiceChainStorage s) {
		this.serviceChainStorage = s;
		messageQueueMap = new HashMap<String, ConcurrentLinkedQueue<Message>>();
		slaveMap = new HashMap<String, SlaveServiceChain>();
		exitFlag = false;
	}
	
	public void register(SlaveServiceChain slave){
		slaveMap.put(slave.getId(), slave);
		ConcurrentLinkedQueue<Message> messageQueue = new ConcurrentLinkedQueue<Message>();
		messageQueueMap.put(slave.getId(), messageQueue);
		slave.setMaster(this);
	}
	
	public ConcurrentLinkedQueue<Message> getMessageQueueForSlave(String id){
		return messageQueueMap.get(id);
	}

	@Override
	public void run() {
		while(!getExitFlag()){
			for(String key : messageQueueMap.keySet()){
				ConcurrentLinkedQueue<Message> messageQueue = messageQueueMap.get(key);
				SlaveServiceChain slave = slaveMap.get(key);
				slave.processMessagesSentToMaster(messageQueue);
			}
			//Then add some method for the NFVServiceChainStorage.s
			pollFlowStatusFromStorage();
		}
		
	}
	
	private void pollFlowStatusFromStorage(){
		@SuppressWarnings("unused")
		List<NFVNode> deletedNode = serviceChainStorage.pollFlowStatus();
		//do something with deletedNode, sumit the work to the worker thread.
	}
	
	public boolean getExitFlag(){
		boolean flag;
		synchronized(this){
			flag = exitFlag;
		}
		return flag;
	}
	
	public void setExitFlag(boolean flag){
		synchronized(this){
			exitFlag = flag;
		}
	}

	public NFVServiceChainStorage getServiceChainStorage() {
		return serviceChainStorage;
	}
	
}
