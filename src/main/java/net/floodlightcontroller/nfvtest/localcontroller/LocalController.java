package localcontroller;

import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQ.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import org.zeromq.ZMQ;

public class LocalController {
	
	private String globalIp; //global controller IP
	private int publishPort; //global controller publish port
	private int syncPort;    //global controller replier port
	private int pullPort;    //global controller poll port
	
	private String localIp;  //local controller IP
	
	private Context context;
	private Socket subscriber;
	private Socket requester;
	private Socket pusher;
	
	private boolean hasScscf;
	private HashMap<String, Integer> localcIndexMap;
	
	private int delayPollInterval;
	private int statPollInterval;
	
	private String curState;
	
	private int dpCapacity[];
	private MeasureDelay measDelay;
	
	public LocalController(String globalIp, int publishPort, int syncPort, int pullPort,
			String localIp, int subPort, int reqPort, int pushPort, boolean hasScscf,
			int delayPollInterval, int statPollInterval, int dpCapacity[]){
		this.globalIp = globalIp;
		this.publishPort = publishPort;
		this.syncPort = syncPort;
		this.pullPort = pullPort;
		
		this.localIp = localIp;
		
		this.context = null;
		this.subscriber = null;
		this.requester = null;
		this.pusher = null;
		
		this.hasScscf = hasScscf;
		this.localcIndexMap = new HashMap<String, Integer>();
		
		this.delayPollInterval = delayPollInterval;
		this.statPollInterval = statPollInterval;
		
		this.curState = "initial";
		this.dpCapacity = dpCapacity;
		
		
	}
	
	public void start(){
		System.out.println("start local controller on IP: "+localIp+"\n");
		System.out.println("connecting to globalc ontroller on IP: "+globalIp+"\n");
		
		context = ZMQ.context(1);
		subscriber = context.socket(ZMQ.SUB);
		subscriber.connect("tcp://"+globalIp+":"+Integer.toString(publishPort));
		subscriber.subscribe("".getBytes());
		
		pusher = context.socket(ZMQ.PUSH);
		pusher.connect("tcp://"+globalIp+":"+Integer.toString(pullPort));
		
		requester = context.socket(ZMQ.REQ);
		requester.connect("tcp://"+globalIp+":"+Integer.toString(syncPort));
		
		//send to global controller necessary information for sync
		//JOIN -> IP of local controller -> whether has SCSCF servers 
		requester.send("JOIN", ZMQ.SNDMORE);
		requester.send(localIp, ZMQ.SNDMORE);
		if(hasScscf==true){
			requester.send("HASSCSCF", 0);
		}
		else{
			requester.send("NOSCSCF", 0);
		}
		requester.recv(0);
		
		System.out.println("local controller connects to global controller, waiting for final configuration"+"\n");
		
		//wait for the final sync message from global controller
		//global controller will broadcast all the local controller IPs and their corresponding indexes.
		//save this information in a map
		String initMsg = subscriber.recvStr();
		assert initMsg.equals("SYNC");
		boolean hasMore = true;
		while(hasMore){
			String ip = subscriber.recvStr();
			String index = subscriber.recvStr();
			localcIndexMap.put(ip, new Integer(Integer.parseInt(index)));
			System.out.println("learns a local controller at IP: "+ip+" with index "+index+"\n");
			hasMore = subscriber.hasReceiveMore();
		}
		
		//initialize measure here
		//The measure class will measure delay, control plane stat
		//if the local controller owns scscf, measure will also measure control plane stat
		//The measurement result are acquired through regular polling the measure class
		this.measDelay = new MeasureDelay(localIp, delayPollInterval/2, localcIndexMap);
		Thread th = new Thread(this.measDelay);
		th.start();
		
		localLoop();
	}
	
	private void localLoop(){
		ZMQ.Poller items = new ZMQ.Poller (1);
		items.register(subscriber, ZMQ.Poller.POLLIN);
		
		long delayPollTime = System.currentTimeMillis();
		long statPollTime = System.currentTimeMillis();
		
		
		while (!Thread.currentThread ().isInterrupted ()) {
			items.poll(100);
			
			//process broadcast messages from global controller
			//we maintain a state to check whether we behave correctly
			if (items.pollin(0)) {
				processSubscriber();
			}
			
			//It's time to check for delay and report delay.
			if((System.currentTimeMillis()-delayPollTime)>delayPollInterval){
				processDelayPoll();
				delayPollTime = System.currentTimeMillis();
			}
			
			//It's time to check traffic stats and report traffic stats
			if((System.currentTimeMillis()-statPollTime)>statPollInterval){
				processStatPoll();
				statPollTime = System.currentTimeMillis();
			}
		}
	}
	
