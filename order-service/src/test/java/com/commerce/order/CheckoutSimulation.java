package com.commerce.order;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.util.UUID;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class CheckoutSimulation extends Simulation {

    HttpProtocolBuilder httpProtocol = http
            .baseUrl("http://localhost:8081")
            .acceptHeader("application/json")
            .contentTypeHeader("application/json");

    ScenarioBuilder scn = scenario("High Concurrency Checkout")
            .exec(session -> session.set("idempotencyKey", UUID.randomUUID().toString()))
            .exec(http("checkout_request")
                    .post("/orders/checkout")
                    .header("Idempotency-Key", "#{idempotencyKey}")
                    .body(StringBody("{\"productId\": \"PROD1\", \"quantity\": 1}"))
                    .check(status().in(200, 429)));

    {
        setUp(
                scn.injectOpen(
                        rampUsersPerSec(10).to(200).during(10), // Ramp up to 200 users/sec
                        constantUsersPerSec(200).during(30) // Sustain for 30s
                )).protocols(httpProtocol);
    }
}
