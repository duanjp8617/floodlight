package net.floodlightcontroller.nfvtest.nfvmasterservice;

import net.floodlightcontroller.nfvtest.nfvcorestructure.*;
import net.floodlightcontroller.nfvtest.nfvutils.*;
import net.floodlightcontroller.nfvtest.nfvslaveservice.SlaveService;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Map;

import org.zeromq.*;

public class MasterService implements Runnable{
	
	public class ServiceThread implements Runnable{
		private final SlaveService slave;
		private final MasterService master;
		private boolean exitFlag;
		ServiceThread(MasterService master, SlaveService slave){
			this.master = master;
			this.slave = slave;
			exitFlag = false;
		}
		
		@Override
		public void run(){
			while(!getExitFlag()){
				ConcurrentLinkedQueue<Message> msgQ = master.getMessageQueueForSlave(slave.getId());
				slave.processMessagesSentToMaster(msgQ);
			}
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
	}
	
	private final String id="master";
	private final Map<String, ConcurrentLinkedQueue<Message>> messageQueueMap;
	private final Map<String, SlaveService> slaveMap;
	private final NFVServiceChainStorage serviceChainStorage;
	
	private boolean exitFlag;
	
	MasterService(NFVServiceChainStorage s) {
		this.serviceChainStorage = s;
		messageQueueMap = new HashMap<String, ConcurrentLinkedQueue<Message>>();
		slaveMap = new HashMap<String, SlaveService>();
		exitFlag = false;
	}
	
	public void register(SlaveService slave){
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
				SlaveService slave = slaveMap.get(key);
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
