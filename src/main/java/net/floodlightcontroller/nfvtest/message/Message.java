package net.floodlightcontroller.nfvtest.message;

import java.util.UUID;

public abstract class Message {
	private final UUID uuid = UUID.randomUUID();
	
	public UUID getUUID(){
		return this.uuid;
	}
}
