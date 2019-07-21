/* Copyright (c) 2013-2018 Pivotal Software, Inc. All rights reserved. */
package com.rabbitmq.jms.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import javax.jms.*;
import javax.jms.IllegalStateException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.jms.util.WhiteListObjectInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;
import com.rabbitmq.jms.util.RMQJMSException;

/**
 * Implementation of the {@link Connection}, {@link QueueConnection} and {@link TopicConnection} interfaces.
 * A {@link RMQConnection} object holds a list of {@link RMQSession} objects as well as the actual
 * {link com.rabbitmq.client.Connection} object that represents the TCP connection to the RabbitMQ broker.
 * <p>
 * This implementation also holds a reference to the executor service that is used by the connection so that we
 * can pause incoming messages.
 * </p>
 */
public class RMQConnection implements Connection, QueueConnection, TopicConnection {

    public static final int NO_CHANNEL_QOS = -1;

    private final Logger logger = LoggerFactory.getLogger(RMQConnection.class);

    /**
     * the TCP connection wrapper to the RabbitMQ broker
     */
    private final com.rabbitmq.client.Connection rabbitConnection;
    /**
     * Hard coded connection meta data returned in the call {@link #getMetaData()} call
     */
    private static final ConnectionMetaData connectionMetaData = new RMQConnectionMetaData();
    /**
     * The client ID for this connection
     */
    private String clientID;
    /**
     * The exception listener
     */
    private final AtomicReference<ExceptionListener> exceptionListener = new AtomicReference<ExceptionListener>();
    /**
     * The list of all {@link RMQSession} objects created by this connection
     */
    private final List<RMQSession> sessions = Collections.<RMQSession>synchronizedList(new ArrayList<RMQSession>());
    /**
     * value to see if this connection has been closed
     */
    private volatile boolean closed = false;
    /**
     * atomic flag to pause and unpause the connection consumers (see {@link #start()} and {@link #stop()} methods)
     */
    private final AtomicBoolean stopped = new AtomicBoolean(true);

    /**
     * maximum time (in ms) to wait for close() to complete
     */
    private final long terminationTimeout;

    /**
     * max number of messages to read from a browsed queue
     */
    private final int queueBrowserReadMax;

    /**
     * How long to wait for onMessage to return, in milliseconds
     */
    private final int onMessageTimeoutMs;

    private static ConcurrentHashMap<String, String> CLIENT_IDS = new ConcurrentHashMap<String, String>();

    /**
     * List of all our durable subscriptions so we can track them on a per connection basis (maintained by sessions).
     */
    private final Map<String, RMQMessageConsumer> subscriptions = new ConcurrentHashMap<String, RMQMessageConsumer>();

    /**
     * This is used for JMSCTS test cases, as ClientID should only be configurable right after the connection has been created
     */
    private volatile boolean canSetClientID = true;

    /**
     * QoS setting for channels created by this connection.
     *
     * @see com.rabbitmq.client.Channel#basicQos(int)
     */
    private final int channelsQos;

    /**
     * Whether {@link MessageProducer} properties (delivery mode,
     * priority, TTL) take precedence over respective {@link Message}
     * properties or not.
     * Default is true.
     */
    private boolean preferProducerMessageProperty;

    /**
     * Whether requeue message on {@link RuntimeException} in the
     * {@link javax.jms.MessageListener} or not.
     * Default is false.
     */
    private boolean requeueOnMessageListenerException;

    private boolean requeueOnNackException;

    /**
     * Whether using auto-delete for server-named queues for non-durable topics.
     * If set to true, those queues will be deleted when the session is closed.
     * If set to false, queues will be deleted when the owning connection is closed.
     * Default is false.
     *
     * @since 1.8.0
     */
    private final boolean cleanUpServerNamedQueuesForNonDurableTopicsOnSessionClose;

    /**
     * Callback to customise properties of outbound AMQP messages.
     *
     * @since 1.9.0
     */
    private final BiFunction<AMQP.BasicProperties.Builder, Message, AMQP.BasicProperties.Builder> amqpPropertiesCustomiser;

