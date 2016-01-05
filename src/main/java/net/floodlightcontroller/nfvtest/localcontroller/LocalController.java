package net.floodlightcontroller.nfvtest.localcontroller;

import org.zeromq.ZMQ.Socket;

import net.floodlightcontroller.nfvtest.message.ConcreteMessage.*;
import net.floodlightcontroller.nfvtest.message.MessageHub;

import org.zeromq.ZMQ.Context;

import java.util.ArrayList;
import java.util.HashMap;

import org.zeromq.ZMQ;

public class LocalController implements Runnable{
	
	private String globalIp; //global controller IP
	private int publishPort; //global controller publish port
	private int syncPort;    //global controller replier port
	private int pullPort;    //global controller poll port
	private int repPort;     //rep port that is used to reply to ping message
	
	private String localIp;  //local controller IP
	
	private Context context;
	private Socket subscriber;
	private Socket requester;
	private Socket pusher;
	private Socket statPuller;
	private Socket replier;
	
	private Socket schSync;
	
	private boolean hasScscf;
	private HashMap<String, Integer> localcIndexMap;
	
	private int delayPollInterval;
	
	private String curState;
	
	private int dpCapacity[];
	
	private MeasureDelay measureDelay;
	private StatCollector statCollector;
	
	private MessageHub mh;
	
	private int cpProvision[][];
	private int dpProvision[][];
	private int dpPaths[][][];
	
	private HashMap<String, String> srcAddrDstAddrMap;
	
	public LocalController(String globalIp, int publishPort, int syncPort, int pullPort, int repPort,
			String localIp, boolean hasScscf, int delayPollInterval, int dpCapacity[], MessageHub mh, Context context){
		this.globalIp = globalIp;
		this.publishPort = publishPort;
		this.syncPort = syncPort;
		this.pullPort = pullPort;
		
		this.localIp = localIp;
		
		this.context = null;
		this.subscriber = null;
		this.requester = null;
		this.pusher = null;
		this.schSync = null;
		
		this.hasScscf = hasScscf;
		this.localcIndexMap = new HashMap<String, Integer>();
		
		this.delayPollInterval = delayPollInterval;
		
		this.curState = "initial";
		this.dpCapacity = dpCapacity;
		
		this.measureDelay = null;
		this.statCollector = null;
		
		this.mh = mh;
		this.context = context;
		
		this.srcAddrDstAddrMap = new HashMap<String, String>();
	}
	
	public int getSrcDcIndex(){
		return localcIndexMap.get(localIp).intValue();
	}
	
	@Override
	public void run() {
		System.out.println("start local controller on IP: "+localIp+" \n");
		System.out.println("connecting to globalc ontroller on IP: "+globalIp+"\n");
		
		subscriber = context.socket(ZMQ.SUB);
		subscriber.connect("tcp://"+globalIp+":"+Integer.toString(publishPort));
		subscriber.subscribe("".getBytes());
		
		pusher = context.socket(ZMQ.PUSH);
		pusher.connect("tcp://"+globalIp+":"+Integer.toString(pullPort));
		
		requester = context.socket(ZMQ.REQ);
		requester.connect("tcp://"+globalIp+":"+Integer.toString(syncPort));
		
		statPuller = context.socket(ZMQ.PULL);
		statPuller.bind("inproc://statPull");
		
		replier = context.socket(ZMQ.REP);
		replier.bind("tcp://"+localIp+":"+Integer.toString(repPort));
		
		schSync = context.socket(ZMQ.REP);
		schSync.bind("inproc://schSync");
		
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
		
		cpProvision = new int[localcIndexMap.size()][2];
		dpProvision = new int[localcIndexMap.size()][dpCapacity.length];
		dpPaths = new int[localcIndexMap.size()][localcIndexMap.size()][dpCapacity.length];
		
		//initialize measure here
		//The measure class will measure delay, data plane stat
		//if the local controller owns scscf, measure will also measure control plane stat
		//The measurement result are acquired through regular polling the measure class
		measureDelay = new MeasureDelay(localIp, Integer.toString(repPort), replier, 2000, localcIndexMap, context);
		measureDelay.init();
		Thread mdThread = new Thread(measureDelay);
		mdThread.start();
		
		statCollector = new StatCollector(context, localIp, 5000, 5001, 2000, 5000);
		Thread scThread = new Thread(statCollector);
		scThread.start();
		
		int srcIndex = localcIndexMap.get(localIp).intValue();
		int dcNum = localcIndexMap.size();
		mh.sendTo("chainHandler", new LocalControllerNotification("lc", srcIndex, dcNum));
		
		mh.sendTo("chainHandler", new CreateInterDcTunnelMash("chainHandler", localIp, 400, localcIndexMap));
		schSync.recvStr();
		schSync.send("", 0);
		
		localLoop();
	}
	
