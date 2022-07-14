/*
 *  Copyright (c) 2017 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package io.siddhi.extension.io.mqtt.source;

import io.siddhi.core.stream.input.source.SourceEventListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@code ConsumerMqtt }Handle the Mqtt consuming tasks.
 */
public class MqttConsumer {
    private static final Logger log = LogManager.getLogger(MqttConsumer.class);
    public SourceEventListener sourceEventListener;
    private boolean isPaused;
    private ReentrantLock lock;
    private Condition condition;
    private String topicOption;
    private int qosOption;
    private MqttClient client;

    public MqttConsumer(SourceEventListener sourceEventListener) {
        this.sourceEventListener = sourceEventListener;
        lock = new ReentrantLock();
        condition = lock.newCondition();
    }

    public void subscribe(String topicOption, int qosOption,
                          MqttClient client) throws MqttException {
        MqttSourceCallBack callback = new MqttSourceCallBack();
        this.topicOption = topicOption;
        this.qosOption = qosOption;
        this.client = client;
        client.setCallback(callback);
        client.subscribe(topicOption, qosOption);
    }

    /**
     * MqttCallback is called when an event is received.
     */
    public class MqttSourceCallBack implements MqttCallbackExtended {

        @Override
        public void connectionLost(Throwable throwable) {
            log.error("MQTT connection not reachable");
        }

        @Override
        public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
            if (isPaused) {
                lock.lock();
                try {
                    while (!isPaused) {
                        condition.await();
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } finally {
                    lock.unlock();
                }
            }
            String message = new String(mqttMessage.getPayload(), "UTF-8");
            sourceEventListener.onEvent(message, null);
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        }

        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            log.info("MQTT connectComplete");
            if (reconnect) {
                log.info("MQTT resubscribing");
                try {
                    client.subscribe(topicOption, qosOption);
                } catch (MqttException e) {
                    log.error("MQTT Cannot subscribe to topic: " + e.toString());
                }
            }
            
        }
    }

    public void pause() {
        isPaused = true;
    }

    public void resume() {
        isPaused = false;
        try {
            lock.lock();
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }
}
