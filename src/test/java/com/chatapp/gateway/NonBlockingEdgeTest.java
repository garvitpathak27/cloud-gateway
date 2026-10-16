package com.chatapp.gateway;                                              
                                                                            
import org.junit.jupiter.api.Test;                                        
import org.springframework.beans.factory.annotation.Autowired;            
import org.springframework.boot.test.context.SpringBootTest;              
import org.springframework.test.web.reactive.server.WebTestClient;        
import reactor.blockhound.BlockHound;                                     
                                                                            
@SpringBootTest(                                                          
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,       
        properties = "eureka.client.enabled=false") // no Eureka in tests:
lb:// finds no instances -> 503 path                                        
class NonBlockingEdgeTest {                                               
                                                                            
    static {                                                              
        BlockHound.install();                                             
    }                                                                     
                                                                            
    @Autowired                                                            
    WebTestClient client;                                                 
                                                                            
    @Test                                                                 
    void edgePathsNeverBlockTheEventLoop() {                              
        client.get().uri("/actuator/info").exchange().expectStatus().     
isOk();               // access log                                         
        client.get().uri("/api/does-not-exist").exchange().expectStatus().
isNotFound()     // 404 handler                                             
                .expectBody().jsonPath("$.code").                           
isEqualTo("ROUTE_NOT_FOUND");                                               
        client.get().uri("/api/rooms/").exchange().expectStatus().        
isEqualTo(503)           // LB + 503 handler                                
                .expectBody().jsonPath("$.code").isEqualTo("NO_INSTANCES"); 
        client.options().uri("/api/rooms/")                               
// CORS preflight                                                           
                .header("Origin", "http://localhost:5173")                  
                .header("Access-Control-Request-Method", "GET")             
                .exchange().expectStatus().isOk();                          
    }                                                                     
}                           