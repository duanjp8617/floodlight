package net.floodlightcontroller.nfvtest.nfvslaveservice;

import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
	private final Logger logger =  LoggerFactory.getLogger(SubscriberConnector.class);
	
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
		private final Logger logger;
		
		public SubConnThread(SubConnRequest request, Context zmqContext, MessageHub mh, Logger logger){
			this.request = request;
			this.zmqContext = zmqContext;
			this.mh = mh;
			this.logger = logger;
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
			//String port2 = this.request.getPort2();
			Socket subscriber1 = null;
			
			logger.info("trying to connect subscriber1 for node "+ipAddress);
			
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
			
			logger.info("finish connecting subscriber1 for node "+ipAddress);
	
			SubConnReply reply = new SubConnReply("hehe", this.request, subscriber1, null);
    		mh.sendTo(this.request.getSourceId(), reply);
    		return;
		}
	}
	
	private void subscriberConnect(SubConnRequest request){
		SubConnThread newThread = new SubConnThread(request, this.zmqContext, this.mh, this.logger);
		Thread t = new Thread(newThread);
		t.start();
	}
	
}