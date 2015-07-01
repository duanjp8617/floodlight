package net.floodlightcontroller.nfvtest;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Map;

public class MasterServiceChain implements Runnable{
	private final Map<String, ConcurrentLinkedQueue<Message>> queueMap;
	private final Map<String, IQueueProcessor> processorMap;
	private final NFVServiceChainStorage serviceChainStorage;
	
	private boolean exitFlag;
	
	MasterServiceChain(NFVServiceChainStorage s) {
		this.serviceChainStorage = s;
		queueMap = new HashMap<String, ConcurrentLinkedQueue<Message>>();
		processorMap = new HashMap<String, IQueueProcessor>();
		exitFlag = false;
	}
	
	public ConcurrentLinkedQueue<Message> register(String id, IQueueProcessor queueProcessor){
		ConcurrentLinkedQueue<Message> q = new ConcurrentLinkedQueue<Message>();
		queueMap.put(id, q);
		processorMap.put(id, queueProcessor);
		return q;
	}

	@Override
	public void run() {
		while(!getExitFlag()){
			for(String key : queueMap.keySet()){
				ConcurrentLinkedQueue<Message> queue = queueMap.get(key);
				IQueueProcessor processor = processorMap.get(key);
				processor.processQueue(queue);
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
