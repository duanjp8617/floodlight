package localcontroller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZStar.Set;

public class MeasureDelay implements Runnable
{
	private final int id;
	private final String ip;
	private final int interval;
	private final Map<String, Integer> map;
	private static Map<Integer, Integer> delay;
	
	private ZMQ.Socket requester;
	private ZMQ.Context zmqContext;
	
	private Replier replier;
	
	private boolean bool;
	private static List<Map.Entry<Integer,Integer>> list;
	
	MeasureDelay(String ip, int interval, Map<String, Integer> map)
	{
		this.ip = ip;
		this.id = map.get(ip).intValue();
		this.interval = interval;
		this.map = map;
		delay = new HashMap<Integer, Integer>();
		this.bool = true;

		init();
	}
	
	private void init()
	{
		//create socket to reply other local controllers
		zmqContext = ZMQ.context(1);
	    replier = new Replier(zmqContext);
		Thread th = new Thread(replier);
		th.start();
	}
	
	private void exit()
	{
		replier.setRun(false);
		zmqContext.term();
	}
	
	public void setRun(boolean bool)
	{
		this.bool = bool;
	}
	
	private void measure()
	{
		delay.clear();
		Set set = (Set) map.keySet();
		Iterator it = ((ArrayList) set).iterator();
		while(it.hasNext())
		{
			String remoteIp = (String) it.next();
			int index = map.get(remoteIp).intValue();

			if (index <= this.id)
			{
				continue;
			}
			
			
			requester = zmqContext.socket(ZMQ.REQ);
			requester.monitor("inproc://monitorServerConnection", ZMQ.EVENT_CONNECTED);
			
			Socket monitor = zmqContext.socket(ZMQ.PAIR);
			monitor.setReceiveTimeOut(5000);
			monitor.connect("inproc://monitorServerConnection");
			
			requester.connect("tcp://"+ remoteIp + ":6000");
			
			while(ZMQ.Event.recv(monitor) == null || ZMQ.Event.recv(monitor).getEvent() != ZMQ.EVENT_CONNECTED)
			{
				requester.connect("tcp://"+ remoteIp + ":6000");
				requester.close();
			}
			monitor.close();
			
			//start to measure
			long startTime = System.nanoTime();
			requester.send("Hello!");
			requester.recv();
			int consumingTime = (int) ((System.nanoTime() - startTime)/1000);
			
			delay.put(index, consumingTime);
			
			requester.close();
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
