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
			String port = this.request.getPort();
			
			for(int i=0; i<1000; i++){
				Socket subscriber = zmqContext.socket(ZMQ.SUB);
				subscriber.monitor("inproc://monitor"+ipAddress, ZMQ.EVENT_CONNECTED);
			
				Socket monitor = zmqContext.socket(ZMQ.PAIR);
				monitor.setReceiveTimeOut(10000);
				monitor.connect("inproc://monitor"+ipAddress);
				ZMQ.Event event;
			
				subscriber.connect("tcp://"+ipAddress+":"+port);
	        	event = ZMQ.Event.recv(monitor);
	        	
	        	if((event != null)&&(event.getEvent() == ZMQ.EVENT_CONNECTED)){
	        		subscriber.subscribe("".getBytes());
	        		
	        		SubConnReply reply = new SubConnReply("hehe", this.request, subscriber);
	        		mh.sendTo(this.request.getSourceId(), reply);
	        		
	        		break;
	        	}
	        	else{
	        		monitor.close();
	        		subscriber.close();
	        	}
			}
		}
	}
	
	private void subscriberConnect(SubConnRequest request){
		SubConnThread newThread = new SubConnThread(request, this.zmqContext, this.mh);
		Thread t = new Thread(newThread);
		t.start();
	}
	
}