    /**
     * Callback before sending a message.
     *
     * @since 1.11.0
     */
    private final SendingContextConsumer sendingContextConsumer;

    private final ReceivingContextConsumer receivingContextConsumer;

    /**
     * Classes in these packages can be transferred via ObjectMessage.
     *
     * @see WhiteListObjectInputStream
     */
    private List<String> trustedPackages = WhiteListObjectInputStream.DEFAULT_TRUSTED_PACKAGES;

    /**
     * Creates an RMQConnection object.
     *
     * @param connectionParams parameters for this connection
     */
    public RMQConnection(ConnectionParams connectionParams) {

        connectionParams.getRabbitConnection().addShutdownListener(new RMQConnectionShutdownListener());

        this.rabbitConnection = connectionParams.getRabbitConnection();
        this.terminationTimeout = connectionParams.getTerminationTimeout();
        this.queueBrowserReadMax = connectionParams.getQueueBrowserReadMax();
        this.onMessageTimeoutMs = connectionParams.getOnMessageTimeoutMs();
        this.channelsQos = connectionParams.getChannelsQos();
        this.preferProducerMessageProperty = connectionParams.willPreferProducerMessageProperty();
        this.requeueOnMessageListenerException = connectionParams.willRequeueOnMessageListenerException();
        this.requeueOnNackException = connectionParams.willRequeueOnNackException();
        this.cleanUpServerNamedQueuesForNonDurableTopicsOnSessionClose = connectionParams.isCleanUpServerNamedQueuesForNonDurableTopicsOnSessionClose();
        this.amqpPropertiesCustomiser = connectionParams.getAmqpPropertiesCustomiser();
        this.sendingContextConsumer = connectionParams.getSendingContextConsumer();
        this.receivingContextConsumer = connectionParams.getReceivingContextConsumer();
    }

    /**
     * Creates an RMQConnection object.
     *
     * @param rabbitConnection    the TCP connection wrapper to the RabbitMQ broker
     * @param terminationTimeout  timeout for close in milliseconds
     * @param queueBrowserReadMax maximum number of messages to read from a QueueBrowser (before filtering)
     * @param onMessageTimeoutMs  how long to wait for onMessage to return, in milliseconds
     */
    public RMQConnection(com.rabbitmq.client.Connection rabbitConnection, long terminationTimeout, int queueBrowserReadMax, int onMessageTimeoutMs) {
        this(new ConnectionParams()
                .setRabbitConnection(rabbitConnection)
                .setTerminationTimeout(terminationTimeout)
                .setQueueBrowserReadMax(queueBrowserReadMax)
                .setOnMessageTimeoutMs(onMessageTimeoutMs)
        );
    }

    private static final long FIFTEEN_SECONDS_MS = 15000;
    private static final int TWO_SECONDS_MS = 2000;

    /**
     * Creates an RMQConnection object, with default termination timeout of 15 seconds, a 2 seconds timeout for onMessage, and unlimited reads from QueueBrowsers.
     *
     * @param rabbitConnection the TCP connection wrapper to the RabbitMQ broker
     */
    public RMQConnection(com.rabbitmq.client.Connection rabbitConnection) {
        this(rabbitConnection, FIFTEEN_SECONDS_MS, 0, TWO_SECONDS_MS);
    }

