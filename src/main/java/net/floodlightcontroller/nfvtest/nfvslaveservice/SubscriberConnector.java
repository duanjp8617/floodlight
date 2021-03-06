package net.floodlightcontroller.nfvtest.nfvslaveservice;

import java.util.concurrent.LinkedBlockingQueue;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

import net.floodlightcontroller.nfvtest.message.Message;
import net.floodlightcontroller.nfvtest.message.MessageHub;
import net.floodlightcontroller.nfvtest.message.MessageProcessor;
import net.floodlightcontroller.nfvtest.message.ConcreteMessage.KillSelfRequest;

import net.floodlightcontroller.nfvtest.message.ConcreteMessage.*;

public class SubscriberConnector extends MessageProcessor{
	private final Context zmqContext;
	
	
	public SubscriberConnector(String id, Context zmqContext){
		this.id = id;
		this.queue = new LinkedBlockingQueue<Message>();
		this.zmqContext = zmqContext;
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
		if(m instanceof SubConnRequest){
			SubConnRequest request = (SubConnRequest)m;
			subscriberConnect(request);
		}
	}
	
	public class SubConnThread implements Runnable{
		private final SubConnRequest request;
		private final Context zmqContext;
		private final MessageHub mh;
		
		public SubConnThread(SubConnRequest request, Context zmqContext, MessageHub mh){
			this.request = request;
			this.zmqContext = zmqContext;
			this.mh = mh;
		}
		
		@Override
		public void run() {
			// TODO Auto-generated method stub
			try{
				Thread.sleep(5000);
			}
			catch (Exception e){
				e.printStackTrace();
			}
			
			String ipAddress = this.request.getManagementIp();
			
			String port1 = this.request.getPort1();
			String port2 = this.request.getPort2();
			Socket subscriber1 = null;
				
			for(int i=0; i<1000; i++){
				subscriber1 = zmqContext.socket(ZMQ.SUB);
				subscriber1.monitor("inproc://monitor"+ipAddress, ZMQ.EVENT_CONNECTED);
			
				Socket monitor = zmqContext.socket(ZMQ.PAIR);
				monitor.setReceiveTimeOut(5000);
				monitor.connect("inproc://monitor"+ipAddress);
				ZMQ.Event event;
			
				subscriber1.connect("tcp://"+ipAddress+":"+port1);
	        	event = ZMQ.Event.recv(monitor);
	        	
	        	if((event != null)&&(event.getEvent() == ZMQ.EVENT_CONNECTED)){
	        		subscriber1.subscribe("".getBytes());
	        		break;
	        	}
	        	else{
	        		monitor.close();
	        		subscriber1.close();
	        	}
			}
			
			if(this.request.getVmInstance().serviceChainConfig.nVmInterface == 3){
				SubConnReply reply = new SubConnReply("hehe", this.request, subscriber1, null);
        		mh.sendTo(this.request.getSourceId(), reply);
        		return;
			}
			
			Socket subscriber2 = null;
			for(int i=0; i<1000; i++){
				subscriber2 = zmqContext.socket(ZMQ.SUB);
				subscriber2.monitor("inproc://monitor"+ipAddress, ZMQ.EVENT_CONNECTED);
			
				Socket monitor = zmqContext.socket(ZMQ.PAIR);
				monitor.setReceiveTimeOut(10000);
				monitor.connect("inproc://monitor"+ipAddress);
				ZMQ.Event event;
			
				subscriber2.connect("tcp://"+ipAddress+":"+port2);
	        	event = ZMQ.Event.recv(monitor);
	        	
	        	if((event != null)&&(event.getEvent() == ZMQ.EVENT_CONNECTED)){
	        		subscriber2.subscribe("".getBytes());  		
	        		break;
	        	}
	        	else{
	        		monitor.close();
	        		subscriber2.close();
	        	}
			}
			
			SubConnReply reply = new SubConnReply("hehe", this.request, subscriber1, subscriber2);
    		mh.sendTo(this.request.getSourceId(), reply);
    		return;
		}
	}
	
	private void subscriberConnect(SubConnRequest request){
		SubConnThread newThread = new SubConnThread(request, this.zmqContext, this.mh);
		Thread t = new Thread(newThread);
		t.start();
	}
	
}
