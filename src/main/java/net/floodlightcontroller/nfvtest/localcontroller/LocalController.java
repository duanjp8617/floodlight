package net.floodlightcontroller.nfvtest.localcontroller;

import org.zeromq.ZMQ.Socket;

import net.floodlightcontroller.nfvtest.message.ConcreteMessage.*;
import net.floodlightcontroller.nfvtest.nfvcorestructure.NFVTest;
import net.floodlightcontroller.nfvtest.nfvslaveservice.ServiceChainHandler;
import net.floodlightcontroller.nfvtest.message.MessageHub;

import org.zeromq.ZMQ.Context;

import java.util.ArrayList;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;

public class LocalController implements Runnable{
	
	private String globalIp; //global controller IP
	private int publishPort; //global controller publish port
	private int syncPort;    //global controller replier port
	private int pullPort;    //global controller poll port
	private int repPort;     //rep port that is used to reply to ping message
	private int pcscfPort;   //pcscfPort that is used to communicate with pcscf(traffic generator)
	
	private String localIp;  //local controller IP
	
	private Context context;
	private Socket subscriber;
	private Socket pusher;
	private Socket statPuller;
	
	private Socket pcscfPuller;
	private HashMap<String, Socket> pcscfPusherMap;
	
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
	
	private final Logger logger =  LoggerFactory.getLogger(LocalController.class);
	
	private String entryIp;
	
	private DpTrafficPuller dpTrafficPuller;
	
	//all the following 3 maps are indexed using src address, which will never be changed through
	//out the service chain path.
	private HashMap<String, Integer> entryFlowDstDcMap;   //contains the exit datacenter for the entry flow
	private HashMap<String, String>  exitFLowDstAddrMap;  //contains the destination address for exit flow
	private HashMap<String, String>  exitFlowSrcIpMap;    //contains the src IP address for exit flow
	
	private ServiceChainHandler      chainHandler;
	
	private ArrayList<Integer> cpStatArray;
	
	public LocalController(String globalIp, int publishPort, int syncPort, int pullPort, int repPort, int pcscfPort,
			String localIp, boolean hasScscf, int delayPollInterval, int dpCapacity[], MessageHub mh, Context context,
			String entryIp, ServiceChainHandler chainHandler){
		this.globalIp = globalIp;
		this.publishPort = publishPort;
		this.syncPort = syncPort;
		this.pullPort = pullPort;
		this.repPort = repPort;
		this.pcscfPort = pcscfPort;
		
		this.localIp = localIp;
		
		this.context = null;
		this.subscriber = null;
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
		
		this.entryIp = entryIp;
		
		this.pcscfPusherMap = new HashMap<String, Socket>();
		
		this.entryFlowDstDcMap  = new HashMap<String, Integer>();
		this.exitFLowDstAddrMap = new HashMap<String, String>();
		this.exitFlowSrcIpMap   = new HashMap<String, String>(); 
		this.chainHandler = chainHandler;
		
		this.dpTrafficPuller = new DpTrafficPuller(2000, context);
		
		this.cpStatArray = new ArrayList<Integer>();
	}
	
	public int getCurrentDcIndex(){
		return localcIndexMap.get(localIp).intValue();
	}
	