    /**
     * For RMQSession to retrieve
     */
    int getQueueBrowserReadMax() {
        return this.queueBrowserReadMax;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Session createSession(boolean transacted, int acknowledgeMode) throws JMSException {
        logger.trace("transacted={}, acknowledgeMode={}", transacted, acknowledgeMode);
        illegalStateExceptionIfClosed();
        freezeClientID();
        RMQSession session = new RMQSession(new SessionParams()
                .setConnection(this)
                .setTransacted(transacted)
                .setOnMessageTimeoutMs(onMessageTimeoutMs)
                .setMode(acknowledgeMode)
                .setSubscriptions(this.subscriptions)
                .setPreferProducerMessageProperty(this.preferProducerMessageProperty)
                .setRequeueOnMessageListenerException(this.requeueOnMessageListenerException)
                .setRequeueOnNackException(this.requeueOnNackException)
                .setCleanUpServerNamedQueuesForNonDurableTopics(this.cleanUpServerNamedQueuesForNonDurableTopicsOnSessionClose)
                .setAmqpPropertiesCustomiser(this.amqpPropertiesCustomiser)
                .setSendingContextConsumer(this.sendingContextConsumer)
                .setReceivingContextConsumer(this.receivingContextConsumer)
        );
        session.setTrustedPackages(this.trustedPackages);
        this.sessions.add(session);
        return session;
    }

    private void freezeClientID() {
        this.canSetClientID = false;
    }

    private void illegalStateExceptionIfClosed() throws IllegalStateException {
        if (this.closed) throw new IllegalStateException("Connection is closed");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getClientID() throws JMSException {
        illegalStateExceptionIfClosed();
        return this.clientID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setClientID(String clientID) throws JMSException {
        logger.trace("set ClientID to '{}'", clientID);
        illegalStateExceptionIfClosed();
        if (!canSetClientID)
            throw new IllegalStateException("Client ID can only be set right after connection creation");
        if (this.clientID == null) {
            if (CLIENT_IDS.putIfAbsent(clientID, clientID) == null) {
                this.clientID = clientID;
            } else {
                throw new InvalidClientIDException(String.format("A connection with client ID [%s] already exists.", clientID));
            }
        } else {
            throw new IllegalStateException("Client ID already set.");
        }

    }

    public List<String> getTrustedPackages() {
        return trustedPackages;
    }

    /**
     * @param value list of trusted package prefixes
     * @see com.rabbitmq.jms.admin.RMQConnectionFactory#setTrustedPackages(List)
     */
    public void setTrustedPackages(List<String> value) {
        this.trustedPackages = value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConnectionMetaData getMetaData() throws JMSException {
        illegalStateExceptionIfClosed();
        freezeClientID();
        return connectionMetaData;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExceptionListener getExceptionListener() throws JMSException {
        illegalStateExceptionIfClosed();
        freezeClientID();
        return this.exceptionListener.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setExceptionListener(ExceptionListener listener) throws JMSException {
        logger.trace("set ExceptionListener ({}) on connection ({})", listener, this);
        illegalStateExceptionIfClosed();
        freezeClientID();
        this.exceptionListener.set(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() throws JMSException {
        logger.trace("starting connection ({})", this);
        illegalStateExceptionIfClosed();
        freezeClientID();
        if (stopped.compareAndSet(true, false)) {
            for (RMQSession session : this.sessions) {
                session.resume();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() throws JMSException {
        logger.trace("stopping connection ({})", this);
        illegalStateExceptionIfClosed();
        freezeClientID();
        if (stopped.compareAndSet(false, true)) {
            for (RMQSession session : this.sessions) {
                session.pause();
            }
        }
    }

    /**
     * @return <code>true</code> if this connection is in a stopped state
     */
    public boolean isStopped() {
        return stopped.get();
    }

    /**
     * From the JMS Spec:
     * <blockquote>
     * <p>This call blocks until a
     * receive or message listener in progress has completed. A blocked message consumer receive call returns null when
     * this message consumer is closed.</p>
     * </blockquote>
     * {@inheritDoc}
     */
    @Override
    public void close() throws JMSException {
        logger.trace("closing connection ({})", this);

        if (this.closed) return;
        this.closed = true;

        removeClientID();

        // We null any exception listener since we don't want it driven during close().
        this.exceptionListener.set(null);

        closeAllSessions();

        try {
            this.rabbitConnection.close();
        } catch (ShutdownSignalException x) {
            //nothing to do
        } catch (IOException x) {
            if (!(x.getCause() instanceof ShutdownSignalException)) {
                throw new RMQJMSException(x);
            }
        }
    }

    private void removeClientID() throws JMSException {
        String cID = this.clientID;  // even if closed!
        if (cID != null)
            CLIENT_IDS.remove(cID);
    }

    private void closeAllSessions() {
        for (RMQSession session : this.sessions) {
            try {
                session.internalClose();
            } catch (Exception e) {
                if (e instanceof ShutdownSignalException) {
                    // do nothing
                } else {
                    logger.error("exception closing session ({})", session, e);
                }
            }
        }
        this.sessions.clear();
    }

    Channel createRabbitChannel(boolean transactional) throws IOException {
        Channel channel = this.rabbitConnection.createChannel();
        if (this.channelsQos != NO_CHANNEL_QOS) {
            channel.basicQos(channelsQos);
        }
        if (transactional) {
            channel.txSelect();
        }
        return channel;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TopicSession createTopicSession(boolean transacted, int acknowledgeMode) throws JMSException {
        return (TopicSession) this.createSession(transacted, acknowledgeMode);
    }

    /**
     * @throws UnsupportedOperationException - optional method not implemented
     */
    @Override
    public ConnectionConsumer
    createConnectionConsumer(Topic topic, String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public QueueSession createQueueSession(boolean transacted, int acknowledgeMode) throws JMSException {
        return (QueueSession) this.createSession(transacted, acknowledgeMode);
    }

    /**
     * @throws UnsupportedOperationException - optional method not implemented
     */
    @Override
    public ConnectionConsumer createConnectionConsumer(Queue queue,
                                                       String messageSelector,
                                                       ServerSessionPool sessionPool,
                                                       int maxMessages) throws JMSException {
        throw new UnsupportedOperationException();
    }

    /**
     * @throws UnsupportedOperationException - optional method not implemented
     */
    @Override
    public ConnectionConsumer createConnectionConsumer(Destination destination,
                                                       String messageSelector,
                                                       ServerSessionPool sessionPool,
                                                       int maxMessages) throws JMSException {
        throw new UnsupportedOperationException();
    }

    /**
     * @throws UnsupportedOperationException - optional method not implemented
     */
    @Override
    public ConnectionConsumer createDurableConnectionConsumer(Topic topic,
                                                              String subscriptionName,
                                                              String messageSelector,
                                                              ServerSessionPool sessionPool,
                                                              int maxMessages) throws JMSException {
        throw new UnsupportedOperationException();
    }

    /* Internal methods. */

    /**
     * A connection must track all sessions that are created,
     * but when we call {@link RMQSession#close()} we must unregister this
     * session with the connection.
     * This method is called by {@link RMQSession#close()} and should not be called from anywhere else.
     *
     * @param session - the session that is being closed
     */
    void sessionClose(RMQSession session) throws JMSException {
        logger.trace("internal:sessionClose({})", session);
        if (this.sessions.remove(session)) {
            session.internalClose();
        }
    }

    long getTerminationTimeout() {
        return this.terminationTimeout;
    }

    @Override
    public String toString() {
        return new StringBuilder("RMQConnection{")
                .append("rabbitConnection=").append(this.rabbitConnection)
                .append(", stopped=").append(this.stopped.get())
                .append(", queueBrowserReadMax=").append(this.queueBrowserReadMax)
                .append('}').toString();
    }

    public boolean willRequeueOnNackException() {
        return requeueOnNackException;
    }

    public void setRequeueOnNackException(boolean requeueOnNackException) {
        this.requeueOnNackException = requeueOnNackException;
    }

    private class RMQConnectionShutdownListener implements ShutdownListener {
        @Override
        public void shutdownCompleted(ShutdownSignalException cause) {
            if (null == exceptionListener.get() || cause.isInitiatedByApplication())
                return; // Ignore this
            exceptionListener.get().onException(new RMQJMSException(String.format("error in %s, connection closed, with reason %s", cause.getReference(), cause.getReason()), cause));
        }
    }

}
