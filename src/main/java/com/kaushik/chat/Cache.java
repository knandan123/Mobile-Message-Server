package com.kaushik.chat;

public class Cache {
	
	String message;
	
	long time;
	
	Cache(){
		message="";
		time=System.currentTimeMillis();
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public long getTime() {
		return time;
	}

	public void setTime(long time) {
		this.time = time;
	}

}
