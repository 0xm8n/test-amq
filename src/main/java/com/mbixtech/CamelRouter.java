package com.mbixtech;
import org.apache.camel.*;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.stereotype.Component;
import com.mbixtech.service.AmqService;

@Component
public class CamelRouter extends RouteBuilder {

    private final String PushLoopOption = "{{mbix.loop-param.push-option}}";
    private final String PullLoopOption = "{{mbix.loop-param.pull-option}}";

    @Override
    public void configure() throws Exception {

        // @formatter:off

        // Note: Configure context setting
        CamelContext context = this.getContext();
        context.setUseMDCLogging(true);

        // Note: For generic exception
        onException(Exception.class, RuntimeCamelException.class)
                .handled(true)
                .log(LoggingLevel.ERROR, "Caught Error while retry message")
                .log(LoggingLevel.ERROR, "${exception.stacktrace}")
        ;

        restConfiguration()
                .apiContextPath("/api-doc")
                .apiProperty("api.title", "Test AMQ")
                .apiProperty("api.version", "1.0")
                .apiProperty("cors", "true")
                .apiProperty("base.path", "v{{mbix.api.version}}")
                .apiProperty("api.path", "/")
                .apiContextRouteId("doc-api")
                .component("servlet")
                .bindingMode(RestBindingMode.off)
        ;

        rest("/test-amq").description("Test AMQ")
                .get("/clear")
                .to("direct:clearMsg")
        ;

        from("timer:testPushLoop"+PushLoopOption).description("Test Push Loop").routeId("TestPushLoop")
                .streamCaching()

                .to("log:com.mbixtech.log.msg?level=DEBUG&showBody=false&showBodyType=false&showExchangePattern=false&marker='### Start PUSH Loop'")
                .bean(AmqService.class,"pushMsg")
                .to("log:com.mbixtech.log.msg?level=DEBUG&showBody=false&showBodyType=false&showExchangePattern=false&marker='### End PUSH Loop'")
                .end()
        ;

        from("timer:testPullLoop"+PullLoopOption).description("Test Pull Loop").routeId("TestPullLoop")
                .streamCaching()

                .to("log:com.mbixtech.log.msg?level=DEBUG&showBody=false&showBodyType=false&showExchangePattern=false&marker='### Start PULL Loop'")
                .bean(AmqService.class,"getMsg")
                .to("log:com.mbixtech.log.msg?level=DEBUG&showBody=false&showBodyType=false&showExchangePattern=false&marker='### End PULL Loop'")
                .end()
        ;

        from("direct:clearMsg").description("Clear messages").routeId("ClearMsg")
                .streamCaching()

                .to("log:com.mbixtech.log.msg?level=DEBUG&showBody=false&showBodyType=false&showExchangePattern=false&marker='### Start CLEAR Messages'")
                // Create and put Last Message in to queue.
                .bean(AmqService.class,"clearMsg")
                .to("log:com.mbixtech.log.msg?level=DEBUG&showBody=false&showBodyType=false&showExchangePattern=false&marker='### End CLEAR Messages'")
                .end()
        ;

        // @formatter:on
    }
}
