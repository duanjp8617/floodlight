package net.floodlightcontroller.nfvtest;

import java.util.Collection;
import java.util.Map;
import java.lang.String;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SlaveServiceChain extends NFVServiceChain {
	//These two strings represent the type of the message:
	public static final String request = "Request";
	public static final String response = "Response";
	
	//These two strings represent the task of the message:
	public static final String stateTransition = "StateTransition";
	public static final String nodeModification = "NodeModification";
	public static final String propertyQuery = "PropertyQuery";
	
	
	
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
	
	//Accept request sent from MasterServiceChain
	ConcurrentLinkedQueue<Message> inputQueue;
	//Return response back to the MasterServiceChain
	ConcurrentLinkedQueue<Message> outputQueue;
	
	SlaveServiceChain(List<String> argumentStageType) {
		super(argumentStageType);
		inputQueue = new ConcurrentLinkedQueue<Message>();
		outputQueue = new ConcurrentLinkedQueue<Message>();
	}
	
	public boolean processRequest(){
		Message message = inputQueue.poll();
		if(message == null){
			return false;
		}
		if(message.getType() != SlaveServiceChain.request){
			throw new NFVException("Incorrect type. Should be a request.");
		}
		
		if(message.getTask() == SlaveServiceChain.nodeModification){
			handleNodeModificationReq(message);
		}
		else if(message.getTask() == SlaveServiceChain.propertyQuery){
			handlePropertyQueryReq(message);
		}
		else if(message.getTask() == SlaveServiceChain.stateTransition){
			handleStateTransitionReq(message);
		}
		else{
			throw new NFVException("Incorrect task.");
		}
		return true;
	}
	
	private void handleNodeModificationReq(Message message){
		
	}
	private void handlePropertyQueryReq(Message message){
		
	}
	private void handleStateTransitionReq(Message message){
		
	}
	
	public boolean processResponse(){
		Message message = outputQueue.poll();
		if(message == null){
			return false;
		}
		
		if(message.getType() != SlaveServiceChain.response){
			throw new NFVException("Incorrect type. Should be a response.");
		}
		
		if(message.getTask() == SlaveServiceChain.nodeModification){
			handleNodeModificationRes(message);
		}
		else if(message.getTask() == SlaveServiceChain.propertyQuery){
			handlePropertyQueryRes(message);
		}
		else if(message.getTask() == SlaveServiceChain.stateTransition){
			handleStateTransitionRes(message);
		}
		else{
			throw new NFVException("Incorrect task.");
		}
		return true;
	}
	
	private void handleNodeModificationRes(Message message){
		
	}
	private void handlePropertyQueryRes(Message message){
		
	}
	private void handleStateTransitionRes(Message message){
		
	}
}