	private void localLoop(){
		ZMQ.Poller items = new ZMQ.Poller (3);
		items.register(subscriber, ZMQ.Poller.POLLIN);
		items.register(statPuller, ZMQ.Poller.POLLIN);
		items.register(schSync, ZMQ.Poller.POLLIN);
		
		long delayPollTime = System.currentTimeMillis();

		
		while (!Thread.currentThread ().isInterrupted ()) {
			items.poll(100);
			
			//process broadcast messages from global controller
			//we maintain a state to check whether we behave correctly
			if (items.pollin(0)) {
				processSubscriber();
			}
			
			if(items.pollin(1)) {
				processStatPuller();
			}
			
			if(items.pollin(2)){
				processSchSync();
			}
			
			//It's time to check for delay and report delay.
			if((System.currentTimeMillis()-delayPollTime)>delayPollInterval){
				processDelayPoll();
				delayPollTime = System.currentTimeMillis();
			}
		}
	}
	
	private void processSchSync(){
		//Do a quick reply to the global controller
		String result = schSync.recvStr();
		if(result.equals("COMPLETE")){
			schSync.send("", 0);
			pusher.send("PROACTIVEFINISH", 0);
			curState = "waitNewInterval";
		}
		else{
			String dpProvisionStr = schSync.recvStr();
			String cpProvisionStr = schSync.recvStr();
			schSync.send("", 0);
			
			String dpArray[] = dpProvisionStr.split("\\s+");
			int dpProvision[] = new int[dpArray.length];
			for(int i=0; i<dpProvision.length; i++){
				dpProvision[i] = Integer.parseInt(dpArray[i]);
			}
			
			String cpArray[] = cpProvisionStr.split("\\s+");
			int cpProvision[] = new int[cpArray.length];
			for(int i=0; i<cpProvision.length; i++){
				cpProvision[i] = Integer.parseInt(cpArray[i]);
			}
			
			//send data plane service chain configuration
			pusher.send("LOCCONFIG", ZMQ.SNDMORE);
			pusher.send("DATA", ZMQ.SNDMORE);
			pusher.send(localcIndexMap.get(localIp).toString(), ZMQ.SNDMORE);			
			for(int i=0; i<dpProvision.length;i++){
				if(i==dpProvision.length-1){
					pusher.send(Integer.toString(dpProvision[i]), 0);
				}
				else{
					pusher.send(Integer.toString(dpProvision[i]), ZMQ.SNDMORE);
				}
			}
			
			//send control plane service chain configuration
			pusher.send("LOCCONFIG", ZMQ.SNDMORE);
			pusher.send("CONTROL", ZMQ.SNDMORE);
			pusher.send(localcIndexMap.get(localIp).toString(), ZMQ.SNDMORE);
			pusher.send(Integer.toString(cpProvision[0]), ZMQ.SNDMORE);
			pusher.send(Integer.toString(cpProvision[1]), 0);
			
			curState = "waitNewConfigPath";
		}
	}
	
	private void processSubscriber(){
		String initMsg = subscriber.recvStr();
		
		if(curState.equals("initial")){
			//This is the starting point of a proactive scaling interval
			//local controller receives SCALINGSTART message broadcasted from
			//global controller. local controller will then report its 
			//local service chain configuration back to the global controller.
			//Then transform its state into 
			
			//disable reactive scaling for both control plane and data plane service chain
			
			System.out.println("receive proactive start message, send local configuration\n");
			
			assert initMsg.equals("SCALINGSTART");
			boolean hasMore = subscriber.hasReceiveMore();
			assert hasMore == false;
			
			this.mh.sendTo("chainHandler", new ProactiveScalingStartRequest("lc"));
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
			
			//print and see if we are correct
			int dcNum = localcIndexMap.size();
			for(int i=0; i<dcNum; i++){
				for(int j=0; j<2; j++){
					cpProvision[i][j] = Integer.parseInt(list.get(cpStart+dcNum*i+j));
				}
			}		
			for(int i=0; i<dcNum; i++){
				for(int j=0; j<dpCapacity.length; j++){
					dpProvision[i][j] = Integer.parseInt(list.get(dpStart+dcNum*i+j));
				}
			}
			for(int i=0; i<dcNum; i++){
				for(int j=0; j<dcNum; j++){
					for(int k=0; k<dpCapacity.length; k++){
						dpPaths[i][j][k] = Integer.parseInt(list.get(dpPathStart+dcNum*i+dcNum*j+k));
					}
				}
			}

			int srcIndex = localcIndexMap.get(localIp).intValue();
			int localCpProvision[] = new int[2];
			localCpProvision[0] = cpProvision[srcIndex][0];
			localCpProvision[1] = cpProvision[srcIndex][1];
			
			int localDpProvision[] = new int[dpCapacity.length];
			for(int i=0; i<dpCapacity.length; i++){
				localDpProvision[i] = dpProvision[srcIndex][i];
			}
			
			ProactiveScalingRequest m = new ProactiveScalingRequest("lc", localCpProvision, localDpProvision, dpPaths);
			this.mh.sendTo("chainHandler", m);
		}
		else if(curState.equals("waitNewInterval")){
			System.out.println("local controller at local IP: "+localIp+" with index: "+
		localcIndexMap.get(localIp)+"finish proactive scaling");
			curState = "initial";
			this.mh.sendTo("chainHandler", new NewProactiveIntervalRequest("lc"));
		}
	}
	
