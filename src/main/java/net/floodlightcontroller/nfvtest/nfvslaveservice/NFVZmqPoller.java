package net.floodlightcontroller.nfvtest.nfvslaveservice;

import net.floodlightcontroller.nfvtest.message.ConcreteMessage.StatUpdateRequest;
import net.floodlightcontroller.nfvtest.message.MessageHub;
import net.floodlightcontroller.nfvtest.nfvutils.Pair;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.Set;

import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ;


public class NFVZmqPoller implements Runnable{
	private final LinkedList<Pair<String, Socket>> registerQueue;
	private final LinkedList<String> unregisterQueue;
	private final LinkedHashMap<String, Socket> socketMap;
	private final MessageHub mh;

	
	public NFVZmqPoller(MessageHub mh){
		this.registerQueue = new LinkedList<Pair<String, Socket>>();
		this.unregisterQueue = new LinkedList<String>();
		this.socketMap = new LinkedHashMap<String, Socket>();
		this.mh = mh;
	}
	
	public synchronized void register(Pair<String, Socket> ipSocketPair){
		this.registerQueue.add(ipSocketPair);
	}
	
	public synchronized void unregister(String managementIp){
		this.unregisterQueue.add(managementIp);
	}
	
	
	@Override
	public void run() {
		// TODO Auto-generated method stub
		while(true){
			synchronized(this){
				while(!this.registerQueue.isEmpty()){
					Pair<String, Socket> socketPair = this.registerQueue.removeFirst();
					this.socketMap.put(socketPair.first, socketPair.second);
				}
				while(!this.unregisterQueue.isEmpty()){
					String managementIp = this.unregisterQueue.removeFirst();
					if(this.socketMap.containsKey(managementIp)){
						this.socketMap.remove(managementIp);
					}
				}
			}
			
			Poller poller = new Poller (this.socketMap.size());
			Set<Entry<String, Socket>> socketSet = this.socketMap.entrySet();
			Iterator<Entry<String, Socket>> i = socketSet.iterator();
			
			while(i.hasNext()){
				Entry<String, Socket> entry = i.next();
				poller.register(entry.getValue(), ZMQ.Poller.POLLIN);
			}
			
			poller.poll(100);
			
			int socketIndex = 0;
			i = socketSet.iterator();
			while(i.hasNext()){
				Entry<String, Socket> entry = i.next();
				if(poller.pollin(socketIndex)){
					Socket socket = poller.getSocket(socketIndex);
					handlePollIn(entry.getKey(),socket);
				}
				socketIndex+=1;
			}
		}
	}
	
	private void handlePollIn(String managementIp,Socket socket){
		ArrayList<String> strList = new ArrayList<String>();
		boolean hasMore = true;
		while(hasMore){
			String result = socket.recvStr();
			strList.add(result);
			hasMore = socket.hasReceiveMore();
		}
		StatUpdateRequest request = new StatUpdateRequest("hehe", managementIp, strList);
		this.mh.sendTo("chainHandler", request);
	}
	
}