	@Override
	public void run() {
		
		logger.info("start local controller on IP: "+localIp);
		logger.info("connecting to globalc ontroller on IP: "+globalIp);
		
		subscriber = context.socket(ZMQ.SUB);
		subscriber.connect("tcp://"+globalIp+":"+Integer.toString(publishPort));
		subscriber.subscribe("".getBytes());
		
		pusher = context.socket(ZMQ.PUSH);
		pusher.connect("tcp://"+globalIp+":"+Integer.toString(pullPort));
		
		statPuller = context.socket(ZMQ.PULL);
		statPuller.bind("inproc://statPull");
		
		schSync = context.socket(ZMQ.REP);
		schSync.bind("inproc://schSync");
		
		pcscfPuller = context.socket(ZMQ.PULL);
		pcscfPuller.bind("tcp://"+localIp+":"+Integer.toString(pcscfPort));
		
		//send to global controller necessary information for sync
		//JOIN -> IP of local controller -> whether has SCSCF servers 
		Socket requester = context.socket(ZMQ.REQ);
		requester.connect("tcp://"+globalIp+":"+Integer.toString(syncPort));
		
		requester.send("JOIN", ZMQ.SNDMORE);
		requester.send(localIp, ZMQ.SNDMORE);
		if(hasScscf==true){
			requester.send("HASSCSCF", ZMQ.SNDMORE);
		}
		else{
			requester.send("NOSCSCF", ZMQ.SNDMORE);
		}
		requester.send(entryIp, 0);
		
		requester.recv(0);
		requester.close();
		
		logger.info("local controller connects to global controller, waiting for final configuration"+"\n");
		
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
		Socket replier = context.socket(ZMQ.REP);
		replier.bind("tcp://"+localIp+":"+Integer.toString(repPort));
		measureDelay = new MeasureDelay(localIp, Integer.toString(repPort), replier, 2000, localcIndexMap, context);
		measureDelay.init();
		Thread mdThread = new Thread(measureDelay);
		mdThread.start();
		
		statCollector = new StatCollector(context, localIp, 5000, 5001, 2000, 5000, this.hasScscf);
		Thread scThread = new Thread(statCollector);
		scThread.start();
		
		int srcIndex = localcIndexMap.get(localIp).intValue();
		int dcNum = localcIndexMap.size();
		mh.sendTo("chainHandler", new LocalControllerNotification("lc", srcIndex, dcNum));
		
		mh.sendTo("chainHandler", new CreateInterDcTunnelMash("chainHandler", localIp, 400, localcIndexMap));
		schSync.recvStr();
		schSync.send("", 0);
		
		Thread dpTrafficPullerThread = new Thread(dpTrafficPuller);
		dpTrafficPullerThread.start();
		for(int i=0; i<localcIndexMap.size(); i++){
			cpStatArray.add(0);
		}
		localLoop();
	}
	
