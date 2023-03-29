package com.mbixtech.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;


@Service
@Slf4j
public class AmqService {
    @Value("${mbix.activemq.queue-name}")
    private String queueName;

    @Value("${mbix.activemq.get-timeout}")
    private Integer getTimeout;

    @Value("${mbix.loop-param.last-msg-expired}")
    private Integer expiredTime;

    @Value("${mbix.loop-param.push-count}")
    private Integer pushCount;

    @Value("${mbix.loop-param.pull-count}")
    private Integer pullCount;

    public void pushMsg(Exchange exchange){
        CamelContext camelContext = exchange.getContext();
        ProducerTemplate producerTemplate = camelContext.createProducerTemplate();
        for(Integer i = 0; i<pushCount; i++){
            String body = "{\"message\":\"test on "+LocalDateTime.now()+"\"}";
            log.debug(body);
            producerTemplate.sendBody("amqp:queue:"+queueName+"?timeToLive="+expiredTime, body);
        }
    }

    public void getMsg(Exchange exchange){
        CamelContext camelContext = exchange.getContext();
        ConsumerTemplate consumerTemplate = camelContext.createConsumerTemplate();
        for(Integer i = 0; i<pullCount; i++){
            String body = consumerTemplate.receiveBody("amqp:queue:"+queueName, getTimeout, String.class);
//            log.debug(body);
        }
    }

    public void clearMsg(Exchange exchange){
        CamelContext camelContext = exchange.getContext();
        ConsumerTemplate consumerTemplate = camelContext.createConsumerTemplate();
        Boolean qStored = true;
        while(qStored){
            String body = consumerTemplate.receiveBody("amqp:queue:"+queueName, getTimeout, String.class);
            if(body == null) qStored = false;
        }
    }
}
