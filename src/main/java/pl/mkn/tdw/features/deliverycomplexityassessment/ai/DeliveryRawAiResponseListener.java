package pl.mkn.tdw.features.deliverycomplexityassessment.ai;

@FunctionalInterface
public interface DeliveryRawAiResponseListener {

    DeliveryRawAiResponseListener NO_OP = rawResponse -> {
    };

    void onRawAiResponse(String rawResponse);
}
