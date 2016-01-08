package net.floodlightcontroller.nfvtest.localcontroller;

import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQ.Context;

import java.util.ArrayList;
import java.util.HashMap;

import org.zeromq.ZMQ;


public class StatCollector implements Runnable {
	private Context context;
	private String bindIp;
	
	private Socket cpStatRecv;
	private int cpStatRecvPort;
	
	private ArrayList<Integer> cpStat;
	private long previousPushTime;
	private long cpPushInterval;
	
	private Socket tfcGenListener;
	private int tfcGenPort;
	
	private ArrayList<String> ipList;
	private HashMap<String, String> ipPortMap;
	
	private Socket dpStatQuery;
	
	private ArrayList<Integer> dpStat;
	private long dpPollInterval;
	private long previousPollTime;
	private boolean pollStart;
	private int pollIndex;
	private int totalPollNum;
	
	private Socket statPush;
	private boolean hasScscf;

	
	private boolean quit;
	private synchronized boolean getQuit(){
		return this.quit;
	}
	
	public StatCollector(Context context, String bindIp, int cpStatRecvPort, int tfcGenPort, long dpPollInterval,
			long cpPushInterval, boolean hasScscf){
		this.context = context;
		this.cpStatRecvPort = cpStatRecvPort;
		this.bindIp = bindIp;
		this.tfcGenPort = tfcGenPort;
		this.hasScscf = hasScscf;
		
		this.quit = false;
		
		this.ipList = new ArrayList<String>();
		this.ipPortMap = new HashMap<String, String>();
		
		this.cpStat = new ArrayList<Integer>();
		this.previousPushTime = 0;
		this.cpPushInterval = cpPushInterval;
		
		this.dpPollInterval = dpPollInterval;
		this.previousPollTime = 0;
		this.pollStart = false;
		this.pollIndex = 0;
		this.totalPollNum = 0;
		this.dpStat = new ArrayList<Integer>();
	}
	
	@Override
	public void run() {
		//cpStatRecv is used to receive control plane stat report from scscf servers.
		cpStatRecv = context.socket(ZMQ.PULL);
		cpStatRecv.bind("tcp://"+bindIp+":"+Integer.toString(cpStatRecvPort));
		
		//dpStatQuery is used to query data plane stat from openvswitch.
		//in current implementation, we query stat from traffic generator.
		dpStatQuery = context.socket(ZMQ.REQ);
		
		//tfcGenListener listens for traffic generators
		tfcGenListener = context.socket(ZMQ.REP);
		tfcGenListener.bind("tcp://"+bindIp+":"+Integer.toString(tfcGenPort));
		
		//statPush is used to push collected stat to the main local controller thread
		statPush = context.socket(ZMQ.PUSH);
		statPush.connect("inproc://statPull");
		
		ZMQ.Poller items = new ZMQ.Poller(3);
		items.register(cpStatRecv, ZMQ.Poller.POLLIN);
		items.register(tfcGenListener, ZMQ.Poller.POLLIN);
		items.register(dpStatQuery, ZMQ.Poller.POLLIN);
		
		previousPushTime = System.currentTimeMillis();
		previousPollTime = System.currentTimeMillis();
		pollStart = false;
		
		while(getQuit()!=true){
			items.poll(100);
			
			//process control plane stats report from scscf servers
			if(items.pollin(0)){
				processCpStatRecv();
			}
			
			//process traffic generator ip and port
			if(items.pollin(1)){
				processTfcGenListener();
			}
			
			//process data plane stats queried 
			if(items.pollin(2)){
				processDpStatQuery();
			}
			
			//push control plane stat to the main thread every cpPushInterval milliseconds
			if((System.currentTimeMillis() - previousPushTime)>cpPushInterval){
				pushCpStat();
			}
			
			//poll data plane stat, and then push it to main thread every dpPollIntervval milliseconds
			if(((System.currentTimeMillis() - previousPollTime)>dpPollInterval)&&(pollStart==false)){
				pollDpStat();
			}
		}
		
		cpStatRecv.close();
		statPush.close();
		tfcGenListener.close();
		dpStatQuery.close();
	}
	
