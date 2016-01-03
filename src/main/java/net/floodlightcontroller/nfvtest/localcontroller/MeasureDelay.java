package net.floodlightcontroller.nfvtest.localcontroller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.zeromq.ZMQ;

public class MeasureDelay implements Runnable
{
	public class Replier implements Runnable
	{
		private ZMQ.Socket replier;
		private boolean bool = true;
		
		Replier(ZMQ.Socket replier)
		{
			this.replier = replier;
		}
		
		public void setRun(boolean bool)
		{
			this.bool = bool;
		}
		
		private void exit()
		{
			replier.close();
		}
		
		@Override
		public void run()
		{
			// TODO Auto-generated method stub
			while (bool)
			{
				replier.recv();
				replier.send("OK");
			}
			
			this.exit();
			
		}
	}
	
	private int id;
	private String ip;
	private String repPort;
	private int interval;
	private Map<String, Integer> map;
	private static Map<Integer, Integer> delay;
	
	private HashMap<String, ZMQ.Socket> requesterMap;
	private ZMQ.Context zmqContext;
	private ZMQ.Socket replySocket;
	
	private Replier replier;
	
	private volatile boolean bool;
	private static List<Map.Entry<Integer,Integer>> list;
	
	MeasureDelay(String ip, String repPort, ZMQ.Socket replySocket, int interval, Map<String, Integer> map, ZMQ.Context context)
	{
		this.ip = ip;
		this.id = map.get(this.ip).intValue();
		this.repPort = repPort;
		this.interval = interval;
		this.map = map;
		delay = new HashMap<Integer, Integer>();
		this.bool = true;
		this.zmqContext = context;
		this.requesterMap = new HashMap<String, ZMQ.Socket>();
		this.replySocket= replySocket;
	}
	
	public void init()
	{
	    this.replier = new Replier(this.replySocket);
		Thread th = new Thread(this.replier);
		th.start();
		
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		for(String key : this.map.keySet()){
			int dstIndex = this.map.get(key).intValue();
			if(this.id<dstIndex){
				ZMQ.Socket requester = this.zmqContext.socket(ZMQ.REQ);
				requester.connect("tcp://"+key+":"+repPort);
				
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				requesterMap.put(key, requester);
			}
		}
	}
	
	private void exit()
	{
		replier.setRun(false);
		for(String key : requesterMap.keySet()){
			requesterMap.get(key).close();
		}
	}
	
	public void setRun(boolean bool)
	{
		this.bool = bool;
	}
	
	private void measure()
	{
		delay.clear();
		for(String key : requesterMap.keySet()){
			ZMQ.Socket requester = requesterMap.get(key);
			
			long startTime = System.nanoTime();
			requester.send("Hello");
			requester.recv();
			int consumingTime = (int) ((System.nanoTime() - startTime)/1000);
			
			delay.put(this.map.get(key).intValue(), consumingTime);
		}
		sortDelay();
		
	}

	private static void sortDelay()
	{
		//delay: first integer is index, second integer is delay
		list = new ArrayList<Map.Entry<Integer,Integer>>(delay.entrySet());
        Collections.sort(list,new Comparator<Map.Entry<Integer,Integer>>() 
        {
            public int compare(Entry<Integer, Integer> o1, Entry<Integer, Integer> o2) 
            {
                return o1.getKey().compareTo(o2.getKey());
            }
        });
		
	}
	public synchronized int[] getDelay()
	{
		int array[] = new int[delay.size()];
		
        int i = 0;
        for(Map.Entry<Integer,Integer> mapping:list)
        { 
            array[i++] = mapping.getValue(); 
       } 
        
		return array;	
	}
	
	
	@Override
	public void run()
	{
		// TODO Auto-generated method stub
		while (bool)
		{
			synchronized(this)
			{
				measure();
			}
			try
			{
				Thread.sleep(this.interval);
			} catch (InterruptedException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		this.exit();
	}

}
