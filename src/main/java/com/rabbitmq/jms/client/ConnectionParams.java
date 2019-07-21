/* Copyright (c) 2016-2018 Pivotal Software, Inc. All rights reserved. */
package com.rabbitmq.jms.client;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Connection;

import javax.jms.Message;
import javax.jms.MessageProducer;
import java.util.function.BiFunction;

/**
 * Holder for {@link RMQConnection} constructor arguments.
 */
public class ConnectionParams {

    /** The TCP connection wrapper to the RabbitMQ broker */
    private com.rabbitmq.client.Connection rabbitConnection;

    /** Maximum time (in ms) to wait for close() to complete */
    private long terminationTimeout;

    /** Max number of messages to read from a browsed queue */
    private int queueBrowserReadMax;

    /** How long to wait for onMessage to return, in milliseconds */
    private int onMessageTimeoutMs;

    /**
     * QoS setting for channels
     *
     * @see com.rabbitmq.client.Channel#basicQos(int)
     */
    private int channelsQos = RMQConnection.NO_CHANNEL_QOS;

    /**
     * Whether {@link MessageProducer} properties (delivery mode,
     * priority, TTL) take precedence over respective {@link Message}
     * properties or not.
     * Default is true.
     */
    private boolean preferProducerMessageProperty = true;

    /**
     * Whether requeue message on {@link RuntimeException} in the
     * {@link javax.jms.MessageListener} or not.
     * Default is false.
     */
    private boolean requeueOnMessageListenerException = false;

    private boolean requeueOnNackException = false;

    /**
     * Whether using auto-delete for server-named queues for non-durable topics.
     * If set to true, those queues will be deleted when the session is closed.
     * If set to false, queues will be deleted when the owning connection is closed.
     * Default is false.
     *
     * @since 1.8.0
     */
    private boolean cleanUpServerNamedQueuesForNonDurableTopicsOnSessionClose = false;

    /**
     * Callback to customise properties of outbound AMQP messages.
     *
     * @since 1.9.0
     */
    private BiFunction<AMQP.BasicProperties.Builder, Message, AMQP.BasicProperties.Builder> amqpPropertiesCustomiser;

    /**
     * Callback before sending a message.
     *
     * @since 1.11.0
     */
    private SendingContextConsumer sendingContextConsumer;

    /**
     * Callback before receiving a message.
     *
     * @since 1.11.0
     */
    private ReceivingContextConsumer receivingContextConsumer;

    public Connection getRabbitConnection() {
        return rabbitConnection;
    }

    public ConnectionParams setRabbitConnection(Connection rabbitConnection) {
        this.rabbitConnection = rabbitConnection;
        return this;
    }

    public long getTerminationTimeout() {
        return terminationTimeout;
    }

    public ConnectionParams setTerminationTimeout(long terminationTimeout) {
        this.terminationTimeout = terminationTimeout;
        return this;
    }

    public int getQueueBrowserReadMax() {
        return queueBrowserReadMax;
    }

    public ConnectionParams setQueueBrowserReadMax(int queueBrowserReadMax) {
        this.queueBrowserReadMax = queueBrowserReadMax;
        return this;
    }

    public int getOnMessageTimeoutMs() {
        return onMessageTimeoutMs;
    }

    public ConnectionParams setOnMessageTimeoutMs(int onMessageTimeoutMs) {
        this.onMessageTimeoutMs = onMessageTimeoutMs;
        return this;
    }

    public int getChannelsQos() {
        return channelsQos;
    }

    public ConnectionParams setChannelsQos(int channelsQos) {
        this.channelsQos = channelsQos;
        return this;
    }

    public boolean willPreferProducerMessageProperty() {
        return preferProducerMessageProperty;
    }

    public ConnectionParams setPreferProducerMessageProperty(boolean preferProducerMessageProperty) {
        this.preferProducerMessageProperty = preferProducerMessageProperty;
        return this;
    }

    public boolean willRequeueOnMessageListenerException() {
        return requeueOnMessageListenerException;
    }

    public ConnectionParams setRequeueOnMessageListenerException(boolean requeueOnMessageListenerException) {
        this.requeueOnMessageListenerException = requeueOnMessageListenerException;
        return this;
    }

    public boolean isCleanUpServerNamedQueuesForNonDurableTopicsOnSessionClose() {
        return this.cleanUpServerNamedQueuesForNonDurableTopicsOnSessionClose;
    }

    public ConnectionParams setCleanUpServerNamedQueuesForNonDurableTopicsOnSessionClose(boolean cleanUpServerNamedQueuesForNonDurableTopicsOnSessionClose) {
        this.cleanUpServerNamedQueuesForNonDurableTopicsOnSessionClose = cleanUpServerNamedQueuesForNonDurableTopicsOnSessionClose;
        return this;
    }

    public BiFunction<AMQP.BasicProperties.Builder, Message, AMQP.BasicProperties.Builder> getAmqpPropertiesCustomiser() {
        return amqpPropertiesCustomiser;
    }

    public ConnectionParams setAmqpPropertiesCustomiser(BiFunction<AMQP.BasicProperties.Builder, Message, AMQP.BasicProperties.Builder> amqpPropertiesCustomiser) {
        this.amqpPropertiesCustomiser = amqpPropertiesCustomiser;
        return this;
    }

    public SendingContextConsumer getSendingContextConsumer() {
        return sendingContextConsumer;
    }

    public ConnectionParams setSendingContextConsumer(SendingContextConsumer sendingContextConsumer) {
        this.sendingContextConsumer = sendingContextConsumer;
        return this;
    }

    public ReceivingContextConsumer getReceivingContextConsumer() {
        return receivingContextConsumer;
    }

    public ConnectionParams setReceivingContextConsumer(ReceivingContextConsumer receivingContextConsumer) {
        this.receivingContextConsumer = receivingContextConsumer;
        return this;
    }

    public boolean willRequeueOnNackException() {
        return requeueOnNackException;
    }

    public ConnectionParams setRequeueOnNackException(boolean requeueOnNackException) {
        this.requeueOnNackException = requeueOnNackException;
        return this;
    }
}
