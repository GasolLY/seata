/*
 *  Copyright 1999-2019 Seata.io Group.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.seata.rm.tcc.rocketmq;

import java.net.UnknownHostException;
import org.apache.rocketmq.client.Validators;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageAccessor;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.remoting.exception.RemotingException;

public class RocketMQUtils {

    public static SendResult halfSend(DefaultMQProducer defaultMQProducer,
        Message msg) throws MQClientException {
        // ignore DelayTimeLevel parameter
        if (msg.getDelayTimeLevel() != 0) {
            MessageAccessor.clearProperty(msg, MessageConst.PROPERTY_DELAY_TIME_LEVEL);
        }

        Validators.checkMessage(msg, defaultMQProducer);

        MessageAccessor.putProperty(msg, MessageConst.PROPERTY_TRANSACTION_PREPARED, "true");
        MessageAccessor.putProperty(msg, MessageConst.PROPERTY_PRODUCER_GROUP, defaultMQProducer.getProducerGroup());
        SendResult sendResult;
        try {
            sendResult = defaultMQProducer.send(msg);
        } catch (Exception e) {
            throw new MQClientException("send message Exception", e);
        }

        switch (sendResult.getSendStatus()) {
            case SEND_OK: {
                if (sendResult.getTransactionId() != null) {
                    msg.putUserProperty("__transactionId__", sendResult.getTransactionId());
                }
                String transactionId = msg.getProperty(MessageConst.PROPERTY_UNIQ_CLIENT_MESSAGE_ID_KEYIDX);
                if (null != transactionId && !"".equals(transactionId)) {
                    msg.setTransactionId(transactionId);
                }
            }
            break;
            case FLUSH_DISK_TIMEOUT:
            case FLUSH_SLAVE_TIMEOUT:
            case SLAVE_NOT_AVAILABLE:
            default:
                throw new RuntimeException("Message send fail.");
        }
        return sendResult;
    }

    public static void confirm(DefaultMQProducer defaultMQProducer, Message msg,
        SendResult sendResult) throws UnknownHostException, MQBrokerException, RemotingException, InterruptedException {
        DefaultMQProducerImpl defaultMQProducerImpl = defaultMQProducer.getDefaultMQProducerImpl();
        defaultMQProducerImpl.endTransaction(msg, sendResult, LocalTransactionState.COMMIT_MESSAGE, null);
    }

    public static void cancel(DefaultMQProducer defaultMQProducer, Message msg,
        SendResult sendResult) throws UnknownHostException, MQBrokerException, RemotingException, InterruptedException {
        DefaultMQProducerImpl defaultMQProducerImpl = defaultMQProducer.getDefaultMQProducerImpl();
        defaultMQProducerImpl.endTransaction(msg, sendResult, LocalTransactionState.ROLLBACK_MESSAGE, null);
    }
}