	private void processDelayPoll(){
		int delay[] = this.measureDelay.getDelay();
		int srcIndex = localcIndexMap.get(localIp).intValue();
		
		pusher.send("DELAY", ZMQ.SNDMORE);
		pusher.send(Integer.toString(srcIndex), ZMQ.SNDMORE);
		
		for(int i=0; i<delay.length; i++){
			if(i==delay.length-1){
				pusher.send(Integer.toString(delay[i]), 0);
			}
			else{
				pusher.send(Integer.toString(delay[i]), ZMQ.SNDMORE);
			}
		}
		
		pusher.send("", 0);
		
		System.out.println("local controller"+localIp+"sends delay");
	}
	
	private void processStatPuller(){
		String whichPlane = statPuller.recvStr();
		String interval = statPuller.recvStr();
		String statMat = statPuller.recvStr();
		
		if(whichPlane.equals("DATA")){
			processDpStatPoll(interval, statMat);
		}
		else{
			processCpStatPoll(interval, statMat);
		}
	}
	
	private void processDpStatPoll(String interval, String statMat){
		int srcIndex = localcIndexMap.get(localIp).intValue();
		
		pusher.send("STATREPORT", ZMQ.SNDMORE);
		pusher.send("DATA", ZMQ.SNDMORE);
		pusher.send(Integer.toString(srcIndex), ZMQ.SNDMORE);
		pusher.send(interval, ZMQ.SNDMORE);
		
		String statArray[] = statMat.split("\\s+");
		if(statArray.length != localcIndexMap.size()){
			for(int i=0; i<localcIndexMap.size(); i++){
				if(i==localcIndexMap.size()-1){
					pusher.send(Integer.toString(0), 0);
				}
				else{
					pusher.send(Integer.toString(0), ZMQ.SNDMORE);
				}
			}
		}
		else{
			for(int i=0; i<localcIndexMap.size(); i++){
				if(i == localcIndexMap.size()-1){
					pusher.send(statArray[i], 0);
				}
				else{
					pusher.send(statArray[i], ZMQ.SNDMORE);
				}
			}
		}
		System.out.println("local controller"+localIp+"sends data plane stats");
	}
	
	private void processCpStatPoll(String interval, String statMat){
		
		String statArray[] = statMat.split("\\s+");
		if(statArray.length != localcIndexMap.size()*localcIndexMap.size()){
			for(int i=0; i<localcIndexMap.size(); i++){
				sendZero(i, interval);
			}
		}
		else{
			for(int i=0; i<localcIndexMap.size(); i++){
				sendCpStat(i, interval, statArray, i*localcIndexMap.size(), i*localcIndexMap.size()+localcIndexMap.size()-1 );
			}
		}
		System.out.println("local controller"+localIp+"sends control plane stats");
	}
	
	private void sendCpStat(int srcIndex, String interval, String[] statArray, int start, int end){		
		pusher.send("STATREPORT", ZMQ.SNDMORE);
		pusher.send("CONTROL", ZMQ.SNDMORE);
		pusher.send(Integer.toString(srcIndex), ZMQ.SNDMORE);
		pusher.send(interval, ZMQ.SNDMORE);
		
		for(int i=start; i<end; i++){
			if(i == end-1){
				pusher.send(statArray[i], 0);
			}
			else{
				pusher.send(statArray[i], ZMQ.SNDMORE);
			}
		}
	}
	
	private void sendZero(int srcIndex, String interval){
		pusher.send("STATREPORT", ZMQ.SNDMORE);
		pusher.send("CONTROL", ZMQ.SNDMORE);
		pusher.send(Integer.toString(srcIndex), ZMQ.SNDMORE);
		pusher.send(interval, ZMQ.SNDMORE);
		
		for(int i=0; i<localcIndexMap.size(); i++){
			if(i == localcIndexMap.size()-1){
				pusher.send(Integer.toString(0), 0);
			}
			else{
				pusher.send(Integer.toString(0), ZMQ.SNDMORE);
			}
		}
	}
	
	public void addSrcAddrDstAddr(String srcAddr, String dstAddr){
		synchronized(this.srcAddrDstAddrMap){
			this.srcAddrDstAddrMap.put(srcAddr, dstAddr);
		}
	}
	
	public int[] getSrcDcDstDc(String srcAddr){
		int srcDst[] = new int[2];
		synchronized(this.srcAddrDstAddrMap){
			int srcDcIndex = localcIndexMap.get(localIp).intValue();
			int dstDcIndex = 1; //we do some query here;
			srcDst[0] = srcDcIndex;
			srcDst[1] = dstDcIndex;
		}
		
		return srcDst;
	}
	
}
