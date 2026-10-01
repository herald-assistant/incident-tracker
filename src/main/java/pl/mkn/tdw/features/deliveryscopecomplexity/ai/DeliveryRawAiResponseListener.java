package pl.mkn.tdw.features.deliveryscopecomplexity.ai;

@FunctionalInterface
public interface DeliveryRawAiResponseListener {

    DeliveryRawAiResponseListener NO_OP = rawResponse -> {
    };

    void onRawAiResponse(String rawResponse);
}
