package com.mahitotsu.arachne.samples.delivery.orderservice.application;

import java.util.Optional;

import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.NormalizedOrderIntent;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.OrderSession;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.StoredOrder;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.SuggestOrderRequest;

public interface OrderIntentPlanner {

    NormalizedOrderIntent plan(
            String sessionId,
            SuggestOrderRequest request,
            OrderSession existing,
            Optional<StoredOrder> recentOrder);
}