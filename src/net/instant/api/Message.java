package net.instant.api;

import org.json.JSONObject;

/**
 * A single message as processed by MessageHook.
 */
public interface Message {

    /**
     * The raw message data as received from the client.
     * The core only handles textual WebSocket messages, thus, binary ones
     * do not have to be considered; the text is valid JSON.
     */
    String getRawData();

    /**
     * The message data as an untreated JSON object.
     * May be used to examine fields that are not present in getData().
     */
    JSONObject getParsedData();

    /**
     * The decoded message data.
     * Since messages which are not valid JSON (objects) are rejected, this
     * is always present.
     * The content should not be changed unless there are appropriate reasons
     * to do so.
     */
    MessageContents getData();

    /**
     * The client the message originated from.
     */
    ClientConnection getSource();

    /**
     * The room the message was submitted to.
     */
    Room getRoom();

    /**
     * Send a message back to the client.
     * The sequence ID field is automatically adjusted to match this message;
     * otherwise, resp is not modified.
     */
    void sendResponse(MessageContents resp);

    /**
     * Construct a new message body.
     * The given type is assigned to the corresponding field.
     * This is a convenience function; no specific tasks are performed.
     */
    MessageContents makeMessage(String type);

}
