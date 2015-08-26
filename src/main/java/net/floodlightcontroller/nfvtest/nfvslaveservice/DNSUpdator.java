package net.floodlightcontroller.nfvtest.nfvslaveservice;

import java.util.concurrent.LinkedBlockingQueue;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

import net.floodlightcontroller.nfvtest.message.Message;
import net.floodlightcontroller.nfvtest.message.ConcreteMessage.*;
import net.floodlightcontroller.nfvtest.message.MessageProcessor;
import net.floodlightcontroller.nfvtest.message.ConcreteMessage.KillSelfRequest;

public class DNSUpdator extends MessageProcessor{
	
	private final String dnsIp;
	private final String dnsPort;
	private final Context zmqContext;
	private Socket requester;
	
	public DNSUpdator(String id, String dnsIp, String dnsPort, Context zmqContext){
		this.id = id;
		this.queue = new LinkedBlockingQueue<Message>();
		
		this.dnsIp = dnsIp;
		this.dnsPort = dnsPort;
		this.zmqContext = zmqContext;
	}
	
	public void connect(){
		this.requester = this.zmqContext.socket(ZMQ.REQ);
		requester.monitor("inproc://monitorDNS", ZMQ.EVENT_CONNECTED);
		
		Socket monitor = zmqContext.socket(ZMQ.PAIR);
		monitor.setReceiveTimeOut(10000);
		monitor.connect("inproc://monitorDNS");
		ZMQ.Event event;
	
		requester.connect("tcp://"+this.dnsIp+":"+this.dnsPort);
    	event = ZMQ.Event.recv(monitor);
    	
    	if((event != null)&&(event.getEvent() == ZMQ.EVENT_CONNECTED)){
    		monitor.close();
    		return;
    	}
    	else{
    		System.out.println("DNS connection failure");
    		while(true){
    		}
    	}
	}
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
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
		if(m instanceof DNSUpdateRequest){
			DNSUpdateRequest req = (DNSUpdateRequest)m;
			updateDNS(req);
		}
	}
	
	private void updateDNS(DNSUpdateRequest request){
		this.requester.send(request.getAddOrDelete(), ZMQ.SNDMORE);
		this.requester.send(request.getDomainName(), ZMQ.SNDMORE);
		this.requester.send(request.getIpAddress(), 0);
		String recvResult = requester.recvStr();
		
		DNSUpdateReply reply = new DNSUpdateReply(this.getId(), request);
		this.mh.sendTo(request.getSourceId(), reply);
	}
	
}
