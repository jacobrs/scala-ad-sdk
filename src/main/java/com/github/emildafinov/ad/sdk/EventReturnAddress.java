package com.github.emildafinov.ad.sdk;

/**
 * Information that is passed to the {@link EventHandler} instance when an event is handled. 
 * Contains contextual information about the incoming event that can be used to send the 
 * "event processing completed" signal back to the AppMarket when the event processing has
 * finished. See {@link com.github.emildafinov.ad.sdk.event.payloads.EventResolver} for more details
 * regarding resolving events whose processing has been completed
 */
public interface EventReturnAddress {
	String eventId();
	String marketplaceBaseUrl();
	String clientId();
}
