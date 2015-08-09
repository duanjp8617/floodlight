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
	
	
	public SubscriberConnector(String id){
		this.id = id;
		this.queue = new LinkedBlockingQueue<Message>();
		this.zmqContext = ZMQ.context(1);
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
			
			Socket subscriber = zmqContext.socket(ZMQ.SUB);
			subscriber.monitor("inproc://monitor"+ipAddress, ZMQ.EVENT_CONNECTED);
			
			Socket monitor = zmqContext.socket(ZMQ.PAIR);
			monitor.setReceiveTimeOut(100);
			monitor.connect("inproc://monitor"+ipAddress);
			ZMQ.Event event;
			
			//retry 10 times
			for(int i=0; i<10; i++){
				subscriber.connect("tcp://"+ipAddress+":"+port);
	        	event = ZMQ.Event.recv(monitor);
	        	
	        	if((event != null)&&(event.getEvent() == ZMQ.EVENT_CONNECTED)){
	        		subscriber.subscribe("".getBytes());
	        		
	        		SubConnReply reply = new SubConnReply("hehe", this.request, subscriber);
	        		mh.sendTo(this.request.getSourceId(), reply);
	        		
	        		break;
	        	}
	        	else{
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
