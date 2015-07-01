package net.floodlightcontroller.nfvtest.nfvutils;

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
