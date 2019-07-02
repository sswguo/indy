package org.commonjava.indy.sli.metrics;

import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.Timer;
import com.codahale.metrics.health.HealthCheck;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

public class GoldenSignalsFunctionMetrics
{
    private String name;

    private Timer latency = new Timer();

    private Meter errors = new Meter();

    private Meter throughput = new Meter();

    GoldenSignalsFunctionMetrics( String name )
    {
        this.name = name;
    }

    Map<String, Metric> getMetrics()
    {
        Map<String, Metric> metrics = new HashMap<>();
        metrics.put( name + ".latency", latency );
        metrics.put( name + ".errors", errors );
        metrics.put( name + ".throughput", throughput );

        return metrics;
    }

    public GoldenSignalsFunctionMetrics latency( long duration )
    {
        latency.update( duration, NANOSECONDS );
        return this;
    }

    public GoldenSignalsFunctionMetrics error()
    {
        errors.mark();
        return this;
    }

    public GoldenSignalsFunctionMetrics call()
    {
        throughput.mark();
        return this;
    }

    public HealthCheck getHealthCheck()
    {
        return new GSFunctionHealthCheck();
    }

    final class GSFunctionHealthCheck
            extends HealthCheck
    {
        @Override
        protected Result check()
                throws Exception
        {
            // FIXME: We need need to incorporate the SLO targets to determine whether health / unhealthy.
            return Result.builder()
                         .withDetail( "latency", latency.getSnapshot().get99thPercentile() )
                         .withDetail( "errors", errors.getOneMinuteRate() )
                         .withDetail( "throughput", throughput.getOneMinuteRate() )
                         .healthy()
                         .build();
        }
    }
}
