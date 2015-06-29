package net.floodlightcontroller.nfvtest;

import java.util.Collection;
import java.util.Map;
import java.lang.String;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SlaveServiceChain extends NFVServiceChain {
	//These two strings represent the type of the message:
	public final String request = "Request";
	public final String response = "Response";
	
	//These two strings represent the task of the message:
	public final String stateTransition = "StateTransition";
	public final String modifyNode = "ModifyNode";
	public final String queryProperty = "queryProperty";
	
	
	
	public class Message {
		private final String type;
		private final String task;
		private final String messageBody;
		private final Object object;
		
		Message(String type, String task, String messageBody, Object object){
			this.type = type;
			this.task = task;
			this.messageBody = messageBody;
			this.object = object;
		}
		
		public String getType(){
			return type;
		}
		public String getTask(){
			return task;
		}
		public String getMessageBody(){
			return messageBody;
		}
		public Object getObject(){
			return object;
		}
	}
	
	ConcurrentLinkedQueue<Message> inputQueue;
	ConcurrentLinkedQueue<Message> outputQueue;
	
	SlaveServiceChain(List<String> argumentStageType) {
		super(argumentStageType);
		inputQueue = new ConcurrentLinkedQueue<Message>();
		outputQueue = new ConcurrentLinkedQueue<Message>();
	}
	
	public void processInputMessages(){
		Message message = inputQueue.poll();
		if(message == null){
			return;
		}
		
		
		
	}
	
	
	
	
	
	
}
