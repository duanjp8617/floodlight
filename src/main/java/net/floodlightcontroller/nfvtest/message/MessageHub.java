package net.floodlightcontroller.nfvtest.message;
import net.floodlightcontroller.nfvtest.message.MessageProcessor;
import java.util.Map;
import java.util.HashMap;

public class MessageHub {
	private Map<String, MessageProcessor> messageProcessorMap;
	
	public MessageHub(){
		messageProcessorMap = new HashMap<String, MessageProcessor>();
	}
	
	public boolean addToHub(MessageProcessor messageProcessor){
		if(!messageProcessorMap.containsKey(messageProcessor.getId())){
			messageProcessorMap.put(messageProcessor.getId(), messageProcessor);
			return true;
		}
		else{
			return false;
		}
	}
	
	public void startProcessors(){
		for(String id : messageProcessorMap.keySet()){
			MessageProcessor mp = messageProcessorMap.get(id);
			Thread t = new Thread(mp);
			t.start();
		}
		try{
			Thread.sleep(500);
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}
	
	public void stopProcessors(){
	}
	
	public void sendTo(String messageProcessorId, Message m){
		if(messageProcessorMap.containsKey(messageProcessorId)){
			while(!messageProcessorMap.get(messageProcessorId).obtainQueue().offer(m)){
			}
		}
	}
}