	//traffic generator will sends its ip and port to local controller
	//local controller connects to ip:port to query data plane stat
	private void processTfcGenListener(){
		String tfcGenIp = tfcGenListener.recvStr();
		String tfcGenPort = tfcGenListener.recvStr();
		
		ipList.add(tfcGenIp);
		ipPortMap.put(tfcGenIp, tfcGenPort);
		
		tfcGenListener.send("", 0);
	}
	
	//scscf servers (in this implementation the global traffic generator controller)
	// will connects to cpStatRecv and push control plane stats regularly to this local controller.
	//the stat contains aggregated number of transactions between different datacenters
	//the local controller will aggregate the stat for cpPushInterval
	private void processCpStatRecv(){
		String cpStatMat = cpStatRecv.recvStr();
		
		//cpStatMat contains whitespace separated cp stat. The unit is transaction.
		String[] statArray = cpStatMat.split("\\s+");
		
		for(int i=0; i<statArray.length; i++){
			if(i >= cpStat.size()){
				//current index equals the length of cpStat, we can safely add this number
				//to cpStat
				cpStat.add(new Integer(Integer.parseInt(statArray[i])));
			}
			else{
				int curVal = cpStat.get(i).intValue();
				int newVal = Integer.parseInt(statArray[i]);
				cpStat.set(i, new Integer(curVal+newVal));
			}
		}
	}
	
	//every cpPushInterval, this function will be called
	//what we do in this function is to push everything we aggregated during the previous
	//cpPushInterval to the main thread, as long with the cpPushInterval.
	//the main thread will handle everyhing
	//don't forget to clear the cpStat ArrayList
	private void pushCpStat(){
		if(hasScscf){
			statPush.send("CONTROL", ZMQ.SNDMORE);
			
			statPush.send(Long.toString(cpPushInterval), ZMQ.SNDMORE);
			
			String cpStatMat = "";
			for(int i=0; i<cpStat.size(); i++){
				cpStatMat = cpStatMat + cpStat.get(i).toString() + " ";
			}
			statPush.send(cpStatMat, 0);
			
			cpStat.clear();
		}
		previousPushTime = System.currentTimeMillis();
	}
	
	//every dpPollInterval, this function will be called
	//what we do in this function is that we connect to the traffic generator 
	//one by one, and poll the data plane stat.
	private void pollDpStat(){
		if(ipList.size()>0){
			pollStart = true;
			pollIndex = 0;
			totalPollNum = ipList.size();
			
			String ip = ipList.get(pollIndex);
			String port = ipPortMap.get(ip);
			dpStatQuery.connect("tcp://"+ip+":"+port);
			dpStatQuery.send("POLL");
		}
	}
	
	//receive data plane stat collected from traffic generator
	//if all traffic generators have answered, push the result to 
	//main thread
	private void processDpStatQuery(){
		String dpStatMat = dpStatQuery.recvStr();
		String[] statArray = dpStatMat.split("\\s+");
		
		for(int i=0; i<statArray.length; i++){
			if(i>=dpStat.size()){
				dpStat.add(new Integer(Integer.parseInt(statArray[i])));
			}
			else{
				int curVal = dpStat.get(i).intValue();
				int newVal = Integer.parseInt(statArray[i]);
				dpStat.set(i, new Integer(curVal+newVal));
			}
		}
		
		String ip = ipList.get(pollIndex);
		String port = ipPortMap.get(ip);
		dpStatQuery.disconnect("tcp://"+ip+":"+port);
		
		pollIndex += 1;
		if(pollIndex<totalPollNum){
			ip = ipList.get(pollIndex);
			port = ipPortMap.get(ip);
			dpStatQuery.connect("tcp://"+ip+":"+port);
			dpStatQuery.send("POLL");
		}
		else{
			statPush.send("DATA", ZMQ.SNDMORE);
			statPush.send(Long.toString(dpPollInterval), ZMQ.SNDMORE);
			
			dpStatMat = "";
			for(int i=0; i<dpStat.size(); i++){
				dpStatMat = dpStatMat + dpStat.get(i).toString() + " ";
			}
			statPush.send(dpStatMat, 0);
			
			dpStat.clear();
			pollStart = false;
			previousPollTime = System.currentTimeMillis();
		}
	}
}