	private void processSubscriber(){
		String initMsg = subscriber.recvStr();
		Random rn = new Random();
		
		if(curState.equals("initial")){
			//This is the starting point of a proactive scaling interval
			//local controller receives SCALINGSTART message broadcasted from
			//global controller. local controller will then report its 
			//local service chain configuration back to the global controller.
			//Then transform its state into 
			
			assert initMsg.equals("SCALINGSTART");
			boolean hasMore = subscriber.hasReceiveMore();
			assert hasMore == false;
			
			//send data plane service chain configuration
			pusher.send("LOCCONFIG", ZMQ.SNDMORE);
			pusher.send("DATA", ZMQ.SNDMORE);
			pusher.send(localcIndexMap.get(localIp).toString(), ZMQ.SNDMORE);
			
			for(int i=0; i<dpCapacity.length;i++){
				if(i==dpCapacity.length-1){
					pusher.send(Integer.toString(rn.nextInt(10)+5), 0);
				}
				else{
					pusher.send(Integer.toString(rn.nextInt(10)+5), ZMQ.SNDMORE);
				}
			}
			
			//send control plane service chain configuration
			pusher.send("LOCCONFIG", ZMQ.SNDMORE);
			pusher.send("CONTROL", ZMQ.SNDMORE);
			pusher.send(localcIndexMap.get(localIp).toString(), ZMQ.SNDMORE);
			
			pusher.send(Integer.toString(rn.nextInt(10)+5), ZMQ.SNDMORE);
			pusher.send(Integer.toString(rn.nextInt(10)+5), 0);
			
			curState = "waitNewConfigPath";
		}
		else if(curState.equals("waitNewConfigPath")){
			ArrayList<String> list = new ArrayList<String>();
			boolean hasMore = true;
			while(hasMore){
				String result = subscriber.recvStr();
				list.add(result);
				hasMore = subscriber.hasReceiveMore();
			}
			
			//Here we decode the received array
			int cpStart = 0;
			int cpEnd = localcIndexMap.size()*2-1;
			int dpStart = cpEnd+1;
			int dpEnd = dpStart+localcIndexMap.size()*dpCapacity.length-1;
			int dpPathStart = dpEnd+1;
			//int dpPathEnd = dpPathStart+localcIndexMap.size()*localcIndexMap.size()*dpCapacity.length-1;
			
			//print and see if we are correct
			int dcNum = localcIndexMap.size();
			
			System.out.println("control plane provision:\n");
			if(localcIndexMap.get(localIp).intValue() == 0){
				for(int i=0; i<dcNum; i++){
					for(int j=0; j<2; j++){
						System.out.println(list.get(cpStart+dcNum*i+j)+" ");
					}
					System.out.print("*******************\n");
				}
			}
			
			System.out.println("data plane provision: \n");
			if(localcIndexMap.get(localIp).intValue() == 0){
				for(int i=0; i<dcNum; i++){
					for(int j=0; j<dpCapacity.length; j++){
						System.out.println(list.get(dpStart+dcNum*i+j)+" ");
					}
					System.out.print("*******************\n");
				}
			}
			
			System.out.println("data plane paths: \n");
			if(localcIndexMap.get(localIp).intValue() == 0){
				for(int i=0; i<dcNum; i++){
					for(int j=0; j<dcNum; j++){
						for(int k=0; k<dpCapacity.length; k++){
							System.out.println(list.get(dpPathStart+dcNum*i+j)+" ");
						}
						System.out.println(" | ");
					}
					System.out.print("*******************\n");
				}
			}
			
			//Do a quick reply to the global controller
			pusher.send("PROACTIVEFINISH", 0);
			curState = "waitNewInterval";
		}
		else if(curState.equals("waitNewInterval")){
			System.out.println("local controller at local IP: "+localIp+" with index: "+
		localcIndexMap.get(localIp)+"finish proactive scaling");
			curState = "initial";
		}
	}
	
	private void processDelayPoll(){
		int srcIndex = localcIndexMap.get(localIp).intValue();
		
		int delay[] = measDelay.getDelay();
		if (delay == null || delay.length == 0)
		{
			return;
		}
		
		pusher.send("DELAY", ZMQ.SNDMORE);
		pusher.send(Integer.toString(srcIndex), ZMQ.SNDMORE);
		
		for(int i=0; i<delay.length; i++)
		{
			if (i == delay.length -1)
			{
				pusher.send(Integer.toString(delay[i]), 0);
			}
			else
			{
				pusher.send(Integer.toString(delay[i]), ZMQ.SNDMORE);
			}
		}

	}
	
	private void processStatPoll(){
		int srcIndex = localcIndexMap.get(localIp).intValue();
		Random rn = new Random();
		
		pusher.send("STATREPORT", ZMQ.SNDMORE);
		pusher.send(Integer.toString(srcIndex), ZMQ.SNDMORE);
		pusher.send(Integer.toString(10), ZMQ.SNDMORE);
		
		for(int i=0; i<localcIndexMap.size(); i++){
			if(i==localcIndexMap.size()-1){
				pusher.send(Integer.toString(rn.nextInt(10)+5), 0);
			}
			else{
				pusher.send(Integer.toString(rn.nextInt(10)+5), ZMQ.SNDMORE);
			}
		}
	}
}