	private void localLoop(){
		ZMQ.Poller items = new ZMQ.Poller (3);
		items.register(subscriber,  ZMQ.Poller.POLLIN);
		items.register(statPuller,  ZMQ.Poller.POLLIN);
		items.register(schSync,     ZMQ.Poller.POLLIN);
		items.register(pcscfPuller, ZMQ.Poller.POLLIN);
		
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
			
			if(items.pollin(3)){
				processPcscfPuller();
			}
			
			//It's time to check for delay and report delay.
			if((System.currentTimeMillis()-delayPollTime)>delayPollInterval){
				processDelayPoll();
				processCpStatPoll(delayPollInterval);
				delayPollTime = System.currentTimeMillis();
			}
		}
	}
	
	private void processPcscfPuller(){
		String initMsg = pcscfPuller.recvStr();
		if(initMsg.equals("PCSCFCONNECT")){
			System.out.println("Receive connection request from pcscf");
			String pcscfAddr = pcscfPuller.recvStr();
			String splitResult[] = pcscfAddr.split("\\s+");
			String pcscfIp = splitResult[0];
			String pcscfPort = splitResult[1];
			
			Socket newPcscfPusher = context.socket(ZMQ.PUSH);
			newPcscfPusher.connect("tcp://"+pcscfIp+":"+pcscfPort);
			
			newPcscfPusher.send("OK", 0);
			
			this.pcscfPusherMap.put(pcscfIp+":"+pcscfPort, newPcscfPusher);
		}
		else if(initMsg.equals("OPEN")){
			String pcscfIpPort      = pcscfPuller.recvStr();
			
			String entryFlowSrcAddr = pcscfPuller.recvStr();
			
			String split[] = entryFlowSrcAddr.split(":");
			String entryMinorFlowSrcAddr = split[0]+":"+new Integer(Integer.parseInt(split[1])+1).toString();
			
			String entryFlowDstDc   = pcscfPuller.recvStr();
			
			String exitFlowSrcAddr  = pcscfPuller.recvStr();
			
			split = exitFlowSrcAddr.split(":");
			String exitMinorFlowSrcAddr = split[0]+":"+new Integer(Integer.parseInt(split[1])+1).toString();
			
			String exitFlowDstAddr  = pcscfPuller.recvStr();
			
			split = exitFlowDstAddr.split(":");
			String exitMinorFlowDstAddr = split[0]+":"+new Integer(Integer.parseInt(split[1])+1).toString();
			
			String exitFlowSrcIp    = pcscfPuller.recvStr();
			
			System.out.println("entryFlowSrcAddr: "+entryFlowSrcAddr+" entryFlowDstDc: "+entryFlowDstDc
					+" exitFlowSrcAddr: "+exitFlowSrcAddr+" exitFlowDstAddr"+exitFlowDstAddr
					+" exitFlowSrcIp "+exitFlowSrcIp);
			
			System.out.println("entryMinorFlowSrcAddr: "+entryMinorFlowSrcAddr+" entryFlowDstDc: "+entryFlowDstDc
					+" exitMinorFlowSrcAddr: "+exitMinorFlowSrcAddr+" exitMinorFlowDstAddr"+exitMinorFlowDstAddr
					+" exitFlowSrcIp "+exitFlowSrcIp);
			
			synchronized(this){
				this.entryFlowDstDcMap.put(entryFlowSrcAddr, Integer.parseInt(entryFlowDstDc));
				this.entryFlowDstDcMap.put(entryMinorFlowSrcAddr, Integer.parseInt(entryFlowDstDc));
				
				this.exitFLowDstAddrMap.put(exitFlowSrcAddr, exitFlowDstAddr);
				this.exitFLowDstAddrMap.put(exitMinorFlowSrcAddr, exitMinorFlowDstAddr);
				
				this.exitFlowSrcIpMap.put(exitFlowSrcAddr, exitFlowSrcIp);
				this.exitFlowSrcIpMap.put(exitMinorFlowSrcAddr, exitFlowSrcIp);
			}
			
			Socket socket = this.pcscfPusherMap.get(pcscfIpPort);
			socket.send("REPLY", ZMQ.SNDMORE);
			socket.send(entryFlowSrcAddr,ZMQ.SNDMORE);
			String regIp = chainHandler.getRegIp();
			socket.send(regIp, 0);
		}
		else if(initMsg.equals("CLOSE")){
			String pcscfIpPort      = pcscfPuller.recvStr();
			String entryFlowSrcAddr = pcscfPuller.recvStr();
			String exitFlowSrcAddr  = pcscfPuller.recvStr();
			
			String split[] = entryFlowSrcAddr.split(":");
			String entryMinorFlowSrcAddr = split[0]+":"+new Integer(Integer.parseInt(split[1])+1).toString();
			
			split = exitFlowSrcAddr.split(":");
			String exitMinorFlowSrcAddr = split[0]+":"+new Integer(Integer.parseInt(split[1])+1).toString();
			
			synchronized(this){
				this.entryFlowDstDcMap.remove(entryFlowSrcAddr);
				this.entryFlowDstDcMap.remove(entryMinorFlowSrcAddr);
				
				this.exitFLowDstAddrMap.remove(exitFlowSrcAddr);
				this.exitFLowDstAddrMap.remove(exitMinorFlowSrcAddr);
				
				this.exitFlowSrcIpMap.remove(exitFlowSrcAddr);
				this.exitFlowSrcIpMap.remove(exitMinorFlowSrcAddr);
			}
			
			Socket socket = this.pcscfPusherMap.get(pcscfIpPort);
			socket.send("REPLY", ZMQ.SNDMORE);
			socket.send(entryFlowSrcAddr, ZMQ.SNDMORE);
			socket.send(" ", 0);
		}
		else{
			String statMat = pcscfPuller.recvStr();
			String[] statArray = statMat.split("\\s+");
			for(int i=0; i<statArray.length; i++){
				int val = cpStatArray.get(i);
				cpStatArray.set(i, val+Integer.parseInt(statArray[i]));
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
			
			String print = "Data plane service chain configuration: ";
			for(int i=0; i<dpProvision.length; i++){
				print = print + " " + Integer.toString(dpProvision[i]);
			}
			logger.info("{}", print);
			print = "Control plane service chain configuration: ";
			for(int i=0; i<cpProvision.length; i++){
				print = print + " " + Integer.toString(cpProvision[i]);
			}
			logger.info("{}", print);
		}
	}
	
	private void processSubscriber(){
		String initMsg = subscriber.recvStr();
		//logger.info(initMsg);
		
		if(curState.equals("initial")){
			//This is the starting point of a proactive scaling interval
			//local controller receives SCALINGSTART message broadcasted from
			//global controller. local controller will then report its 
			//local service chain configuration back to the global controller.
			//Then transform its state into 
			
			//disable reactive scaling for both control plane and data plane service chain
			
			logger.info("receive proactive start message, send local configuration\n");
			
			assert initMsg.equals("SCALINGSTART");
			boolean hasMore = subscriber.hasReceiveMore();
			assert hasMore == false;
			
			this.mh.sendTo("chainHandler", new ProactiveScalingStartRequest("lc"));
		}
		else if(curState.equals("waitNewConfigPath")){
			logger.info("receives new configuration, start executing proactive scaling decision");
			
			ArrayList<String> list = new ArrayList<String>();
			boolean hasMore = true;
			while(hasMore){
				String result = subscriber.recvStr();
				//logger.info(result);
				if(!result.equals("HEHE")){
					list.add(result);
				}
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
					cpProvision[i][j] = Integer.parseInt(list.get(cpStart+2*i+j));
				}
			}		
			for(int i=0; i<dcNum; i++){
				for(int j=0; j<dpCapacity.length; j++){
					dpProvision[i][j] = Integer.parseInt(list.get(dpStart+dpCapacity.length*i+j));
				}
			}
			for(int i=0; i<dcNum; i++){
				for(int j=0; j<dcNum; j++){
					for(int k=0; k<dpCapacity.length; k++){
						dpPaths[i][j][k] = Integer.parseInt(list.get(dpPathStart+dcNum*dpCapacity.length*i+
								dpCapacity.length*j+k));
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
			logger.info("all local controller have finish proactive scaling, enter new proactive scaling interval");
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
				pusher.send(Integer.toString(delay[i]), ZMQ.SNDMORE);
			}
			else{
				pusher.send(Integer.toString(delay[i]), ZMQ.SNDMORE);
			}
		}
		
		pusher.send("", 0);
		
		String print = "delay stats: ";
		for(int i=0; i<delay.length; i++){
			print = print+" "+Integer.toString(delay[i]);
		}
		//logger.info("{}", print);
	}
	
	private void processStatPuller(){
		String whichPlane = statPuller.recvStr();
		String interval = statPuller.recvStr();
		String statMat = statPuller.recvStr();
		
		if(whichPlane.equals("DATA")){
			processDpStatPoll(interval, statMat);
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
		
		String print = "data plane traffic stat for dc "+Integer.toString(srcIndex)+": ";
		if(statArray.length != localcIndexMap.size()){
			for(int i=0; i<localcIndexMap.size(); i++){
				print = print+" 0";
			}
		}
		else{
			for(int i=0; i<localcIndexMap.size(); i++){
				print = print+" "+statArray[i];
			}
		}
		logger.info("{}", print);
	}
	
	private void processCpStatPoll(int interval){
		
		String statArray[] = new String[cpStatArray.size()];
		for(int i=0; i<cpStatArray.size(); i++){
			statArray[i] = cpStatArray.get(i).toString();
			cpStatArray.set(i, 0);
		}
		int srcIndex = localcIndexMap.get(localIp);
		sendCpStat(srcIndex, interval, statArray, 0, statArray.length );

	}
	
	private void sendCpStat(int srcIndex, int interval, String[] statArray, int start, int end){		
		pusher.send("STATREPORT", ZMQ.SNDMORE);
		pusher.send("CONTROL", ZMQ.SNDMORE);
		pusher.send(Integer.toString(srcIndex), ZMQ.SNDMORE);
		pusher.send(Integer.toString(interval), ZMQ.SNDMORE);
		
		for(int i=start; i<end; i++){
			if(i == end-1){
				pusher.send(statArray[i], 0);
			}
			else{
				pusher.send(statArray[i], ZMQ.SNDMORE);
			}
		}
		
		String print = "control plane traffic stat for dc "+Integer.toString(srcIndex)+": ";
		for(int i=start; i<end; i++){
			print = print+" "+statArray[i];
		}
		logger.info("{}", print);
	}
	
	public int[] getSrcDstPair(String srcAddr){
		int srcDst[] = new int[2];
		synchronized(this){
			System.out.println("the entryFlow addr is:"+srcAddr);
			int dstDcIndex = entryFlowDstDcMap.get(srcAddr).intValue();
			int srcDcIndex = localcIndexMap.get(localIp).intValue();
			srcDst[0] = srcDcIndex;
			srcDst[1] = dstDcIndex;
		}
		return srcDst;
	}
	
	public String getExitFlowDstAddr(String exitFlowSrcAddr){
		String returnVal = null;
		synchronized(this){
			returnVal = new String(this.exitFLowDstAddrMap.get(exitFlowSrcAddr));
		}
		return returnVal;
	}
	
	public String getExitFlowSrcIp(String exitFlowSrcAddr){
		String returnVal = null;
		synchronized(this){
			returnVal = new String(this.exitFlowSrcIpMap.get(exitFlowSrcAddr));
		}
		return returnVal;
	}
}
