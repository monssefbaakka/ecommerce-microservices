package com.monssef.orderservice.client;

// Looks through the causes of a failure: Feign wraps the IOException of a failed call in a RetryableException
final class Causes {

    private Causes() {
    }

    // true if the failure itself or one of its causes is one of the given types
    @SafeVarargs
    static boolean contain(Throwable failure, Class<? extends Throwable>... types) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            for (Class<? extends Throwable> type : types) {
                if (type.isInstance(current)) {
                    return true;
                }
            }
        }
        return false;
    }
}
