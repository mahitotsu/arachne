package io.arachne.strands.eventloop;

/**
 * Thrown when the event loop cannot complete normally (e.g. max cycles exceeded).
 */
public class EventLoopException extends RuntimeException {

    public EventLoopException(String message) {
        super(message);
    }

    public EventLoopException(String message, Throwable cause) {
        super(message, cause);
    }
}
