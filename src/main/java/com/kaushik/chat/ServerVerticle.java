package com.kaushik.chat;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.http.ServerWebSocket;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Verticle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/*
a basic mobile messaging server
*/

public class ServerVerticle extends Verticle {
	
	Cache cache = new Cache();
	
	@Override
	public void start(){
		//HTTP Server
		RouteMatcher routeMatcher = getRoute();
		vertx.createHttpServer().requestHandler(routeMatcher).listen(8080,"localhost");

		//Websocket Sever
		vertx.createHttpServer().websocketHandler(generateSocketHandler()).listen(8090);
	}
	
	private RouteMatcher getRoute() {	
		return new RouteMatcher().get("/", new Handler<HttpServerRequest>() {
			public void handle(HttpServerRequest event) {
				event.response().sendFile("web/display.html");
			}
		}).get(".*\\.(css|js)$",new Handler<HttpServerRequest>() {

			public void handle(HttpServerRequest event) {
				event.response().sendFile("web/"+new File(event.path()));
			}
		});
	}

	private Handler<ServerWebSocket> generateSocketHandler() {
		final Logger logger = container.logger();
		final Pattern pattern = Pattern.compile("/chat/(\\w+)"); //identifies individual users
		final EventBus eventBus = vertx.eventBus();
		return new Handler<ServerWebSocket>() {

			public void handle(final ServerWebSocket event) {
				
				final Matcher m = pattern.matcher(event.path());
				if (!m.matches()) {
					event.reject();
					return;
				}

				final String chatRoom = m.group(1);
				final String id = event.textHandlerID();
				logger.info("Registering new connection");
				vertx.sharedData().getSet(chatRoom).add(id);

				event.closeHandler(new Handler<Void>() {
					public void handle(final Void event) {
						logger.info("Unregistering connection id: " + id);
						vertx.sharedData().getSet(chatRoom).remove(id);
					}
				});
				
				event.dataHandler(new Handler<Buffer>() {

					public void handle(Buffer buffer) {
						ObjectMapper mapper = new ObjectMapper();
						try{
							JsonNode rootNode = mapper.readTree(buffer.toString());
							
							//Cache Behavior
							Date date =new Date();
							long storedTime=cache.getTime();
							String storedMessage=cache.getMessage();
							long currentTime=date.getTime();
							String currentMessage=rootNode.get("message").toString();
							cache.setTime(currentTime);
							cache.setMessage(currentMessage);
							
							if(checkCondition(storedTime,storedMessage,currentTime,currentMessage)){
								((ObjectNode) rootNode).put("received", date.toString());
								String jsonOutput = mapper.writeValueAsString(rootNode);
								logger.info("JSON: " + jsonOutput);
								for (Object chatter : vertx.sharedData().getSet(chatRoom)) {
									eventBus.send((String) chatter, jsonOutput);
								}
							}
							
						}catch(IOException e){
							 event.reject();
						}	
					}
					private boolean checkCondition(long storedTime, String storedMessage, long currentTime,
							String currentMessage) {
						if(storedMessage.equals(currentMessage) && (currentTime-storedTime)<= 5000){
							logger.info("Duplicate Rejected");
							return false;
						}
						return true;
					}
				});
				
				
			}
		};
	}
}
