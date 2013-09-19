/* Copyright (c) 2013 GoPivotal, Inc. All rights reserved. */
package com.rabbitmq.jms.client.message;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import javax.jms.JMSException;
import javax.jms.MessageNotWriteableException;
import javax.jms.TextMessage;

import com.rabbitmq.jms.client.RMQMessage;


/**
 * Implements {@link TextMessage} interface.
 */
public class RMQTextMessage extends RMQMessage implements TextMessage {

    private volatile String text;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setText(String string) throws JMSException {
        if (isReadonlyBody()) throw new MessageNotWriteableException("Message has been received and is read only.");
        this.text = string;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getText() throws JMSException {
        return this.text;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void clearBodyInternal() throws JMSException {
        this.text = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBody(ObjectOutput out) throws IOException {
        String text = this.text;
        out.writeBoolean(text == null);
        if (text!=null) {
            byte[] ba = text.getBytes("UTF-8");
            out.writeInt(ba.length);
            out.write(ba);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readBody(ObjectInput inputStream) throws IOException, ClassNotFoundException {
        boolean isnull = inputStream.readBoolean();
        if (!isnull) {
            int len = inputStream.readInt();
            byte[] ba = new byte[len];
            inputStream.readFully(ba, 0, len);
            this.text = new String(ba, "UTF-8");
        }
    }

}
