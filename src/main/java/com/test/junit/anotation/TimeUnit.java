package com.test.junit.anotation;

public enum TimeUnit {
    MILLISECOND (java.util.concurrent.TimeUnit.MILLISECONDS),
    SECONDS(java.util.concurrent.TimeUnit.SECONDS),

    MINUTES(java.util.concurrent.TimeUnit.MINUTES);

    java.util.concurrent.TimeUnit timeUnit;
    TimeUnit(java.util.concurrent.TimeUnit timeUnit) {
        this.timeUnit=timeUnit;
    }

    public java.util.concurrent.TimeUnit getTimeUnit() {
        return timeUnit;
    }
}
